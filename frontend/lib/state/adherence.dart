import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';

/// Adherence outcome for one calendar day (history week strip dots).
enum DayOutcome { none, full, partial, missed }

class DayAdherence {
  final DateTime day;
  final int expected;
  final int taken;

  const DayAdherence({
    required this.day,
    required this.expected,
    required this.taken,
  });

  DayOutcome get outcome {
    if (expected == 0) return DayOutcome.none;
    if (taken == 0) return DayOutcome.missed;
    if (taken >= expected) return DayOutcome.full;
    return DayOutcome.partial;
  }
}

class WeekSummary {
  final List<DayAdherence> days;

  const WeekSummary(this.days);

  int get _expectedTotal => days.fold(0, (sum, d) => sum + d.expected);
  int get _takenTotal => days.fold(0, (sum, d) => sum + d.taken);

  /// Percentage of expected doses taken across the week, capped at 100.
  int get percent {
    if (_expectedTotal == 0) return 100;
    final p = (_takenTotal * 100 / _expectedTotal).round();
    return p > 100 ? 100 : p;
  }

  int get daysOnTrack => days.where((d) => d.outcome == DayOutcome.full).length;

  int get daysWithDoses => days.where((d) => d.expected > 0).length;
}

/// Builds the last [dayCount] days (oldest first, ending today).
///
/// Expected doses per day come from the current medication list (meds that
/// existed on that day x their slots). Logs for meds that were since deleted
/// still count toward both expected and taken, so history stays truthful.
WeekSummary buildWeekSummary({
  required DateTime today,
  required List<Medication> meds,
  required List<DoseLog> logs,
  int dayCount = 7,
}) {
  final logsByDay = <String, List<DoseLog>>{};
  for (final log in logs) {
    logsByDay.putIfAbsent(log.dateKey, () => []).add(log);
  }

  final currentIds = meds.map((m) => m.id).toSet();
  final days = <DayAdherence>[];

  for (var i = dayCount - 1; i >= 0; i--) {
    final day = DateTime(today.year, today.month, today.day - i);
    final dayEnd = DateTime(day.year, day.month, day.day, 23, 59, 59);
    final key = DoseLog.dateKeyFor(day);
    final dayLogs = logsByDay[key] ?? const [];

    var expected = 0;
    for (final med in meds) {
      if (!med.createdAt.isAfter(dayEnd)) {
        expected += med.reminderMinutes.length;
      }
    }
    // Doses logged against meds that no longer exist.
    expected += dayLogs.where((l) => !currentIds.contains(l.medId)).length;

    final taken = dayLogs.where((l) => l.status == DoseStatus.taken).length;

    days.add(
      DayAdherence(
        day: day,
        expected: expected,
        taken: taken > expected ? expected : taken,
      ),
    );
  }
  return WeekSummary(days);
}
