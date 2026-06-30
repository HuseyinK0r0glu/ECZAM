import 'package:medtrack/core/api/api_envelope.dart';
import 'package:medtrack/core/sync/connectivity.dart';
import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/features/inventory/inventory_dto.dart';
import 'package:medtrack/features/inventory/inventory_repository.dart';
import 'package:medtrack/features/logs/log_repository.dart';
import 'package:medtrack/features/medications/medication_repository.dart';
import 'package:medtrack/features/schedules/schedule_dto.dart';
import 'package:medtrack/features/schedules/schedule_repository.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';

/// Online-first [MedicationRepository]: the Spring Boot backend is the source of
/// truth, the SQLite [mirror] is an offline cache, and an outbox carries writes
/// made while offline.
///
/// Policy (per the migration plan, Phase 4):
///  * Read online  → backend → mirror → return.
///  * Read offline → mirror.
///  * Write online → backend → mirror.
///  * Write offline → mirror optimistically + enqueue; drained on reconnect.
///
/// Only *taken* doses sync (`POST /medication-logs`, decrements inventory);
/// skipped/snoozed are local-only. Server-driven push is out of scope for the
/// native app — local notifications cover reminders (see docs).
class BackendMedicationRepository implements MedicationRepository {
  final CatalogRepository catalog;
  final InventoryRepository inventory;
  final ScheduleRepository schedules;
  final LogRepository logs;
  final SqliteMedicationRepository mirror;
  final ConnectivityService connectivity;

  BackendMedicationRepository({
    required this.catalog,
    required this.inventory,
    required this.schedules,
    required this.logs,
    required this.mirror,
    required this.connectivity,
  });

  bool _isNetwork(Object e) => e is ApiException && e.code == 'NETWORK_ERROR';

  // ── Reads ──────────────────────────────────────────────────────────────────

  @override
  Future<List<Medication>> getMedications() async {
    if (connectivity.isOnline) {
      try {
        final items = await inventory.list();
        final scheds = await schedules.listForUser();
        final existing = {
          for (final m in await mirror.getMedications()) m.id: m,
        };
        final assembled = items
            .map((it) => _assemble(it, scheds, existing[it.id]))
            .toList();
        await mirror.replaceServerMeds(assembled);
        // Read back so locally-cached presentation data + any still-pending
        // offline creates are merged into one ordered list.
        return mirror.getMedications();
      } catch (_) {
        // Reads degrade gracefully to the cache on any failure (offline,
        // timeout, or a 401 while the auth gate is already redirecting to
        // login) — never crash the screen over a read.
      }
    }
    return mirror.getMedications();
  }

  Medication _assemble(
    InventoryItem it,
    List<ScheduleView> allSchedules,
    Medication? cached,
  ) {
    final mine = allSchedules.where((s) => s.userMedicationId == it.id).toList();
    final minutes = <int>{for (final s in mine) ...s.reminderMinutes}.toList()
      ..sort();
    return Medication(
      id: it.id,
      catalogId: it.medicationId,
      scheduleId: mine.isEmpty ? null : mine.first.id,
      name: it.medicationName,
      dose: (it.strength?.isNotEmpty ?? false)
          ? it.strength!
          : (cached?.dose ?? ''),
      kind: cached?.kind ?? _kindForForm(it.form),
      reminderMinutes: minutes,
      photoFile: cached?.photoFile,
      createdAt: cached?.createdAt ?? DateTime.now(),
      quantity: it.quantity,
      unit: it.unit ?? cached?.unit ?? 'pills',
      expirationDate: it.expirationDate,
      lowStock: it.lowStock,
      expiryStatus: it.expiryStatus,
    );
  }

  // ── Medication writes ───────────────────────────────────────────────────────

