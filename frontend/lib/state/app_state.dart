import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';

import 'package:medtrack/core/sync/connectivity.dart';
import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';
import 'package:medtrack/services/notification_service.dart';
import 'package:medtrack/services/photo_service.dart';
import 'package:medtrack/state/adherence.dart';

/// Single source of truth between the UI and the repository/notification
/// layers. UI widgets call methods here; data flows back via notify.
///
/// Since the backend migration [repo] is the online-first
/// `BackendMedicationRepository` in production (backend + SQLite mirror), or a
/// pure-local / in-memory repo in tests. Offline writes are flushed via
/// [syncDrain] on reconnect.
class AppState extends ChangeNotifier {
  final MedicationRepository repo;
  final NotificationService notifications;
  final PhotoService photos;

  /// Online/offline signal. Null in tests.
  final ConnectivityService? connectivity;

  /// Drains the offline outbox; returns true if anything was flushed. Wired to
  /// `BackendMedicationRepository.drainOutbox` in production. Null in tests.
  final Future<bool> Function()? syncDrain;

  /// Wipes the on-device mirror on sign-out so the next account starts clean.
  /// Null in tests.
  final Future<void> Function()? wipeLocal;

  AppState({
    required this.repo,
    required this.notifications,
    required this.photos,
    this.connectivity,
    this.syncDrain,
    this.wipeLocal,
  });

  bool _booted = false;

  List<Medication> meds = [];

  /// Today's logs keyed by `<medId>|<minuteOfDay>`.
  Map<String, DoseLog> todayLogs = {};
  List<DoseLog> recentLogs = [];
  WeekSummary week = const WeekSummary([]);

  bool get isOnline => connectivity?.isOnline ?? true;

  /// Resolved once so UI can build photo paths synchronously.
  String? photoDirPath;

  /// Recent-log display window and cleanup threshold: one week.
  static const logRetention = Duration(days: 7);

  static String logKey(String medId, int minuteOfDay) => '$medId|$minuteOfDay';

  StreamSubscription<bool>? _connSub;

  /// Loads data for the authenticated user. Idempotent: a second call (e.g. a
  /// re-login in the same session) just re-syncs instead of re-wiring listeners.
  Future<void> init() async {
    if (_booted) {
      await syncNow();
      return;
    }
    _booted = true;
    photoDirPath = (await photos.pathFor('')).replaceAll(RegExp(r'/$'), '');
    notifications.onForegroundResponse = (_, _) => refresh();
    await refresh();
    // Flush anything queued offline, then re-pull from the backend.
    await syncNow();
    // Prune logs older than the retention window at launch. Fire-and-forget:
    // sqflite runs off the UI isolate, so this never blocks the first frame.
    unawaited(cleanupOldLogs());
    await notifications.rescheduleAll(meds);
    // Drain + refresh whenever connectivity returns.
    _connSub = connectivity?.onlineStream.listen((online) {
      if (online) unawaited(syncNow());
    });
  }

  @override
  void dispose() {
    _connSub?.cancel();
    super.dispose();
  }

  /// Tears down the session on sign-out: cancels reminders, wipes the local
  /// mirror, and clears in-memory state so the next account starts fresh.
  Future<void> signOutCleanup() async {
    for (final med in meds) {
      await notifications.cancelForMedication(med.id);
    }
    await wipeLocal?.call();
    meds = [];
    todayLogs = {};
    recentLogs = [];
    week = const WeekSummary([]);
    _booted = false;
    notifyListeners();
  }

  /// Drains the offline outbox and refreshes from the backend. Safe to call on
  /// resume / reconnect.
  Future<void> syncNow() async {
    if (syncDrain == null) return;
    try {
      await syncDrain!.call();
    } catch (_) {
      // Transport hiccup — leave the queue for the next attempt.
    }
    await refresh();
    await notifications.rescheduleAll(meds);
  }

  Future<void> refresh() async {
    final now = DateTime.now();
    final weekStart = DoseLog.dateKeyFor(
      DateTime(now.year, now.month, now.day - 6),
    );
    meds = await repo.getMedications();
    final today = await repo.logsForDate(DoseLog.dateKeyFor(now));
    todayLogs = {
      for (final log in today) logKey(log.medId, log.minuteOfDay): log,
    };
    recentLogs = await repo.recentLogs(since: now.subtract(logRetention));
    week = buildWeekSummary(
      today: now,
      meds: meds,
      logs: await repo.logsSince(weekStart),
    );
    notifyListeners();
  }

