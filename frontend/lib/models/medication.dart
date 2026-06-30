import 'package:flutter/material.dart';

/// Container kinds from the design's add-flow ("this becomes the 3D object").
/// The backend has no concept of a container shape — `kind` is a presentation
/// detail kept in the local mirror cache only.
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

/// Mirrors the backend `InventoryDtos.ExpiryStatus` (OK / EXPIRING_SOON /
/// EXPIRED). Drives the expiry badge on the cabinet.
enum ExpiryStatus { ok, expiringSoon, expired }

extension ExpiryStatusJson on ExpiryStatus {
  String get wire => switch (this) {
    ExpiryStatus.ok => 'OK',
    ExpiryStatus.expiringSoon => 'EXPIRING_SOON',
    ExpiryStatus.expired => 'EXPIRED',
  };

  static ExpiryStatus parse(String? raw) => switch (raw) {
    'EXPIRING_SOON' => ExpiryStatus.expiringSoon,
    'EXPIRED' => ExpiryStatus.expired,
    _ => ExpiryStatus.ok,
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

/// Minutes-since-midnight (480) ↔ backend `"HH:mm"` ("08:00").
String minuteToHHmm(int minuteOfDay) {
  final h = (minuteOfDay ~/ 60).toString().padLeft(2, '0');
  final m = (minuteOfDay % 60).toString().padLeft(2, '0');
  return '$h:$m';
}

int hhmmToMinute(String hhmm) {
  final parts = hhmm.split(':');
  if (parts.length < 2) return 0;
  final h = int.tryParse(parts[0]) ?? 0;
  final m = int.tryParse(parts[1]) ?? 0;
  return h * 60 + m;
}

/// UI-facing aggregate for "a medicine in my cabinet with reminder times".
///
/// One [Medication] maps to a backend `user_medications` row (the inventory
/// item, identified by [id]) plus its catalog product ([catalogId]) and a
/// `DAILY` reminder schedule ([scheduleId]) whose `scheduledTimes` become
/// [reminderMinutes]. [kind] and [photoFile] are local-only presentation data.
class Medication {
  /// `user_medications.id` (UUID). Empty for a draft not yet persisted.
  final String id;

  /// `medications.id` — the global catalog product this inventory item is of.
  final String? catalogId;

  /// The reminder schedule attached to this item (`medication_schedules.id`).
  final String? scheduleId;

  final String name;

  /// Free-text dose/strength shown in the UI (maps to catalog `strength`).
  final String dose;

  final MedKind kind;

  /// Exact reminder times as minutes-since-midnight (0..1439), sorted
  /// ascending. Empty means an as-needed medication with no reminders.
  final List<int> reminderMinutes;

  /// File name (not full path) inside the app's med_photos directory.
  final String? photoFile;
  final DateTime createdAt;

  // ── Inventory facts (from user_medications) ──
  final double quantity;
  final String unit;
  final DateTime? expirationDate;
  final bool lowStock;
  final ExpiryStatus expiryStatus;

  Medication({
    required this.id,
    this.catalogId,
    this.scheduleId,
    required this.name,
    required this.dose,
    required this.kind,
    required List<int> reminderMinutes,
    this.photoFile,
    required this.createdAt,
    this.quantity = 0,
    this.unit = 'pills',
    this.expirationDate,
    this.lowStock = false,
    this.expiryStatus = ExpiryStatus.ok,
  }) : reminderMinutes = _normalize(reminderMinutes);

  static List<int> _normalize(List<int> minutes) {
    final unique = minutes.toSet().toList()..sort();
    return List.unmodifiable(unique);
  }

  /// Stable 32-bit-safe notification key derived from the UUID. The local
  /// notification plugin keys reminders by int id, so we fold the UUID's
  /// hashCode into a bounded positive range (see notification_service.dart).
  int get notificationKey => (id.hashCode & 0x7fffffff) % 20000000;

  Medication copyWith({
    String? id,
    String? catalogId,
    String? scheduleId,
    String? name,
    String? dose,
    MedKind? kind,
    List<int>? reminderMinutes,
    String? photoFile,
    bool clearPhoto = false,
    double? quantity,
    String? unit,
    DateTime? expirationDate,
    bool clearExpiration = false,
    bool? lowStock,
    ExpiryStatus? expiryStatus,
  }) {
    return Medication(
      id: id ?? this.id,
      catalogId: catalogId ?? this.catalogId,
      scheduleId: scheduleId ?? this.scheduleId,
      name: name ?? this.name,
      dose: dose ?? this.dose,
      kind: kind ?? this.kind,
      reminderMinutes: reminderMinutes ?? this.reminderMinutes,
      photoFile: clearPhoto ? null : (photoFile ?? this.photoFile),
      createdAt: createdAt,
      quantity: quantity ?? this.quantity,
      unit: unit ?? this.unit,
      expirationDate:
          clearExpiration ? null : (expirationDate ?? this.expirationDate),
      lowStock: lowStock ?? this.lowStock,
      expiryStatus: expiryStatus ?? this.expiryStatus,
    );
  }

  static String? dateToIso(DateTime? d) => d == null
      ? null
      : '${d.year.toString().padLeft(4, '0')}-'
          '${d.month.toString().padLeft(2, '0')}-'
          '${d.day.toString().padLeft(2, '0')}';

  static DateTime? isoToDate(String? s) =>
      (s == null || s.isEmpty) ? null : DateTime.tryParse(s);

  /// Local mirror-cache serialization (SQLite). Not the wire format.
  Map<String, Object?> toMap() => {
    'id': id,
    'catalog_id': catalogId,
    'schedule_id': scheduleId,
    'name': name,
    'dose': dose,
    'kind': kind.name,
    'reminder_minutes': reminderMinutes.join(','),
    'photo_file': photoFile,
    'created_at': createdAt.millisecondsSinceEpoch,
    'quantity': quantity,
    'unit': unit,
    'expiration_date': dateToIso(expirationDate),
    'low_stock': lowStock ? 1 : 0,
    'expiry_status': expiryStatus.wire,
  };

  factory Medication.fromMap(Map<String, Object?> map) {
    final raw = (map['reminder_minutes'] as String?) ?? '';
    return Medication(
      id: (map['id'] as String?) ?? '',
      catalogId: map['catalog_id'] as String?,
      scheduleId: map['schedule_id'] as String?,
      name: map['name'] as String,
      dose: (map['dose'] as String?) ?? '',
      kind: MedKind.values.asNameMap()[map['kind'] as String?] ?? MedKind.amber,
      reminderMinutes: raw.isEmpty
          ? const <int>[]
          : raw.split(',').map(int.tryParse).whereType<int>().toList(),
      photoFile: map['photo_file'] as String?,
      createdAt: DateTime.fromMillisecondsSinceEpoch(map['created_at'] as int),
      quantity: (map['quantity'] as num?)?.toDouble() ?? 0,
      unit: (map['unit'] as String?) ?? 'pills',
      expirationDate: isoToDate(map['expiration_date'] as String?),
      lowStock: (map['low_stock'] as int? ?? 0) == 1,
      expiryStatus: ExpiryStatusJson.parse(map['expiry_status'] as String?),
    );
  }
}
