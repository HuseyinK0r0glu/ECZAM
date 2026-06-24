import 'package:flutter/material.dart';

/// Container kinds from the design's add-flow ("this becomes the 3D object").
enum MedKind { amber, white, syrup, jar, blister }

extension MedKindLabel on MedKind {
  String get label => switch (this) {
    MedKind.amber => 'Amber pill bottle',
    MedKind.white => 'Pharmacy bottle',
    MedKind.syrup => 'Syrup bottle',
    MedKind.jar => 'Gel-cap jar',
    MedKind.blister => 'Blister pack',
  };
}

/// Coarse time-of-day bucket, derived from an exact reminder minute. Used only
/// for grouping on the schedule and placement on the cabinet's "by time of
/// day" shelves — reminders themselves are scheduled at exact minutes.
enum DaySlot { morning, midday, evening }

extension DaySlotInfo on DaySlot {
  String get label => switch (this) {
    DaySlot.morning => 'Morning',
    DaySlot.midday => 'Midday',
    DaySlot.evening => 'Evening',
  };
}

/// Buckets: morning < 12:00 <= midday < 17:00 <= evening.
DaySlot bucketForMinute(int minuteOfDay) {
  if (minuteOfDay < 12 * 60) return DaySlot.morning;
  if (minuteOfDay < 17 * 60) return DaySlot.midday;
  return DaySlot.evening;
}

TimeOfDay minuteToTimeOfDay(int minuteOfDay) =>
    TimeOfDay(hour: minuteOfDay ~/ 60, minute: minuteOfDay % 60);

int timeOfDayToMinute(TimeOfDay time) => time.hour * 60 + time.minute;

class Medication {
  final int id;
  final String name;
  final String dose;
  final MedKind kind;

  /// Exact reminder times as minutes-since-midnight (0..1439), sorted
  /// ascending. Empty means an as-needed medication with no reminders.
  final List<int> reminderMinutes;

  /// File name (not full path) inside the app's med_photos directory.
  /// Stored relative because the iOS app container path changes on update.
  final String? photoFile;
  final DateTime createdAt;

  Medication({
    required this.id,
    required this.name,
    required this.dose,
    required this.kind,
    required List<int> reminderMinutes,
    this.photoFile,
    required this.createdAt,
  }) : reminderMinutes = _normalize(reminderMinutes);

  static List<int> _normalize(List<int> minutes) {
    final unique = minutes.toSet().toList()..sort();
    return List.unmodifiable(unique);
  }

  Medication copyWith({
    String? name,
    String? dose,
    MedKind? kind,
    List<int>? reminderMinutes,
    String? photoFile,
    bool clearPhoto = false,
  }) {
    return Medication(
      id: id,
      name: name ?? this.name,
      dose: dose ?? this.dose,
      kind: kind ?? this.kind,
      reminderMinutes: reminderMinutes ?? this.reminderMinutes,
      photoFile: clearPhoto ? null : (photoFile ?? this.photoFile),
      createdAt: createdAt,
    );
  }

  Map<String, Object?> toMap() => {
    'name': name,
    'dose': dose,
    'kind': kind.name,
    'reminder_minutes': reminderMinutes.join(','),
    'photo_file': photoFile,
    'created_at': createdAt.millisecondsSinceEpoch,
  };

  factory Medication.fromMap(Map<String, Object?> map) {
    final raw = (map['reminder_minutes'] as String?) ?? '';
    return Medication(
      id: map['id'] as int,
      name: map['name'] as String,
      dose: map['dose'] as String,
      kind: MedKind.values.asNameMap()[map['kind'] as String] ?? MedKind.amber,
      reminderMinutes: raw.isEmpty
          ? const <int>[]
          : raw.split(',').map(int.tryParse).whereType<int>().toList(),
      photoFile: map['photo_file'] as String?,
      createdAt: DateTime.fromMillisecondsSinceEpoch(map['created_at'] as int),
    );
  }
}
