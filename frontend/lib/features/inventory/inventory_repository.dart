import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/features/inventory/inventory_dto.dart';
import 'package:medtrack/models/medication.dart' show Medication;

/// Personal cabinet (`/user-medications`).
class InventoryRepository {
  final ApiClient api;
  InventoryRepository(this.api);

  Future<List<InventoryItem>> list() async {
    final (items, _) = await api.getList(
      '/user-medications',
      (j) => InventoryItem.fromJson((j as Map).cast<String, dynamic>()),
    );
    return items;
  }

  Future<InventoryItem> get(String id) => api.getOne(
        '/user-medications/$id',
        (j) => InventoryItem.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<InventoryItem> create({
    required String medicationId,
    required double quantity,
    String? unit,
    DateTime? expirationDate,
    String? notes,
  }) =>
      api.postJson(
        '/user-medications',
        {
          'medicationId': medicationId,
          'quantity': quantity,
          if (unit != null) 'unit': unit,
          if (expirationDate != null)
            'expirationDate': Medication.dateToIso(expirationDate),
          if (notes != null) 'notes': notes,
        },
        (j) => InventoryItem.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<InventoryItem> update(
    String id, {
    double? quantity,
    String? unit,
    DateTime? expirationDate,
    bool clearExpiration = false,
    String? notes,
  }) =>
      api.patchJson(
        '/user-medications/$id',
        {
          if (quantity != null) 'quantity': quantity,
          if (unit != null) 'unit': unit,
          if (expirationDate != null)
            'expirationDate': Medication.dateToIso(expirationDate)
          else if (clearExpiration)
            'expirationDate': null,
          if (notes != null) 'notes': notes,
        },
        (j) => InventoryItem.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<void> delete(String id) => api.deleteOne('/user-medications/$id');
}
