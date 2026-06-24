import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/state/adherence.dart';
import 'package:medtrack/state/app_state.dart';
import 'package:medtrack/theme/med_theme.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  /// Day whose logs are listed below the strip; null means "today".
  DateTime? _selectedDay;

  bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  @override
  Widget build(BuildContext context) {
    final app = context.watch<AppState>();
    final topPad = MediaQuery.paddingOf(context).top;
    final week = app.week;

    // Default to today (the last day in the strip) until a day is tapped.
    final today = week.days.isNotEmpty ? week.days.last.day : DateTime.now();
    final selected = _selectedDay ?? today;
    final selectedKey = DoseLog.dateKeyFor(selected);
    final dayLogs = app.recentLogs
        .where((log) => log.dateKey == selectedKey)
        .toList();

    return ListView(
      padding: EdgeInsets.fromLTRB(20, topPad + 68, 20, 150),
      children: [
        Row(
          children: [
            for (var i = 0; i < week.days.length; i++) ...[
              if (i > 0) const SizedBox(width: 6),
              Expanded(
                child: _DayCard(
                  day: week.days[i],
                  isSelected: _isSameDay(week.days[i].day, selected),
                  onTap: () =>
                      setState(() => _selectedDay = week.days[i].day),
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 16),
        _AdherenceCard(week: week),
        const SizedBox(height: 18),
        Padding(
          padding: const EdgeInsets.fromLTRB(6, 0, 6, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('RECENT LOG', style: MedText.sectionLabel),
              const SizedBox(height: 3),
              Text(
                'Showing ${_dayLabel(selected)}',
                style: const TextStyle(
                  fontSize: 10.5,
                  fontStyle: FontStyle.italic,
                  color: MedColors.textFaint,
                ),
              ),
            ],
          ),
        ),
        if (dayLogs.isEmpty)
          const Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'No doses logged on this day.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontFamily: MedText.serif,
                fontStyle: FontStyle.italic,
                fontSize: 16,
                color: MedColors.textMuted,
              ),
            ),
          )
        else
          Container(
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
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: [for (final log in dayLogs) _LogRow(log: log)],
            ),
          ),
      ],
    );
  }

  String _dayLabel(DateTime day) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final d = DateTime(day.year, day.month, day.day);
    if (d == today) return 'today';
    if (d == today.subtract(const Duration(days: 1))) return 'yesterday';
    return DateFormat('EEE, MMM d').format(day);
  }
}

class _DayCard extends StatelessWidget {
  final DayAdherence day;
  final bool isSelected;
  final VoidCallback onTap;

  const _DayCard({
    required this.day,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final dotColor = switch (day.outcome) {
      DayOutcome.full => MedColors.teal,
      DayOutcome.partial => MedColors.brassMid,
      DayOutcome.missed => MedColors.danger,
      DayOutcome.none => const Color(0x335A4C37),
    };

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.fromLTRB(0, 9, 0, 8),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(14),
          color: isSelected
              ? const Color(0xBFB2E8E4)
              : const Color(0x8CFFFFFF),
          border: isSelected
              ? Border.all(color: MedColors.tealDeep, width: 1.5)
              : null,
          boxShadow: const [
            BoxShadow(
              color: Color(0x1A403626),
              offset: Offset(0, 2),
              blurRadius: 8,
            ),
          ],
        ),
        child: Column(
          children: [
            Text(
              DateFormat('E').format(day.day).toUpperCase(),
              style: const TextStyle(
                fontSize: 9.5,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.8,
                color: MedColors.textMuted,
              ),
            ),
            const SizedBox(height: 1),
            Text(
              '${day.day.day}',
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: MedColors.text,
              ),
            ),
            const SizedBox(height: 5),
            Container(
              width: 7,
              height: 7,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: dotColor,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AdherenceCard extends StatelessWidget {
  final WeekSummary week;

  const _AdherenceCard({required this.week});

  @override
  Widget build(BuildContext context) {
    final tracked = week.daysWithDoses;
    final line = tracked == 0
        ? 'no doses scheduled this week yet'
        : '${week.daysOnTrack} of $tracked days fully on track';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xD908282C), Color(0xC70D3C40)],
        ),
        boxShadow: const [
          BoxShadow(
            color: Color(0x4D062A2A),
            offset: Offset(0, 8),
            blurRadius: 24,
          ),
        ],
      ),
      child: Row(
        children: [
          Text(
            '${week.percent}%',
            style: const TextStyle(
              fontFamily: MedText.serif,
              fontSize: 30,
              color: MedColors.tealText,
              height: 1,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Text(
              'adherence this week\n$line',
              style: const TextStyle(
                fontSize: 12,
                height: 1.45,
                color: Color(0xD9D2F5F3),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _LogRow extends StatelessWidget {
  final DoseLog log;

  const _LogRow({required this.log});

  @override
  Widget build(BuildContext context) {
    final (statusText, statusColor) = switch (log.status) {
      DoseStatus.taken when log.isLate => ('Taken late', MedColors.late),
      DoseStatus.taken => ('Taken', MedColors.tealStatus),
      DoseStatus.skipped => ('Skipped', MedColors.skipped),
      DoseStatus.snoozed => ('Snoozed', MedColors.late),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: Color(0x145A4C37), width: 1)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  log.medDose.isEmpty
                      ? log.medName
                      : '${log.medName} ${log.medDose}',
                  style: const TextStyle(
                    fontSize: 13.5,
                    fontWeight: FontWeight.w600,
                    color: MedColors.text,
                  ),
                ),
                const SizedBox(height: 1),
                Text(
                  _timeText(log.loggedAt),
                  style: const TextStyle(
                    fontSize: 11,
                    color: MedColors.textMuted,
                  ),
                ),
              ],
            ),
          ),
          Text(
            statusText,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: statusColor,
            ),
          ),
        ],
      ),
    );
  }

  static String _timeText(DateTime when) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final day = DateTime(when.year, when.month, when.day);
    final time = DateFormat.jm().format(when);
    if (day == today) return 'Today · $time';
    if (day == today.subtract(const Duration(days: 1))) {
      return 'Yesterday · $time';
    }
    return '${DateFormat.MMMd().format(when)} · $time';
  }
}
