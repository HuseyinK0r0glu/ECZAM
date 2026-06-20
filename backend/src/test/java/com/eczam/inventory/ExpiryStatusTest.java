package com.eczam.inventory;

import com.eczam.inventory.dto.InventoryDtos.ExpiryStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryStatusTest {
    @Test void expired_when_before_today() {
        assertThat(UserMedicationService.expiryStatus(LocalDate.now().minusDays(1), 30))
                .isEqualTo(ExpiryStatus.EXPIRED);
    }
    @Test void expiring_soon_within_window() {
        assertThat(UserMedicationService.expiryStatus(LocalDate.now().plusDays(10), 30))
                .isEqualTo(ExpiryStatus.EXPIRING_SOON);
    }
    @Test void ok_beyond_window() {
        assertThat(UserMedicationService.expiryStatus(LocalDate.now().plusDays(60), 30))
                .isEqualTo(ExpiryStatus.OK);
    }
}
