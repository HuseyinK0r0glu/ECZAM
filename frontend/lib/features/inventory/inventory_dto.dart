import 'package:medtrack/models/medication.dart' show ExpiryStatus, ExpiryStatusJson;

/// Mirrors `com.eczam.inventory.dto.InventoryDtos.InventoryItem` — one
/// `user_medications` row with derived `lowStock` / `expiryStatus` flags.
class InventoryItem {
  final String id;
  final String medicationId;
  final String medicationName;
  final String? strength;
  final String? form;
  final double quantity;
  final String? unit;
  final DateTime? expirationDate; // date-only
  final String? notes;
  // Per-physical-box GS1 facts (AI 10 / AI 21).
  final String? batch;
  final String? serialNumber;
  final bool lowStock;
  final ExpiryStatus expiryStatus;

  const InventoryItem({
    required this.id,
    required this.medicationId,
    required this.medicationName,
    this.strength,
    this.form,
    required this.quantity,
    this.unit,
    this.expirationDate,
    this.notes,
    this.batch,
    this.serialNumber,
    required this.lowStock,
    required this.expiryStatus,
  });

  factory InventoryItem.fromJson(Map<String, dynamic> j) => InventoryItem(
        id: j['id'] as String,
        medicationId: (j['medicationId'] as String?) ?? '',
        medicationName: (j['medicationName'] as String?) ?? '',
        strength: j['strength'] as String?,
        form: j['form'] as String?,
        quantity: (j['quantity'] as num?)?.toDouble() ?? 0,
        unit: j['unit'] as String?,
        expirationDate: j['expirationDate'] == null
            ? null
            : DateTime.tryParse(j['expirationDate'] as String),
        notes: j['notes'] as String?,
        batch: j['batch'] as String?,
        serialNumber: j['serialNumber'] as String?,
        lowStock: (j['lowStock'] as bool?) ?? false,
        expiryStatus: ExpiryStatusJson.parse(j['expiryStatus'] as String?),
      );
}
