/// Mirrors `com.eczam.logs.dto.LogDtos`. The backend only records *taken*
/// doses (each decrements inventory); skipped/snoozed stay local-only.
library;

class LogView {
  final String id;
  final String userMedicationId;
  final String? scheduleId;
  final DateTime takenAt;
  final double quantityUsed;
  final String? notes;

  const LogView({
    required this.id,
    required this.userMedicationId,
    this.scheduleId,
    required this.takenAt,
    required this.quantityUsed,
    this.notes,
  });

  factory LogView.fromJson(Map<String, dynamic> j) => LogView(
        id: j['id'] as String,
        userMedicationId: (j['userMedicationId'] as String?) ?? '',
        scheduleId: j['scheduleId'] as String?,
        takenAt: DateTime.parse(j['takenAt'] as String),
        quantityUsed: (j['quantityUsed'] as num?)?.toDouble() ?? 0,
        notes: j['notes'] as String?,
      );
}

/// `{ log, newQuantity, lowStock }` returned by `POST /medication-logs`.
class LogResult {
  final LogView log;
  final double newQuantity;
  final bool lowStock;

  const LogResult({
    required this.log,
    required this.newQuantity,
    required this.lowStock,
  });

  factory LogResult.fromJson(Map<String, dynamic> j) => LogResult(
        log: LogView.fromJson((j['log'] as Map).cast<String, dynamic>()),
        newQuantity: (j['newQuantity'] as num?)?.toDouble() ?? 0,
        lowStock: (j['lowStock'] as bool?) ?? false,
      );
}

/// One day's expected-vs-taken dose count within an [AdherenceSummary].
class AdherenceDay {
  final DateTime date;
  final int expected;
  final int taken;

  const AdherenceDay({
    required this.date,
    required this.expected,
    required this.taken,
  });

  factory AdherenceDay.fromJson(Map<String, dynamic> j) => AdherenceDay(
        date: DateTime.parse(j['date'] as String),
        expected: (j['expected'] as num?)?.toInt() ?? 0,
        taken: (j['taken'] as num?)?.toInt() ?? 0,
      );
}

/// `{ currentStreak, longestStreak, windowDays, days }` returned by
/// `GET /medication-logs/adherence` — a server-computed streak over the
/// user's full, never-purged dose-log history. Unlike the client-only
/// [WeekSummary] (state/adherence.dart), which only sees the local 7-day
/// SQLite mirror, this can reflect a real multi-week streak.
class AdherenceSummary {
  final int currentStreak;
  final int longestStreak;
  final int windowDays;
  final List<AdherenceDay> days;

  const AdherenceSummary({
    required this.currentStreak,
    required this.longestStreak,
    required this.windowDays,
    required this.days,
  });

  factory AdherenceSummary.fromJson(Map<String, dynamic> j) =>
      AdherenceSummary(
        currentStreak: (j['currentStreak'] as num?)?.toInt() ?? 0,
        longestStreak: (j['longestStreak'] as num?)?.toInt() ?? 0,
        windowDays: (j['windowDays'] as num?)?.toInt() ?? 0,
        days: ((j['days'] as List?) ?? const [])
            .map((e) => AdherenceDay.fromJson((e as Map).cast<String, dynamic>()))
            .toList(),
      );
}
