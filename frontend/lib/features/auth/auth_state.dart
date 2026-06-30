import 'package:flutter/foundation.dart';

import 'package:medtrack/core/api/api_envelope.dart';
import 'package:medtrack/features/auth/auth_dto.dart';
import 'package:medtrack/features/auth/auth_repository.dart';

enum AuthStatus { unknown, authenticated, unauthenticated }

/// Top-of-tree auth model (Provider `ChangeNotifier`, matching the app's
/// existing pattern). `main.dart` shows the auth screens while
/// [status] is `unauthenticated` and the app shell once `authenticated`.
class AuthState extends ChangeNotifier {
  final AuthRepository repo;

  /// Fired right after a successful sign-in/restore so the data layer can load
  /// the freshly-authenticated user's medications, and right before sign-out so
  /// it can clear cached state.
  final Future<void> Function()? onAuthenticated;
  final Future<void> Function()? onSignedOut;

  AuthState({required this.repo, this.onAuthenticated, this.onSignedOut});

  AuthStatus status = AuthStatus.unknown;
  AuthUser? user;
  bool busy = false;

  bool get isAuthenticated => status == AuthStatus.authenticated;

  /// Boot path: attempt a silent token refresh before deciding which UI to show.
  Future<void> bootstrap() async {
    final restored = await repo.tryRestoreSession();
    if (restored != null) {
      user = restored;
      status = AuthStatus.authenticated;
      await onAuthenticated?.call();
    } else {
      status = AuthStatus.unauthenticated;
    }
    notifyListeners();
  }

  Future<void> register({
    required String email,
    required String password,
    String? displayName,
  }) =>
      _run(() => repo.register(
            email: email,
            password: password,
            displayName: displayName,
          ));

  Future<void> login({required String email, required String password}) =>
      _run(() => repo.login(email: email, password: password));

  Future<void> logout() async {
    await onSignedOut?.call();
    await repo.logout();
    user = null;
    status = AuthStatus.unauthenticated;
    notifyListeners();
  }

  /// Called by the API client when a refresh ultimately fails mid-session.
  void onSessionExpired() {
    if (status == AuthStatus.unauthenticated) return;
    user = null;
    status = AuthStatus.unauthenticated;
    notifyListeners();
  }

  Future<void> _run(Future<AuthUser> Function() action) async {
    busy = true;
    notifyListeners();
    try {
      user = await action();
      status = AuthStatus.authenticated;
      await onAuthenticated?.call();
    } finally {
      busy = false;
      notifyListeners();
    }
  }
}

/// Maps an [ApiException] into something showable on the auth forms: a top-level
/// message plus any per-field 422 messages.
({String message, Map<String, String> fields}) describeAuthError(Object error) {
  if (error is ApiException) {
    final msg = switch (error.code) {
      'INVALID_CREDENTIALS' => 'Email or password is incorrect.',
      'EMAIL_TAKEN' => 'That email is already registered.',
      'WEAK_PASSWORD' =>
        'Password must be at least 8 characters with upper, lower, a digit and a symbol.',
      'ACCOUNT_LOCKED' =>
        'Too many attempts. The account is locked for a few minutes.',
      'RATE_LIMITED' => 'Too many requests. Please wait a moment.',
      _ => error.message,
    };
    return (message: msg, fields: error.fields);
  }
  return (message: 'Something went wrong. Please try again.', fields: const {});
}
