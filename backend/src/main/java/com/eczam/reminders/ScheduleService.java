package com.eczam.reminders;

import com.eczam.inventory.UserMedication;
import com.eczam.inventory.UserMedicationRepository;
import com.eczam.reminders.dto.ScheduleDtos.*;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {

    private final MedicationScheduleRepository repo;
    private final UserMedicationRepository inventory;

    public ScheduleService(MedicationScheduleRepository repo, UserMedicationRepository inventory) {
        this.repo = repo; this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> listForUser(UUID userId) {
        return repo.findAllForUser(userId).stream().map(ScheduleService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> listForUserMedication(UUID userId, UUID umId) {
        return repo.findForUserMedication(umId, userId).stream().map(ScheduleService::toView).toList();
    }

    @Transactional
    public ScheduleView create(UUID userId, UUID umId, CreateScheduleRequest req) {
        UserMedication um = inventory.findByIdAndUserId(umId, userId)
                .orElseThrow(() -> ApiException.notFound("Inventory entry not found"));
        validate(req.frequencyType(), req.frequencyValue(), req.daysOfWeek());

        MedicationSchedule s = new MedicationSchedule();
        s.setUserMedication(um);
        s.setDosageAmount(req.dosageAmount());
        s.setFrequencyType(req.frequencyType());
        s.setFrequencyValue(req.frequencyValue());
        s.setScheduledTimes(parseTimes(req.scheduledTimes()));
        s.setDaysOfWeek(toShortArray(req.daysOfWeek()));
        if (req.startsOn() != null) s.setStartsOn(req.startsOn());
        s.setEndsOn(req.endsOn());
        repo.save(s);
        return toView(s);
    }

    @Transactional
    public ScheduleView update(UUID userId, UUID id, UpdateScheduleRequest req) {
        MedicationSchedule s = load(userId, id);
        if (req.dosageAmount() != null) s.setDosageAmount(req.dosageAmount());
        if (req.frequencyType() != null) s.setFrequencyType(req.frequencyType());
        if (req.frequencyValue() != null) s.setFrequencyValue(req.frequencyValue());
        if (req.scheduledTimes() != null) s.setScheduledTimes(parseTimes(req.scheduledTimes()));
        if (req.daysOfWeek() != null) s.setDaysOfWeek(toShortArray(req.daysOfWeek()));
        if (req.startsOn() != null) s.setStartsOn(req.startsOn());
        if (req.endsOn() != null) s.setEndsOn(req.endsOn());
        validate(s.getFrequencyType(), s.getFrequencyValue(),
                 s.getDaysOfWeek() == null ? null : Arrays.stream(s.getDaysOfWeek()).map(Short::intValue).toList());
        return toView(s);
    }

    @Transactional
    public ScheduleView setActive(UUID userId, UUID id, boolean active) {
        MedicationSchedule s = load(userId, id);
        s.setActive(active);
        return toView(s);
    }

    @Transactional
    public void delete(UUID userId, UUID id) { repo.delete(load(userId, id)); }

    private MedicationSchedule load(UUID userId, UUID id) {
        return repo.findByIdForUser(id, userId).orElseThrow(() -> ApiException.notFound("Schedule not found"));
    }

    private void validate(FrequencyType type, Integer value, List<Integer> days) {
        if (type == FrequencyType.interval && (value == null || value < 1))
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "interval schedules require frequencyValue >= 1");
        if (type == FrequencyType.weekly && (days == null || days.isEmpty()))
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "weekly schedules require daysOfWeek");
    }

    /** Core scheduling rule, used by the Phase 4 scheduler. Pure & unit-tested. */
    public static boolean isDue(MedicationSchedule s, LocalDateTime when) {
        if (!s.isActive()) return false;
        LocalDate date = when.toLocalDate();
        if (date.isBefore(s.getStartsOn())) return false;
        if (s.getEndsOn() != null && date.isAfter(s.getEndsOn())) return false;

        LocalTime minute = when.toLocalTime().withSecond(0).withNano(0);
        boolean timeMatches = s.getScheduledTimes() != null && Arrays.stream(s.getScheduledTimes())
                .anyMatch(t -> t.withSecond(0).withNano(0).equals(minute));
        if (!timeMatches) return false;

        return switch (s.getFrequencyType()) {
            case daily -> true;
            case weekly -> s.getDaysOfWeek() != null &&
                    Arrays.asList(s.getDaysOfWeek()).contains((short) date.getDayOfWeek().getValue());
            case interval -> {
                int n = s.getFrequencyValue() == null ? 1 : s.getFrequencyValue();
                long days = ChronoUnit.DAYS.between(s.getStartsOn(), date);
                yield n > 0 && days % n == 0;
            }
        };
    }

    private static LocalTime[] parseTimes(List<String> times) {
        return times.stream().map(LocalTime::parse).toArray(LocalTime[]::new);
    }
    private static Short[] toShortArray(List<Integer> days) {
        return days == null ? null : days.stream().map(Integer::shortValue).toArray(Short[]::new);
    }

    static ScheduleView toView(MedicationSchedule s) {
        return new ScheduleView(
                s.getId().toString(),
                s.getUserMedication().getId().toString(),
                s.getUserMedication().getMedication().getName(),
                s.getDosageAmount(), s.getFrequencyType(), s.getFrequencyValue(),
                s.getScheduledTimes() == null ? List.of() :
                        Arrays.stream(s.getScheduledTimes()).map(LocalTime::toString).toList(),
                s.getDaysOfWeek() == null ? null :
                        Arrays.stream(s.getDaysOfWeek()).map(Short::intValue).toList(),
                s.isActive(), s.getStartsOn(), s.getEndsOn());
    }
}
