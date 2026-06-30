import 'dart:convert';
import 'dart:math';

import 'package:sqflite/sqflite.dart';

import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';

/// Storage boundary used by [AppState]. Implemented by:
///  * [SqliteMedicationRepository] — the on-device mirror cache,
///  * `BackendMedicationRepository` — the online-first wrapper (backend +
///    mirror),
///  * `FakeMedicationRepository` — the in-memory test double.
///
/// Ids are backend UUIDs (`String`); an offline-created row carries a temporary
/// `local-…` id until the outbox drain swaps in the real one.
abstract interface class MedicationRepository {
  Future<List<Medication>> getMedications();

  /// Persists [med] and returns its id (echoes `med.id`, or a generated id when
  /// it was empty).
  Future<String> insertMedication(Medication med);
  Future<void> updateMedication(Medication med);
  Future<void> deleteMedication(String id);
  Future<void> upsertLog(DoseLog log);
  Future<void> deleteLog(String medId, int minuteOfDay, String dateKey);
  Future<List<DoseLog>> logsForDate(String dateKey);
  Future<List<DoseLog>> logsSince(String fromDateKey);
  Future<List<DoseLog>> recentLogs({int limit, DateTime? since});

  /// Permanently deletes log rows older than [cutoff]. Returns rows removed.
  Future<int> purgeLogsBefore(DateTime cutoff);
}

/// A queued offline write. [op] is one of `taken_log`, `update_inventory`,
/// `delete_medication`, `create_medication`; [payload] is op-specific JSON.
class PendingOp {
  final int id;
  final String entity;
  final String op;
  final Map<String, dynamic> payload;

  const PendingOp({
    required this.id,
    required this.entity,
    required this.op,
    required this.payload,
  });
}

/// On-device mirror cache backed by sqflite. Pure local I/O — it never reaches
/// the network. The backend wrapper reconciles it against the server.
class SqliteMedicationRepository implements MedicationRepository {
  final Database db;
  final Random _rng = Random();

  SqliteMedicationRepository(this.db);

  String _localId() =>
      'local-${DateTime.now().microsecondsSinceEpoch}-${_rng.nextInt(1 << 32)}';

  // ── Medications ──

  @override
  Future<List<Medication>> getMedications() async {
    final rows = await db.query('medications', orderBy: 'created_at ASC');
    return rows.map(Medication.fromMap).toList();
  }

  Future<Medication?> medById(String id) async {
    final rows =
        await db.query('medications', where: 'id = ?', whereArgs: [id]);
    return rows.isEmpty ? null : Medication.fromMap(rows.first);
  }

  @override
  Future<String> insertMedication(Medication med) async {
    final id = med.id.isEmpty ? _localId() : med.id;
    final map = med.toMap()
      ..['id'] = id
      ..['updated_at'] = DateTime.now().millisecondsSinceEpoch
      ..['sync_state'] = med.id.isEmpty ? 'pending' : 'synced';
    await db.insert('medications', map,
        conflictAlgorithm: ConflictAlgorithm.replace);
    return id;
  }

  @override
  Future<void> updateMedication(Medication med) async {
    final map = med.toMap()
      ..['updated_at'] = DateTime.now().millisecondsSinceEpoch;
    await db.update('medications', map, where: 'id = ?', whereArgs: [med.id]);
  }

  @override
  Future<void> deleteMedication(String id) async {
    await db.delete('medications', where: 'id = ?', whereArgs: [id]);
  }

  /// Refreshes the cache from a fresh server snapshot: upsert every [meds] row,
  /// then drop locally-cached synced rows the server no longer returns. Rows
  /// still `pending` (offline creates) are preserved.
  Future<void> replaceServerMeds(List<Medication> meds) async {
    final keepIds = meds.map((m) => m.id).toList();
    await db.transaction((txn) async {
      for (final med in meds) {
        final map = med.toMap()
          ..['updated_at'] = DateTime.now().millisecondsSinceEpoch
          ..['sync_state'] = 'synced';
        await txn.insert('medications', map,
            conflictAlgorithm: ConflictAlgorithm.replace);
      }
      if (keepIds.isEmpty) {
        await txn.delete('medications', where: "sync_state = 'synced'");
      } else {
        final placeholders = List.filled(keepIds.length, '?').join(',');
        await txn.delete(
          'medications',
          where: "sync_state = 'synced' AND id NOT IN ($placeholders)",
          whereArgs: keepIds,
        );
      }
    });
  }

  // ── Dose logs ──

  @override
  Future<void> upsertLog(DoseLog log, {String syncState = 'local'}) async {
    final map = log.toMap()..['sync_state'] = syncState;
    // UNIQUE(med_id, minute_of_day, date_key) ON CONFLICT REPLACE → upsert.
    await db.insert('dose_logs', map);
  }

  Future<DoseLog?> logFor(String medId, int minuteOfDay, String dateKey) async {
    final rows = await db.query(
      'dose_logs',
      where: 'med_id = ? AND minute_of_day = ? AND date_key = ?',
      whereArgs: [medId, minuteOfDay, dateKey],
    );
    return rows.isEmpty ? null : DoseLog.fromMap(rows.first);
  }

  Future<String?> syncStateFor(
      String medId, int minuteOfDay, String dateKey) async {
    final rows = await db.query(
      'dose_logs',
      columns: ['sync_state'],
      where: 'med_id = ? AND minute_of_day = ? AND date_key = ?',
      whereArgs: [medId, minuteOfDay, dateKey],
    );
    return rows.isEmpty ? null : rows.first['sync_state'] as String?;
  }

  Future<void> markLogSynced(
    String medId,
    int minuteOfDay,
    String dateKey,
    String backendId,
  ) async {
    await db.update(
      'dose_logs',
      {'sync_state': 'synced', 'backend_id': backendId},
      where: 'med_id = ? AND minute_of_day = ? AND date_key = ?',
      whereArgs: [medId, minuteOfDay, dateKey],
    );
  }

  /// Locally-recorded taken doses not yet pushed to the backend.
  Future<List<DoseLog>> unsyncedTakenLogs() async {
    final rows = await db.query(
      'dose_logs',
      where: "status = 'taken' AND sync_state = 'local'",
      orderBy: 'logged_at ASC',
    );
    return rows.map(DoseLog.fromMap).toList();
  }

  @override
  Future<void> deleteLog(String medId, int minuteOfDay, String dateKey) async {
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

  // ── Outbox ──

  Future<void> enqueueOp(String entity, String op, Map<String, dynamic> payload)
      async {
    await db.insert('pending_ops', {
      'entity': entity,
      'op': op,
      'payload': jsonEncode(payload),
      'created_at': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<List<PendingOp>> pendingOps() async {
    final rows = await db.query('pending_ops', orderBy: 'created_at ASC, id ASC');
    return rows.map((r) {
      final decoded = jsonDecode(r['payload'] as String);
      return PendingOp(
        id: r['id'] as int,
        entity: r['entity'] as String,
        op: r['op'] as String,
        payload: (decoded as Map).cast<String, dynamic>(),
      );
    }).toList();
  }

  Future<void> deleteOp(int id) async {
    await db.delete('pending_ops', where: 'id = ?', whereArgs: [id]);
  }

  /// Clears every cached row. Used on sign-out so the next account starts fresh.
  Future<void> wipe() async {
    await db.delete('pending_ops');
    await db.delete('dose_logs');
    await db.delete('medications');
  }
}
