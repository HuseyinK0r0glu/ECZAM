import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/features/logs/log_dto.dart';

void main() {
  group('exportLogEntriesToCsv', () {
    test('emits the RFC 4180 header row even with no entries', () {
      final csv = exportLogEntriesToCsv(const []);
      expect(csv, 'Date,Time,Medication,Quantity,Notes\r\n');
    });

    test('formats a plain row with local date/time and no escaping needed', () {
      final entries = [
        ExportLogEntry(
          takenAt: DateTime(2026, 3, 5, 8, 30),
          medicationName: 'Ibuprofen',
          quantityUsed: 1,
          notes: 'with food',
        ),
      ];
      final csv = exportLogEntriesToCsv(entries);
      expect(
        csv,
        'Date,Time,Medication,Quantity,Notes\r\n'
        '2026-03-05,08:30,Ibuprofen,1,with food\r\n',
      );
    });

    test('quotes a medication name containing a comma', () {
      final entries = [
        ExportLogEntry(
          takenAt: DateTime(2026, 1, 1, 9),
          medicationName: 'Amoxicillin, 500mg',
          quantityUsed: 2,
          notes: null,
        ),
      ];
      final csv = exportLogEntriesToCsv(entries);
      expect(
        csv,
        'Date,Time,Medication,Quantity,Notes\r\n'
        '2026-01-01,09:00,"Amoxicillin, 500mg",2,\r\n',
      );
    });

    test('quotes and preserves a note containing a newline', () {
      final entries = [
        ExportLogEntry(
          takenAt: DateTime(2026, 1, 1, 9),
          medicationName: 'Aspirin',
          quantityUsed: 1,
          notes: 'line one\nline two',
        ),
      ];
      final csv = exportLogEntriesToCsv(entries);
      expect(
        csv,
        'Date,Time,Medication,Quantity,Notes\r\n'
        '2026-01-01,09:00,Aspirin,1,"line one\nline two"\r\n',
      );
    });

    test('doubles an internal double-quote and wraps the field in quotes', () {
      final entries = [
        ExportLogEntry(
          takenAt: DateTime(2026, 1, 1, 9),
          medicationName: 'Aspirin',
          quantityUsed: 1,
          notes: 'said "take with water"',
        ),
      ];
      final csv = exportLogEntriesToCsv(entries);
      expect(
        csv,
        'Date,Time,Medication,Quantity,Notes\r\n'
        '2026-01-01,09:00,Aspirin,1,"said ""take with water"""\r\n',
      );
    });

    test('renders a fractional quantity without unnecessary escaping', () {
      final entries = [
        ExportLogEntry(
          takenAt: DateTime(2026, 1, 1, 9),
          medicationName: 'Syrup',
          quantityUsed: 2.5,
          notes: null,
        ),
      ];
      final csv = exportLogEntriesToCsv(entries);
      expect(
        csv,
        'Date,Time,Medication,Quantity,Notes\r\n'
        '2026-01-01,09:00,Syrup,2.5,\r\n',
      );
    });

    test('an empty notes field renders as an empty CSV field, not "null"', () {
      final entries = [
        ExportLogEntry(
          takenAt: DateTime(2026, 1, 1, 9),
          medicationName: 'Vitamin D',
          quantityUsed: 1,
          notes: '',
        ),
      ];
      final csv = exportLogEntriesToCsv(entries);
      expect(csv, contains('Vitamin D,1,\r\n'));
    });

    test('orders multiple rows exactly as given (caller controls sort order)', () {
      final entries = [
        ExportLogEntry(
          takenAt: DateTime(2026, 1, 2, 8),
          medicationName: 'Med A',
          quantityUsed: 1,
          notes: null,
        ),
        ExportLogEntry(
          takenAt: DateTime(2026, 1, 1, 8),
          medicationName: 'Med B',
          quantityUsed: 1,
          notes: null,
        ),
      ];
      final csv = exportLogEntriesToCsv(entries);
      final lines = csv.split('\r\n');
      expect(lines[1], startsWith('2026-01-02'));
      expect(lines[2], startsWith('2026-01-01'));
    });
  });
}
