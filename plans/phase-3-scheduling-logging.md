# Phase 3 — Scheduling & Logging

> **Goal:** per-medication dose schedules (daily / weekly / interval, pause/resume) and
> one-tap dose logging that **atomically** decrements inventory, plus consumption history.
>
> **Realizes:** EP-03, EP-04 · FR-030…045 · UC-004, UC-005.
> **Prerequisites:** [phase-2-core-inventory.md](phase-2-core-inventory.md).
> **Exit criteria:** schedule a medication, log a dose in one tap with inventory
> decrement, view history; the `isDue` logic is unit-tested and decrement is atomic.

---

## 1. Dependencies / migrations

No new dependencies, no new migrations (schedule + log tables exist from Phase 1).

---

## 2. Backend

### Reminders (schedules)

#### `backend/src/main/java/com/eczam/reminders/FrequencyType.java`

```java
package com.eczam.reminders;

public enum FrequencyType { daily, weekly, interval }
```

#### `backend/src/main/java/com/eczam/reminders/MedicationSchedule.java`

```java
package com.eczam.reminders;

import com.eczam.inventory.UserMedication;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medication_schedules")
@Getter @Setter @NoArgsConstructor
public class MedicationSchedule {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_medication_id", nullable = false)
    private UserMedication userMedication;

    @Column(name = "dosage_amount", nullable = false)
    private BigDecimal dosageAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false)
    private FrequencyType frequencyType;

    @Column(name = "frequency_value")
    private Integer frequencyValue;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scheduled_times", columnDefinition = "time[]", nullable = false)
    private LocalTime[] scheduledTimes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", columnDefinition = "smallint[]")
    private Short[] daysOfWeek;   // ISO: 1=Mon … 7=Sun

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn = LocalDate.now();

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
```

#### `backend/src/main/java/com/eczam/reminders/MedicationScheduleRepository.java`

```java
package com.eczam.reminders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, UUID> {

    @Query("SELECT s FROM MedicationSchedule s WHERE s.userMedication.userId = :userId ORDER BY s.createdAt DESC")
    List<MedicationSchedule> findAllForUser(@Param("userId") UUID userId);

    @Query("SELECT s FROM MedicationSchedule s WHERE s.userMedication.id = :umId AND s.userMedication.userId = :userId")
    List<MedicationSchedule> findForUserMedication(@Param("umId") UUID umId, @Param("userId") UUID userId);

    @Query("SELECT s FROM MedicationSchedule s WHERE s.id = :id AND s.userMedication.userId = :userId")
    Optional<MedicationSchedule> findByIdForUser(@Param("id") UUID id, @Param("userId") UUID userId);

    // Used by the Phase 4 scheduler:
    @Query("SELECT s FROM MedicationSchedule s WHERE s.active = true")
    List<MedicationSchedule> findAllActive();
}
```

#### `backend/src/main/java/com/eczam/reminders/dto/ScheduleDtos.java`

```java
package com.eczam.reminders.dto;

import com.eczam.reminders.FrequencyType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ScheduleDtos {

    public record ScheduleView(
            String id, String userMedicationId, String medicationName,
            BigDecimal dosageAmount, FrequencyType frequencyType, Integer frequencyValue,
            List<String> scheduledTimes, List<Integer> daysOfWeek,
            boolean active, LocalDate startsOn, LocalDate endsOn) {}

    public record CreateScheduleRequest(
            @NotNull @Positive BigDecimal dosageAmount,
            @NotNull FrequencyType frequencyType,
            Integer frequencyValue,
            @NotEmpty List<String> scheduledTimes,   // ["08:00","20:00"]
            List<Integer> daysOfWeek,                 // [1,3,5] for weekly
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record UpdateScheduleRequest(
            BigDecimal dosageAmount, FrequencyType frequencyType, Integer frequencyValue,
            List<String> scheduledTimes, List<Integer> daysOfWeek,
            LocalDate startsOn, LocalDate endsOn) {}

    private ScheduleDtos() {}
}
```

