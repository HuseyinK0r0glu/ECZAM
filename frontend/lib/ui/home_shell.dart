import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/state/app_state.dart';
import 'package:medtrack/theme/med_theme.dart';
import 'package:medtrack/ui/add_med/add_med_sheet.dart';
import 'package:medtrack/ui/ai/ai_assistant_screen.dart';
import 'package:medtrack/ui/cabinet/cabinet_screen.dart';
import 'package:medtrack/ui/expiration/expiration_screen.dart';
import 'package:medtrack/ui/history/history_screen.dart';
import 'package:medtrack/ui/profile/profile_screen.dart';
import 'package:medtrack/ui/schedule/schedule_screen.dart';

enum _Screen { home, schedule, history }

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> with WidgetsBindingObserver {
  _Screen _screen = _Screen.home;

  /// Two back presses within this window exit the app from Home.
  static const _exitWindow = Duration(seconds: 2);
  DateTime? _lastBackPress;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _routeFromLaunch();
    // First run: surface the notification permission prompt right away so
    // reminders work without the user hunting for a setting.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<AppState>().notifications.requestPermissions();
    });
  }

  Future<void> _routeFromLaunch() async {
    final app = context.read<AppState>();
    if (await app.notifications.launchedFromNotification() && mounted) {
      setState(() => _screen = _Screen.schedule);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Catch dose logs made from notification actions while backgrounded.
    if (state == AppLifecycleState.resumed) {
      context.read<AppState>().refresh();
    }
  }

  void _toggle(_Screen target) {
    final next = _screen == target ? _Screen.home : target;
    setState(() => _screen = next);
    // Opening History prunes stale logs in the background so a long-running
    // session still trims the database without a relaunch. Fire-and-forget so
    // navigation stays instant.
    if (next == _Screen.history) {
      unawaited(context.read<AppState>().cleanupOldLogs());
    }
  }

  String get _title => switch (_screen) {
    _Screen.home => 'My MedCabinet',
    _Screen.schedule => "Today's Schedule",
    _Screen.history => 'History',
  };

  void _goHome() => setState(() => _screen = _Screen.home);

  /// Secondary destinations that aren't part of the main cabinet/schedule/
  /// history loop: the leaflet assistant, expiry monitor and profile.
  void _openMenu(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: const Color(0xF7FAF7F1),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(26)),
      ),
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 12),
            ListTile(
              leading: const Icon(Icons.auto_awesome, color: MedColors.teal),
              title: const Text('Leaflet assistant'),
              subtitle: const Text('Ask about side effects, dosage, storage'),
              onTap: () {
                Navigator.pop(sheetContext);
                Navigator.of(context).push(MaterialPageRoute<void>(
                  builder: (_) => const AiAssistantScreen(),
                ));
              },
            ),
            ListTile(
              leading: const Icon(Icons.schedule, color: MedColors.late),
              title: const Text('Expiration'),
              subtitle: const Text('Expiring soon & expired stock'),
              onTap: () {
                Navigator.pop(sheetContext);
                Navigator.of(context).push(MaterialPageRoute<void>(
                  builder: (_) => const ExpirationScreen(),
                ));
              },
            ),
            ListTile(
              leading: const Icon(Icons.person_outline,
                  color: MedColors.textSoft),
              title: const Text('Profile & settings'),
              onTap: () {
                Navigator.pop(sheetContext);
                Navigator.of(context).push(MaterialPageRoute<void>(
                  builder: (_) => const ProfileScreen(),
                ));
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  /// Double-press-to-exit on Home: the first back press warns; a second press
  /// within [_exitWindow] closes the app.
  void _handleExitPress() {
    final now = DateTime.now();
    final last = _lastBackPress;
    if (last != null && now.difference(last) < _exitWindow) {
      SystemNavigator.pop();
      return;
    }
    _lastBackPress = now;
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: const Text(
            'Press back again to exit',
            style: TextStyle(color: MedColors.frameTop),
          ),
          duration: _exitWindow,
          behavior: SnackBarBehavior.floating,
          backgroundColor: MedColors.text,
          elevation: 6,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          margin: const EdgeInsets.fromLTRB(20, 0, 20, 110),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    final topPad = MediaQuery.paddingOf(context).top;
    final bottomPad = MediaQuery.paddingOf(context).bottom;
    final onHome = _screen == _Screen.home;

    return PopScope(
      // Back never pops the route directly: on a sub-screen it returns Home;
      // on Home it requires a second press within [_exitWindow] to exit.
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (onHome) {
          _handleExitPress();
        } else {
          _goHome();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [MedColors.bgTop, MedColors.bgMid, MedColors.bgBottom],
              stops: [0, 0.55, 1],
            ),
          ),
          child: Stack(
            children: [
              // Screens. IndexedStack keeps cabinet state alive across tabs.
              Positioned.fill(
                child: IndexedStack(
                  index: _screen.index,
                  children: [
                    CabinetScreen(
                      onEditMed: (med) =>
                          showAddMedSheet(context, editing: med),
                    ),
                    const ScheduleScreen(),
                    const HistoryScreen(),
                  ],
                ),
              ),
              // Top bar.
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: Container(
                  padding: EdgeInsets.fromLTRB(20, topPad + 10, 20, 14),
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Color(0xF2EAE4DA), Color(0x00EAE4DA)],
                      stops: [0.55, 1],
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Row(
                          children: [
                            if (!onHome) ...[
                              _BackButton(onTap: _goHome),
                              const SizedBox(width: 10),
                            ],
                            Flexible(
                              child: AnimatedSwitcher(
                                duration: const Duration(milliseconds: 250),
                                child: Text(
                                  _title,
                                  key: ValueKey(_title),
                                  style: MedText.screenTitle,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Semantics(
                        button: true,
                        label: 'More',
                        child: GestureDetector(
                          onTap: () => _openMenu(context),
                          child: Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: const Color(0x8CFFFFFF),
                              boxShadow: const [
                                BoxShadow(
                                  color: Color(0x24403626),
                                  offset: Offset(0, 2),
                                  blurRadius: 8,
                                ),
                              ],
                            ),
                            child: const Icon(
                              Icons.menu,
                              size: 20,
                              color: MedColors.textSoft,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Semantics(
                        button: true,
                        label: 'Return to cabinet',
                        child: GestureDetector(
                          onTap: _goHome,
                          child: Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: const Color(0x8CFFFFFF),
                              boxShadow: const [
                                BoxShadow(
                                  color: Color(0x24403626),
                                  offset: Offset(0, 2),
                                  blurRadius: 8,
                                ),
                              ],
                            ),
                            child: const Icon(
                              Icons.local_pharmacy,
                              size: 20,
                              color: MedColors.textSoft,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              // Bottom nav.
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: Container(
                  padding: EdgeInsets.fromLTRB(
                    0,
                    18,
                    0,
                    bottomPad > 0 ? bottomPad + 10 : 28,
                  ),
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.bottomCenter,
                      end: Alignment.topCenter,
                      colors: [Color(0xE6CABEAA), Color(0x00CABEAA)],
                      stops: [0.3, 1],
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _NavCircle(
                        icon: Icons.calendar_today_outlined,
                        active: _screen == _Screen.history,
                        onTap: () => _toggle(_Screen.history),
                      ),
                      const SizedBox(width: 30),
                      _BrassAddButton(onTap: () => showAddMedSheet(context)),
                      const SizedBox(width: 30),
                      _NavCircle(
                        icon: Icons.access_time,
                        active: _screen == _Screen.schedule,
                        onTap: () => _toggle(_Screen.schedule),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

}

/// Back affordance shown in the top bar on History / Schedule, styled to
/// match the round glass controls. Returns to the cabinet home.
class _BackButton extends StatelessWidget {
  final VoidCallback onTap;

  const _BackButton({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: 'Back to cabinet',
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: const Color(0x8CFFFFFF),
            boxShadow: const [
              BoxShadow(
                color: Color(0x24403626),
                offset: Offset(0, 2),
                blurRadius: 8,
              ),
            ],
          ),
          child: const Icon(
            Icons.arrow_back,
            size: 20,
            color: MedColors.textSoft,
          ),
        ),
      ),
    );
  }
}

class _NavCircle extends StatelessWidget {
  final IconData icon;
  final bool active;
  final VoidCallback onTap;

  const _NavCircle({
    required this.icon,
    required this.active,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipOval(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 250),
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: active ? const Color(0xD9B2E8E4) : const Color(0x80FFFFFF),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x38403626),
                  offset: Offset(0, 4),
                  blurRadius: 14,
                ),
              ],
            ),
            child: Icon(icon, size: 21, color: MedColors.textSoft),
          ),
        ),
      ),
    );
  }
}

class _BrassAddButton extends StatefulWidget {
  final VoidCallback onTap;

  const _BrassAddButton({required this.onTap});

  @override
  State<_BrassAddButton> createState() => _BrassAddButtonState();
}

class _BrassAddButtonState extends State<_BrassAddButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) {
        setState(() => _pressed = false);
        widget.onTap();
      },
      child: AnimatedScale(
        duration: const Duration(milliseconds: 150),
        scale: _pressed ? 0.96 : 1,
        child: Container(
          width: 74,
          height: 74,
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(
              center: Alignment(-0.32, -0.44),
              radius: 1.1,
              colors: [
                MedColors.brassLight,
                MedColors.brassMid,
                MedColors.brassDark,
                MedColors.brassDeep,
              ],
              stops: [0, 0.38, 0.66, 1],
            ),
            boxShadow: [
              BoxShadow(
                color: Color(0x8C6E5014),
                offset: Offset(0, 10),
                blurRadius: 24,
                spreadRadius: -4,
              ),
              BoxShadow(
                color: Color(0x4D6E5014),
                offset: Offset(0, 3),
                blurRadius: 6,
              ),
            ],
          ),
          child: const Center(
            child: Padding(
              padding: EdgeInsets.only(bottom: 3),
              child: Text(
                '+',
                style: TextStyle(
                  fontSize: 38,
                  fontWeight: FontWeight.w300,
                  color: MedColors.brassText,
                  height: 1,
                  shadows: [
                    Shadow(color: Color(0x995A3C0A), offset: Offset(0, -1)),
                    Shadow(color: Color(0x80FFFAE6), offset: Offset(0, 1)),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
