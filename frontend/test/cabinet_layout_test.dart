import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/models/medication.dart';
import 'package:medtrack/ui/cabinet/cabinet_layout.dart';

const _morning = 8 * 60; // 08:00
const _midday = 14 * 60; // 14:00
const _evening = 21 * 60; // 21:00

Medication med(int id, MedKind kind, List<int> minutes) => Medication(
  id: '$id',
  name: 'Med $id',
  dose: '',
  kind: kind,
  reminderMinutes: minutes,
  createdAt: DateTime(2026, 1, 1),
);

int _rowOf(CabinetLayout layout, int id) =>
    layout.placements.firstWhere((p) => p.med.id == '$id').row;

void main() {
  test('byType groups bottles, jars/blisters and syrups onto shelves', () {
    final layout = layoutCabinet([
      med(1, MedKind.amber, [_morning]),
      med(2, MedKind.white, [_evening]),
      med(3, MedKind.jar, [_morning]),
      med(4, MedKind.blister, [_midday]),
      med(5, MedKind.syrup, [_evening]),
    ], CabinetMode.byType);

    expect(_rowOf(layout, 1), 0);
    expect(_rowOf(layout, 2), 0);
    expect(_rowOf(layout, 3), 1);
    expect(_rowOf(layout, 4), 1);
    expect(_rowOf(layout, 5), 2);
    expect(layout.rowCount, 3);
  });

  test('byTime places meds on their earliest time bucket, as-needed at '
      'bottom', () {
    final layout = layoutCabinet([
      med(1, MedKind.amber, [_midday, _evening]),
      med(2, MedKind.white, [_morning, _evening]),
      med(3, MedKind.jar, <int>[]),
    ], CabinetMode.byTime);

    expect(_rowOf(layout, 1), 1);
    expect(_rowOf(layout, 2), 0);
    expect(_rowOf(layout, 3), 2);
  });

  test('objects stand on the shelf surface', () {
    final layout = layoutCabinet([
      med(1, MedKind.syrup, [_evening]),
    ], CabinetMode.byType);
    final p = layout.placements.single;
    // Bottom edge = top + height = row * 188 + 158.
    expect(p.top + p.size.height, p.row * kCompartmentHeight + kShelfSurfaceY);
  });

  test('always renders three labelled shelves when nothing overflows', () {
    final layout = layoutCabinet([
      med(1, MedKind.amber, [_morning]),
    ], CabinetMode.byType);

    expect(layout.rowCount, 3);
    // One label per category, no continuation rows.
    expect(
      layout.shelves.map((s) => s.label).whereType<String>(),
      hasLength(3),
    );
  });

  test('a category over the limit splits across extra shelves below it', () {
    final layout = layoutCabinet([
      for (var i = 1; i <= 8; i++) med(i, MedKind.amber, [_morning]),
    ], CabinetMode.byType, maxPerShelf: 5);

    // 8 pill bottles -> 5 on row 0, 3 on a new row 1; jars/syrups shift down.
    expect(layout.rowCount, 4);
    expect(layout.placements.where((p) => p.row == 0), hasLength(5));
    expect(layout.placements.where((p) => p.row == 1), hasLength(3));

    // Title only above the first chunk; the continuation row has no label.
    expect(layout.shelves[0].label, 'Pill bottles');
    expect(layout.shelves[1].label, isNull);
    expect(layout.shelves[1].categoryIndex, 0);
    expect(layout.shelves[2].label, 'Jars & blisters');
    expect(layout.shelves[3].label, 'Syrups & liquids');
  });

  test('chunked rows keep creation order and never overflow the cabinet', () {
    final layout = layoutCabinet([
      for (var i = 1; i <= 6; i++) med(i, MedKind.amber, [_morning]),
    ], CabinetMode.byType, maxPerShelf: 5);

    expect(layout.placements.map((p) => p.med.id),
        ['1', '2', '3', '4', '5', '6']);
    for (final p in layout.placements) {
      expect(p.x, greaterThanOrEqualTo(0));
      expect(p.x + p.size.width, lessThanOrEqualTo(kCabinetInnerWidth));
    }
  });

  test('shelf labels match the active mode', () {
    expect(shelfLabels(CabinetMode.byTime), ['Morning', 'Midday', 'Evening']);
    expect(shelfLabels(CabinetMode.byType), hasLength(3));
  });
}