#### `backend/src/main/java/com/eczam/reminders/ScheduleService.java`

```java
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
```

#### `backend/src/main/java/com/eczam/reminders/ScheduleController.java`

```java
package com.eczam.reminders;

import com.eczam.reminders.dto.ScheduleDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ScheduleController {

    private final ScheduleService service;
    public ScheduleController(ScheduleService service) { this.service = service; }

    @GetMapping("/schedules")
    public ApiResponse<List<ScheduleView>> all(@CurrentUser UUID userId) {
        return ApiResponse.ok(service.listForUser(userId));
    }

    @GetMapping("/user-medications/{umId}/schedules")
    public ApiResponse<List<ScheduleView>> forMed(@CurrentUser UUID userId, @PathVariable UUID umId) {
        return ApiResponse.ok(service.listForUserMedication(userId, umId));
    }

    @PostMapping("/user-medications/{umId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleView> create(@CurrentUser UUID userId, @PathVariable UUID umId,
                                            @Valid @RequestBody CreateScheduleRequest req) {
        return ApiResponse.ok(service.create(userId, umId, req));
    }

    @PatchMapping("/schedules/{id}")
    public ApiResponse<ScheduleView> update(@CurrentUser UUID userId, @PathVariable UUID id,
                                            @Valid @RequestBody UpdateScheduleRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @PostMapping("/schedules/{id}/pause")
    public ApiResponse<ScheduleView> pause(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.setActive(userId, id, false));
    }

    @PostMapping("/schedules/{id}/resume")
    public ApiResponse<ScheduleView> resume(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.setActive(userId, id, true));
    }

    @DeleteMapping("/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
```

### Logs (dose logging)

#### Add a locked finder — `UserMedicationRepository` (extend Phase 2 file)

```java
// add these imports + method to com.eczam.inventory.UserMedicationRepository
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT um FROM UserMedication um WHERE um.id = :id AND um.userId = :userId")
Optional<UserMedication> findByIdAndUserIdForUpdate(java.util.UUID id, java.util.UUID userId);
```

#### `backend/src/main/java/com/eczam/logs/MedicationLog.java`

```java
package com.eczam.logs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable record of a taken dose (brief §6.5). */
@Entity
@Table(name = "medication_logs")
@Getter @Setter @NoArgsConstructor
public class MedicationLog {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "user_medication_id", nullable = false)
    private UUID userMedicationId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "taken_at", nullable = false)
    private OffsetDateTime takenAt = OffsetDateTime.now();

    @Column(name = "quantity_used", nullable = false)
    private BigDecimal quantityUsed;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
```

#### `backend/src/main/java/com/eczam/logs/MedicationLogRepository.java`

```java
package com.eczam.logs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, UUID> {

    @Query("""
           SELECT l FROM MedicationLog l
           WHERE l.userMedicationId = :umId
             AND (:from IS NULL OR l.takenAt >= :from)
             AND (:to IS NULL OR l.takenAt <= :to)
           ORDER BY l.takenAt DESC
           """)
    Page<MedicationLog> history(@Param("umId") UUID umId,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);
}
```

#### `backend/src/main/java/com/eczam/logs/dto/LogDtos.java`

```java
package com.eczam.logs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class LogDtos {

    public record CreateLogRequest(
            @NotNull String userMedicationId,
            @NotNull @Positive BigDecimal quantityUsed,
            String scheduleId,
            String notes) {}

    public record LogView(String id, String userMedicationId, String scheduleId,
                          OffsetDateTime takenAt, BigDecimal quantityUsed, String notes) {}

    public record LogResult(LogView log, BigDecimal newQuantity, boolean lowStock) {}

    private LogDtos() {}
}
```

#### `backend/src/main/java/com/eczam/logs/MedicationLogService.java`

