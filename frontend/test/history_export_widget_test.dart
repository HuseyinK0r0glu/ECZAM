import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/features/logs/log_dto.dart';
import 'package:medtrack/main.dart';
import 'package:medtrack/state/app_state.dart';

import 'fakes.dart';

// pumpAndSettle never settles because the cabinet LED flicker animation
// repeats forever; tests pump fixed durations instead (mirrors widget_test.dart).
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

void main() {
  // The "Copy to clipboard" button calls the real `Clipboard.setData` platform
  // channel method. In a widget test there's no platform side to answer it, so
  // without a mock handler the call never resolves (it neither completes nor
  // throws) and the test hangs — register one so `SystemChannels.platform`
  // acks `Clipboard.setData`, mirroring how the fakes above stand in for other
  // platform channels (notifications, photos) in this test suite.
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') return null;
      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });

  testWidgets(
    'the History export action fetches history, previews CSV, and copies it',
    (tester) async {
      final state = AppState(
        repo: FakeMedicationRepository(),
        notifications: FakeNotificationService(),
        photos: FakePhotoService(),
      );
      await state.init();

      final fakeLogs = FakeLogRepository()
        ..exportResult = [
          ExportLogEntry(
            takenAt: DateTime(2026, 1, 1, 8, 0),
            medicationName: 'Aspirin',
            quantityUsed: 1,
            notes: 'with food',
          ),
        ];

      await tester.pumpWidget(EczamApp(appState: state, logs: fakeLogs));
      await pumpFrames(tester);

      // Navigate to History.
      await tester.tap(find.byIcon(Icons.calendar_today_outlined));
      await pumpFrames(tester, const Duration(milliseconds: 400));
      expect(find.text('History'), findsOneWidget);

      // The export action is reachable via its tooltip/icon.
      final exportButton =
          find.byTooltip('Export dose history for a doctor visit');
      expect(exportButton, findsOneWidget);
      await tester.tap(exportButton);
      await pumpFrames(tester, const Duration(milliseconds: 400));

      // The fake repository's canned rows were fetched and rendered into a
      // CSV preview inside the dialog.
      expect(find.text('Dose history export'), findsOneWidget);
      expect(fakeLogs.lastFrom, isNull);
      expect(fakeLogs.lastTo, isNull);
      expect(
        find.textContaining('Date,Time,Medication,Quantity,Notes'),
        findsOneWidget,
      );
      expect(find.textContaining('Aspirin'), findsWidgets);

      // Copy to clipboard shows a confirmation.
      await tester.tap(find.text('Copy to clipboard'));
      await pumpFrames(tester, const Duration(milliseconds: 400));
      expect(find.text('Copied to clipboard.'), findsOneWidget);

      // Close dismisses the dialog.
      await tester.tap(find.text('Close'));
      await pumpFrames(tester, const Duration(milliseconds: 300));
      expect(find.text('Dose history export'), findsNothing);
    },
  );

  testWidgets(
    'an empty export result shows a friendly message instead of a blank table',
    (tester) async {
      final state = AppState(
        repo: FakeMedicationRepository(),
        notifications: FakeNotificationService(),
        photos: FakePhotoService(),
      );
      await state.init();

      final fakeLogs = FakeLogRepository(); // exportResult defaults to empty

      await tester.pumpWidget(EczamApp(appState: state, logs: fakeLogs));
      await pumpFrames(tester);

      await tester.tap(find.byIcon(Icons.calendar_today_outlined));
      await pumpFrames(tester, const Duration(milliseconds: 400));

      await tester.tap(
        find.byTooltip('Export dose history for a doctor visit'),
      );
      await pumpFrames(tester, const Duration(milliseconds: 400));

      expect(find.text('No doses logged in this period.'), findsOneWidget);
    },
  );
}
