import 'package:medtrack/models/medication.dart' show hhmmToMinute, minuteToHHmm;

/// Backend frequency model. The app only emits `daily` schedules (a med's
/// reminder times → one daily schedule with `scheduledTimes`), but parses all
/// three so an externally-created schedule still renders.
enum FrequencyType { daily, weekly, interval }

FrequencyType _parseFrequency(String? raw) => switch (raw) {
      'weekly' => FrequencyType.weekly,
      'interval' => FrequencyType.interval,
      _ => FrequencyType.daily,
    };

/// Mirrors `com.eczam.reminders.dto.ScheduleDtos.ScheduleView`.
class ScheduleView {
  final String id;
  final String userMedicationId;
  final String medicationName;
  final double dosageAmount;
  final FrequencyType frequencyType;
  final int? frequencyValue;

  /// `["08:00","20:00"]` on the wire.
  final List<String> scheduledTimes;
  final List<int> daysOfWeek;
  final bool active;
  final DateTime? startsOn;
  final DateTime? endsOn;

  const ScheduleView({
    required this.id,
    required this.userMedicationId,
    required this.medicationName,
    required this.dosageAmount,
    required this.frequencyType,
    this.frequencyValue,
    required this.scheduledTimes,
    required this.daysOfWeek,
    required this.active,
    this.startsOn,
    this.endsOn,
  });

  /// Reminder times expressed as minutes-since-midnight, sorted.
  List<int> get reminderMinutes =>
      (scheduledTimes.map(hhmmToMinute).toList()..sort());

  factory ScheduleView.fromJson(Map<String, dynamic> j) => ScheduleView(
        id: j['id'] as String,
        userMedicationId: (j['userMedicationId'] as String?) ?? '',
        medicationName: (j['medicationName'] as String?) ?? '',
        dosageAmount: (j['dosageAmount'] as num?)?.toDouble() ?? 1,
        frequencyType: _parseFrequency(j['frequencyType'] as String?),
        frequencyValue: (j['frequencyValue'] as num?)?.toInt(),
        scheduledTimes:
            (j['scheduledTimes'] as List?)?.map((e) => e.toString()).toList() ??
                const [],
        daysOfWeek: (j['daysOfWeek'] as List?)
                ?.map((e) => (e as num).toInt())
                .toList() ??
            const [],
        active: (j['active'] as bool?) ?? true,
        startsOn: j['startsOn'] == null
            ? null
            : DateTime.tryParse(j['startsOn'] as String),
        endsOn: j['endsOn'] == null
            ? null
            : DateTime.tryParse(j['endsOn'] as String),
      );

  /// Build the wire body for a daily schedule from reminder minutes.
  static Map<String, dynamic> dailyBody({
    required double dosageAmount,
    required List<int> reminderMinutes,
  }) =>
      {
        'dosageAmount': dosageAmount,
        'frequencyType': 'daily',
        'scheduledTimes': reminderMinutes.map(minuteToHHmm).toList(),
      };
}
