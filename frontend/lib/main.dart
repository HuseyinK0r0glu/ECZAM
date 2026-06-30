import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/core/sync/connectivity.dart';
import 'package:medtrack/core/token_store.dart';
import 'package:medtrack/data/app_database.dart';
import 'package:medtrack/data/backend_medication_repository.dart';
import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/features/ai/ai_repository.dart';
import 'package:medtrack/features/auth/auth_repository.dart';
import 'package:medtrack/features/auth/auth_state.dart';
import 'package:medtrack/features/auth/login_screen.dart';
import 'package:medtrack/features/expiration/expiration_repository.dart';
import 'package:medtrack/features/inventory/inventory_repository.dart';
import 'package:medtrack/features/logs/log_repository.dart';
import 'package:medtrack/features/medications/medication_repository.dart';
import 'package:medtrack/features/profile/profile_repository.dart';
import 'package:medtrack/features/schedules/schedule_repository.dart';
import 'package:medtrack/services/notification_service.dart';
import 'package:medtrack/services/photo_service.dart';
import 'package:medtrack/state/app_state.dart';
import 'package:medtrack/theme/med_theme.dart';
import 'package:medtrack/ui/home_shell.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Lock to portrait — the cabinet layout is designed for a tall screen.
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  // ── Networking + auth ──
  final tokenStore = TokenStore();
  await tokenStore.load();
  final apiClient = ApiClient(tokenStore: tokenStore);
  final authRepo = AuthRepository(api: apiClient, tokenStore: tokenStore);

  // ── Local cache + connectivity ──
  final db = await AppDatabase.open();
  final mirror = SqliteMedicationRepository(db);
  final connectivity = ConnectivityService();
  await connectivity.init();

  // ── Feature repositories (backend) ──
  final catalog = CatalogRepository(apiClient);
  final inventory = InventoryRepository(apiClient);
  final scheduleRepo = ScheduleRepository(apiClient);
  final logRepo = LogRepository(apiClient);
  final expirationRepo = ExpirationRepository(apiClient);
  final profileRepo = ProfileRepository(apiClient);
  final aiRepo = AiRepository(apiClient);

  final backendRepo = BackendMedicationRepository(
    catalog: catalog,
    inventory: inventory,
    schedules: scheduleRepo,
    logs: logRepo,
    mirror: mirror,
    connectivity: connectivity,
  );

  // ── Platform services ──
  final notifications = NotificationService();
  await notifications.init();

  final appState = AppState(
    repo: backendRepo,
    notifications: notifications,
    photos: PhotoService(),
    connectivity: connectivity,
    syncDrain: backendRepo.drainOutbox,
    wipeLocal: mirror.wipe,
  );

  final authState = AuthState(
    repo: authRepo,
    onAuthenticated: appState.init,
    onSignedOut: appState.signOutCleanup,
  );
  // Mid-session refresh failure → bounce to login.
  apiClient.onSessionExpired = authState.onSessionExpired;

  // Try a silent token refresh before deciding which screen to show.
  await authState.bootstrap();

  runApp(EczamApp(
    appState: appState,
    authState: authState,
    catalog: catalog,
    expiration: expirationRepo,
    profile: profileRepo,
    ai: aiRepo,
  ));
}

/// Root widget. In production [authState] gates the app; widget tests construct
/// it with just an [appState] (already `init()`-ed) and land straight on the
/// shell.
class EczamApp extends StatelessWidget {
  final AppState appState;
  final AuthState? authState;
  final CatalogRepository? catalog;
  final ExpirationRepository? expiration;
  final ProfileRepository? profile;
  final AiRepository? ai;

  const EczamApp({
    super.key,
    required this.appState,
    this.authState,
    this.catalog,
    this.expiration,
    this.profile,
    this.ai,
  });

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: appState),
        if (authState != null)
          ChangeNotifierProvider.value(value: authState!),
        if (catalog != null) Provider.value(value: catalog!),
        if (expiration != null) Provider.value(value: expiration!),
        if (profile != null) Provider.value(value: profile!),
        if (ai != null) Provider.value(value: ai!),
      ],
      child: MaterialApp(
        title: 'ECZAM',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(
            seedColor: MedColors.teal,
            surface: MedColors.bgTop,
          ),
          scaffoldBackgroundColor: MedColors.bgMid,
          splashFactory: NoSplash.splashFactory,
        ),
        home: authState == null ? const HomeShell() : const _AuthGate(),
      ),
    );
  }
}

/// Shows the login screen until authenticated, then the app shell. Tests bypass
/// this by not supplying an [AuthState].
class _AuthGate extends StatelessWidget {
  const _AuthGate();

  @override
  Widget build(BuildContext context) {
    final status = context.watch<AuthState>().status;
    return switch (status) {
      AuthStatus.unknown => const _Splash(),
      AuthStatus.unauthenticated => const LoginScreen(),
      AuthStatus.authenticated => const HomeShell(),
    };
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) => const Scaffold(
        backgroundColor: MedColors.bgMid,
        body: Center(
          child: Icon(Icons.local_pharmacy, size: 48, color: MedColors.teal),
        ),
      );
}

/// Backwards-compatible alias used by widget tests.
class MedTrackApp extends EczamApp {
  const MedTrackApp({super.key, required super.appState});
}
