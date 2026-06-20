package com.eczam.logs;

import com.eczam.inventory.UserMedication;
import com.eczam.inventory.UserMedicationRepository;
import com.eczam.logs.dto.LogDtos.*;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.shared.web.Inputs;
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
        UUID umId = Inputs.uuid(req.userMedicationId(), "userMedicationId");
        UserMedication um = inventory.findByIdAndUserIdForUpdate(umId, userId)
                .orElseThrow(() -> ApiException.notFound("Inventory entry not found"));

        BigDecimal qty = req.quantityUsed();
        if (um.getQuantity().compareTo(qty) < 0) {
            throw ApiException.badRequest(ErrorCode.INSUFFICIENT_STOCK,
                    "Not enough stock to log this dose");
        }

        MedicationLog log = new MedicationLog();
        log.setUserMedicationId(umId);
        log.setScheduleId(Inputs.uuidOrNull(req.scheduleId(), "scheduleId"));
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
