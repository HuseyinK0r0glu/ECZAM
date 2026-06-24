import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/models/medication.dart';
import 'package:medtrack/state/app_state.dart';
import 'package:medtrack/theme/med_theme.dart';
import 'package:medtrack/ui/cabinet/action_panel.dart';
import 'package:medtrack/ui/cabinet/cabinet_layout.dart';
import 'package:medtrack/ui/cabinet/med_object.dart';

const double _cabinetOuterWidth = kCabinetInnerWidth + 2 * kFramePadding;

class CabinetScreen extends StatefulWidget {
  final void Function(Medication med) onEditMed;

  const CabinetScreen({super.key, required this.onEditMed});

  @override
  State<CabinetScreen> createState() => _CabinetScreenState();
}

class _CabinetScreenState extends State<CabinetScreen>
    with TickerProviderStateMixin {
  CabinetMode _mode = CabinetMode.byType;
  int? _selectedId;

  late final AnimationController _floatCtrl = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 3600),
  );
  late final AnimationController _ledCtrl = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 3400),
  )..repeat();

  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _floatCtrl.dispose();
    _ledCtrl.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _select(int? id, [BuildContext? itemContext]) {
    setState(() => _selectedId = _selectedId == id ? null : id);
    if (_selectedId != null) {
      _floatCtrl.repeat();
      // Lift the tapped bottle to the upper-middle so the zoom lands it clear
      // of the bottom action panel. Runs while the cabinet transform is still
      // identity (the rebuild that applies the zoom hasn't happened yet), so
      // the scroll target is computed against the un-zoomed layout.
      if (itemContext != null && itemContext.mounted) {
        Scrollable.ensureVisible(
          itemContext,
          alignment: 0.28,
          duration: const Duration(milliseconds: 450),
          curve: medEase,
        );
      }
    } else {
      _floatCtrl.stop();
    }
  }

  void _setMode(CabinetMode mode) {
    if (_mode == mode) return;
    setState(() {
      _mode = mode;
      _selectedId = null;
    });
    _floatCtrl.stop();
  }

  /// A horizontal swipe across the cabinet toggles the two grouping modes,
  /// mirroring the switcher chips: swipe left reveals "by time of day" (the
  /// right chip), swipe right "by type" (the left chip).
  void _onModeSwipe(DragEndDetails details) {
    final velocity = details.primaryVelocity ?? 0;
    if (velocity.abs() < 200) return;
    _setMode(velocity < 0 ? CabinetMode.byTime : CabinetMode.byType);
  }

  @override
  Widget build(BuildContext context) {
    final app = context.watch<AppState>();
    final layout = layoutCabinet(app.meds, _mode);
    final placements = layout.placements;
    final selectedPlacement = _selectedId == null
        ? null
        : placements.where((p) => p.med.id == _selectedId).firstOrNull;
    final selected = selectedPlacement?.med;
    final outerHeight =
        layout.rowCount * kCompartmentHeight + 2 * kFramePadding;
    final topPad = MediaQuery.paddingOf(context).top;

    return Stack(
      children: [
        Column(
          children: [
            SizedBox(height: topPad + 56),
            Center(
              child: _ModeSwitcher(mode: _mode, onChanged: _setMode),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) => GestureDetector(
                  // Horizontal swipe toggles By type / By time of day; the
                  // vertical scroll keeps the two drag axes from colliding.
                  onHorizontalDragEnd: _onModeSwipe,
                  child: SingleChildScrollView(
                    controller: _scrollController,
                    // Full-size cabinet at its design dimensions — no shrinking.
                    // The extra bottom room lets a tapped low shelf scroll up
                    // clear of the bottom action panel.
                    padding: EdgeInsets.only(
                      bottom: 116 + constraints.maxHeight * 0.5,
                    ),
                    child: Center(
                      child: SizedBox(
                        width: _cabinetOuterWidth,
                        height: outerHeight,
                        child: _buildCabinet(
                          app,
                          layout,
                          selectedPlacement,
                          outerHeight,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
        if (selected != null) ...[
          Positioned.fill(
            child: GestureDetector(
              onTap: () => _select(null),
              child: const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    center: Alignment(0, -0.16),
                    radius: 1.1,
                    colors: [Color(0x00082428), Color(0x61082428)],
                    stops: [0.3, 1],
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            left: 26,
            right: 26,
            bottom: 142,
            child: ActionPanel(
              med: selected,
              photoPath: app.photoPath(selected),
              onLogDose: () => _logDose(app, selected),
              onEdit: () {
                _select(null);
                widget.onEditMed(selected);
              },
              onDelete: () => _confirmDelete(app, selected),
              onPickPhoto: (source) => _pickPhoto(app, selected, source),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildCabinet(
    AppState app,
    CabinetLayout layout,
    MedPlacement? selectedPlacement,
    double outerHeight,
  ) {
    final placements = layout.placements;
    // Camera zoom onto the selected object: scale(2.05) translateY(14)
    // about the object's center, like the design's camTransform. Unchanged by
    // chunking — it still targets the bottle's exact row + x.
    Matrix4 transform = Matrix4.identity();
    AlignmentGeometry alignment = Alignment.center;
    if (selectedPlacement != null) {
      final p = selectedPlacement;
      final ox = p.x + p.size.width / 2 + kFramePadding;
      final oy =
          p.row * kCompartmentHeight +
          kShelfSurfaceY -
          p.size.height / 2 +
          kFramePadding;
      alignment = Alignment(
        ox / _cabinetOuterWidth * 2 - 1,
        oy / outerHeight * 2 - 1,
      );
      transform = Matrix4.identity()
        ..scaleByDouble(2.05, 2.05, 1, 1)
        ..translateByDouble(0, 14, 0, 1);
    }

    return AnimatedContainer(
      duration: const Duration(milliseconds: 850),
      curve: medEase,
      transform: transform,
      transformAlignment: alignment,
      child: Container(
        padding: const EdgeInsets.all(kFramePadding),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          gradient: const LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              MedColors.frameTop,
              MedColors.frameMid,
              MedColors.frameBottom,
            ],
            stops: [0, 0.6, 1],
          ),
          boxShadow: const [
            BoxShadow(
              color: Color(0x73403626),
              offset: Offset(0, 28),
              blurRadius: 60,
              spreadRadius: -18,
            ),
            BoxShadow(
              color: Color(0x2E403626),
              offset: Offset(0, 4),
              blurRadius: 14,
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Stack(
            children: [
              Container(color: MedColors.cabinetInner),
              for (var row = 0; row < layout.rowCount; row++)
                _Compartment(
                  shelf: row,
                  ledCtrl: _ledCtrl,
                  isLast: row == layout.rowCount - 1,
                ),
              for (var row = 0; row < layout.rowCount; row++)
                if (layout.shelves[row].label != null)
                  Positioned(
                    top: row * kCompartmentHeight + 10,
                    left: 14,
                    child: Text(
                      layout.shelves[row].label!.toUpperCase(),
                      style: const TextStyle(
                        fontSize: 9,
                        letterSpacing: 1.6,
                        fontWeight: FontWeight.w600,
                        color: Color(0x8C5A4C37),
                      ),
                    ),
                  ),
              if (placements.isEmpty)
                const Positioned(
                  top: kCompartmentHeight,
                  left: 24,
                  right: 24,
                  height: kCompartmentHeight,
                  child: Center(
                    child: Text(
                      'Your cabinet is empty.\nTap + to add your first '
                      'medication.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontFamily: MedText.serif,
                        fontStyle: FontStyle.italic,
                        fontSize: 16,
                        height: 1.5,
                        color: MedColors.textMuted,
                      ),
                    ),
                  ),
                ),
              for (final p in placements)
                AnimatedPositioned(
                  key: ValueKey('med-${p.med.id}'),
                  duration: const Duration(milliseconds: 800),
                  curve: medEase,
                  left: p.x,
                  top: p.top,
                  width: p.size.width,
                  height: p.size.height,
                  child: Builder(
                    builder: (itemContext) => GestureDetector(
                      onTap: () => _select(p.med.id, itemContext),
                      child: _FloatWhenSelected(
                        floating: p.med.id == _selectedId,
                        controller: _floatCtrl,
                        child: MedObject(
                          med: p.med,
                          photoPath: app.photoPath(p.med),
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _logDose(AppState app, Medication med) async {
    final minute = await app.logNextPendingDose(med);
    _select(null);
    if (!mounted) return;
    final time = minuteToTimeOfDay(minute).format(context);
    _showSnack('${med.name} logged — $time');
  }

  Future<void> _confirmDelete(AppState app, Medication med) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: const Color(0xFFFAF7F1),
        title: Text('Remove ${med.name}?', style: MedText.sheetTitle),
        content: const Text(
          'Reminders for this medication will be cancelled. Your dose '
          'history is kept.',
          style: TextStyle(color: MedColors.textSoft, height: 1.4),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text(
              'Keep',
              style: TextStyle(color: MedColors.textSoft),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text(
              'Delete',
              style: TextStyle(
                color: MedColors.danger,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    _select(null);
    await app.deleteMedication(med);
    if (mounted) _showSnack('${med.name} removed from your cabinet');
  }

  Future<void> _pickPhoto(AppState app, Medication med, dynamic source) async {
    final ok = await app.attachPhoto(med, source);
    if (!ok && mounted) {
      _showSnack(
        'Could not get a photo — check camera/photo permissions '
        'in Settings',
      );
    }
  }

  void _showSnack(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        behavior: SnackBarBehavior.floating,
        backgroundColor: MedColors.text,
        margin: const EdgeInsets.fromLTRB(24, 0, 24, 132),
        duration: const Duration(seconds: 2),
      ),
    );
  }
}

class _ModeSwitcher extends StatelessWidget {
  final CabinetMode mode;
  final void Function(CabinetMode) onChanged;

  const _ModeSwitcher({required this.mode, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: const Color(0x80FFFFFF),
        boxShadow: const [
          BoxShadow(
            color: Color(0x24403626),
            offset: Offset(0, 2),
            blurRadius: 8,
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _chip('By type', CabinetMode.byType),
          const SizedBox(width: 2),
          _chip('By time of day', CabinetMode.byTime),
        ],
      ),
    );
  }

  Widget _chip(String label, CabinetMode value) {
    final active = mode == value;
    return GestureDetector(
      onTap: () => onChanged(value),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(999),
          color: active ? MedColors.switcherActive : Colors.transparent,
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 11.5,
            fontWeight: FontWeight.w600,
            color: active ? MedColors.switcherActiveText : MedColors.textSoft,
          ),
        ),
      ),
    );
  }
}

/// Gentle hover float + sway while a med is selected (design "medspin").
class _FloatWhenSelected extends StatelessWidget {
  final bool floating;
  final AnimationController controller;
  final Widget child;

  const _FloatWhenSelected({
    required this.floating,
    required this.controller,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    if (!floating) return child;
    return AnimatedBuilder(
      animation: controller,
      builder: (context, animatedChild) {
        final f = (1 - math.cos(2 * math.pi * controller.value)) / 2;
        return Transform.translate(
          offset: Offset(0, -10 - 6 * f),
          child: Transform.rotate(
            angle: (-4 + 8 * f) * math.pi / 180,
            child: animatedChild,
          ),
        );
      },
      child: child,
    );
  }
}

class _Compartment extends StatelessWidget {
  final int shelf;
  final AnimationController ledCtrl;
  final bool isLast;

  const _Compartment({
    required this.shelf,
    required this.ledCtrl,
    required this.isLast,
  });

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: shelf * kCompartmentHeight,
      left: 0,
      right: 0,
      height: kCompartmentHeight,
      child: Stack(
        children: [
          // Back wall.
          const Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    MedColors.compartmentTop,
                    MedColors.compartmentMid,
                    MedColors.compartmentBottom,
                  ],
                  stops: [0, 0.4, 1],
                ),
              ),
            ),
          ),
          // Side inner shadows.
          const Positioned(
            top: 0,
            bottom: 0,
            left: 0,
            width: 16,
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [Color(0x4D463A28), Color(0x00463A28)],
                ),
              ),
            ),
          ),
          const Positioned(
            top: 0,
            bottom: 0,
            right: 0,
            width: 16,
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.centerRight,
                  end: Alignment.centerLeft,
                  colors: [Color(0x4D463A28), Color(0x00463A28)],
                ),
              ),
            ),
          ),
          // Warm LED strip with a slow flicker, phase-shifted per shelf.
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            height: 3,
            child: AnimatedBuilder(
              animation: ledCtrl,
              builder: (context, _) {
                final phase = ledCtrl.value + shelf * 0.18;
                final flicker = 0.96 + 0.04 * math.sin(phase * 2 * math.pi);
                return Opacity(
                  opacity: flicker.clamp(0.92, 1.0),
                  child: const DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Color(0x00FFE9C4),
                          MedColors.ledEdge,
                          MedColors.ledCore,
                          MedColors.ledEdge,
                          Color(0x00FFE9C4),
                        ],
                        stops: [0.04, 0.3, 0.5, 0.7, 0.96],
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: Color(0xCCFFD696),
                          blurRadius: 12,
                          spreadRadius: 2,
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          // LED down-glow.
          const Positioned(
            top: 0,
            left: 0,
            right: 0,
            height: 64,
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0x8CFFEAC4), Color(0x00FFEAC4)],
                ),
              ),
            ),
          ),
          // Shelf surface lip (slight perspective trapezoid).
          Positioned(
            bottom: 22,
            left: 0,
            right: 0,
            height: 16,
            child: ClipPath(
              clipper: _ShelfLipClipper(),
              child: const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [MedColors.shelfLipTop, MedColors.shelfLipBottom],
                  ),
                ),
              ),
            ),
          ),
          // Shelf front edge.
          const Positioned(
            bottom: 12,
            left: 0,
            right: 0,
            height: 10,
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [MedColors.shelfEdgeTop, MedColors.shelfEdgeBottom],
                ),
              ),
            ),
          ),
          // Shadow under the shelf edge (not on the bottom compartment).
          if (!isLast)
            const Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              height: 12,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Color(0x473C3223), Color(0x0D3C3223)],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _ShelfLipClipper extends CustomClipper<Path> {
  @override
  Path getClip(Size size) => Path()
    ..moveTo(size.width * 0.025, 0)
    ..lineTo(size.width * 0.975, 0)
    ..lineTo(size.width, size.height)
    ..lineTo(0, size.height)
    ..close();

  @override
  bool shouldReclip(covariant CustomClipper<Path> oldClipper) => false;
}