```java
package com.eczam.logs;

import com.eczam.inventory.UserMedication;
import com.eczam.inventory.UserMedicationRepository;
import com.eczam.logs.dto.LogDtos.*;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MedicationLogService {

    private final MedicationLogRepository logs;
    private final UserMedicationRepository inventory;
    private final UserRepository users;

    public MedicationLogService(MedicationLogRepository logs, UserMedicationRepository inventory, UserRepository users) {
        this.logs = logs; this.inventory = inventory; this.users = users;
    }

    /** Atomic: lock the inventory row, guard stock, insert log, decrement (UC-005, FR-040..043). */
    @Transactional
    public LogResult logDose(UUID userId, CreateLogRequest req) {
        UUID umId = UUID.fromString(req.userMedicationId());
        UserMedication um = inventory.findByIdAndUserIdForUpdate(umId, userId)
                .orElseThrow(() -> ApiException.notFound("Inventory entry not found"));

        BigDecimal qty = req.quantityUsed();
        if (um.getQuantity().compareTo(qty) < 0) {
            throw ApiException.badRequest(ErrorCode.INSUFFICIENT_STOCK,
                    "Not enough stock to log this dose");
        }

        MedicationLog log = new MedicationLog();
        log.setUserMedicationId(umId);
        log.setScheduleId(req.scheduleId() == null ? null : UUID.fromString(req.scheduleId()));
        log.setQuantityUsed(qty);
        log.setNotes(req.notes());
        log.setTakenAt(OffsetDateTime.now());
        logs.save(log);

        um.setQuantity(um.getQuantity().subtract(qty));

        int threshold = users.findById(userId)
                .map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
        boolean lowStock = um.getQuantity().doubleValue() <= threshold;

        return new LogResult(toView(log), um.getQuantity(), lowStock);
    }

    @Transactional(readOnly = true)
    public List<LogView> history(UUID userId, UUID umId, OffsetDateTime from, OffsetDateTime to, int limit) {
        // ownership check
        inventory.findByIdAndUserId(umId, userId)
                .orElseThrow(() -> ApiException.notFound("Inventory entry not found"));
        return logs.history(umId, from, to, PageRequest.of(0, limit))
                .map(MedicationLogService::toView).getContent();
    }

    static LogView toView(MedicationLog l) {
        return new LogView(l.getId().toString(), l.getUserMedicationId().toString(),
                l.getScheduleId() == null ? null : l.getScheduleId().toString(),
                l.getTakenAt(), l.getQuantityUsed(), l.getNotes());
    }
}
```

#### `backend/src/main/java/com/eczam/logs/MedicationLogController.java`

```java
package com.eczam.logs;

import com.eczam.logs.dto.LogDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/medication-logs")
public class MedicationLogController {

    private final MedicationLogService service;
    public MedicationLogController(MedicationLogService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LogResult> log(@CurrentUser UUID userId, @Valid @RequestBody CreateLogRequest req) {
        return ApiResponse.ok(service.logDose(userId, req));
    }

    @GetMapping
    public ApiResponse<List<LogView>> history(
            @CurrentUser UUID userId,
            @RequestParam UUID userMedicationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.history(userId, userMedicationId, from, to, limit));
    }
}
```

### Backend tests

#### `backend/src/test/java/com/eczam/reminders/ScheduleIsDueTest.java`

```java
package com.eczam.reminders;

import com.eczam.inventory.UserMedication;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleIsDueTest {

    private MedicationSchedule base(FrequencyType type) {
        MedicationSchedule s = new MedicationSchedule();
        s.setUserMedication(new UserMedication());
        s.setActive(true);
        s.setDosageAmount(BigDecimal.ONE);
        s.setFrequencyType(type);
        s.setScheduledTimes(new LocalTime[]{ LocalTime.of(8, 0), LocalTime.of(20, 0) });
        s.setStartsOn(LocalDate.of(2026, 1, 1));
        return s;
    }

    @Test void daily_due_at_scheduled_minute() {
        var s = base(FrequencyType.daily);
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isTrue();
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 9, 0))).isFalse();
    }

    @Test void paused_is_never_due() {
        var s = base(FrequencyType.daily); s.setActive(false);
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isFalse();
    }

    @Test void weekly_only_on_listed_days() {
        var s = base(FrequencyType.weekly);
        s.setDaysOfWeek(new Short[]{ 1, 3, 5 }); // Mon/Wed/Fri
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 17, 8, 0))).isTrue();  // Wed
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isFalse(); // Thu
    }

    @Test void interval_every_n_days_from_start() {
        var s = base(FrequencyType.interval); s.setFrequencyValue(2);
        s.setStartsOn(LocalDate.of(2026, 6, 16));
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 16, 8, 0))).isTrue();
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 17, 8, 0))).isFalse();
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isTrue();
    }
}
```

