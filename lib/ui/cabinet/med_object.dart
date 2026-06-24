import 'dart:io';

import 'package:flutter/material.dart';

import 'package:medtrack/models/medication.dart';
import 'package:medtrack/ui/cabinet/cabinet_layout.dart';

/// One volumetric medicine container, drawn in the cabinet's coordinate
/// space (sizes from [kindSize]). Visual recipe per kind comes from the
/// design's kindStyles: cap + gradient body + paper label.
class MedObject extends StatelessWidget {
  final Medication med;
  final String? photoPath;

  const MedObject({super.key, required this.med, this.photoPath});

  @override
  Widget build(BuildContext context) {
    final size = kindSize(med.kind);
    final spec = _KindSpec.of(med.kind);

    return SizedBox(
      width: size.width,
      height: size.height,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          // Ground contact shadow.
          Positioned(
            left: size.width / 2 - (size.width + 10) / 2,
            bottom: -6,
            child: Container(
              width: size.width + 10,
              height: 12,
              decoration: const BoxDecoration(
                gradient: RadialGradient(
                  colors: [Color(0x61322816), Color(0x00322816)],
                  stops: [0, 0.68],
                ),
              ),
            ),
          ),
          // Body.
          Positioned(
            top: spec.bodyTop,
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: spec.bodyRadius,
                gradient: spec.bodyGradient,
              ),
              foregroundDecoration: BoxDecoration(
                borderRadius: spec.bodyRadius,
                gradient: const LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Color(0x59FFFFFF),
                    Color(0x00FFFFFF),
                    Color(0x003C2800),
                    Color(0x4D3C2800),
                  ],
                  stops: [0, 0.18, 0.72, 1],
                ),
              ),
            ),
          ),
          // Cap.
          if (spec.capWidth > 0)
            Positioned(
              top: 0,
              left: spec.capLeft,
              child: Container(
                width: spec.capWidth,
                height: 16,
                decoration: const BoxDecoration(
                  borderRadius: BorderRadius.vertical(
                    top: Radius.circular(4),
                    bottom: Radius.circular(2),
                  ),
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Color(0xFFFFFFFF),
                      Color(0xFFE4E1D9),
                      Color(0xFFD6D2C8),
                    ],
                    stops: [0, 0.7, 1],
                  ),
                ),
              ),
            ),
          // Blister bubbles for texture.
          if (med.kind == MedKind.blister)
            Positioned(
              top: 6,
              left: 10,
              right: 10,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: List.generate(3, (_) => const _BlisterBubble()),
              ),
            ),
          // Paper label.
          Positioned(
            top: spec.labelTop,
            left: 4,
            right: 4,
            child: Container(
              height: spec.labelHeight,
              padding: const EdgeInsets.fromLTRB(2, 2, 2, 0),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(3),
                gradient: const LinearGradient(
                  colors: [
                    Color(0xFFE9E4D8),
                    Color(0xFFFDFCF8),
                    Color(0xFFE7E2D5),
                  ],
                  stops: [0, 0.4, 1],
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x4D3C1E00),
                    offset: Offset(0, 1),
                    blurRadius: 2,
                  ),
                ],
              ),
              child: Column(
                children: [
                  if (photoPath != null)
                    ClipRRect(
                      borderRadius: BorderRadius.circular(2),
                      child: Image.file(
                        File(photoPath!),
                        width: double.infinity,
                        height: 22,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => const SizedBox(height: 22),
                      ),
                    )
                  else
                    Container(
                      height: 5,
                      margin: const EdgeInsets.symmetric(
                        horizontal: 2,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        color: spec.labelStripe,
                        borderRadius: BorderRadius.circular(1),
                      ),
                    ),
                  const SizedBox(height: 2),
                  Text(
                    med.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 6.5,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF2E2A22),
                      height: 1.2,
                    ),
                  ),
                  Text(
                    med.dose,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 6,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF7A7263),
                      height: 1.2,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _BlisterBubble extends StatelessWidget {
  const _BlisterBubble();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 14,
      height: 14,
      decoration: const BoxDecoration(
        shape: BoxShape.circle,
        gradient: RadialGradient(
          center: Alignment(-0.24, -0.36),
          colors: [Color(0xFFFFFFFF), Color(0xFFDDE0E5), Color(0xFFA9AEB8)],
          stops: [0, 0.55, 1],
        ),
      ),
      child: Center(
        child: Container(
          width: 7,
          height: 7,
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(
              center: Alignment(-0.2, -0.3),
              colors: [Color(0xFFFFFFFF), Color(0xFFE8E6E0)],
              stops: [0, 0.7],
            ),
          ),
        ),
      ),
    );
  }
}

