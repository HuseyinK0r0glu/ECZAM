import 'dart:io';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import 'package:medtrack/models/medication.dart';
import 'package:medtrack/theme/med_theme.dart';

/// Holographic action panel shown over the cabinet when a med is selected.
class ActionPanel extends StatelessWidget {
  final Medication med;
  final String? photoPath;
  final VoidCallback onLogDose;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final void Function(ImageSource source) onPickPhoto;

  const ActionPanel({
    super.key,
    required this.med,
    required this.photoPath,
    required this.onLogDose,
    required this.onEdit,
    required this.onDelete,
    required this.onPickPhoto,
  });

  String _formLine(BuildContext context) {
    final timeText = med.reminderMinutes.isEmpty
        ? 'As needed'
        : med.reminderMinutes
              .map((m) => minuteToTimeOfDay(m).format(context))
              .join(' · ');
    return '${med.kind.label} · $timeText';
  }

  @override
  Widget build(BuildContext context) {
    // holoin: fade + rise + settle, as in the design keyframes.
    return TweenAnimationBuilder<double>(
      key: ValueKey(med.id),
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 450),
      curve: const Cubic(0.2, 1, 0.3, 1),
      builder: (context, t, child) => Opacity(
        opacity: t.clamp(0, 1),
        child: Transform.translate(
          offset: Offset(0, 14 * (1 - t)),
          child: Transform.scale(scale: 0.96 + 0.04 * t, child: child),
        ),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
          child: Container(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [MedColors.holoTop, MedColors.holoBottom],
              ),
              border: Border.all(color: MedColors.holoBorder),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x80062A2A),
                  offset: Offset(0, 10),
                  blurRadius: 36,
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Expanded(
                      child: Text(
                        med.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: MedText.panelTitle,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      med.dose,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: MedColors.tealText,
                        letterSpacing: 0.4,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  _formLine(context),
                  style: const TextStyle(
                    fontSize: 11,
                    color: Color(0xBFBEEBE8),
                    letterSpacing: 0.4,
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    _PhotoSlot(photoPath: photoPath, onPick: onPickPhoto),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Text(
                        'Add a photo of the real package to keep it with '
                        'this med.',
                        style: TextStyle(
                          fontSize: 11,
                          height: 1.45,
                          color: Color(0xB3BEEBE8),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      flex: 29,
                      child: _PanelButton.filled(
                        label: 'Log Dose',
                        onTap: onLogDose,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      flex: 20,
                      child: _PanelButton.outlined(
                        label: 'Edit',
                        borderColor: const Color(0x736EEBE4),
                        textColor: MedColors.tealTextSoft,
                        onTap: onEdit,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      flex: 20,
                      child: _PanelButton.outlined(
                        label: 'Delete',
                        borderColor: MedColors.dangerBorder,
                        textColor: MedColors.dangerSoft,
                        onTap: onDelete,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _PhotoSlot extends StatelessWidget {
  final String? photoPath;
  final void Function(ImageSource source) onPick;

  const _PhotoSlot({required this.photoPath, required this.onPick});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => showPhotoSourceSheet(context, onPick),
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: const Color(0x596EEBE4)),
          color: const Color(0x1A6EEBE4),
        ),
        clipBehavior: Clip.antiAlias,
        child: photoPath != null
            ? Image.file(
                File(photoPath!),
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => const _PlusGlyph(),
              )
            : const _PlusGlyph(),
      ),
    );
  }
}

class _PlusGlyph extends StatelessWidget {
  const _PlusGlyph();

  @override
  Widget build(BuildContext context) => const Center(
    child: Text(
      '+',
      style: TextStyle(
        fontSize: 22,
        color: MedColors.tealTextSoft,
        fontWeight: FontWeight.w300,
        height: 1,
      ),
    ),
  );
}

/// Camera-or-gallery chooser, shared with the add-medication sheet.
void showPhotoSourceSheet(
  BuildContext context,
  void Function(ImageSource) onPick,
) {
  showModalBottomSheet<void>(
    context: context,
    backgroundColor: const Color(0xF7FAF7F1),
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(26)),
    ),
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 12),
          ListTile(
            leading: const Icon(
              Icons.photo_camera_outlined,
              color: MedColors.textSoft,
            ),
            title: const Text(
              'Take photo',
              style: TextStyle(color: MedColors.text),
            ),
            onTap: () {
              Navigator.pop(sheetContext);
              onPick(ImageSource.camera);
            },
          ),
          ListTile(
            leading: const Icon(
              Icons.photo_library_outlined,
              color: MedColors.textSoft,
            ),
            title: const Text(
              'Choose from gallery',
              style: TextStyle(color: MedColors.text),
            ),
            onTap: () {
              Navigator.pop(sheetContext);
              onPick(ImageSource.gallery);
            },
          ),
          const SizedBox(height: 8),
        ],
      ),
    ),
  );
}

class _PanelButton extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  final bool filled;
  final Color? borderColor;
  final Color? textColor;

  const _PanelButton.filled({required this.label, required this.onTap})
    : filled = true,
      borderColor = null,
      textColor = null;

  const _PanelButton.outlined({
    required this.label,
    required this.onTap,
    required this.borderColor,
    required this.textColor,
  }) : filled = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 44,
        alignment: Alignment.center,
        decoration: filled
            ? BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                gradient: const LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [MedColors.tealBright, MedColors.tealDeep],
                ),
                boxShadow: const [
                  BoxShadow(color: Color(0x7335D9D1), blurRadius: 14),
                ],
              )
            : BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: borderColor!),
              ),
        child: Text(
          label,
          style: filled
              ? const TextStyle(
                  fontSize: 13.5,
                  fontWeight: FontWeight.w700,
                  color: MedColors.tealInk,
                )
              : TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: textColor,
                ),
        ),
      ),
    );
  }
}
