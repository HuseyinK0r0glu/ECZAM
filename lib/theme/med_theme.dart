import 'package:flutter/material.dart';

/// Design tokens extracted from design/MedTrack Cabinet.dc.html.
abstract final class MedColors {
  // Warm cream environment.
  static const bgTop = Color(0xFFEAE4DA);
  static const bgMid = Color(0xFFE0D8CB);
  static const bgBottom = Color(0xFFCFC5B4);

  // Ink.
  static const text = Color(0xFF33302A);
  static const textSoft = Color(0xFF5C5446);
  static const textMuted = Color(0xFF8A8170);
  static const textFaint = Color(0xFFA39A88);
  static const label = Color(0xFF6B6354);

  // Teal accent family.
  static const teal = Color(0xFF1FB6AF);
  static const tealBright = Color(0xFF35D9D1);
  static const tealDeep = Color(0xFF17A89F);
  static const tealInk = Color(0xFF06302E);
  static const tealGlow = Color(0xFF2DD4CF);
  static const tealText = Color(0xFF57E6DE);
  static const tealTextSoft = Color(0xFF9FF0EB);
  static const tealStatus = Color(0xFF178F88);
  static const switcherActive = Color(0xFF2F6F6B);
  static const switcherActiveText = Color(0xFFF2FBFA);

  // Holographic panel.
  static const holoTop = Color(0xD108282C);
  static const holoBottom = Color(0xB80A3438);
  static const holoBorder = Color(0x666EEBE4);
  static const holoText = Color(0xFFEFFFFE);

  // Brass add button.
  static const brassLight = Color(0xFFFBEBC0);
  static const brassMid = Color(0xFFE5B968);
  static const brassDark = Color(0xFFC2913C);
  static const brassDeep = Color(0xFF8F6A20);
  static const brassText = Color(0xFFFFF8E8);

  // Status.
  static const late = Color(0xFFB98A2E);
  static const skipped = Color(0xFFC2543F);
  static const danger = Color(0xFFD8483A);
  static const dangerSoft = Color(0xFFFFB3A3);
  static const dangerBorder = Color(0x73FF9C8A);

  // Cabinet surfaces.
  static const frameTop = Color(0xFFFBFAF7);
  static const frameMid = Color(0xFFEFECE6);
  static const frameBottom = Color(0xFFE6E2DA);
  static const cabinetInner = Color(0xFFF1EDE5);
  static const compartmentTop = Color(0xFFE8E2D6);
  static const compartmentMid = Color(0xFFF3EFE7);
  static const compartmentBottom = Color(0xFFF7F4EE);
  static const shelfLipTop = Color(0xFFFDFCF9);
  static const shelfLipBottom = Color(0xFFEDE9E0);
  static const shelfEdgeTop = Color(0xFFF6F3EC);
  static const shelfEdgeBottom = Color(0xFFDCD6CA);
  static const ledCore = Color(0xFFFFF3DC);
  static const ledEdge = Color(0xFFFFE9C4);
  static const ledGlow = Color(0xFFFFD696);
  static const shadowBrown = Color(0xFF403626);
  static const objectShadow = Color(0xFF322816);
}

abstract final class MedText {
  static const serif = 'InstrumentSerif';

  static const screenTitle = TextStyle(
    fontFamily: serif,
    fontSize: 27,
    color: MedColors.text,
    letterSpacing: 0.2,
    height: 1.1,
  );

  static const panelTitle = TextStyle(
    fontFamily: serif,
    fontSize: 23,
    color: MedColors.holoText,
    height: 1.1,
  );

  static const sheetTitle = TextStyle(
    fontFamily: serif,
    fontSize: 24,
    color: MedColors.text,
    height: 1.1,
  );

  static const sectionLabel = TextStyle(
    fontSize: 11,
    fontWeight: FontWeight.w700,
    letterSpacing: 1.4,
    color: MedColors.label,
  );
}

/// Shared easing: cubic-bezier(0.22, 1, 0.3, 1) from the design.
const Curve medEase = Cubic(0.22, 1, 0.3, 1);
