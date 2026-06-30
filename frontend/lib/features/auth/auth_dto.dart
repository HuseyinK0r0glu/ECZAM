/// DTOs mirroring the backend `AuthController` contract
/// (com.eczam.auth.dto.AuthDtos). Field names match the JSON exactly.
library;

/// Compact user summary embedded in `AuthResponse.user` and close to the full
/// `GET /users/me` profile (which adds `hasPassword`, `hasGoogleLinked`,
/// `notificationPreferences`).
class AuthUser {
  final String id;
  final String email;
  final String? displayName;
  final bool emailVerified;
  final String role;

  const AuthUser({
    required this.id,
    required this.email,
    this.displayName,
    required this.emailVerified,
    required this.role,
  });

  bool get isAdmin => role == 'ADMIN';

  factory AuthUser.fromJson(Map<String, dynamic> json) => AuthUser(
        id: json['id'] as String,
        email: (json['email'] as String?) ?? '',
        displayName: json['displayName'] as String?,
        emailVerified: (json['emailVerified'] as bool?) ?? false,
        role: (json['role'] as String?) ?? 'USER',
      );
}

/// `{ user, accessToken, refreshToken }` returned by register/login/refresh.
class AuthResult {
  final AuthUser user;
  final String accessToken;
  final String refreshToken;

  const AuthResult({
    required this.user,
    required this.accessToken,
    required this.refreshToken,
  });

  factory AuthResult.fromJson(Map<String, dynamic> json) => AuthResult(
        user: AuthUser.fromJson(
          (json['user'] as Map).cast<String, dynamic>(),
        ),
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
      );
}
