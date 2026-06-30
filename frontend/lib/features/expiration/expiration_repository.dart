import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/features/inventory/inventory_dto.dart';

/// Proactive expiry monitoring (`/expiration`). Both endpoints return inventory
/// items with their derived status flags.
class ExpirationRepository {
  final ApiClient api;
  ExpirationRepository(this.api);

  Future<List<InventoryItem>> expiringSoon({int? days}) async {
    final (items, _) = await api.getList(
      '/expiration/expiring-soon',
      (j) => InventoryItem.fromJson((j as Map).cast<String, dynamic>()),
      query: {if (days != null) 'days': days},
    );
    return items;
  }

  Future<List<InventoryItem>> expired() async {
    final (items, _) = await api.getList(
      '/expiration/expired',
      (j) => InventoryItem.fromJson((j as Map).cast<String, dynamic>()),
    );
    return items;
  }
}
