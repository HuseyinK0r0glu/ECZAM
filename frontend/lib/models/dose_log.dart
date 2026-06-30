enum DoseStatus { taken, skipped, snoozed }

class DoseLog {
  /// Local mirror row id (auto-increment int as text) or the backend log UUID
  /// once synced. Empty for a brand-new local row before insert.
  final String id;

  /// `user_medications.id` of the medication this dose belongs to (UUID).
  final String medId;

  /// Name/dose are snapshotted so history stays readable after a
  /// medication is deleted (FR-3.2).
  final String medName;
  final String medDose;

  /// The exact reminder time this dose belongs to (minutes-since-midnight).
  final int minuteOfDay;

  /// Calendar day the dose belongs to, formatted yyyy-MM-dd.
  final String dateKey;
  final DoseStatus status;
  final DateTime scheduledAt;
  final DateTime loggedAt;

  const DoseLog({
    required this.id,
    required this.medId,
    required this.medName,
    required this.medDose,
    required this.minuteOfDay,
    required this.dateKey,
    required this.status,
    required this.scheduledAt,
    required this.loggedAt,
  });

  /// Taken more than 60 minutes after the scheduled time.
  bool get isLate =>
      status == DoseStatus.taken &&
      loggedAt.difference(scheduledAt).inMinutes > 60;

  Map<String, Object?> toMap() => {
    if (id.isNotEmpty) 'id': id,
    'med_id': medId,
    'med_name': medName,
    'med_dose': medDose,
    'minute_of_day': minuteOfDay,
    'date_key': dateKey,
    'status': status.name,
    'scheduled_at': scheduledAt.millisecondsSinceEpoch,
    'logged_at': loggedAt.millisecondsSinceEpoch,
  };

  factory DoseLog.fromMap(Map<String, Object?> map) => DoseLog(
    id: map['id'] == null ? '' : map['id'].toString(),
    medId: map['med_id'].toString(),
    medName: map['med_name'] as String,
    medDose: map['med_dose'] as String,
    minuteOfDay: map['minute_of_day'] as int,
    dateKey: map['date_key'] as String,
    status:
        DoseStatus.values.asNameMap()[map['status'] as String] ??
        DoseStatus.taken,
    scheduledAt: DateTime.fromMillisecondsSinceEpoch(
      map['scheduled_at'] as int,
    ),
    loggedAt: DateTime.fromMillisecondsSinceEpoch(map['logged_at'] as int),
  );

  /// Builds the scheduled DateTime for [minuteOfDay] on [day].
  static DateTime scheduledAtFor(DateTime day, int minuteOfDay) => DateTime(
    day.year,
    day.month,
    day.day,
    minuteOfDay ~/ 60,
    minuteOfDay % 60,
  );

  static String dateKeyFor(DateTime day) =>
      '${day.year.toString().padLeft(4, '0')}-'
      '${day.month.toString().padLeft(2, '0')}-'
      '${day.day.toString().padLeft(2, '0')}';
}
