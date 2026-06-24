import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import 'package:medtrack/services/notification_service.dart';

void main() {
  setUpAll(tzdata.initializeTimeZones);

  const morning = TimeOfDay(hour: 8, minute: 0);

  group('nextInstanceOf', () {
    test('schedules today when the slot time is still ahead', () {
      final ny = tz.getLocation('America/New_York');
      final from = tz.TZDateTime(ny, 2026, 6, 10, 6, 0);
      final next = NotificationService.nextInstanceOf(
        morning,
        location: ny,
        from: from,
      );
      expect(next, tz.TZDateTime(ny, 2026, 6, 10, 8, 0));
    });

    test('rolls to tomorrow when the slot time has passed', () {
      final ny = tz.getLocation('America/New_York');
      final from = tz.TZDateTime(ny, 2026, 6, 10, 9, 0);
      final next = NotificationService.nextInstanceOf(
        morning,
        location: ny,
        from: from,
      );
      expect(next, tz.TZDateTime(ny, 2026, 6, 11, 8, 0));
    });

    test('keeps 8:00 wall-clock across spring-forward (FR-2.3)', () {
      // US DST starts 2026-03-08 02:00: clocks jump to 03:00.
      final ny = tz.getLocation('America/New_York');
      final from = tz.TZDateTime(ny, 2026, 3, 7, 21, 0); // EST, UTC-5
      final next = NotificationService.nextInstanceOf(
        morning,
        location: ny,
        from: from,
      );

      expect(next.hour, 8);
      expect(next.day, 8);
      expect(next.timeZoneOffset, const Duration(hours: -4)); // now EDT
      // Wall-clock distance is 11h but one hour vanished overnight.
      expect(next.difference(from), const Duration(hours: 10));
    });

    test('keeps 8:00 wall-clock across fall-back (FR-2.3)', () {
      // US DST ends 2026-11-01 02:00: clocks repeat 01:00-02:00.
      final ny = tz.getLocation('America/New_York');
      final from = tz.TZDateTime(ny, 2026, 10, 31, 21, 0); // EDT, UTC-4
      final next = NotificationService.nextInstanceOf(
        morning,
        location: ny,
        from: from,
      );

      expect(next.hour, 8);
      expect(next.day, 1);
      expect(next.timeZoneOffset, const Duration(hours: -5)); // back to EST
      expect(next.difference(from), const Duration(hours: 12));
    });

    test('rolls over month boundaries', () {
      final utc = tz.UTC;
      final from = tz.TZDateTime(utc, 2026, 6, 30, 23, 0);
      final next = NotificationService.nextInstanceOf(
        morning,
        location: utc,
        from: from,
      );
      expect(next, tz.TZDateTime(utc, 2026, 7, 1, 8, 0));
    });
  });

  group('ReminderPayload', () {
    test('round-trips, with pipes folded into the dose tail', () {
      const payload = ReminderPayload(
        7,
        1290, // 21:30
        2,
        'Med | strange name',
        '5 ml',
      );
      final decoded = ReminderPayload.tryDecode(payload.encode());
      expect(decoded, isNotNull);
      expect(decoded!.medId, 7);
      expect(decoded.minuteOfDay, 1290);
      expect(decoded.index, 2);
      expect(decoded.name, 'Med ');
      expect(decoded.dose, ' strange name|5 ml');
    });

    test('rejects malformed payloads', () {
      expect(ReminderPayload.tryDecode(null), isNull);
      expect(ReminderPayload.tryDecode(''), isNull);
      expect(ReminderPayload.tryDecode('x|540|0|a|b'), isNull);
      expect(ReminderPayload.tryDecode('1|noon|0|a|b'), isNull);
      expect(ReminderPayload.tryDecode('1|540|0|a'), isNull); // too few parts
    });
  });

  test('notification ids never collide across meds and reminder slots', () {
    final seen = <int>{};
    for (var medId = 1; medId <= 50; medId++) {
      for (var index = 0; index < kMaxReminders; index++) {
        seen.add(NotificationService.dailyNotificationId(medId, index));
        seen.add(NotificationService.snoozeNotificationId(medId, index));
      }
    }
    expect(seen, hasLength(50 * kMaxReminders * 2));
  });
}
