import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';

import 'package:medtrack/main.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';
import 'package:medtrack/state/app_state.dart';
import 'package:medtrack/ui/cabinet/action_panel.dart';
import 'package:medtrack/ui/cabinet/cabinet_screen.dart';
import 'package:medtrack/ui/schedule/schedule_screen.dart';

import 'fakes.dart';

// pumpAndSettle never settles because the cabinet LED flicker animation
// repeats forever; tests pump fixed durations instead.
Future<void> pumpFrames(
  WidgetTester tester, [
  Duration total = const Duration(milliseconds: 900),
]) async {
  const step = Duration(milliseconds: 100);
  var elapsed = Duration.zero;
  while (elapsed < total) {
    await tester.pump(step);
    elapsed += step;
  }
}

DoseLog _takenLogAt(DateTime when, String name, int minute) => DoseLog(
  id: 0,
  medId: 1,
  medName: name,
  medDose: '',
  minuteOfDay: minute,
  dateKey: DoseLog.dateKeyFor(when),
  status: DoseStatus.taken,
  scheduledAt: DoseLog.scheduledAtFor(when, minute),
  loggedAt: when,
);

void main() {
  // Widget tests use the in-memory FakeMedicationRepository, not sqflite —
  // real FFI I/O deadlocks inside the widget-test fake-async zone.
  Future<AppState> buildState(WidgetTester tester) async {
    final state = AppState(
      repo: FakeMedicationRepository(),
      notifications: FakeNotificationService(),
      photos: FakePhotoService(),
    );
    await state.init();
    return state;
  }

  testWidgets('add flow with a picked time places med and schedules it', (
    tester,
  ) async {
    final state = await buildState(tester);
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    expect(find.text('My MedCabinet'), findsOneWidget);
    expect(find.textContaining('Your cabinet is empty'), findsOneWidget);

    // Open the add sheet via the brass + button.
    await tester.tap(find.text('+'));
    await pumpFrames(tester);
    expect(find.text('Add medication'), findsOneWidget);

    // Step 1: name + dose.
    await tester.enterText(
      find.widgetWithText(TextField, 'Medication name'),
      'Ibuprofen',
    );
    await tester.enterText(
      find.widgetWithText(TextField, 'Dosage — e.g. 400 mg'),
      '400 mg',
    );
    await tester.pump();
    await tester.tap(find.text('Next'));
    await pumpFrames(tester, const Duration(milliseconds: 300));

    // Step 2: container kind grid.
    expect(find.textContaining('Pick the container'), findsOneWidget);
    await tester.tap(find.text('Next'));
    await pumpFrames(tester, const Duration(milliseconds: 300));

    // Step 3: precise time picker (replaces the old fixed slots).
    expect(
      find.textContaining('Pick the exact reminder times'),
      findsOneWidget,
    );
    await tester.tap(find.text('Add time'));
    await pumpFrames(tester, const Duration(milliseconds: 500));
    // The native time picker dialog is up; accept the default time.
    expect(find.text('OK'), findsOneWidget);
    await tester.tap(find.text('OK'));
    await pumpFrames(tester, const Duration(milliseconds: 500));

    // A time chip (with a remove affordance) is now shown.
    expect(find.byIcon(Icons.close), findsOneWidget);

    await tester.tap(find.text('Place in cabinet'));
    await pumpFrames(tester);

    // The med is a cabinet object, and the exact time was scheduled.
    expect(find.text('Ibuprofen'), findsOneWidget);
    expect(find.textContaining('Your cabinet is empty'), findsNothing);
    final med = state.meds.single;
    expect(med.reminderMinutes, hasLength(1));
    final fake = state.notifications as FakeNotificationService;
    expect(fake.scheduledMinutes[med.id], med.reminderMinutes);
  });

  testWidgets('schedule row shows the exact time and toggles to taken', (
    tester,
  ) async {
    final state = await buildState(tester);
    await state.addMedication(
      name: 'Amoxicillin',
      dose: '500 mg',
      kind: MedKind.white,
      reminderMinutes: [8 * 60], // 08:00
    );
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    // Switch to Today's Schedule via the clock nav button.
    await tester.tap(find.byIcon(Icons.access_time));
    await pumpFrames(tester, const Duration(milliseconds: 400));
    expect(find.text("Today's Schedule"), findsOneWidget);
    expect(find.text('MORNING'), findsOneWidget);
    // The exact time is rendered on the row.
    expect(find.text('8:00 AM'), findsOneWidget);

    // The cabinet stays built behind the IndexedStack, so the name appears
    // twice; scope the tap to the schedule's dose row.
    final scheduleRow = find.descendant(
      of: find.byType(ScheduleScreen),
      matching: find.text('Amoxicillin'),
    );
    expect(scheduleRow, findsOneWidget);
    await tester.tap(scheduleRow);
    await pumpFrames(tester, const Duration(milliseconds: 400));

    expect(find.text('✓'), findsOneWidget);
    expect(state.todayLogs, hasLength(1));
  });

  testWidgets('back arrow returns from a sub-screen to the cabinet', (
    tester,
  ) async {
    final state = await buildState(tester);
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    // Go to History; the home title is gone, a back arrow appears.
    await tester.tap(find.byIcon(Icons.calendar_today_outlined));
    await pumpFrames(tester, const Duration(milliseconds: 400));
    expect(find.text('History'), findsOneWidget);
    expect(find.byIcon(Icons.arrow_back), findsOneWidget);

    // Tapping back returns to the cabinet home.
    await tester.tap(find.byIcon(Icons.arrow_back));
    await pumpFrames(tester, const Duration(milliseconds: 400));
    expect(find.text('My MedCabinet'), findsOneWidget);
    expect(find.byIcon(Icons.arrow_back), findsNothing);
  });

  testWidgets('first system back press on Home warns instead of exiting', (
    tester,
  ) async {
    final state = await buildState(tester);
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    expect(find.text('My MedCabinet'), findsOneWidget);

    // Simulate the Android hardware back button.
    await tester.binding.handlePopRoute();
    await pumpFrames(tester, const Duration(milliseconds: 300));

    // Still on Home, now showing the exit warning.
    expect(find.text('My MedCabinet'), findsOneWidget);
    expect(find.text('Press back again to exit'), findsOneWidget);
  });

  testWidgets('opening the History tab purges stale logs in the background', (
    tester,
  ) async {
    final repo = FakeMedicationRepository();
    final state = AppState(
      repo: repo,
      notifications: FakeNotificationService(),
      photos: FakePhotoService(),
    );
    await state.init();
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    // A stale log slips in after launch (e.g. the app stayed open for weeks),
    // so the launch-time cleanup already ran without catching it.
    await repo.upsertLog(
      DoseLog(
        id: 0,
        medId: 1,
        medName: 'Ibuprofen',
        medDose: '400 mg',
        minuteOfDay: 8 * 60,
        dateKey: '2026-05-01',
        status: DoseStatus.taken,
        scheduledAt: DateTime(2026, 5, 1, 8, 0),
        loggedAt: DateTime.now().subtract(const Duration(days: 30)),
      ),
    );
    expect(await repo.recentLogs(), hasLength(1));

    // Navigating to History triggers the silent background cleanup.
    await tester.tap(find.byIcon(Icons.calendar_today_outlined));
    await pumpFrames(tester, const Duration(milliseconds: 400));

    expect(await repo.recentLogs(), isEmpty);
  });

  testWidgets('tall cabinet scrolls vertically and a low shelf bottle selects', (
    tester,
  ) async {
    final state = await buildState(tester);
    // 8 pill bottles -> two pill shelves, plus empty jar & syrup shelves: the
    // cabinet is taller than the viewport and must scroll, not shrink.
    for (var i = 0; i < 8; i++) {
      await state.addMedication(
        name: 'Pill $i',
        dose: '${i}0 mg',
        kind: MedKind.amber,
        reminderMinutes: [8 * 60],
      );
    }
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    final scrollable = tester.state<ScrollableState>(
      find
          .descendant(
            of: find.byType(CabinetScreen),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(scrollable.position.axis, Axis.vertical);
    expect(scrollable.position.maxScrollExtent, greaterThan(0));

    // Selecting a bottle on the second pill shelf still opens its action panel.
    await tester.tap(find.text('Pill 7'));
    await pumpFrames(tester, const Duration(milliseconds: 600));
    expect(find.byType(ActionPanel), findsOneWidget);
  });

  testWidgets('the cabinet button returns to My MedCabinet from a sub-screen', (
    tester,
  ) async {
    final state = await buildState(tester);
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    await tester.tap(find.byIcon(Icons.calendar_today_outlined));
    await pumpFrames(tester, const Duration(milliseconds: 400));
    expect(find.text('History'), findsOneWidget);

    // The top-right cabinet button jumps straight back to the home cabinet.
    await tester.tap(find.byIcon(Icons.local_pharmacy));
    await pumpFrames(tester, const Duration(milliseconds: 400));
    expect(find.text('My MedCabinet'), findsOneWidget);
  });

  testWidgets('History date strip filters logs to the tapped day', (
    tester,
  ) async {
    final state = await buildState(tester);
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day, 9);
    final yesterday = today.subtract(const Duration(days: 1));
    final repo = state.repo as FakeMedicationRepository;
    await repo.upsertLog(_takenLogAt(today, 'Aspirin', 8 * 60));
    await repo.upsertLog(_takenLogAt(yesterday, 'Vitamin D', 9 * 60));
    await state.refresh();

    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);
    await tester.tap(find.byIcon(Icons.calendar_today_outlined));
    await pumpFrames(tester, const Duration(milliseconds: 400));

    // Defaults to today: only today's log is listed.
    expect(find.text('Aspirin'), findsOneWidget);
    expect(find.text('Vitamin D'), findsNothing);

    // Tapping yesterday's day card swaps the list to that day's log.
    final yLabel = DateFormat('E').format(yesterday).toUpperCase();
    await tester.tap(find.text(yLabel));
    await pumpFrames(tester, const Duration(milliseconds: 300));
    expect(find.text('Vitamin D'), findsOneWidget);
    expect(find.text('Aspirin'), findsNothing);
  });

  testWidgets('horizontal swipe toggles the cabinet grouping mode', (
    tester,
  ) async {
    final state = await buildState(tester);
    await state.addMedication(
      name: 'Aspirin',
      dose: '100 mg',
      kind: MedKind.amber,
      reminderMinutes: [8 * 60],
    );
    await tester.pumpWidget(MedTrackApp(appState: state));
    await pumpFrames(tester);

    final cabinetScroll = find.descendant(
      of: find.byType(CabinetScreen),
      matching: find.byType(SingleChildScrollView),
    );

    // Starts in "By type": shelf labels are category names.
    expect(find.text('PILL BOTTLES'), findsOneWidget);
    expect(find.text('MORNING'), findsNothing);

    // Swipe left -> "By time of day".
    await tester.fling(cabinetScroll, const Offset(-300, 0), 1000);
    await pumpFrames(tester, const Duration(milliseconds: 900));
    expect(find.text('MORNING'), findsOneWidget);
    expect(find.text('PILL BOTTLES'), findsNothing);

    // Swipe right -> back to "By type".
    await tester.fling(cabinetScroll, const Offset(300, 0), 1000);
    await pumpFrames(tester, const Duration(milliseconds: 900));
    expect(find.text('PILL BOTTLES'), findsOneWidget);
  });
}
