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

/// One row of `GET /medication-logs/export` — a flattened, cross-medication
/// dose-history entry for a doctor/pharmacist hand-off. Mirrors the backend's
/// `LogDtos.ExportLogEntry` (`takenAt` is an ISO-8601 string on the wire).
class ExportLogEntry {
  final DateTime takenAt;
  final String medicationName;
  final double quantityUsed;
  final String? notes;

  const ExportLogEntry({
    required this.takenAt,
    required this.medicationName,
    required this.quantityUsed,
    this.notes,
  });

  factory ExportLogEntry.fromJson(Map<String, dynamic> j) => ExportLogEntry(
        takenAt: DateTime.parse(j['takenAt'] as String),
        medicationName: (j['medicationName'] as String?) ?? '',
        quantityUsed: (j['quantityUsed'] as num?)?.toDouble() ?? 0,
        notes: j['notes'] as String?,
      );
}

/// Pure (no I/O, no Flutter) conversion of exported dose-history rows into a
/// CSV string, RFC 4180-escaped: a field containing a comma, double quote, or
/// newline is wrapped in double quotes with internal quotes doubled.
/// Header: `Date,Time,Medication,Quantity,Notes`. Dates/times render in the
/// caller's local time zone (each [ExportLogEntry.takenAt] is converted with
/// `.toLocal()`), since `takenAt` off the wire may carry a UTC offset.
String exportLogEntriesToCsv(List<ExportLogEntry> entries) {
  final buffer = StringBuffer()
    ..write('Date,Time,Medication,Quantity,Notes\r\n');
  for (final e in entries) {
    final local = e.takenAt.toLocal();
    final date = _twoDigitDate(local);
    final time = _twoDigitTime(local);
    final quantity = _formatQuantity(e.quantityUsed);
    final fields = [
      date,
      time,
      e.medicationName,
      quantity,
      e.notes ?? '',
    ].map(_csvEscape).join(',');
    buffer.write('$fields\r\n');
  }
  return buffer.toString();
}

String _twoDigitDate(DateTime d) {
  final y = d.year.toString().padLeft(4, '0');
  final m = d.month.toString().padLeft(2, '0');
  final day = d.day.toString().padLeft(2, '0');
  return '$y-$m-$day';
}

String _twoDigitTime(DateTime d) {
  final h = d.hour.toString().padLeft(2, '0');
  final min = d.minute.toString().padLeft(2, '0');
  return '$h:$min';
}

String _formatQuantity(double q) =>
    q == q.roundToDouble() ? q.toStringAsFixed(0) : q.toString();

/// Escapes a single CSV field per RFC 4180: wraps in double quotes (doubling
/// any internal quote) when the field contains a comma, quote, or newline.
String _csvEscape(String field) {
  final needsQuoting =
      field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r');
  if (!needsQuoting) return field;
  final escaped = field.replaceAll('"', '""');
  return '"$escaped"';
}
