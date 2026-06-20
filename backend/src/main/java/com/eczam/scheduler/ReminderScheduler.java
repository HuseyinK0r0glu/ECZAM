package com.eczam.scheduler;

import com.eczam.inventory.UserMedication;
import com.eczam.inventory.UserMedicationRepository;
import com.eczam.notifications.NotificationService;
import com.eczam.notifications.NotificationType;
import com.eczam.reminders.MedicationSchedule;
import com.eczam.reminders.MedicationScheduleRepository;
import com.eczam.reminders.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Runs every minute (brief §7.1). For clustering, wrap with ShedLock so one node runs it. */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final MedicationScheduleRepository schedules;
    private final UserMedicationRepository inventory;
    private final NotificationService notifications;
    private final NotificationDedupe dedupe;

    public ReminderScheduler(MedicationScheduleRepository schedules, UserMedicationRepository inventory,
                             NotificationService notifications, NotificationDedupe dedupe) {
        this.schedules = schedules; this.inventory = inventory;
        this.notifications = notifications; this.dedupe = dedupe;
    }

    // Not wrapped in a single (read-only) transaction: each repository read runs in its
    // own transaction, and NotificationService.notifyUser opens its own read-write
    // transaction so it can prune dead (404/410) push subscriptions. A read-only outer
    // transaction would set Hibernate's flush mode to MANUAL and silently drop those
    // deletes. Schedule/inventory associations are EAGER, so detached access is safe.
    @Scheduled(cron = "0 * * * * *")  // top of every minute
    public void tick() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        sendDoseReminders(now);
        sendLowStock();
        sendExpiry();
    }

    private void sendDoseReminders(LocalDateTime now) {
        for (MedicationSchedule s : schedules.findAllActive()) {
            if (!ScheduleService.isDue(s, now)) continue;
            String key = "dose:" + s.getId() + ":" + now;
            if (!dedupe.firstTimeForMinute(key)) continue;
            UserMedication um = s.getUserMedication();
            notifications.notifyUser(um.getUserId(), NotificationType.DOSE_REMINDER,
                    "İlaç zamanı: " + um.getMedication().getName(),
                    "Doz: " + s.getDosageAmount() + " " + um.getUnit(),
                    Map.of("userMedicationId", um.getId().toString(),
                           "scheduleId", s.getId().toString(),
                           "action", "MARK_TAKEN"));
        }
    }

    private void sendLowStock() {
        for (UserMedication um : inventory.findLowStock()) {
            String key = "low:" + um.getId() + ":" + LocalDate.now();
            if (!dedupe.firstTimeToday(key)) continue;
            notifications.notifyUser(um.getUserId(), NotificationType.LOW_STOCK,
                    "Az kaldı: " + um.getMedication().getName(),
                    "Kalan: " + um.getQuantity() + " " + um.getUnit(),
                    Map.of("userMedicationId", um.getId().toString()));
        }
    }

    private void sendExpiry() {
        for (UserMedication um : inventory.findExpiringSoon()) {
            String key = "exp:" + um.getId() + ":" + LocalDate.now();
            if (!dedupe.firstTimeToday(key)) continue;
            long days = ChronoUnit.DAYS.between(LocalDate.now(), um.getExpirationDate());
            notifications.notifyUser(um.getUserId(), NotificationType.EXPIRY_WARNING,
                    "Yakında dolacak: " + um.getMedication().getName(),
                    days + " gün kaldı", Map.of("userMedicationId", um.getId().toString()));
        }
        for (UserMedication um : inventory.findExpired()) {
            String key = "expd:" + um.getId() + ":" + LocalDate.now();
            if (!dedupe.firstTimeToday(key)) continue;
            notifications.notifyUser(um.getUserId(), NotificationType.EXPIRED,
                    "Süresi doldu: " + um.getMedication().getName(),
                    "Son kullanma: " + um.getExpirationDate(),
                    Map.of("userMedicationId", um.getId().toString()));
        }
    }
}
