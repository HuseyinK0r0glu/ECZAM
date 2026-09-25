import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/features/profile/profile_dto.dart';

/// Authenticated user profile + notification preferences (`/users/me`).
class ProfileRepository {
  final ApiClient api;
  ProfileRepository(this.api);

  Future<UserProfile> me() => api.getOne(
        '/users/me',
        (j) => UserProfile.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<UserProfile> updateDisplayName(String displayName) => api.patchJson(
        '/users/me',
        {'displayName': displayName},
        (j) => UserProfile.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<UserProfile> updatePreferences({
    bool? push,
    bool? email,
    int? lowStockThreshold,
    int? expiryWarningDays,
  }) =>
      api.patchJson(
        '/users/me/preferences',
        {
          'push': ?push,
          'email': ?email,
          'lowStockThreshold': ?lowStockThreshold,
          'expiryWarningDays': ?expiryWarningDays,
        },
        (j) => UserProfile.fromJson((j as Map).cast<String, dynamic>()),
      );
}
