/// DTOs mirroring `com.eczam.medications.dto.MedicationDtos` (the global
/// catalog). The catalog is read-only for the app except for find-or-create
/// during the add-medication flow.
library;

class CatalogMedication {
  final String id;
  final String name;
  final String? genericName;
  final String? manufacturer;
  final String? barcode;
  final String? form;
  final String? strength;
  final bool vectorIndexed;

  const CatalogMedication({
    required this.id,
    required this.name,
    this.genericName,
    this.manufacturer,
    this.barcode,
    this.form,
    this.strength,
    this.vectorIndexed = false,
  });

  factory CatalogMedication.fromJson(Map<String, dynamic> j) =>
      CatalogMedication(
        id: j['id'] as String,
        name: (j['name'] as String?) ?? '',
        genericName: j['genericName'] as String?,
        manufacturer: j['manufacturer'] as String?,
        barcode: j['barcode'] as String?,
        form: j['form'] as String?,
        strength: j['strength'] as String?,
        vectorIndexed: (j['vectorIndexed'] as bool?) ?? false,
      );
}

/// `LeafletSections` JSONB (dosage / side_effects / contraindications /
/// storage / interactions / missed_dose). Surfaced read-only on the leaflet
/// view. Stored sparsely — any field may be null.
class LeafletSections {
  final String? dosage;
  final String? sideEffects;
  final String? contraindications;
  final String? storage;
  final String? interactions;
  final String? missedDose;

  const LeafletSections({
    this.dosage,
    this.sideEffects,
    this.contraindications,
    this.storage,
    this.interactions,
    this.missedDose,
  });

  bool get isEmpty =>
      dosage == null &&
      sideEffects == null &&
      contraindications == null &&
      storage == null &&
      interactions == null &&
      missedDose == null;

  factory LeafletSections.fromJson(Map<String, dynamic> j) => LeafletSections(
        dosage: j['dosage'] as String?,
        sideEffects: j['side_effects'] as String?,
        contraindications: j['contraindications'] as String?,
        storage: j['storage'] as String?,
        interactions: j['interactions'] as String?,
        missedDose: j['missed_dose'] as String?,
      );

  /// Ordered (label, text) pairs for non-null sections, for display.
  List<(String, String)> get entries => [
        if (dosage != null) ('Dosage', dosage!),
        if (sideEffects != null) ('Side effects', sideEffects!),
        if (contraindications != null) ('Contraindications', contraindications!),
        if (storage != null) ('Storage', storage!),
        if (interactions != null) ('Interactions', interactions!),
        if (missedDose != null) ('Missed dose', missedDose!),
      ];
}

/// One semantic hit from `GET /medications/{id}/leaflet/search`.
class LeafletSearchHit {
  final String section;
  final String snippet;

  const LeafletSearchHit({required this.section, required this.snippet});

  factory LeafletSearchHit.fromJson(Map<String, dynamic> j) => LeafletSearchHit(
        section: (j['section'] as String?) ?? '',
        snippet: (j['snippet'] as String?) ?? '',
      );
}

class CatalogMedicationDetail {
  final CatalogMedication medication;
  final LeafletSections leafletSections;

  const CatalogMedicationDetail({
    required this.medication,
    required this.leafletSections,
  });

  factory CatalogMedicationDetail.fromJson(Map<String, dynamic> j) =>
      CatalogMedicationDetail(
        medication: CatalogMedication.fromJson(j),
        leafletSections: j['leafletSections'] is Map
            ? LeafletSections.fromJson(
                (j['leafletSections'] as Map).cast<String, dynamic>())
            : const LeafletSections(),
      );
}
