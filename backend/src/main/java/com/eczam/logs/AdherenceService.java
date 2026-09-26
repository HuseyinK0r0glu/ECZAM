package com.eczam.logs;

import com.eczam.logs.dto.LogDtos.AdherenceSummary;
import com.eczam.logs.dto.LogDtos.DayAdherence;
import com.eczam.reminders.MedicationSchedule;
import com.eczam.reminders.MedicationScheduleRepository;
import com.eczam.reminders.ScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server-side adherence/streak computation over the FULL, never-purged
 * {@code medication_logs} history (CLAUDE.md §6) — unlike the Flutter client's
 * {@code buildWeekSummary} (frontend/lib/state/adherence.dart), which only sees
 * the last 7 days of the local SQLite mirror and so cannot reconstruct a real
 * multi-week streak.
 *
 * <p><b>Rule set (a deliberate simplification — documented here rather than
 * silently diverged, per CLAUDE.md's "one rule above all"):</b>
 * <ol>
 *   <li><b>Window:</b> the last {@link #MAX_WINDOW_DAYS} days ending today, or
 *       since the user's earliest schedule's {@code startsOn} if that's more
 *       recent — whichever gives the SMALLER window (so we never scan
 *       unbounded history). A user with no schedules at all gets a 1-day
 *       window (today only).</li>
 *   <li><b>Expected:</b> for day D, the number of (schedule, scheduledTime)
 *       pairs where {@link ScheduleService#isDue} matches D — reusing that
 *       primitive as the single source of truth for day-matching across
 *       daily/weekly/interval schedules, rather than reimplementing it here.</li>
 *   <li><b>Taken:</b> the count of the user's dose logs whose OWN offset
 *       places {@code takenAt} on calendar day D (i.e. {@code takenAt.toLocalDate()}),
 *       capped at {@code expected}.</li>
 *   <li><b>Streak day:</b> a day counts toward a streak when
 *       {@code expected == 0} (nothing was due — neither breaks nor extends a
 *       streak) OR {@code taken >= expected} (fully adherent). A day with
 *       {@code expected > 0 && taken < expected} breaks the streak. See
 *       {@link #isStreakDay}.</li>
 *   <li><b>currentStreak</b> = consecutive streak-days counting backward from
 *       today until the first breaking day. <b>longestStreak</b> = the longest
 *       run of streak-days anywhere in the window.</li>
 * </ol>
 * This evaluates every day in the window against schedules AS THEY EXIST NOW —
 * it does not retroactively reconstruct schedules that were later edited or
 * deactivated. That's the same simplification the client's local computation
 * already makes, so it's consistent with precedent rather than a new gap.
 */
@Service
public class AdherenceService {

    static final int MAX_WINDOW_DAYS = 90;

    private final MedicationScheduleRepository schedules;
    private final MedicationLogRepository logs;

    public AdherenceService(MedicationScheduleRepository schedules, MedicationLogRepository logs) {
        this.schedules = schedules;
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public AdherenceSummary compute(UUID userId) {
        LocalDate today = LocalDate.now();
        List<MedicationSchedule> userSchedules = schedules.findAllForUser(userId);

        LocalDate earliestStart = userSchedules.stream()
                .map(MedicationSchedule::getStartsOn)
                .min(LocalDate::compareTo)
                .orElse(today);
        LocalDate maxWindowStart = today.minusDays(MAX_WINDOW_DAYS - 1);
        LocalDate windowStart = earliestStart.isAfter(maxWindowStart) ? earliestStart : maxWindowStart;
        if (windowStart.isAfter(today)) windowStart = today; // every schedule starts in the future

        // Padded instant range: a log's takenAt carries ITS OWN offset (which may
        // differ from the server's), so we widen the instant-based DB filter by a
        // day on each side, then re-bucket precisely by toLocalDate() below —
        // that keeps the "log's own offset" rule exact without risking an
        // off-by-one at either window edge.
        OffsetDateTime from = OffsetDateTime.of(windowStart.minusDays(1), LocalTime.MIN, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(today.plusDays(1), LocalTime.MAX, ZoneOffset.UTC);
        Map<LocalDate, Long> takenByDay = logs.findForUserInRange(userId, from, to).stream()
                .collect(Collectors.groupingBy(l -> l.getTakenAt().toLocalDate(), Collectors.counting()));

        List<DayAdherence> days = new ArrayList<>();
        for (LocalDate d = windowStart; !d.isAfter(today); d = d.plusDays(1)) {
            int expected = 0;
            for (MedicationSchedule s : userSchedules) {
                LocalTime[] times = s.getScheduledTimes();
                if (times == null) continue;
                for (LocalTime t : times) {
                    if (ScheduleService.isDue(s, LocalDateTime.of(d, t))) expected++;
                }
            }
            long takenRaw = takenByDay.getOrDefault(d, 0L);
            int taken = (int) Math.min(expected, takenRaw);
            days.add(new DayAdherence(d.toString(), expected, taken));
        }

        int currentStreak = 0;
        for (int i = days.size() - 1; i >= 0; i--) {
            DayAdherence day = days.get(i);
            if (!isStreakDay(day.expected(), day.taken())) break;
            currentStreak++;
        }

        int longestStreak = 0;
        int run = 0;
        for (DayAdherence day : days) {
            if (isStreakDay(day.expected(), day.taken())) {
                run++;
                longestStreak = Math.max(longestStreak, run);
            } else {
                run = 0;
            }
        }

        return new AdherenceSummary(currentStreak, longestStreak, days.size(), days);
    }

    /** A day counts toward a streak iff nothing was due, or every due dose was logged. */
    static boolean isStreakDay(int expected, int taken) {
        return expected == 0 || taken >= expected;
    }
}
