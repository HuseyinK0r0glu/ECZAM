import 'package:sqflite/sqflite.dart';

import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';

/// Storage boundary for medications and dose logs. The concrete
/// [SqliteMedicationRepository] is used in the app; widget tests inject a
/// pure-Dart fake so they never touch the sqflite background isolate, whose
/// real I/O deadlocks inside the widget-test fake-async zone.
abstract interface class MedicationRepository {
  Future<List<Medication>> getMedications();
  Future<int> insertMedication(Medication med);
  Future<void> updateMedication(Medication med);
  Future<void> deleteMedication(int id);
  Future<void> upsertLog(DoseLog log);
  Future<void> deleteLog(int medId, int minuteOfDay, String dateKey);
  Future<List<DoseLog>> logsForDate(String dateKey);
  Future<List<DoseLog>> logsSince(String fromDateKey);
  Future<List<DoseLog>> recentLogs({int limit, DateTime? since});

  /// Permanently deletes log rows older than [cutoff]. Returns rows removed.
  Future<int> purgeLogsBefore(DateTime cutoff);
}

class SqliteMedicationRepository implements MedicationRepository {
  final Database db;

  SqliteMedicationRepository(this.db);

  // ── Medications ──

  @override
  Future<List<Medication>> getMedications() async {
    final rows = await db.query('medications', orderBy: 'created_at ASC');
    return rows.map(Medication.fromMap).toList();
  }

  @override
  Future<int> insertMedication(Medication med) =>
      db.insert('medications', med.toMap());

  @override
  Future<void> updateMedication(Medication med) async {
    await db.update(
      'medications',
      med.toMap(),
      where: 'id = ?',
      whereArgs: [med.id],
    );
  }

  @override
  Future<void> deleteMedication(int id) async {
    await db.delete('medications', where: 'id = ?', whereArgs: [id]);
  }

  // ── Dose logs ──

  @override
  Future<void> upsertLog(DoseLog log) async {
    // UNIQUE(med_id, slot, date_key) ON CONFLICT REPLACE makes this an upsert.
    await db.insert('dose_logs', log.toMap());
  }

  @override
  Future<void> deleteLog(int medId, int minuteOfDay, String dateKey) async {
    await db.delete(
      'dose_logs',
      where: 'med_id = ? AND minute_of_day = ? AND date_key = ?',
      whereArgs: [medId, minuteOfDay, dateKey],
    );
  }

  @override
  Future<List<DoseLog>> logsForDate(String dateKey) async {
    final rows = await db.query(
      'dose_logs',
      where: 'date_key = ?',
      whereArgs: [dateKey],
    );
    return rows.map(DoseLog.fromMap).toList();
  }

  @override
  Future<List<DoseLog>> logsSince(String fromDateKey) async {
    final rows = await db.query(
      'dose_logs',
      where: 'date_key >= ?',
      whereArgs: [fromDateKey],
    );
    return rows.map(DoseLog.fromMap).toList();
  }

  @override
  Future<List<DoseLog>> recentLogs({int limit = 30, DateTime? since}) async {
    final rows = await db.query(
      'dose_logs',
      where: since == null ? null : 'logged_at >= ?',
      whereArgs: since == null ? null : [since.millisecondsSinceEpoch],
      orderBy: 'logged_at DESC',
      limit: limit,
    );
    return rows.map(DoseLog.fromMap).toList();
  }

  @override
  Future<int> purgeLogsBefore(DateTime cutoff) => db.delete(
    'dose_logs',
    where: 'logged_at < ?',
    whereArgs: [cutoff.millisecondsSinceEpoch],
  );
}
