import 'package:image_picker/image_picker.dart';
import 'package:medtrack/data/medication_repository.dart';
import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';
import 'package:medtrack/services/notification_service.dart';
import 'package:medtrack/services/photo_service.dart';

/// Pure-Dart, in-memory repository for widget tests. Avoids the sqflite
/// background isolate, whose real I/O deadlocks inside the widget-test
/// fake-async zone. Mirrors the SQLite behaviour the UI relies on: insert
/// auto-ids, log upsert keyed by (medId, slot, dateKey), and ordering.
class FakeMedicationRepository implements MedicationRepository {
  final List<Medication> _meds = [];
  final List<DoseLog> _logs = [];
  int _medSeq = 0;
  int _logSeq = 0;

  @override
  Future<List<Medication>> getMedications() async {
    final sorted = [..._meds]
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return sorted;
  }

  @override
  Future<int> insertMedication(Medication med) async {
    final id = ++_medSeq;
    _meds.add(
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
    return id;
  }

  @override
  Future<void> updateMedication(Medication med) async {
    final i = _meds.indexWhere((m) => m.id == med.id);
    if (i >= 0) _meds[i] = med;
  }

  @override
  Future<void> deleteMedication(int id) async {
    _meds.removeWhere((m) => m.id == id);
  }

  @override
  Future<void> upsertLog(DoseLog log) async {
    _logs.removeWhere(
      (l) =>
          l.medId == log.medId &&
          l.minuteOfDay == log.minuteOfDay &&
          l.dateKey == log.dateKey,
    );
    _logs.add(
      DoseLog(
        id: ++_logSeq,
        medId: log.medId,
        medName: log.medName,
        medDose: log.medDose,
        minuteOfDay: log.minuteOfDay,
        dateKey: log.dateKey,
        status: log.status,
        scheduledAt: log.scheduledAt,
        loggedAt: log.loggedAt,
      ),
    );
  }

  @override
  Future<void> deleteLog(int medId, int minuteOfDay, String dateKey) async {
    _logs.removeWhere(
      (l) =>
          l.medId == medId &&
          l.minuteOfDay == minuteOfDay &&
          l.dateKey == dateKey,
    );
  }

  @override
  Future<List<DoseLog>> logsForDate(String dateKey) async =>
      _logs.where((l) => l.dateKey == dateKey).toList();

  @override
  Future<List<DoseLog>> logsSince(String fromDateKey) async =>
      _logs.where((l) => l.dateKey.compareTo(fromDateKey) >= 0).toList();

  @override
  Future<List<DoseLog>> recentLogs({int limit = 30, DateTime? since}) async {
    final filtered = since == null
        ? _logs
        : _logs.where((l) => !l.loggedAt.isBefore(since));
    final sorted = [...filtered]
      ..sort((a, b) => b.loggedAt.compareTo(a.loggedAt));
    return sorted.take(limit).toList();
  }

  @override
  Future<int> purgeLogsBefore(DateTime cutoff) async {
    final before = _logs.length;
    _logs.removeWhere((l) => l.loggedAt.isBefore(cutoff));
    return before - _logs.length;
  }
}

/// No-op notification layer: platform channels don't exist in tests. Records
/// what was scheduled so tests can assert exact reminder times were wired
/// through.
class FakeNotificationService extends NotificationService {
  final List<int> scheduledMedIds = [];
  final List<int> cancelledMedIds = [];

  /// Exact reminder minutes per medication id from the last schedule call.
  final Map<int, List<int>> scheduledMinutes = {};

  @override
  Future<void> init() async {}

  @override
  Future<bool> requestPermissions() async => true;

  @override
  Future<bool> notificationsEnabled() async => true;

  @override
  Future<void> scheduleForMedication(Medication med) async {
    scheduledMedIds.add(med.id);
    scheduledMinutes[med.id] = List.of(med.reminderMinutes);
  }

  @override
  Future<void> cancelForMedication(int medId) async {
    cancelledMedIds.add(medId);
  }

  @override
  Future<void> rescheduleAll(List<Medication> meds) async {}

  @override
  Future<void> snoozeReminder(Medication med, int minuteOfDay) async {}

  @override
  Future<bool> launchedFromNotification() async => false;
}

/// No-op photo layer: path_provider/image_picker channels don't exist here.
class FakePhotoService extends PhotoService {
  @override
  Future<String> pathFor(String fileName) async =>
      '/tmp/medtrack-test-photos/$fileName';

  @override
  Future<String?> pickAndStore(ImageSource source) async => null;

  @override
  Future<void> delete(String? fileName) async {}
}
