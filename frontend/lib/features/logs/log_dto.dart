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
