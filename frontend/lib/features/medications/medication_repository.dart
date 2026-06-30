import 'package:medtrack/core/api/api_client.dart';
import 'package:medtrack/core/api/api_envelope.dart';
import 'package:medtrack/features/medications/medication_dto.dart';

/// Read-mostly access to the global medication catalog (`/medications`).
class CatalogRepository {
  final ApiClient api;
  CatalogRepository(this.api);

  Future<List<CatalogMedication>> search(String query, {int limit = 20}) async {
    final (items, _) = await api.getList(
      '/medications',
      (j) => CatalogMedication.fromJson((j as Map).cast<String, dynamic>()),
      query: {if (query.isNotEmpty) 'q': query, 'limit': limit},
    );
    return items;
  }

  Future<CatalogMedicationDetail> get(String id) => api.getOne(
        '/medications/$id',
        (j) =>
            CatalogMedicationDetail.fromJson((j as Map).cast<String, dynamic>()),
      );

  Future<CatalogMedicationDetail> leaflet(String id) => api.getOne(
        '/medications/$id/leaflet',
        (j) =>
            CatalogMedicationDetail.fromJson((j as Map).cast<String, dynamic>()),
      );

  /// Semantic search within one medication's leaflet (vector similarity).
  Future<List<LeafletSearchHit>> searchLeaflet(String id, String query) =>
      api.getOne(
        '/medications/$id/leaflet/search',
        (j) => ((j as Map)['hits'] as List? ?? const [])
            .map((h) => LeafletSearchHit.fromJson((h as Map).cast<String, dynamic>()))
            .toList(),
        query: {'q': query},
      );

  /// Returns the catalog medication for a scanned barcode, or null on 404.
  Future<CatalogMedication?> byBarcode(String code) async {
    try {
      final detail = await api.getOne(
        '/medications/barcode/$code',
        (j) => CatalogMedicationDetail.fromJson(
            (j as Map).cast<String, dynamic>()),
      );
      return detail.medication;
    } on ApiException catch (e) {
      if (e.statusCode == 404 || e.code == 'BARCODE_NOT_FOUND') return null;
      rethrow;
    }
  }

  Future<CatalogMedication> create({
    required String name,
    String? genericName,
    String? manufacturer,
    String? barcode,
    String? form,
    String? strength,
  }) =>
      api.postJson(
        '/medications',
        {
          'name': name,
          if (genericName != null) 'genericName': genericName,
          if (manufacturer != null) 'manufacturer': manufacturer,
          if (barcode != null) 'barcode': barcode,
          if (form != null) 'form': form,
          if (strength != null) 'strength': strength,
        },
        (j) => CatalogMedicationDetail.fromJson(
                (j as Map).cast<String, dynamic>())
            .medication,
      );

  /// Find-or-create used by the add-medication orchestration: reuse an existing
  /// catalog row whose name matches (case-insensitive), else create one.
  Future<CatalogMedication> findOrCreate({
    required String name,
    String? strength,
    String? form,
    String? barcode,
  }) async {
    final matches = await search(name, limit: 20);
    final lower = name.trim().toLowerCase();
    final existing = matches.where((m) => m.name.trim().toLowerCase() == lower);
    if (existing.isNotEmpty) return existing.first;
    return create(name: name, strength: strength, form: form, barcode: barcode);
  }
}
