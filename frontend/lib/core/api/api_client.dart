import 'package:dio/dio.dart';

import 'package:medtrack/core/api/api_envelope.dart';
import 'package:medtrack/core/config/env.dart';
import 'package:medtrack/core/token_store.dart';

/// Thin wrapper around Dio that:
///  * attaches `Authorization: Bearer <access>` to every request,
///  * transparently refreshes the access token on a `401` and retries once,
///  * unwraps the `{ data, meta, error }` envelope into typed values,
///  * converts every non-2xx / transport failure into an [ApiException].
///
/// Repositories depend on this, not on Dio directly — except the AI assistant,
/// which streams SSE via [dio] with `ResponseType.stream`.
class ApiClient {
  final Dio dio;
  final TokenStore tokenStore;

  /// Bare Dio used only for `POST /auth/refresh`, so the refresh call itself is
  /// never intercepted (which would recurse).
  final Dio _refreshDio;

  /// Invoked when refresh fails — the app should drop to the login screen.
  void Function()? onSessionExpired;

  Future<bool>? _refreshing;

  ApiClient({
    required this.tokenStore,
    Dio? dio,
    String? baseUrl,
  })  : dio = dio ?? Dio(_baseOptions(baseUrl ?? apiBaseUrl)),
        _refreshDio = Dio(_baseOptions(baseUrl ?? apiBaseUrl)) {
    this.dio.interceptors.add(
          QueuedInterceptorsWrapper(
            onRequest: _onRequest,
            onError: _onError,
          ),
        );
  }

  static BaseOptions _baseOptions(String baseUrl) => BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 15),
        receiveTimeout: const Duration(seconds: 30),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
      );

  // ── Interceptors ──────────────────────────────────────────────────────────

  void _onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = tokenStore.accessToken;
    if (token != null && token.isNotEmpty && !_isAuthFreeEndpoint(options.path)) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  Future<void> _onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final response = err.response;
    final isAuthChallenge = response?.statusCode == 401;
    final alreadyRetried = err.requestOptions.extra['__retried'] == true;
    final refreshable = isAuthChallenge &&
        !alreadyRetried &&
        !err.requestOptions.path.contains('/auth/refresh') &&
        tokenStore.hasRefreshToken;

    if (!refreshable) {
      handler.next(err);
      return;
    }

    final ok = await _refreshOnce();
    if (!ok) {
      await tokenStore.clear();
      onSessionExpired?.call();
      handler.next(err);
      return;
    }

    try {
      final opts = err.requestOptions;
      opts.extra['__retried'] = true;
      opts.headers['Authorization'] = 'Bearer ${tokenStore.accessToken}';
      final cloned = await dio.fetch<dynamic>(opts);
      handler.resolve(cloned);
    } on DioException catch (retryErr) {
      handler.next(retryErr);
    }
  }

  /// Serializes concurrent refresh attempts so a burst of 401s triggers a
  /// single `/auth/refresh` round-trip.
  Future<bool> _refreshOnce() {
    return _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  }

  Future<bool> _doRefresh() async {
    final rt = tokenStore.refreshToken;
    if (rt == null || rt.isEmpty) return false;
    try {
      final res = await _refreshDio.post<dynamic>(
        '/auth/refresh',
        data: {'refreshToken': rt},
      );
      final body = res.data;
      final data = body is Map ? body['data'] as Map<String, dynamic>? : null;
      final access = data?['accessToken'] as String?;
      final newRefresh = data?['refreshToken'] as String?;
      if (access == null || newRefresh == null) return false;
      await tokenStore.save(accessToken: access, refreshToken: newRefresh);
      return true;
    } on DioException {
      return false;
    }
  }

  bool _isAuthFreeEndpoint(String path) =>
      path.endsWith('/auth/login') ||
      path.endsWith('/auth/register') ||
      path.endsWith('/auth/refresh') ||
      path.endsWith('/auth/google');

  // ── Typed envelope helpers ────────────────────────────────────────────────

  Future<T> getOne<T>(
    String path,
    T Function(Object? data) parse, {
    Map<String, dynamic>? query,
  }) async {
    final res = await _send(() => dio.get<dynamic>(path, queryParameters: query));
    return _unwrap(res, parse);
  }

  Future<(List<T>, Meta?)> getList<T>(
    String path,
    T Function(Object? item) parseItem, {
    Map<String, dynamic>? query,
  }) async {
    final res = await _send(() => dio.get<dynamic>(path, queryParameters: query));
    final env = ApiResponse<List<dynamic>>.fromJson(
      _asMap(res.data),
      (data) => (data as List).toList(),
    );
    _throwIfError(env, res.statusCode);
    final items = (env.data ?? const []).map(parseItem).toList();
    return (items, env.meta);
  }

  Future<T> postJson<T>(
    String path,
    Object? body,
    T Function(Object? data) parse,
  ) async {
    final res = await _send(() => dio.post<dynamic>(path, data: body));
    return _unwrap(res, parse);
  }

  Future<T> patchJson<T>(
    String path,
    Object? body,
    T Function(Object? data) parse,
  ) async {
    final res = await _send(() => dio.patch<dynamic>(path, data: body));
    return _unwrap(res, parse);
  }

  /// For 204 endpoints (logout, delete, change-email …). Throws on error.
  Future<void> postNoContent(String path, [Object? body]) async {
    await _send(() => dio.post<dynamic>(path, data: body));
  }

  Future<void> deleteOne(String path, {Map<String, dynamic>? query}) async {
    await _send(() => dio.delete<dynamic>(path, queryParameters: query));
  }

  // ── Plumbing ──────────────────────────────────────────────────────────────

  Future<Response<dynamic>> _send(
    Future<Response<dynamic>> Function() call,
  ) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  T _unwrap<T>(Response<dynamic> res, T Function(Object? data) parse) {
    final env = ApiResponse<T>.fromJson(_asMap(res.data), parse);
    _throwIfError(env, res.statusCode);
    return env.data as T;
  }

  void _throwIfError(ApiResponse<dynamic> env, int? status) {
    if (env.error != null) throw ApiException(env.error!, status);
  }

  Map<String, dynamic> _asMap(Object? data) {
    if (data is Map<String, dynamic>) return data;
    if (data is Map) return data.cast<String, dynamic>();
    return const {};
  }

  /// Converts a Dio failure into an [ApiException], reading the backend's
  /// `error` envelope when present, otherwise a generic network error.
  static ApiException toApiException(DioException e) {
    final data = e.response?.data;
    final status = e.response?.statusCode;
    if (data is Map && data['error'] is Map) {
      return ApiException(
        ApiError.fromJson((data['error'] as Map).cast<String, dynamic>()),
        status,
      );
    }
    return ApiException(ApiError.network, status);
  }
}