  @override
  Future<String> insertMedication(Medication med) async {
    if (connectivity.isOnline) {
      try {
        return await _createOnline(med);
      } catch (e) {
        if (!_isNetwork(e)) rethrow;
      }
    }
    // Offline: optimistic local row + queued create.
    final localId = await mirror.insertMedication(med); // generates local id
    await mirror.enqueueOp('medication', 'create_medication', {
      'localId': localId,
      'name': med.name,
      'dose': med.dose,
      'form': _formForKind(med.kind),
      'quantity': med.quantity,
      'unit': med.unit,
      'expirationDate': Medication.dateToIso(med.expirationDate),
      'reminderMinutes': med.reminderMinutes,
    });
    return localId;
  }

  Future<String> _createOnline(Medication med) async {
    final catalogMed = await catalog.findOrCreate(
      name: med.name,
      strength: med.dose.isEmpty ? null : med.dose,
      form: _formForKind(med.kind),
    );
    final item = await inventory.create(
      medicationId: catalogMed.id,
      quantity: med.quantity <= 0 ? 1 : med.quantity,
      unit: med.unit,
      expirationDate: med.expirationDate,
    );
    String? scheduleId;
    if (med.reminderMinutes.isNotEmpty) {
      final sched = await schedules.createDaily(
        userMedicationId: item.id,
        reminderMinutes: med.reminderMinutes,
      );
      scheduleId = sched.id;
    }
    final synced = med.copyWith(
      id: item.id,
      catalogId: catalogMed.id,
      scheduleId: scheduleId,
      quantity: item.quantity,
      unit: item.unit ?? med.unit,
      lowStock: item.lowStock,
      expiryStatus: item.expiryStatus,
    );
    await mirror.insertMedication(synced);
    return item.id;
  }

  @override
  Future<void> updateMedication(Medication med) async {
    // Presentation-only fields (kind/photo) always go to the mirror.
    await mirror.updateMedication(med);
    if (med.id.startsWith('local-') || !connectivity.isOnline) {
      await mirror.enqueueOp('medication', 'update_inventory', {
        'id': med.id,
        'quantity': med.quantity,
        'unit': med.unit,
        'expirationDate': Medication.dateToIso(med.expirationDate),
      });
      return;
    }
    try {
      await inventory.update(
        med.id,
        quantity: med.quantity,
        unit: med.unit,
        expirationDate: med.expirationDate,
        clearExpiration: med.expirationDate == null,
      );
      await _reconcileSchedule(med);
    } catch (e) {
      if (!_isNetwork(e)) rethrow;
      await mirror.enqueueOp('medication', 'update_inventory', {
        'id': med.id,
        'quantity': med.quantity,
        'unit': med.unit,
        'expirationDate': Medication.dateToIso(med.expirationDate),
      });
    }
  }

  Future<void> _reconcileSchedule(Medication med) async {
    final wantsReminders = med.reminderMinutes.isNotEmpty;
    if (!wantsReminders && med.scheduleId != null) {
      await schedules.delete(med.scheduleId!);
      await mirror.updateMedication(med.copyWith(scheduleId: null));
    } else if (wantsReminders && med.scheduleId != null) {
      await schedules.updateTimes(med.scheduleId!,
          reminderMinutes: med.reminderMinutes);
    } else if (wantsReminders && med.scheduleId == null) {
      final sched = await schedules.createDaily(
        userMedicationId: med.id,
        reminderMinutes: med.reminderMinutes,
      );
      await mirror.updateMedication(med.copyWith(scheduleId: sched.id));
    }
  }

  @override
  Future<void> deleteMedication(String id) async {
    await mirror.deleteMedication(id);
    if (id.startsWith('local-')) {
      // Never reached the server; nothing to delete remotely.
      return;
    }
    if (!connectivity.isOnline) {
      await mirror.enqueueOp('medication', 'delete_medication', {'id': id});
      return;
    }
    try {
      await _deleteOnline(id);
    } catch (e) {
      if (!_isNetwork(e)) rethrow;
      await mirror.enqueueOp('medication', 'delete_medication', {'id': id});
    }
  }

  Future<void> _deleteOnline(String id) async {
    // Remove dependent schedules first so the inventory delete can't trip an
    // FK constraint.
    try {
      final scheds = await schedules.listForMedication(id);
      for (final s in scheds) {
        await schedules.delete(s.id);
      }
    } on ApiException {
      // 404 etc. — proceed to the inventory delete regardless.
    }
    await inventory.delete(id);
  }

