import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/models/medication.dart';

void main() {
  group('minutes ↔ "HH:mm" (reminderMinutes ↔ scheduledTimes)', () {
    test('formats minutes-since-midnight as zero-padded HH:mm', () {
      expect(minuteToHHmm(0), '00:00');
      expect(minuteToHHmm(8 * 60), '08:00');
      expect(minuteToHHmm(20 * 60 + 5), '20:05');
      expect(minuteToHHmm(23 * 60 + 59), '23:59');
    });

    test('parses HH:mm back to minutes', () {
      expect(hhmmToMinute('00:00'), 0);
      expect(hhmmToMinute('08:00'), 480);
      expect(hhmmToMinute('20:05'), 1205);
    });

    test('round-trips every minute of the day', () {
      for (var m = 0; m < 24 * 60; m++) {
        expect(hhmmToMinute(minuteToHHmm(m)), m);
      }
    });

    test('tolerates malformed input without throwing', () {
      expect(hhmmToMinute('garbage'), 0);
      expect(hhmmToMinute('8'), 0);
    });
  });

  group('ExpiryStatus wire mapping', () {
    test('round-trips the backend enum strings', () {
      expect(ExpiryStatus.ok.wire, 'OK');
      expect(ExpiryStatus.expiringSoon.wire, 'EXPIRING_SOON');
      expect(ExpiryStatus.expired.wire, 'EXPIRED');
      expect(ExpiryStatusJson.parse('EXPIRING_SOON'), ExpiryStatus.expiringSoon);
      expect(ExpiryStatusJson.parse('EXPIRED'), ExpiryStatus.expired);
      expect(ExpiryStatusJson.parse(null), ExpiryStatus.ok);
    });
  });
}
