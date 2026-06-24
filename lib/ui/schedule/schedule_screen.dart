import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';
import 'package:medtrack/state/app_state.dart';
import 'package:medtrack/theme/med_theme.dart';
import 'package:medtrack/ui/cabinet/cabinet_layout.dart';

/// One scheduled dose: a medication at one of its exact reminder times.
typedef _Dose = ({Medication med, int minute});

class ScheduleScreen extends StatelessWidget {
  const ScheduleScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final app = context.watch<AppState>();
    final topPad = MediaQuery.paddingOf(context).top;

    final doses = <_Dose>[
      for (final med in app.meds)
        for (final minute in med.reminderMinutes) (med: med, minute: minute),
    ];

    // Group the exact times under coarse Morning/Midday/Evening headers,
    // sorted by time within each.
    final sections = <({DaySlot bucket, List<_Dose> doses})>[];
    for (final bucket in DaySlot.values) {
      final inBucket =
          doses.where((d) => bucketForMinute(d.minute) == bucket).toList()
            ..sort((a, b) => a.minute.compareTo(b.minute));
      if (inBucket.isNotEmpty) {
        sections.add((bucket: bucket, doses: inBucket));
      }
    }

    if (sections.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 40),
          child: Text(
            'Nothing scheduled today.\nAdd a medication with reminder '
            'times and it will show up here.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: MedText.serif,
              fontStyle: FontStyle.italic,
              fontSize: 17,
              height: 1.5,
              color: MedColors.textMuted,
            ),
          ),
        ),
      );
    }

    return ListView(
      padding: EdgeInsets.fromLTRB(20, topPad + 68, 20, 150),
      children: [
        for (final section in sections) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(6, 0, 6, 8),
            child: Text(
              section.bucket.label.toUpperCase(),
              style: MedText.sectionLabel,
            ),
          ),
          _GlassCard(
            child: Column(
              children: [
                for (final dose in section.doses)
                  _DoseRow(med: dose.med, minute: dose.minute),
              ],
            ),
          ),
          const SizedBox(height: 18),
        ],
      ],
    );
  }
}

class _GlassCard extends StatelessWidget {
  final Widget child;

  const _GlassCard({required this.child});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: const Color(0x99FFFFFF),
            boxShadow: const [
              BoxShadow(
                color: Color(0x1F403626),
                offset: Offset(0, 4),
                blurRadius: 14,
              ),
            ],
          ),
          child: child,
        ),
      ),
    );
  }
}

class _DoseRow extends StatelessWidget {
  final Medication med;
  final int minute;

  const _DoseRow({required this.med, required this.minute});

  @override
  Widget build(BuildContext context) {
    final app = context.watch<AppState>();
    final status = app.statusFor(med.id, minute);
    final taken = status == DoseStatus.taken;
    final timeText = minuteToTimeOfDay(minute).format(context);

    return InkWell(
      onTap: () => app.toggleTaken(med, minute),
      onLongPress: () => _showStatusSheet(context, app),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: const BoxDecoration(
          border: Border(
            bottom: BorderSide(color: Color(0x145A4C37), width: 1),
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 10,
              height: 26,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(3),
                color: kindSwatch(med.kind),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    med.name,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: MedColors.text,
                      decoration: taken
                          ? TextDecoration.lineThrough
                          : TextDecoration.none,
                    ),
                  ),
                  const SizedBox(height: 1),
                  Text(
                    _detailText(status),
                    style: const TextStyle(
                      fontSize: 11.5,
                      color: MedColors.textMuted,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Text(
              timeText,
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: MedColors.tealDeep,
              ),
            ),
            const SizedBox(width: 12),
            _StatusCircle(status: status),
          ],
        ),
      ),
    );
  }

  String _detailText(DoseStatus? status) {
    final base = med.dose.isEmpty
        ? med.kind.label
        : '${med.dose} · ${med.kind.label}';
    return switch (status) {
      DoseStatus.skipped => '$base · skipped',
      DoseStatus.snoozed => '$base · snoozed 10 min',
      _ => base,
    };
  }

  void _showStatusSheet(BuildContext context, AppState app) {
    final timeText = minuteToTimeOfDay(minute).format(context);
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
            const SizedBox(height: 14),
            Text('${med.name} — $timeText', style: MedText.sheetTitle),
            const SizedBox(height: 6),
            ListTile(
              leading: const Icon(
                Icons.check_circle,
                color: MedColors.tealStatus,
              ),
              title: const Text('Taken'),
              onTap: () {
                Navigator.pop(sheetContext);
                app.setDoseStatus(med, minute, DoseStatus.taken);
              },
            ),
            ListTile(
              leading: const Icon(
                Icons.cancel_outlined,
                color: MedColors.skipped,
              ),
              title: const Text('Skipped'),
              onTap: () {
                Navigator.pop(sheetContext);
                app.setDoseStatus(med, minute, DoseStatus.skipped);
              },
            ),
            ListTile(
              leading: const Icon(Icons.snooze, color: MedColors.late),
              title: const Text('Snooze 10 min'),
              onTap: () {
                Navigator.pop(sheetContext);
                app.snoozeDose(med, minute);
              },
            ),
            ListTile(
              leading: const Icon(
                Icons.radio_button_unchecked,
                color: MedColors.textMuted,
              ),
              title: const Text('Clear — back to pending'),
              onTap: () {
                Navigator.pop(sheetContext);
                app.setDoseStatus(med, minute, null);
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

class _StatusCircle extends StatelessWidget {
  final DoseStatus? status;

  const _StatusCircle({required this.status});

  @override
  Widget build(BuildContext context) {
    final (border, fill, glyph, glyphColor) = switch (status) {
      DoseStatus.taken => (
        MedColors.teal,
        const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [MedColors.tealBright, MedColors.tealDeep],
        ),
        '✓',
        MedColors.tealInk,
      ),
      DoseStatus.skipped => (MedColors.skipped, null, '✕', MedColors.skipped),
      DoseStatus.snoozed => (MedColors.late, null, 'z', MedColors.late),
      null => (const Color(0x4D5A4C37), null, '', MedColors.text),
    };

    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      width: 26,
      height: 26,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: border, width: 2),
        gradient: fill,
      ),
      child: Text(
        glyph,
        style: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w800,
          color: glyphColor,
        ),
      ),
    );
  }
}
