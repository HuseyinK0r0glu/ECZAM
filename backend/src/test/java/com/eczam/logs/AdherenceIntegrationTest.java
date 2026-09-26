package com.eczam.logs;

import com.eczam.AbstractIntegrationTest;
import com.eczam.inventory.UserMedication;
import com.eczam.inventory.UserMedicationRepository;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import com.eczam.reminders.FrequencyType;
import com.eczam.reminders.MedicationSchedule;
import com.eczam.reminders.MedicationScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@code GET /medication-logs/adherence}, backed by
 * {@link AdherenceService}. Seeds schedules/logs directly via the repositories
 * (as {@code ExpirationIntegrationTest} does) so exact dates/offsets are
 * controlled, then asserts on the HTTP response.
 */
class AdherenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;
    @Autowired UserMedicationRepository inventory;
    @Autowired MedicationScheduleRepository scheduleRepo;
    @Autowired MedicationLogRepository logRepo;

    private record Ctx(UUID userId, String token) {}

    private Ctx registerUser(String email) {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", email, "password", "ValidP@ss1!", "displayName", "Adh"), Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        return new Ctx(UUID.fromString((String) user.get("id")), (String) data.get("accessToken"));
    }

    private UUID seedInventory(UUID userId) {
        Medication med = new Medication();
        med.setName("Adherence Med " + UUID.randomUUID());
        med = medications.save(med);
        UserMedication um = new UserMedication();
        um.setUserId(userId);
        um.setMedication(med);
        um.setQuantity(new BigDecimal("1000"));
        return inventory.save(um).getId();
    }

    private void seedDailySchedule(UUID umId, LocalDate startsOn, LocalTime time) {
        UserMedication um = inventory.findById(umId).orElseThrow();
        MedicationSchedule s = new MedicationSchedule();
        s.setUserMedication(um);
        s.setDosageAmount(BigDecimal.ONE);
        s.setFrequencyType(FrequencyType.daily);
        s.setScheduledTimes(new LocalTime[]{ time });
        s.setStartsOn(startsOn);
        s.setActive(true);
        scheduleRepo.save(s);
    }

    private void seedLog(UUID umId, LocalDate date, LocalTime time) {
        MedicationLog log = new MedicationLog();
        log.setUserMedicationId(umId);
        log.setQuantityUsed(BigDecimal.ONE);
        log.setTakenAt(OffsetDateTime.of(date, time, ZoneOffset.UTC));
        logRepo.save(log);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchAdherence(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        var res = rest.exchange("/medication-logs/adherence", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) res.getBody().get("data");
    }

    @Test
    void consecutive_fully_logged_days_yield_a_matching_current_streak() {
        Ctx ctx = registerUser("streak-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId());
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(4); // 5-day window: start..today

        seedDailySchedule(umId, start, LocalTime.of(8, 0));
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            seedLog(umId, d, LocalTime.of(8, 0));
        }

        Map<String, Object> data = fetchAdherence(ctx.token());
        assertThat(((Number) data.get("currentStreak")).intValue()).isEqualTo(5);
        assertThat(((Number) data.get("longestStreak")).intValue()).isEqualTo(5);
    }

    @Test
    void a_missed_day_breaks_the_streak_but_longest_streak_remembers_the_earlier_run() {
        Ctx ctx = registerUser("miss-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId());
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6); // 7-day window: start..today
        LocalDate missed = today.minusDays(2);

        seedDailySchedule(umId, start, LocalTime.of(8, 0));
        // Logged: start..start+3 (4-day run), MISSED at today-2, then today-1, today (2-day run).
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            if (d.equals(missed)) continue;
            seedLog(umId, d, LocalTime.of(8, 0));
        }

        Map<String, Object> data = fetchAdherence(ctx.token());
        assertThat(((Number) data.get("currentStreak")).intValue()).isEqualTo(2);   // today-1, today
        assertThat(((Number) data.get("longestStreak")).intValue()).isEqualTo(4);  // start..start+3
    }

    @Test
    void a_day_with_nothing_due_does_not_break_an_existing_streak() {
        Ctx ctx = registerUser("weekly-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId());
        LocalDate today = LocalDate.now();

        // Weekly schedule due only on today's day-of-week; every other day in
        // the 14-day window has expected == 0 and must not break the streak.
        UserMedication um = inventory.findById(umId).orElseThrow();
        MedicationSchedule s = new MedicationSchedule();
        s.setUserMedication(um);
        s.setDosageAmount(BigDecimal.ONE);
        s.setFrequencyType(FrequencyType.weekly);
        s.setScheduledTimes(new LocalTime[]{ LocalTime.of(9, 0) });
        s.setDaysOfWeek(new Short[]{ (short) today.getDayOfWeek().getValue() });
        s.setStartsOn(today.minusDays(13));
        s.setActive(true);
        scheduleRepo.save(s);

        seedLog(umId, today, LocalTime.of(9, 0));
        seedLog(umId, today.minusDays(7), LocalTime.of(9, 0)); // the one prior occurrence in-window

        Map<String, Object> data = fetchAdherence(ctx.token());
        assertThat(((Number) data.get("windowDays")).intValue()).isEqualTo(14);
        assertThat(((Number) data.get("currentStreak")).intValue()).isEqualTo(14);
    }

    @Test
    @SuppressWarnings("unchecked")
    void days_array_is_oldest_first_and_spans_the_full_window() {
        Ctx ctx = registerUser("window-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId());
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(9); // 10-day window

        seedDailySchedule(umId, start, LocalTime.of(8, 0));

        Map<String, Object> data = fetchAdherence(ctx.token());
        assertThat(((Number) data.get("windowDays")).intValue()).isEqualTo(10);
        List<Map<String, Object>> days = (List<Map<String, Object>>) data.get("days");
        assertThat(days).hasSize(10);
        assertThat(days.get(0).get("date")).isEqualTo(start.toString());
        assertThat(days.get(days.size() - 1).get("date")).isEqualTo(today.toString());
        assertThat(days).allSatisfy(
                day -> assertThat(((Number) day.get("expected")).intValue()).isEqualTo(1));
    }
}
