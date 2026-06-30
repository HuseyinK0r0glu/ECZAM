import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:medtrack/data/app_database.dart';
import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';

/// Tests the offline mirror + outbox half of the sync engine
/// (SqliteMedicationRepository's new methods) — no network involved.
void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  late SqliteMedicationRepository repo;
  late Database db;

  setUp(() async {
    db = await AppDatabase.open(path: inMemoryDatabasePath, singleInstance: false);
    repo = SqliteMedicationRepository(db);
  });

  tearDown(() => db.close());

  Medication med(String id, {String name = 'Med'}) => Medication(
        id: id,
        name: name,
        dose: '10 mg',
        kind: MedKind.amber,
        reminderMinutes: const [480],
        createdAt: DateTime(2026, 1, 1),
      );

  DoseLog takenLog(String medId) => DoseLog(
        id: '',
        medId: medId,
        medName: 'Med',
        medDose: '10 mg',
        minuteOfDay: 480,
        dateKey: '2026-06-13',
        status: DoseStatus.taken,
        scheduledAt: DateTime(2026, 6, 13, 8, 0),
        loggedAt: DateTime(2026, 6, 13, 8, 5),
      );

  test('replaceServerMeds prunes stale synced rows but keeps pending ones', () async {
    await repo.replaceServerMeds([med('med-a'), med('med-c')]);
    final localId = await repo.insertMedication(med('', name: 'Offline draft')); // pending

    // Server no longer returns med-c; med-a is still there.
    await repo.replaceServerMeds([med('med-a')]);

    final ids = (await repo.getMedications()).map((m) => m.id).toSet();
    expect(ids, contains('med-a'));
    expect(ids, contains(localId)); // pending offline create survives the refresh
    expect(ids, isNot(contains('med-c'))); // stale synced row pruned
  });

  test('outbox enqueues, lists in order, and deletes', () async {
    await repo.enqueueOp('medication', 'create_medication', {'localId': 'x', 'name': 'A'});
    await repo.enqueueOp('medication', 'delete_medication', {'id': 'y'});

    final ops = await repo.pendingOps();
    expect(ops, hasLength(2));
    expect(ops.first.op, 'create_medication');
    expect(ops.first.payload['name'], 'A');

    await repo.deleteOp(ops.first.id);
    expect(await repo.pendingOps(), hasLength(1));
  });

  test('taken log sync-state lifecycle', () async {
    await repo.upsertLog(takenLog('um1'), syncState: 'local');
    expect(await repo.syncStateFor('um1', 480, '2026-06-13'), 'local');
    expect(await repo.unsyncedTakenLogs(), hasLength(1));

    await repo.markLogSynced('um1', 480, '2026-06-13', 'backend-id-1');
    expect(await repo.syncStateFor('um1', 480, '2026-06-13'), 'synced');
    expect(await repo.unsyncedTakenLogs(), isEmpty);
  });

  test('wipe clears meds, logs and outbox', () async {
    await repo.replaceServerMeds([med('med-a')]);
    await repo.upsertLog(takenLog('med-a'));
    await repo.enqueueOp('medication', 'create_medication', {'localId': 'x'});

    await repo.wipe();

    expect(await repo.getMedications(), isEmpty);
    expect(await repo.recentLogs(), isEmpty);
    expect(await repo.pendingOps(), isEmpty);
  });
}