  // ── Dose logs ───────────────────────────────────────────────────────────────

  @override
  Future<void> upsertLog(DoseLog log) async {
    if (log.status != DoseStatus.taken) {
      // Skipped / snoozed never leave the device.
      await mirror.upsertLog(log, syncState: 'local');
      return;
    }
    // Already counted on the server → keep the mirror row, don't double-post.
    if (await mirror.syncStateFor(log.medId, log.minuteOfDay, log.dateKey) ==
        'synced') {
      await mirror.upsertLog(log, syncState: 'synced');
      return;
    }
    if (!connectivity.isOnline || log.medId.startsWith('local-')) {
      await mirror.upsertLog(log, syncState: 'local');
      return;
    }
    try {
      final med = await mirror.medById(log.medId);
      final result = await logs.logTaken(
        userMedicationId: log.medId,
        quantityUsed: 1,
        scheduleId: med?.scheduleId,
        clientRequestId: _logKey(log),
      );
      await mirror.upsertLog(log, syncState: 'synced');
      await mirror.markLogSynced(
          log.medId, log.minuteOfDay, log.dateKey, result.log.id);
      if (med != null) {
        await mirror.updateMedication(med.copyWith(
          quantity: result.newQuantity,
          lowStock: result.lowStock,
        ));
      }
    } catch (e) {
      if (_isNetwork(e)) {
        // Keep it locally; the drain will post it on reconnect.
        await mirror.upsertLog(log, syncState: 'local');
        return;
      }
      rethrow; // e.g. INSUFFICIENT_STOCK — surface to the UI.
    }
  }

  @override
  Future<void> deleteLog(String medId, int minuteOfDay, String dateKey) {
    // Backend logs are immutable and have already decremented stock, so this
    // only clears the local "taken" mark (documented lossy edge).
    return mirror.deleteLog(medId, minuteOfDay, dateKey);
  }

  @override
  Future<List<DoseLog>> logsForDate(String dateKey) =>
      mirror.logsForDate(dateKey);

  @override
  Future<List<DoseLog>> logsSince(String fromDateKey) =>
      mirror.logsSince(fromDateKey);

  @override
  Future<List<DoseLog>> recentLogs({int limit = 30, DateTime? since}) =>
      mirror.recentLogs(limit: limit, since: since);

  @override
  Future<int> purgeLogsBefore(DateTime cutoff) =>
      mirror.purgeLogsBefore(cutoff);

  // ── Outbox drain (called on reconnect) ──────────────────────────────────────

  /// Flushes queued offline writes and unsynced taken doses. Stops at the first
  /// transport failure, leaving the rest queued. Returns true if anything was
  /// flushed (so the caller can refresh).
  Future<bool> drainOutbox() async {
    if (!connectivity.isOnline) return false;
    var flushed = false;
    try {
      for (final op in await mirror.pendingOps()) {
        await _applyOp(op);
        await mirror.deleteOp(op.id);
        flushed = true;
      }
      // Taken doses recorded offline or via notification actions.
      for (final log in await mirror.unsyncedTakenLogs()) {
        if (log.medId.startsWith('local-')) continue; // not yet a real id
        final med = await mirror.medById(log.medId);
        try {
          final result = await logs.logTaken(
            userMedicationId: log.medId,
            quantityUsed: 1,
            scheduleId: med?.scheduleId,
            clientRequestId: _logKey(log),
          );
          await mirror.markLogSynced(
              log.medId, log.minuteOfDay, log.dateKey, result.log.id);
          flushed = true;
        } on ApiException catch (e) {
          if (e.code == 'NETWORK_ERROR') return flushed;
          // INSUFFICIENT_STOCK / NOT_FOUND: keep the local record, stop trying
          // to push this one by marking it synced-without-id.
          await mirror.markLogSynced(
              log.medId, log.minuteOfDay, log.dateKey, '');
        }
      }
    } on ApiException catch (e) {
      if (e.code != 'NETWORK_ERROR') rethrow;
    }
    return flushed;
  }

