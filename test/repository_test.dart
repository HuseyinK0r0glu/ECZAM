import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:medtrack/data/app_database.dart';
import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';

const _morning = 8 * 60; // 08:00
const _midday = 14 * 60; // 14:00
const _evening = 21 * 60; // 21:00

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  late MedicationRepository repo;
  late Database db;

  setUp(() async {
    db = await AppDatabase.open(
      path: inMemoryDatabasePath,
      singleInstance: false,
    );
    repo = SqliteMedicationRepository(db);
  });

  tearDown(() => db.close());

  Medication draft({String name = 'Ibuprofen'}) => Medication(
    id: 0,
    name: name,
    dose: '400 mg',
    kind: MedKind.amber,
    reminderMinutes: [_morning, _evening],
    createdAt: DateTime(2026, 6, 1, 10, 30),
  );

  DoseLog logFor(
    int medId, {
    int minute = _morning,
    String dateKey = '2026-06-13',
    DoseStatus status = DoseStatus.taken,
    DateTime? loggedAt,
  }) => DoseLog(
    id: 0,
    medId: medId,
    medName: 'Ibuprofen',
    medDose: '400 mg',
    minuteOfDay: minute,
    dateKey: dateKey,
    status: status,
    scheduledAt: DateTime(2026, 6, 13, 8, 0),
    loggedAt: loggedAt ?? DateTime(2026, 6, 13, 8, 5),
  );

  test('medication round-trips through insert and read', () async {
    final id = await repo.insertMedication(draft());
    final meds = await repo.getMedications();

    expect(meds, hasLength(1));
    final med = meds.single;
    expect(med.id, id);
    expect(med.name, 'Ibuprofen');
    expect(med.dose, '400 mg');
    expect(med.kind, MedKind.amber);
    expect(med.reminderMinutes, [_morning, _evening]);
    expect(med.photoFile, isNull);
    expect(med.createdAt, DateTime(2026, 6, 1, 10, 30));
  });

  test('update persists changed fields', () async {
    final id = await repo.insertMedication(draft());
    final med = (await repo.getMedications()).single;

    await repo.updateMedication(
      med.copyWith(
        name: 'Naproxen',
        reminderMinutes: [_midday],
        photoFile: 'med_123.jpg',
      ),
    );

    final updated = (await repo.getMedications()).single;
    expect(updated.id, id);
    expect(updated.name, 'Naproxen');
    expect(updated.reminderMinutes, [_midday]);
    expect(updated.photoFile, 'med_123.jpg');
  });

  test('delete removes the medication but keeps its logs', () async {
    final id = await repo.insertMedication(draft());
    await repo.upsertLog(logFor(id));

    await repo.deleteMedication(id);

    expect(await repo.getMedications(), isEmpty);
    expect(await repo.logsForDate('2026-06-13'), hasLength(1));
  });

  test('upsert replaces the log for the same med, time and day', () async {
    final id = await repo.insertMedication(draft());
    await repo.upsertLog(logFor(id, status: DoseStatus.skipped));
    await repo.upsertLog(logFor(id, status: DoseStatus.taken));

    final logs = await repo.logsForDate('2026-06-13');
    expect(logs, hasLength(1));
    expect(logs.single.status, DoseStatus.taken);
  });

  test('deleteLog clears a dose back to pending', () async {
    final id = await repo.insertMedication(draft());
    await repo.upsertLog(logFor(id));

    await repo.deleteLog(id, _morning, '2026-06-13');

    expect(await repo.logsForDate('2026-06-13'), isEmpty);
  });

  test('logsSince filters by date key and recentLogs orders by time', () async {
    final id = await repo.insertMedication(draft());
    await repo.upsertLog(
      logFor(
        id,
        dateKey: '2026-06-10',
        minute: _morning,
        loggedAt: DateTime(2026, 6, 10, 8, 0),
      ),
    );
    await repo.upsertLog(
      logFor(
        id,
        dateKey: '2026-06-13',
        minute: _evening,
        loggedAt: DateTime(2026, 6, 13, 21, 2),
      ),
    );

    expect(await repo.logsSince('2026-06-12'), hasLength(1));
    expect(await repo.logsSince('2026-06-01'), hasLength(2));

    final recent = await repo.recentLogs();
    expect(recent.first.minuteOfDay, _evening);
    expect(recent, hasLength(2));
  });

  test('recentLogs since-filter and purgeLogsBefore drop old entries', () async {
    final id = await repo.insertMedication(draft());
    await repo.upsertLog(
      logFor(
        id,
        dateKey: '2026-06-01',
        minute: _morning,
        loggedAt: DateTime(2026, 6, 1, 8, 0),
      ),
    );
    await repo.upsertLog(
      logFor(
        id,
        dateKey: '2026-06-13',
        minute: _evening,
        loggedAt: DateTime(2026, 6, 13, 21, 0),
      ),
    );

    // Display filter: only the entry within the window is returned.
    final cutoff = DateTime(2026, 6, 6, 8, 0);
    final recent = await repo.recentLogs(since: cutoff);
    expect(recent, hasLength(1));
    expect(recent.single.minuteOfDay, _evening);

    // Cleanup permanently removes anything older than the cutoff.
    expect(await repo.purgeLogsBefore(cutoff), 1);
    expect(await repo.recentLogs(), hasLength(1));
  });
}
