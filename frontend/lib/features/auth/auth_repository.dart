import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/features/auth/auth_dto.dart';

/// Talks to `/auth/*`. Persists the token pair through [TokenStore] so the API
/// client's interceptors can attach/refresh them.
class AuthRepository {
  final ApiClient api;
  final TokenStore tokenStore;

  AuthRepository({required this.api, required this.tokenStore});

  Future<AuthUser> register({
    required String email,
    required String password,
    String? displayName,
  }) async {
    final result = await api.postJson(
      '/auth/register',
      {
        'email': email,
        'password': password,
        if (displayName != null && displayName.isNotEmpty)
          'displayName': displayName,
      },
      (data) => AuthResult.fromJson((data as Map).cast<String, dynamic>()),
    );
    await _persist(result);
    return result.user;
  }

  Future<AuthUser> login({
    required String email,
    required String password,
  }) async {
    final result = await api.postJson(
      '/auth/login',
      {'email': email, 'password': password},
      (data) => AuthResult.fromJson((data as Map).cast<String, dynamic>()),
    );
    await _persist(result);
    return result.user;
  }

  /// On boot: if a refresh token is stored, exchange it for a fresh pair and
  /// recover the current user. Returns null when there's no valid session.
  Future<AuthUser?> tryRestoreSession() async {
    if (!tokenStore.hasRefreshToken) return null;
    try {
      final result = await api.postJson(
        '/auth/refresh',
        {'refreshToken': tokenStore.refreshToken},
        (data) => AuthResult.fromJson((data as Map).cast<String, dynamic>()),
      );
      await _persist(result);
      return result.user;
    } catch (_) {
      await tokenStore.clear();
      return null;
    }
  }

  Future<void> logout() async {
    final rt = tokenStore.refreshToken;
    try {
      if (rt != null && rt.isNotEmpty) {
        await api.postNoContent('/auth/logout', {'refreshToken': rt});
      }
    } catch (_) {
      // Best-effort server revoke; we clear locally regardless.
    } finally {
      await tokenStore.clear();
    }
  }

  Future<void> _persist(AuthResult result) => tokenStore.save(
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
      );
}