  Future<void> _applyOp(PendingOp op) async {
    switch (op.op) {
      case 'create_medication':
        final localId = op.payload['localId'] as String;
        final minutes = (op.payload['reminderMinutes'] as List?)
                ?.map((e) => (e as num).toInt())
                .toList() ??
            const <int>[];
        final draft = Medication(
          id: '',
          name: op.payload['name'] as String,
          dose: (op.payload['dose'] as String?) ?? '',
          kind: _kindForForm(op.payload['form'] as String?),
          reminderMinutes: minutes,
          createdAt: DateTime.now(),
          quantity: (op.payload['quantity'] as num?)?.toDouble() ?? 1,
          unit: (op.payload['unit'] as String?) ?? 'pills',
          expirationDate:
              Medication.isoToDate(op.payload['expirationDate'] as String?),
        );
        final realId = await _createOnline(draft);
        // Re-point the local cache + any dose logs from the temp id to the real
        // one, then drop the temp row.
        await mirror.deleteMedication(localId);
        await _remapLogs(localId, realId);
      case 'update_inventory':
        final id = op.payload['id'] as String;
        if (id.startsWith('local-')) return; // superseded by create remap
        await inventory.update(
          id,
          quantity: (op.payload['quantity'] as num?)?.toDouble(),
          unit: op.payload['unit'] as String?,
          expirationDate:
              Medication.isoToDate(op.payload['expirationDate'] as String?),
          clearExpiration: op.payload['expirationDate'] == null,
        );
      case 'delete_medication':
        final id = op.payload['id'] as String;
        if (id.startsWith('local-')) return;
        try {
          await _deleteOnline(id);
        } on ApiException catch (e) {
          if (e.statusCode != 404) rethrow;
        }
    }
  }

  Future<void> _remapLogs(String fromId, String toId) async {
    // The mirror keys logs by med_id; repoint any temp-id logs at the real id
    // so history survives the create reconciliation.
    final logsForMed = await mirror.logsSince('0000-00-00');
    for (final l in logsForMed.where((l) => l.medId == fromId)) {
      await mirror.deleteLog(l.medId, l.minuteOfDay, l.dateKey);
      await mirror.upsertLog(
        DoseLog(
          id: '',
          medId: toId,
          medName: l.medName,
          medDose: l.medDose,
          minuteOfDay: l.minuteOfDay,
          dateKey: l.dateKey,
          status: l.status,
          scheduledAt: l.scheduledAt,
          loggedAt: l.loggedAt,
        ),
        syncState: 'local',
      );
    }
  }

  /// Stable idempotency key for a taken dose: one logical dose per
  /// (box, reminder-time, day). A queued offline write replayed on reconnect
  /// reuses this key so the backend never double-decrements (≤64 chars).
  static String _logKey(DoseLog log) {
    final raw = '${log.medId}|${log.minuteOfDay}|${log.dateKey}';
    return raw.length <= 64 ? raw : 'h${raw.hashCode}';
  }

  // ── form ↔ kind heuristics (backend has no container concept) ───────────────

  static MedKind _kindForForm(String? form) {
    final f = (form ?? '').toLowerCase();
    if (f.contains('syrup') || f.contains('liquid') || f.contains('solution') ||
        f.contains('suspension')) {
      return MedKind.syrup;
    }
    if (f.contains('gel') || f.contains('cream') || f.contains('ointment') ||
        f.contains('jar')) {
      return MedKind.jar;
    }
    if (f.contains('blister')) return MedKind.blister;
    if (f.contains('tablet')) return MedKind.white;
    return MedKind.amber;
  }

  static String _formForKind(MedKind kind) => switch (kind) {
        MedKind.amber => 'capsule',
        MedKind.white => 'tablet',
        MedKind.syrup => 'syrup',
        MedKind.jar => 'gel',
        MedKind.blister => 'blister',
      };
}
