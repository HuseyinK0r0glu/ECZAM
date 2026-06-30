import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/models/medication.dart';
import 'package:medtrack/state/app_state.dart';

import 'fakes.dart';

/// AppState wiring with the in-memory fakes (no platform channels).
void main() {
  AppState build() => AppState(
        repo: FakeMedicationRepository(),
        notifications: FakeNotificationService(),
        photos: FakePhotoService(),
      );

  test('addMedication persists and schedules reminders', () async {
    final s = build();
    await s.init();
    await s.addMedication(
        name: 'Aspirin', dose: '100 mg', kind: MedKind.amber, reminderMinutes: [480]);

    expect(s.meds, hasLength(1));
    final notif = s.notifications as FakeNotificationService;
    expect(notif.scheduledMinutes[s.meds.single.id], [480]);
  });

  test('signOutCleanup cancels reminders and clears in-memory state', () async {
    final s = build();
    await s.init();
    await s.addMedication(
        name: 'Aspirin', dose: '', kind: MedKind.amber, reminderMinutes: [480]);
    final medId = s.meds.single.id;

    await s.signOutCleanup();

    expect(s.meds, isEmpty);
    expect(s.todayLogs, isEmpty);
    final notif = s.notifications as FakeNotificationService;
    expect(notif.cancelledMedIds, contains(medId));
  });

  test('init is idempotent (second call does not throw)', () async {
    final s = build();
    await s.init();
    await s.init(); // re-login in the same session → re-sync, no double-wire
    expect(s.meds, isEmpty);
  });
}
