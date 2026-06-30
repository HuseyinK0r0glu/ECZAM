package com.eczam.logs;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotent dose logging: replaying a queued offline write (same
 * {@code clientRequestId}) must not decrement stock twice. Pairs with the Flutter
 * sync engine's stable key (plans/testing-plan.md §7.4).
 */
class DoseLogIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;
    @Autowired UserMedicationRepository inventory;

    private record Ctx(UUID userId, String token) {}

    private Ctx register(String email) {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", email, "password", "ValidP@ss1!", "displayName", "T"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        return new Ctx(UUID.fromString((String) user.get("id")), (String) data.get("accessToken"));
    }

    private UUID seedInventory(UUID userId, BigDecimal qty) {
        Medication med = new Medication();
        med.setName("Idem Med " + UUID.randomUUID());
        med = medications.save(med);
        UserMedication um = new UserMedication();
        um.setUserId(userId);
        um.setMedication(med);
        um.setQuantity(qty);
        return inventory.save(um).getId();
    }

    private HttpEntity<Map<String, Object>> body(String token, Map<String, Object> payload) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, h);
    }

    @Test
    void replaying_the_same_client_key_does_not_double_decrement() {
        Ctx ctx = register("idem-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId(), new BigDecimal("10"));

        Map<String, Object> payload = Map.of(
                "userMedicationId", umId.toString(), "quantityUsed", 1, "clientRequestId", "dose-key-1");

        var first = rest.exchange("/medication-logs", HttpMethod.POST, body(ctx.token(), payload), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(inventory.findById(umId).orElseThrow().getQuantity())
                .isEqualByComparingTo(new BigDecimal("9"));

        // Replay with the SAME key → no second decrement.
        var replay = rest.exchange("/medication-logs", HttpMethod.POST, body(ctx.token(), payload), Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(inventory.findById(umId).orElseThrow().getQuantity())
                .isEqualByComparingTo(new BigDecimal("9"));

        // A different key is a new dose → decrements again.
        var second = rest.exchange("/medication-logs", HttpMethod.POST,
                body(ctx.token(), Map.of("userMedicationId", umId.toString(),
                        "quantityUsed", 1, "clientRequestId", "dose-key-2")), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(inventory.findById(umId).orElseThrow().getQuantity())
                .isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void no_key_writes_always_insert() {
        Ctx ctx = register("nokey-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId(), new BigDecimal("5"));
        Map<String, Object> payload = Map.of("userMedicationId", umId.toString(), "quantityUsed", 1);

        rest.exchange("/medication-logs", HttpMethod.POST, body(ctx.token(), payload), Map.class);
        rest.exchange("/medication-logs", HttpMethod.POST, body(ctx.token(), payload), Map.class);

        assertThat(inventory.findById(umId).orElseThrow().getQuantity())
                .isEqualByComparingTo(new BigDecimal("3"));
    }
}
