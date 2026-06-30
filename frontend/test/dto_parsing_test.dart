import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/features/auth/auth_dto.dart';
import 'package:medtrack/features/inventory/inventory_dto.dart';
import 'package:medtrack/features/logs/log_dto.dart';
import 'package:medtrack/features/medications/medication_dto.dart';
import 'package:medtrack/features/profile/profile_dto.dart';
import 'package:medtrack/features/schedules/schedule_dto.dart';
import 'package:medtrack/models/medication.dart' show ExpiryStatus;

void main() {
  group('InventoryItem', () {
    test('parses per-box fields and expiry status', () {
      final it = InventoryItem.fromJson({
        'id': 'um1',
        'medicationId': 'med1',
        'medicationName': 'ARMANAKS',
        'strength': '400 mg',
        'form': 'tablet',
        'quantity': 12,
        'unit': 'pill',
        'expirationDate': '2027-01-31',
        'notes': null,
        'batch': 'LOT9',
        'serialNumber': 'SER9',
        'lowStock': true,
        'expiryStatus': 'EXPIRING_SOON',
      });
      expect(it.quantity, 12);
      expect(it.batch, 'LOT9');
      expect(it.serialNumber, 'SER9');
      expect(it.lowStock, isTrue);
      expect(it.expiryStatus, ExpiryStatus.expiringSoon);
      expect(it.expirationDate, DateTime(2027, 1, 31));
    });
  });

  group('ScheduleView', () {
    test('derives reminderMinutes from scheduledTimes', () {
      final s = ScheduleView.fromJson({
        'id': 's1',
        'userMedicationId': 'um1',
        'medicationName': 'Med',
        'dosageAmount': 1,
        'frequencyType': 'daily',
        'scheduledTimes': ['08:00', '20:30'],
        'daysOfWeek': [1, 3, 5],
        'active': true,
      });
      expect(s.frequencyType, FrequencyType.daily);
      expect(s.reminderMinutes, [480, 1230]);
      expect(s.daysOfWeek, [1, 3, 5]);
    });

    test('dailyBody builds the wire payload from minutes', () {
      final body = ScheduleView.dailyBody(dosageAmount: 1, reminderMinutes: [480, 1200]);
      expect(body['frequencyType'], 'daily');
      expect(body['scheduledTimes'], ['08:00', '20:00']);
    });
  });

  test('LogResult parses newQuantity and lowStock', () {
    final r = LogResult.fromJson({
      'log': {
        'id': 'l1',
        'userMedicationId': 'um1',
        'scheduleId': null,
        'takenAt': '2026-06-13T08:05:00Z',
        'quantityUsed': 1,
        'notes': null,
      },
      'newQuantity': 9,
      'lowStock': false,
    });
    expect(r.newQuantity, 9);
    expect(r.lowStock, isFalse);
    expect(r.log.userMedicationId, 'um1');
  });

  group('Catalog + leaflet', () {
    test('CatalogMedicationDetail parses medication + sections', () {
      final d = CatalogMedicationDetail.fromJson({
        'id': 'med1',
        'name': 'ARMANAKS',
        'strength': '400 mg',
        'vectorIndexed': true,
        'leafletSections': {
          'dosage': 'Günde iki kez',
          'side_effects': 'Baş ağrısı',
          'missed_dose': null,
        },
      });
      expect(d.medication.name, 'ARMANAKS');
      expect(d.medication.vectorIndexed, isTrue);
      expect(d.leafletSections.dosage, 'Günde iki kez');
      expect(d.leafletSections.isEmpty, isFalse);
      expect(d.leafletSections.entries.map((e) => e.$1),
          containsAll(['Dosage', 'Side effects']));
    });

    test('empty leaflet sections', () {
      final s = LeafletSections.fromJson({});
      expect(s.isEmpty, isTrue);
      expect(s.entries, isEmpty);
    });

    test('LeafletSearchHit', () {
      final h = LeafletSearchHit.fromJson({'section': 'storage', 'snippet': 'Oda...'});
      expect(h.section, 'storage');
      expect(h.snippet, 'Oda...');
    });
  });

  test('UserProfile parses snake_case notification preferences', () {
    final p = UserProfile.fromJson({
      'id': 'u1',
      'email': 'a@b.com',
      'displayName': 'Aylin',
      'emailVerified': false,
      'role': 'USER',
      'hasPassword': true,
      'hasGoogleLinked': false,
      'notificationPreferences': {
        'push': true,
        'email': false,
        'low_stock_threshold': 5,
        'expiry_warning_days': 14,
      },
    });
    expect(p.email, 'a@b.com');
    expect(p.preferences.lowStockThreshold, 5);
    expect(p.preferences.expiryWarningDays, 14);
    expect(p.preferences.push, isTrue);
  });

  test('AuthResult parses user + tokens', () {
    final a = AuthResult.fromJson({
      'user': {
        'id': 'u1',
        'email': 'a@b.com',
        'displayName': 'A',
        'emailVerified': true,
        'role': 'ADMIN',
      },
      'accessToken': 'acc',
      'refreshToken': 'ref',
    });
    expect(a.accessToken, 'acc');
    expect(a.refreshToken, 'ref');
    expect(a.user.isAdmin, isTrue);
  });
}
