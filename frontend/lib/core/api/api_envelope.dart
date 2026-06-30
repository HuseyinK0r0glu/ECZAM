/// Parsing for the backend's universal response envelope `{ data, meta, error }`
/// (see CLAUDE.md §5 and docs/api-specification.md). Every endpoint — success
/// or failure — returns this shape, so the whole client funnels through here.
library;

/// Cursor-pagination metadata returned alongside list endpoints.
class Meta {
  final String? nextCursor;
  final int? limit;

  const Meta({this.nextCursor, this.limit});

  factory Meta.fromJson(Map<String, dynamic> json) => Meta(
    nextCursor: json['nextCursor'] as String?,
    limit: (json['limit'] as num?)?.toInt(),
  );
}

/// The `error` member of the envelope. `code` is a stable machine string
/// (e.g. `INSUFFICIENT_STOCK`, `EMAIL_TAKEN`, `VALIDATION_FAILED`); `fields`
/// carries per-field messages on a 422.
class ApiError {
  final String code;
  final String message;
  final Map<String, String> fields;

  const ApiError({
    required this.code,
    required this.message,
    this.fields = const {},
  });

  factory ApiError.fromJson(Map<String, dynamic> json) {
    final rawFields = json['fields'];
    return ApiError(
      code: (json['code'] as String?) ?? 'UNKNOWN',
      message: (json['message'] as String?) ?? 'Something went wrong.',
      fields: rawFields is Map
          ? rawFields.map((k, v) => MapEntry(k.toString(), v.toString()))
          : const {},
    );
  }

  /// Fallback used when the failure never reached the backend (timeout, no
  /// network, malformed body) so the UI can still branch on a code.
  static const ApiError network = ApiError(
    code: 'NETWORK_ERROR',
    message: 'Could not reach the server. Check your connection and try again.',
  );
}

/// Generic envelope holding a parsed `data` payload.
class ApiResponse<T> {
  final T? data;
  final Meta? meta;
  final ApiError? error;

  const ApiResponse({this.data, this.meta, this.error});

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object? data) parseData,
  ) {
    final err = json['error'];
    final rawData = json['data'];
    return ApiResponse(
      data: rawData == null ? null : parseData(rawData),
      meta: json['meta'] is Map
          ? Meta.fromJson(json['meta'] as Map<String, dynamic>)
          : null,
      error: err is Map ? ApiError.fromJson(err as Map<String, dynamic>) : null,
    );
  }
}

/// Thrown for any non-2xx response (or transport failure). The UI catches this
/// to surface field errors and to branch on domain codes such as
/// `INSUFFICIENT_STOCK`, `INVALID_CREDENTIALS`, `EMAIL_TAKEN`, `WEAK_PASSWORD`.
class ApiException implements Exception {
  final ApiError error;
  final int? statusCode;

  const ApiException(this.error, this.statusCode);

  String get code => error.code;
  String get message => error.message;
  Map<String, String> get fields => error.fields;

  bool get isUnauthenticated =>
      statusCode == 401 ||
      code == 'UNAUTHENTICATED' ||
      code == 'TOKEN_INVALID';

  @override
  String toString() => 'ApiException($statusCode $code: ${error.message})';
}