/// Per-kind visual parameters from the design's kindStyles map.
class _KindSpec {
  final double capLeft;
  final double capWidth;
  final double bodyTop;
  final BorderRadius bodyRadius;
  final LinearGradient bodyGradient;
  final double labelTop;
  final double labelHeight;
  final Color labelStripe;

  const _KindSpec({
    required this.capLeft,
    required this.capWidth,
    required this.bodyTop,
    required this.bodyRadius,
    required this.bodyGradient,
    required this.labelTop,
    required this.labelHeight,
    required this.labelStripe,
  });

  static _KindSpec of(MedKind kind) => switch (kind) {
    MedKind.amber => _KindSpec(
      capLeft: 7,
      capWidth: 40,
      bodyTop: 15,
      bodyRadius: const BorderRadius.vertical(
        top: Radius.circular(7),
        bottom: Radius.circular(9),
      ),
      bodyGradient: const LinearGradient(
        colors: [
          Color(0xFF7C440C),
          Color(0xFFC8801C),
          Color(0xFFF0AE48),
          Color(0xFFD08A22),
          Color(0xFF6E3B09),
        ],
        stops: [0, 0.2, 0.45, 0.7, 1],
      ),
      labelTop: 34,
      labelHeight: 48,
      labelStripe: const Color(0xFF1FB6AF),
    ),
    MedKind.white => _KindSpec(
      capLeft: 8,
      capWidth: 40,
      bodyTop: 16,
      bodyRadius: const BorderRadius.vertical(
        top: Radius.circular(8),
        bottom: Radius.circular(10),
      ),
      bodyGradient: const LinearGradient(
        colors: [
          Color(0xFFCFCBC1),
          Color(0xFFFBFAF6),
          Color(0xFFFFFFFF),
          Color(0xFFEFEDE6),
          Color(0xFFC5C1B6),
        ],
        stops: [0, 0.35, 0.5, 0.7, 1],
      ),
      labelTop: 36,
      labelHeight: 50,
      labelStripe: const Color(0xFFD8483A),
    ),
    MedKind.syrup => _KindSpec(
      capLeft: 16,
      capWidth: 24,
      bodyTop: 12,
      bodyRadius: const BorderRadius.vertical(
        top: Radius.circular(6),
        bottom: Radius.circular(8),
      ),
      bodyGradient: const LinearGradient(
        colors: [
          Color(0xFF190C02),
          Color(0xFF4A2406),
          Color(0xFF7A4612),
          Color(0xFF5C2E08),
          Color(0xFF140A02),
        ],
        stops: [0, 0.22, 0.45, 0.68, 1],
      ),
      labelTop: 40,
      labelHeight: 50,
      labelStripe: const Color(0xFFC9B88E),
    ),
    MedKind.jar => _KindSpec(
      capLeft: 8,
      capWidth: 54,
      bodyTop: 15,
      bodyRadius: BorderRadius.circular(10),
      bodyGradient: const LinearGradient(
        colors: [Color(0xFFD6B478), Color(0xFFF8E6C0), Color(0xFFCDAA6E)],
        stops: [0, 0.45, 1],
      ),
      labelTop: 32,
      labelHeight: 42,
      labelStripe: const Color(0xFFE8A026),
    ),
    MedKind.blister => _KindSpec(
      capLeft: 0,
      capWidth: 0,
      bodyTop: 0,
      bodyRadius: BorderRadius.circular(6),
      bodyGradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFFF4F6F9), Color(0xFFC9CDD5), Color(0xFFE8EAEF)],
        stops: [0, 0.55, 1],
      ),
      labelTop: 26,
      labelHeight: 50,
      labelStripe: const Color(0xFF9AA0AB),
    ),
  };
}
