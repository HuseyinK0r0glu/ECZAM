import 'dart:ui';

import 'package:medtrack/models/medication.dart';

/// Cabinet coordinate space from the design: the inside of the frame is
/// 348 wide with 188-tall compartments. Objects stand with their bottom edge
/// on the shelf surface at y = row * 188 + 158. The number of rows is dynamic:
/// a category that exceeds [kMaxItemsPerShelf] is split across extra shelves
/// stacked below it, so the cabinet grows taller instead of crowding a row.
const double kCabinetInnerWidth = 348;
const double kCompartmentHeight = 188;
const double kShelfSurfaceY = 158;
const double kFramePadding = 11;

/// How many objects sit comfortably on one shelf before it is split in two.
const int kMaxItemsPerShelf = 5;

enum CabinetMode { byType, byTime }

/// Object footprint per container kind (design kindStyles).
Size kindSize(MedKind kind) => switch (kind) {
  MedKind.amber => const Size(54, 88),
  MedKind.white => const Size(56, 94),
  MedKind.syrup => const Size(56, 104),
  MedKind.jar => const Size(70, 78),
  MedKind.blister => const Size(68, 80),
};

/// Accent swatch used on schedule rows, derived from the container body.
Color kindSwatch(MedKind kind) => switch (kind) {
  MedKind.amber => const Color(0xFFD08A22),
  MedKind.white => const Color(0xFFC5C1B6),
  MedKind.syrup => const Color(0xFF5C2E08),
  MedKind.jar => const Color(0xFFE8A026),
  MedKind.blister => const Color(0xFF9AA0AB),
};

List<String> shelfLabels(CabinetMode mode) => switch (mode) {
  CabinetMode.byType => const [
    'Pill bottles',
    'Jars & blisters',
    'Syrups & liquids',
  ],
  CabinetMode.byTime => const ['Morning', 'Midday', 'Evening'],
};

int _shelfFor(Medication med, CabinetMode mode) {
  if (mode == CabinetMode.byType) {
    return switch (med.kind) {
      MedKind.amber || MedKind.white => 0,
      MedKind.jar || MedKind.blister => 1,
      MedKind.syrup => 2,
    };
  }
  // By time of day: a med lives on the shelf for its earliest reminder's
  // bucket; as-needed meds (no reminders) sit on the bottom shelf.
  if (med.reminderMinutes.isEmpty) return 2;
  final earliest = med.reminderMinutes.reduce((a, b) => a < b ? a : b);
  return bucketForMinute(earliest).index;
}

/// One rendered shelf row. [label] is set only on the first row of a category;
/// continuation rows leave it null so a split section reads as one cabinet.
class CabinetShelf {
  final int categoryIndex;
  final String? label;

  const CabinetShelf({required this.categoryIndex, required this.label});
}

class MedPlacement {
  final Medication med;

  /// Index of the rendered shelf row this object sits on (vertical position).
  final int row;
  final double x;
  final double top;
  final Size size;

  const MedPlacement({
    required this.med,
    required this.row,
    required this.x,
    required this.top,
    required this.size,
  });
}

/// The full cabinet layout: the ordered rows to render plus every object's
/// placement. [rowCount] drives the cabinet's height.
class CabinetLayout {
  final List<CabinetShelf> shelves;
  final List<MedPlacement> placements;

  const CabinetLayout({required this.shelves, required this.placements});

  int get rowCount => shelves.length;
}

/// Buckets meds into the three categories for [mode], then splits any category
/// holding more than [maxPerShelf] items across additional shelves stacked
/// below it. Empty categories still render one (labelled) empty shelf so the
/// cabinet keeps its three sections.
CabinetLayout layoutCabinet(
  List<Medication> meds,
  CabinetMode mode, {
  int maxPerShelf = kMaxItemsPerShelf,
}) {
  final categories = <int, List<Medication>>{0: [], 1: [], 2: []};
  for (final med in meds) {
    categories[_shelfFor(med, mode)]!.add(med);
  }
  final labels = shelfLabels(mode);

  final shelves = <CabinetShelf>[];
  final placements = <MedPlacement>[];
  for (var category = 0; category < 3; category++) {
    final chunks = _chunk(categories[category]!, maxPerShelf);
    for (var chunk = 0; chunk < chunks.length; chunk++) {
      final row = shelves.length;
      shelves.add(
        CabinetShelf(
          categoryIndex: category,
          // Title only above the first shelf of the category.
          label: chunk == 0 ? labels[category] : null,
        ),
      );
      placements.addAll(_packRow(chunks[chunk], row));
    }
  }
  return CabinetLayout(shelves: shelves, placements: placements);
}

/// Splits [meds] into groups of at most [size]; an empty list yields a single
/// empty group so the category still gets one shelf.
List<List<Medication>> _chunk(List<Medication> meds, int size) {
  if (meds.isEmpty) return [<Medication>[]];
  return [
    for (var i = 0; i < meds.length; i += size)
      meds.sublist(i, i + size > meds.length ? meds.length : i + size),
  ];
}

/// Spreads one shelf's objects evenly across the cabinet width (the restored
/// non-scrolling layout). Capped rows fit comfortably; a near-full row of wide
/// jars still packs gently rather than overflowing.
List<MedPlacement> _packRow(List<Medication> meds, int row) {
  if (meds.isEmpty) return const [];
  final widths = meds
      .map((m) => kindSize(m.kind).width)
      .toList(growable: false);
  final total = widths.fold(0.0, (sum, w) => sum + w);
  final free = kCabinetInnerWidth - total;
  final pad = free / (meds.length + 1);
  final gap = pad.clamp(0.0, double.infinity);
  // When pad goes negative the row is overfull: pack from a small margin and
  // let objects sit flush against each other.
  var x = pad > 0 ? pad : 4.0;
  final squeeze = pad > 0
      ? 0.0
      : (total - (kCabinetInnerWidth - 8)) /
            (meds.length - 1).clamp(1, 1000);

  final placements = <MedPlacement>[];
  for (var i = 0; i < meds.length; i++) {
    final size = kindSize(meds[i].kind);
    placements.add(
      MedPlacement(
        med: meds[i],
        row: row,
        x: x,
        top: row * kCompartmentHeight + kShelfSurfaceY - size.height,
        size: size,
      ),
    );
    x += size.width + gap - squeeze;
  }
  return placements;
}
