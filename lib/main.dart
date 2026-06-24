import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/data/app_database.dart';
import 'package:medtrack/data/medication_repository.dart';
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

  final notifications = NotificationService();
  await notifications.init();

  final db = await AppDatabase.open();
  final appState = AppState(
    repo: SqliteMedicationRepository(db),
    notifications: notifications,
    photos: PhotoService(),
  );
  await appState.init();

  runApp(MyMedCabinetApp(appState: appState));
}

class MyMedCabinetApp extends StatelessWidget {
  final AppState appState;

  const MyMedCabinetApp({super.key, required this.appState});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: appState,
      child: MaterialApp(
        title: 'My MedCabinet',
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
        home: const HomeShell(),
      ),
    );
  }
}
