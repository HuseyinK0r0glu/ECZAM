import 'package:flutter_test/flutter_test.dart';

import 'package:medtrack/models/dose_log.dart';
import 'package:medtrack/models/medication.dart';
import 'package:medtrack/state/adherence.dart';

const _morning = 8 * 60; // 08:00
const _evening = 21 * 60; // 21:00

Medication med(int id, List<int> minutes, {DateTime? createdAt}) => Medication(
  id: id,
  name: 'Med $id',
  dose: '10 mg',
  kind: MedKind.amber,
  reminderMinutes: minutes,
  createdAt: createdAt ?? DateTime(2026, 1, 1),
);

DoseLog log(
  int medId,
  int minute,
  DateTime day, {
  DoseStatus status = DoseStatus.taken,
}) => DoseLog(
  id: 0,
  medId: medId,
  medName: 'Med $medId',
  medDose: '10 mg',
  minuteOfDay: minute,
  dateKey: DoseLog.dateKeyFor(day),
  status: status,
  scheduledAt: day,
  loggedAt: day,
);

void main() {
  final today = DateTime(2026, 6, 13);

  test('empty cabinet yields 100% with no tracked days', () {
    final week = buildWeekSummary(today: today, meds: [], logs: []);
    expect(week.days, hasLength(7));
    expect(week.percent, 100);
    expect(week.daysWithDoses, 0);
    expect(week.days.last.outcome, DayOutcome.none);
  });

  test('full, partial and missed days are classified', () {
    final meds = [
      med(1, [_morning, _evening]),
    ];
    final yesterday = DateTime(2026, 6, 12);
    final twoDaysAgo = DateTime(2026, 6, 11);
    final week = buildWeekSummary(
      today: today,
      meds: meds,
      logs: [
        // Today: both doses taken -> full.
        log(1, _morning, today),
        log(1, _evening, today),
        // Yesterday: one of two -> partial.
        log(1, _morning, yesterday),
        // Two days ago: skipped doesn't count as taken -> missed.
        log(1, _morning, twoDaysAgo, status: DoseStatus.skipped),
      ],
    );

    expect(week.days[6].outcome, DayOutcome.full);
    expect(week.days[5].outcome, DayOutcome.partial);
    expect(week.days[4].outcome, DayOutcome.missed);
    expect(week.days[3].outcome, DayOutcome.missed);
    expect(week.daysOnTrack, 1);
    // 3 taken out of 14 expected over the week.
    expect(week.percent, (3 * 100 / 14).round());
  });

  test('meds created mid-week only count from their creation day', () {
    final meds = [
      med(1, [_morning], createdAt: DateTime(2026, 6, 12)),
    ];
    final week = buildWeekSummary(
      today: today,
      meds: meds,
      logs: [log(1, _morning, DateTime(2026, 6, 12)), log(1, _morning, today)],
    );

    expect(week.daysWithDoses, 2);
    expect(week.percent, 100);
    expect(week.days.first.outcome, DayOutcome.none);
  });

  test('logs from deleted meds still count toward expectation', () {
    final week = buildWeekSummary(
      today: today,
      meds: [],
      logs: [log(99, _morning, today)],
    );
    expect(week.days.last.expected, 1);
    expect(week.days.last.taken, 1);
    expect(week.days.last.outcome, DayOutcome.full);
  });

  test('isLate flags doses taken over an hour after schedule', () {
    final scheduled = DateTime(2026, 6, 13, 8, 0);
    DoseLog mk(DateTime loggedAt) => DoseLog(
      id: 0,
      medId: 1,
      medName: 'Med',
      medDose: '',
      minuteOfDay: _morning,
      dateKey: '2026-06-13',
      status: DoseStatus.taken,
      scheduledAt: scheduled,
      loggedAt: loggedAt,
    );
    expect(mk(DateTime(2026, 6, 13, 8, 30)).isLate, isFalse);
    expect(mk(DateTime(2026, 6, 13, 9, 1)).isLate, isTrue);
  });
}
