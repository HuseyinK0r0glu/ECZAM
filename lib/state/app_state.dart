import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';

import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';
import 'package:medtrack/services/notification_service.dart';
import 'package:medtrack/services/photo_service.dart';
import 'package:medtrack/state/adherence.dart';

/// Single source of truth between the UI and the database/notification
/// layers. UI widgets call methods here; data flows back via notify.
class AppState extends ChangeNotifier {
  final MedicationRepository repo;
  final NotificationService notifications;
  final PhotoService photos;

  AppState({
    required this.repo,
    required this.notifications,
    required this.photos,
  });

  List<Medication> meds = [];

  /// Today's logs keyed by `<medId>|<minuteOfDay>`.
  Map<String, DoseLog> todayLogs = {};
  List<DoseLog> recentLogs = [];
  WeekSummary week = const WeekSummary([]);

  /// Resolved once so UI can build photo paths synchronously.
  String? photoDirPath;

  /// Recent-log display window and cleanup threshold: one week.
  static const logRetention = Duration(days: 7);

  static String logKey(int medId, int minuteOfDay) => '$medId|$minuteOfDay';

  Future<void> init() async {
    photoDirPath = (await photos.pathFor('')).replaceAll(RegExp(r'/$'), '');
    notifications.onForegroundResponse = (_, _) => refresh();
    await refresh();
    // Prune logs older than the retention window at launch. Fire-and-forget:
    // sqflite runs off the UI isolate, so this never blocks the first frame.
    unawaited(cleanupOldLogs());
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

  DoseStatus? statusFor(int medId, int minuteOfDay) =>
      todayLogs[logKey(medId, minuteOfDay)]?.status;

  // ── Medication CRUD ──

  Future<void> addMedication({
    required String name,
    required String dose,
    required MedKind kind,
    required List<int> reminderMinutes,
    String? photoFile,
  }) async {
    final med = Medication(
      id: 0,
      name: name,
      dose: dose,
      kind: kind,
      reminderMinutes: reminderMinutes,
      photoFile: photoFile,
      createdAt: DateTime.now(),
    );
    final id = await repo.insertMedication(med);
    await notifications.scheduleForMedication(
      Medication(
        id: id,
        name: med.name,
        dose: med.dose,
        kind: med.kind,
        reminderMinutes: med.reminderMinutes,
        photoFile: med.photoFile,
        createdAt: med.createdAt,
      ),
    );
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
          id: 0,
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
