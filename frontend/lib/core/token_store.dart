import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Secure persistence for the JWT access token and the opaque refresh token.
/// Backed by the platform keystore/keychain (Android EncryptedSharedPreferences,
/// iOS Keychain) so tokens survive restarts but never touch plain storage.
class TokenStore {
  static const _kAccess = 'eczam.accessToken';
  static const _kRefresh = 'eczam.refreshToken';

  final FlutterSecureStorage _storage;

  TokenStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
            );

  // Cached in memory so the auth interceptor can attach the header without an
  // async keystore read on every request.
  String? _accessToken;
  String? _refreshToken;

  String? get accessToken => _accessToken;
  String? get refreshToken => _refreshToken;
  bool get hasRefreshToken => (_refreshToken ?? '').isNotEmpty;

  /// Loads tokens from secure storage into memory. Call once on boot.
  Future<void> load() async {
    _accessToken = await _storage.read(key: _kAccess);
    _refreshToken = await _storage.read(key: _kRefresh);
  }

  Future<void> save({
    required String accessToken,
    required String refreshToken,
  }) async {
    _accessToken = accessToken;
    _refreshToken = refreshToken;
    await _storage.write(key: _kAccess, value: accessToken);
    await _storage.write(key: _kRefresh, value: refreshToken);
  }

  Future<void> clear() async {
    _accessToken = null;
    _refreshToken = null;
    await _storage.delete(key: _kAccess);
    await _storage.delete(key: _kRefresh);
  }
}
