package com.eczam.inventory;

import com.eczam.inventory.dto.InventoryDtos.*;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class UserMedicationService {

    private final UserMedicationRepository repo;
    private final MedicationRepository medications;
    private final UserRepository users;

    public UserMedicationService(UserMedicationRepository repo, MedicationRepository medications, UserRepository users) {
        this.repo = repo; this.medications = medications; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> list(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        int lowThreshold = u.getNotificationPreferences().lowStockThreshold();
        int expiryDays = u.getNotificationPreferences().expiryWarningDays();
        return repo.findByUserIdOrderByAddedAtDesc(userId).stream()
                .map(um -> toItem(um, lowThreshold, expiryDays)).toList();
    }

    @Transactional
    public InventoryItem create(UUID userId, CreateInventoryRequest req) {
        UUID medId = UUID.fromString(req.medicationId());
        Medication med = medications.findById(medId)
                .orElseThrow(() -> ApiException.notFound("Medication not found"));
        // Per-box model: one physical box = one row. A serial (GS1 AI 21) is
        // unique to a box, so a re-scan of the same box is the only true
        // duplicate; boxes that share a product+expiry but differ by batch/serial
        // are legitimately distinct rows (DB enforces the 5-column UNIQUE).
        if (req.serialNumber() != null && !req.serialNumber().isBlank()
                && repo.existsByUserIdAndSerialNumber(userId, req.serialNumber())) {
            throw ApiException.conflict(ErrorCode.INVENTORY_BATCH_EXISTS,
                    "This exact box (serial number) is already in your inventory");
        }
        UserMedication um = new UserMedication();
        um.setUserId(userId);
        um.setMedication(med);
        um.setQuantity(req.quantity());
        if (req.unit() != null) um.setUnit(req.unit());
        um.setExpirationDate(req.expirationDate());
        um.setBatch(req.batch());
        um.setSerialNumber(req.serialNumber());
        um.setNotes(req.notes());
        repo.save(um);
        return toItem(um, prefsLow(userId), prefsExpiry(userId));
    }

    @Transactional
    public InventoryItem update(UUID userId, UUID id, UpdateInventoryRequest req) {
        UserMedication um = load(userId, id);
        if (req.quantity() != null) um.setQuantity(req.quantity());
        if (req.unit() != null) um.setUnit(req.unit());
        if (req.expirationDate() != null) um.setExpirationDate(req.expirationDate());
        if (req.batch() != null) um.setBatch(req.batch());
        if (req.serialNumber() != null) um.setSerialNumber(req.serialNumber());
        if (req.notes() != null) um.setNotes(req.notes());
        return toItem(um, prefsLow(userId), prefsExpiry(userId));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repo.delete(load(userId, id));
    }

    @Transactional(readOnly = true)
    public InventoryItem get(UUID userId, UUID id) {
        return toItem(load(userId, id), prefsLow(userId), prefsExpiry(userId));
    }

    UserMedication load(UUID userId, UUID id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Inventory entry not found"));
    }

    private int prefsLow(UUID userId) {
        return users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
    }
    private int prefsExpiry(UUID userId) {
        return users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
    }

    public static InventoryItem toItem(UserMedication um, int lowThreshold, int expiryDays) {
        boolean lowStock = um.getQuantity().doubleValue() <= lowThreshold;
        ExpiryStatus status = expiryStatus(um.getExpirationDate(), expiryDays);
        Medication m = um.getMedication();
        return new InventoryItem(um.getId().toString(), m.getId().toString(), m.getName(),
                m.getStrength(), m.getForm(), um.getQuantity(), um.getUnit(),
                um.getExpirationDate(), um.getNotes(), um.getBatch(), um.getSerialNumber(),
                lowStock, status);
    }

    static ExpiryStatus expiryStatus(LocalDate expiry, int warningDays) {
        if (expiry == null) return ExpiryStatus.OK;
        LocalDate today = LocalDate.now();
        if (expiry.isBefore(today)) return ExpiryStatus.EXPIRED;
        if (!expiry.isAfter(today.plusDays(warningDays))) return ExpiryStatus.EXPIRING_SOON;
        return ExpiryStatus.OK;
    }
}