#### `backend/src/test/java/com/eczam/logs/DoseLoggingIntegrationTest.java` (sketch)

```java
package com.eczam.logs;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
// ... arrange a user + medication + inventory(quantity=10) via the API or repositories,
// then POST /medication-logs twice and assert:
//  - first dose: 201, newQuantity = 9
//  - logging quantityUsed > remaining: 422 INSUFFICIENT_STOCK, quantity unchanged
//  - (concurrency) two parallel single-unit logs against quantity=1 → exactly one succeeds
class DoseLoggingIntegrationTest extends AbstractIntegrationTest {
    @Test void decrement_is_atomic_and_guarded() { /* see comments above */ }
}
```

---

## 3. Frontend

### `frontend/src/services/scheduleService.ts`

```ts
import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export type FrequencyType = "daily" | "weekly" | "interval";
export interface ScheduleView {
  id: string; userMedicationId: string; medicationName: string;
  dosageAmount: number; frequencyType: FrequencyType; frequencyValue?: number;
  scheduledTimes: string[]; daysOfWeek?: number[];
  active: boolean; startsOn: string; endsOn?: string;
}

export async function listSchedules(): Promise<ScheduleView[]> {
  const r = await apiClient.get<ApiResponse<ScheduleView[]>>("/schedules");
  return r.data.data!;
}
export async function createSchedule(umId: string, payload: {
  dosageAmount: number; frequencyType: FrequencyType; frequencyValue?: number;
  scheduledTimes: string[]; daysOfWeek?: number[]; startsOn?: string; endsOn?: string;
}) {
  const r = await apiClient.post<ApiResponse<ScheduleView>>(`/user-medications/${umId}/schedules`, payload);
  return r.data.data!;
}
export const pauseSchedule  = (id: string) => apiClient.post(`/schedules/${id}/pause`);
export const resumeSchedule = (id: string) => apiClient.post(`/schedules/${id}/resume`);
export const deleteSchedule = (id: string) => apiClient.delete(`/schedules/${id}`);
```

### `frontend/src/services/logService.ts`

```ts
import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export interface LogResult { log: { id: string; takenAt: string }; newQuantity: number; lowStock: boolean; }
export interface LogView { id: string; takenAt: string; quantityUsed: number; notes?: string; }

export async function logDose(userMedicationId: string, quantityUsed: number, scheduleId?: string): Promise<LogResult> {
  const r = await apiClient.post<ApiResponse<LogResult>>("/medication-logs",
    { userMedicationId, quantityUsed, scheduleId });
  return r.data.data!;
}
export async function listLogs(userMedicationId: string): Promise<LogView[]> {
  const r = await apiClient.get<ApiResponse<LogView[]>>("/medication-logs", { params: { userMedicationId } });
  return r.data.data!;
}
```

### `frontend/src/features/reminders/LogDoseButton.tsx` (one-tap)

