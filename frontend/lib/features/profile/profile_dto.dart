/// Mirrors `com.eczam.users.dto.UserDtos.UserProfile` and the embedded
/// `NotificationPreferences` (note the snake_case keys on the prefs object).
library;

class NotificationPreferences {
  final bool push;
  final bool email;
  final int lowStockThreshold;
  final int expiryWarningDays;

  const NotificationPreferences({
    required this.push,
    required this.email,
    required this.lowStockThreshold,
    required this.expiryWarningDays,
  });

  factory NotificationPreferences.fromJson(Map<String, dynamic> j) =>
      NotificationPreferences(
        push: (j['push'] as bool?) ?? true,
        email: (j['email'] as bool?) ?? false,
        lowStockThreshold: (j['low_stock_threshold'] as num?)?.toInt() ?? 7,
        expiryWarningDays: (j['expiry_warning_days'] as num?)?.toInt() ?? 30,
      );

  static const defaults = NotificationPreferences(
    push: true,
    email: false,
    lowStockThreshold: 7,
    expiryWarningDays: 30,
  );
}

class UserProfile {
  final String id;
  final String email;
  final String? displayName;
  final bool emailVerified;
  final String role;
  final bool hasPassword;
  final bool hasGoogleLinked;
  final NotificationPreferences preferences;

  const UserProfile({
    required this.id,
    required this.email,
    this.displayName,
    required this.emailVerified,
    required this.role,
    required this.hasPassword,
    required this.hasGoogleLinked,
    required this.preferences,
  });

  factory UserProfile.fromJson(Map<String, dynamic> j) => UserProfile(
        id: j['id'] as String,
        email: (j['email'] as String?) ?? '',
        displayName: j['displayName'] as String?,
        emailVerified: (j['emailVerified'] as bool?) ?? false,
        role: (j['role'] as String?) ?? 'USER',
        hasPassword: (j['hasPassword'] as bool?) ?? true,
        hasGoogleLinked: (j['hasGoogleLinked'] as bool?) ?? false,
        preferences: j['notificationPreferences'] is Map
            ? NotificationPreferences.fromJson(
                (j['notificationPreferences'] as Map).cast<String, dynamic>())
            : NotificationPreferences.defaults,
      );
}
