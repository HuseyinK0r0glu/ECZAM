package com.eczam.expiration;

import com.eczam.AbstractIntegrationTest;
import com.eczam.inventory.UserMedication;
import com.eczam.inventory.UserMedicationRepository;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpirationIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;
    @Autowired UserMedicationRepository inventory;

    private record Ctx(UUID userId, String token) {}

    private Ctx registerUser(String email) {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", email, "password", "password1", "displayName", "Exp"), Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        return new Ctx(UUID.fromString((String) user.get("id")), (String) data.get("accessToken"));
    }

    private UUID seed(UUID userId, BigDecimal qty, LocalDate expiry) {
        Medication med = new Medication();
        med.setName("Med " + UUID.randomUUID());
        med = medications.save(med);
        UserMedication um = new UserMedication();
        um.setUserId(userId);
        um.setMedication(med);
        um.setQuantity(qty);
        um.setExpirationDate(expiry);
        return inventory.save(um).getId();
    }

    @Test
    void native_queries_and_endpoints_classify_rows_correctly() {
        Ctx ctx = registerUser("exp-" + UUID.randomUUID() + "@b.com"); // defaults: low=7, expiry=30
        LocalDate today = LocalDate.now();
        UUID low = seed(ctx.userId(), new BigDecimal("3"), null);                 // low stock
        UUID soon = seed(ctx.userId(), new BigDecimal("100"), today.plusDays(10)); // expiring soon
        UUID expired = seed(ctx.userId(), new BigDecimal("100"), today.minusDays(1)); // expired
        UUID healthy = seed(ctx.userId(), new BigDecimal("100"), today.plusDays(365)); // fine

        assertThat(inventory.findLowStock()).extracting(UserMedication::getId).contains(low).doesNotContain(soon, healthy);
        assertThat(inventory.findExpiringSoon()).extracting(UserMedication::getId).contains(soon).doesNotContain(expired, healthy);
        assertThat(inventory.findExpired()).extracting(UserMedication::getId).contains(expired).doesNotContain(soon, healthy);

        // COALESCE null-param path: days=null uses the user's 30-day window → includes the +10 item.
        assertThat(inventory.findExpiringSoonForUser(ctx.userId(), null))
                .extracting(UserMedication::getId).contains(soon);
        // days=5 narrows the window → excludes the +10 item.
        assertThat(inventory.findExpiringSoonForUser(ctx.userId(), 5))
                .extracting(UserMedication::getId).doesNotContain(soon);
        assertThat(inventory.findExpiredForUser(ctx.userId()))
                .extracting(UserMedication::getId).contains(expired);

        // Endpoints
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(ctx.token());
        var soonResp = rest.exchange("/expiration/expiring-soon", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(soonResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> soonItems = (List<Map<String, Object>>) soonResp.getBody().get("data");
        assertThat(soonItems).extracting(i -> i.get("id")).contains(soon.toString());

        var expiredResp = rest.exchange("/expiration/expired", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expiredItems = (List<Map<String, Object>>) expiredResp.getBody().get("data");
        assertThat(expiredItems).extracting(i -> i.get("id")).contains(expired.toString());
    }
}