```tsx
import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { logDose } from "../../services/logService";

export default function LogDoseButton({ userMedicationId, amount = 1, scheduleId }: {
  userMedicationId: string; amount?: number; scheduleId?: string;
}) {
  const qc = useQueryClient();
  const [msg, setMsg] = useState<string | null>(null);

  async function onClick() {
    try {
      const res = await logDose(userMedicationId, amount, scheduleId);
      setMsg(`Alındı. Kalan: ${res.newQuantity}`);
      qc.invalidateQueries({ queryKey: ["inventory"] });
    } catch (e: any) {
      setMsg(e?.response?.data?.error?.code === "INSUFFICIENT_STOCK" ? "Yetersiz stok." : "Hata.");
    }
  }

  return (
    <div>
      <button onClick={onClick} className="rounded bg-green-700 px-4 py-2 text-lg font-semibold text-white">
        ✓ Aldım
      </button>
      {msg && <span role="status" className="ml-3 text-lg">{msg}</span>}
    </div>
  );
}
```

### `frontend/src/features/reminders/ScheduleForm.tsx`

```tsx
import { useState } from "react";
import { createSchedule, type FrequencyType } from "../../services/scheduleService";

const DAYS = [["Pzt",1],["Sal",2],["Çar",3],["Per",4],["Cum",5],["Cmt",6],["Paz",7]] as const;

export default function ScheduleForm({ userMedicationId, onCreated }: {
  userMedicationId: string; onCreated: () => void;
}) {
  const [dosageAmount, setDosage] = useState(1);
  const [frequencyType, setType] = useState<FrequencyType>("daily");
  const [frequencyValue, setValue] = useState(2);
  const [times, setTimes] = useState<string[]>(["08:00"]);
  const [days, setDays] = useState<number[]>([]);

  async function submit() {
    await createSchedule(userMedicationId, {
      dosageAmount, frequencyType,
      frequencyValue: frequencyType === "interval" ? frequencyValue : undefined,
      scheduledTimes: times,
      daysOfWeek: frequencyType === "weekly" ? days : undefined,
    });
    onCreated();
  }

  return (
    <div className="space-y-4 rounded border p-4">
      <h2 className="text-xl font-semibold">Yeni Program</h2>
      <label className="block"><span className="text-lg">Doz (adet)</span>
        <input type="number" min={0} value={dosageAmount} onChange={(e) => setDosage(Number(e.target.value))}
               className="mt-1 w-full rounded border p-3 text-lg" /></label>

      <label className="block"><span className="text-lg">Sıklık</span>
        <select value={frequencyType} onChange={(e) => setType(e.target.value as FrequencyType)}
                className="mt-1 w-full rounded border p-3 text-lg">
          <option value="daily">Her gün</option>
          <option value="weekly">Haftanın belirli günleri</option>
          <option value="interval">Her N günde bir</option>
        </select></label>

      {frequencyType === "interval" && (
        <label className="block"><span className="text-lg">Kaç günde bir</span>
          <input type="number" min={1} value={frequencyValue} onChange={(e) => setValue(Number(e.target.value))}
                 className="mt-1 w-full rounded border p-3 text-lg" /></label>
      )}

      {frequencyType === "weekly" && (
        <fieldset className="flex flex-wrap gap-2">
          <legend className="text-lg">Günler</legend>
          {DAYS.map(([label, n]) => (
            <button type="button" key={n}
              onClick={() => setDays((d) => d.includes(n) ? d.filter((x) => x !== n) : [...d, n])}
              className={`rounded border px-3 py-2 text-lg ${days.includes(n) ? "bg-blue-700 text-white" : ""}`}>
              {label}
            </button>
          ))}
        </fieldset>
      )}

      <div>
        <span className="text-lg">Saatler</span>
        {times.map((t, i) => (
          <input key={i} type="time" value={t}
            onChange={(e) => setTimes((arr) => arr.map((x, j) => j === i ? e.target.value : x))}
            className="mt-1 mr-2 rounded border p-2 text-lg" />
        ))}
        <button type="button" onClick={() => setTimes((a) => [...a, "20:00"])}
                className="rounded border px-3 py-2 text-lg">+ saat</button>
      </div>

      <button onClick={submit} className="w-full rounded bg-blue-700 p-3 text-lg font-semibold text-white">
        Programı Kaydet
      </button>
    </div>
  );
}
```

### `frontend/src/pages/Schedules.tsx`