  /// Deletes logs older than [logRetention] so the local database doesn't grow
  /// without bound. Safe to fire-and-forget: sqflite runs the delete on its
  /// background isolate, so it never blocks the UI thread.
  Future<void> cleanupOldLogs() async {
    await repo.purgeLogsBefore(DateTime.now().subtract(logRetention));
  }

  String? photoPath(Medication med) {
    if (med.photoFile == null || photoDirPath == null) return null;
    return '$photoDirPath/${med.photoFile}';
  }

  DoseStatus? statusFor(String medId, int minuteOfDay) =>
      todayLogs[logKey(medId, minuteOfDay)]?.status;

  // ── Medication CRUD ──

  Future<void> addMedication({
    required String name,
    required String dose,
    required MedKind kind,
    required List<int> reminderMinutes,
    double quantity = 1,
    String unit = 'pills',
    DateTime? expirationDate,
    String? catalogId,
    String? photoFile,
  }) async {
    final draft = Medication(
      id: '',
      catalogId: catalogId,
      name: name,
      dose: dose,
      kind: kind,
      reminderMinutes: reminderMinutes,
      photoFile: photoFile,
      createdAt: DateTime.now(),
      quantity: quantity,
      unit: unit,
      expirationDate: expirationDate,
    );
    final id = await repo.insertMedication(draft);
    await notifications.scheduleForMedication(draft.copyWith(id: id));
    await refresh();
  }

  Future<void> updateMedication(Medication updated) async {
    await repo.updateMedication(updated);
    await notifications.scheduleForMedication(updated);
    await refresh();
  }

  Future<void> deleteMedication(Medication med) async {
    await notifications.cancelForMedication(med.id);
    await photos.delete(med.photoFile);
    await repo.deleteMedication(med.id);
    await refresh();
  }

  // ── Dose logging ──

  /// Records or clears a dose status. May throw an [ApiException] (e.g.
  /// `INSUFFICIENT_STOCK`) when logging a taken dose against the backend — the
  /// caller should catch and surface it.
  Future<void> setDoseStatus(
    Medication med,
    int minuteOfDay,
    DoseStatus? status,
  ) async {
    final now = DateTime.now();
    if (status == null) {
      await repo.deleteLog(med.id, minuteOfDay, DoseLog.dateKeyFor(now));
    } else {
      await repo.upsertLog(
        DoseLog(
          id: '',
          medId: med.id,
          medName: med.name,
          medDose: med.dose,
          minuteOfDay: minuteOfDay,
          dateKey: DoseLog.dateKeyFor(now),
          status: status,
          scheduledAt: DoseLog.scheduledAtFor(now, minuteOfDay),
          loggedAt: now,
        ),
      );
    }
    await refresh();
  }

  /// Toggles today's log for a reminder time between taken and pending.
  Future<void> toggleTaken(Medication med, int minuteOfDay) async {
    final current = statusFor(med.id, minuteOfDay);
    await setDoseStatus(
      med,
      minuteOfDay,
      current == DoseStatus.taken ? null : DoseStatus.taken,
    );
  }

  /// "Log Dose" on the cabinet action panel: marks the earliest reminder time
  /// with no log today. For as-needed meds (no times) the dose is logged at
  /// the current minute. Returns the logged minute-of-day.
  Future<int> logNextPendingDose(Medication med) async {
    final now = DateTime.now();
    final int minute;
    if (med.reminderMinutes.isEmpty) {
      minute = now.hour * 60 + now.minute;
    } else {
      minute = med.reminderMinutes.firstWhere(
        (m) => statusFor(med.id, m) == null,
        orElse: () => med.reminderMinutes.first,
      );
    }
    await setDoseStatus(med, minute, DoseStatus.taken);
    return minute;
  }

  /// Marks the dose snoozed and schedules a one-off reminder in 10 minutes.
  Future<void> snoozeDose(Medication med, int minuteOfDay) async {
    await notifications.snoozeReminder(med, minuteOfDay);
    await setDoseStatus(med, minuteOfDay, DoseStatus.snoozed);
  }

  // ── Photos ──

  /// Returns false when the picker was cancelled or access was denied.
  Future<bool> attachPhoto(Medication med, ImageSource source) async {
    final fileName = await photos.pickAndStore(source);
    if (fileName == null) return false;
    await photos.delete(med.photoFile);
    await repo.updateMedication(med.copyWith(photoFile: fileName));
    await refresh();
    return true;
  }
}