```tsx
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { listSchedules, pauseSchedule, resumeSchedule, deleteSchedule } from "../services/scheduleService";

export default function Schedules() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["schedules"], queryFn: listSchedules });
  if (isLoading) return <p className="p-6 text-xl">Yükleniyor…</p>;
  const refresh = () => qc.invalidateQueries({ queryKey: ["schedules"] });

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="mb-6 text-3xl font-bold">Programlar</h1>
      <ul className="space-y-3">
        {data?.map((s) => (
          <li key={s.id} className="rounded border p-4">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-xl font-semibold">{s.medicationName}</span>
                <p className="text-lg text-gray-600">{s.dosageAmount} · {s.scheduledTimes.join(", ")}
                  {!s.active && <span className="ml-2 rounded bg-gray-200 px-2">Duraklatıldı</span>}</p>
              </div>
              <div className="flex gap-2">
                <button onClick={async () => { await (s.active ? pauseSchedule(s.id) : resumeSchedule(s.id)); refresh(); }}
                        className="rounded border px-3 py-2 text-lg">{s.active ? "Duraklat" : "Devam"}</button>
                <button onClick={async () => { await deleteSchedule(s.id); refresh(); }}
                        className="rounded border px-3 py-2 text-lg text-red-700">Sil</button>
              </div>
            </div>
          </li>
        ))}
        {data?.length === 0 && <p className="text-lg text-gray-600">Henüz program yok.</p>}
      </ul>
    </main>
  );
}
```

### `frontend/src/pages/Logs.tsx`

```tsx
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { listInventory } from "../services/inventoryService";
import { listLogs } from "../services/logService";

export default function Logs() {
  const { data: items } = useQuery({ queryKey: ["inventory"], queryFn: listInventory });
  const [selected, setSelected] = useState<string>("");
  const { data: logs } = useQuery({
    queryKey: ["logs", selected], queryFn: () => listLogs(selected), enabled: !!selected,
  });

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="mb-6 text-3xl font-bold">Geçmiş</h1>
      <select value={selected} onChange={(e) => setSelected(e.target.value)}
              className="mb-4 w-full rounded border p-3 text-lg">
        <option value="">İlaç seçin…</option>
        {items?.map((i) => <option key={i.id} value={i.id}>{i.medicationName}</option>)}
      </select>
      <ul className="space-y-2">
        {logs?.map((l) => (
          <li key={l.id} className="rounded border p-3 text-lg">
            {new Date(l.takenAt).toLocaleString("tr-TR")} — {l.quantityUsed} alındı
          </li>
        ))}
        {selected && logs?.length === 0 && <p className="text-gray-600">Kayıt yok.</p>}
      </ul>
    </main>
  );
}
```

### Wire routes — `frontend/src/App.tsx`

```tsx
import Schedules from "./pages/Schedules";
import Logs from "./pages/Logs";
// inside ProtectedRoute:
<Route path="/schedules" element={<Schedules />} />
<Route path="/logs" element={<Logs />} />
```

> Add a `ScheduleForm` + `LogDoseButton` to `MedicationDetail.tsx` so users can schedule
> and log from the medication page.

---

## 4. Exit criteria (Phase 3)

- [ ] Create daily/weekly/interval schedules; invalid combos → 422.
- [ ] Pause/resume/delete schedules; "all schedules" view works.
- [ ] One-tap "Aldım" logs a dose and decrements inventory atomically.
- [ ] Logging more than remaining → 422 INSUFFICIENT_STOCK, no negative quantity.
- [ ] History page lists doses per medication.

## 5. Tests (Phase 3)

- Unit: `ScheduleIsDueTest` (all frequency types, pause, boundaries).
- Integration: `DoseLoggingIntegrationTest` (decrement, insufficient-stock,
  concurrency); schedule CRUD + authorization.
- Frontend: ScheduleForm variants, LogDoseButton success/insufficient paths.

Covers FR-030…045, NFR-021 (atomicity), UC-004/005. Next:
[phase-4-notifications.md](phase-4-notifications.md).
