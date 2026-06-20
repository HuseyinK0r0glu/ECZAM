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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class DoseLoggingIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;
    @Autowired UserMedicationRepository inventory;

    private record Ctx(UUID userId, String token) {}

    private Ctx registerUser(String email) {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", email, "password", "password1", "displayName", "Test"),
                Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        return new Ctx(UUID.fromString((String) user.get("id")), (String) data.get("accessToken"));
    }

    private UUID seedInventory(UUID userId, BigDecimal quantity) {
        Medication med = new Medication();
        med.setName("Test Med " + UUID.randomUUID());
        med = medications.save(med);
        UserMedication um = new UserMedication();
        um.setUserId(userId);
        um.setMedication(med);
        um.setQuantity(quantity);
        return inventory.save(um).getId();
    }

    private HttpEntity<Map<String, Object>> body(String token, Map<String, Object> payload) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, h);
    }

    @Test
    void decrement_is_atomic_and_guarded() {
        Ctx ctx = registerUser("dose-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId(), new BigDecimal("10"));

        // first dose: 201, newQuantity = 9
        var first = rest.exchange("/medication-logs", HttpMethod.POST,
                body(ctx.token(), Map.of("userMedicationId", umId.toString(), "quantityUsed", 1)),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) first.getBody().get("data");
        assertThat(Double.parseDouble(data.get("newQuantity").toString())).isEqualTo(9.0);

        // logging more than remaining: 422 INSUFFICIENT_STOCK, quantity unchanged
        var over = rest.exchange("/medication-logs", HttpMethod.POST,
                body(ctx.token(), Map.of("userMedicationId", umId.toString(), "quantityUsed", 100)),
                Map.class);
        assertThat(over.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) over.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(inventory.findById(umId).orElseThrow().getQuantity())
                .isEqualByComparingTo(new BigDecimal("9"));
    }

    @Test
    void malformed_user_medication_id_is_422_not_500() {
        Ctx ctx = registerUser("bad-" + UUID.randomUUID() + "@b.com");
        var res = rest.exchange("/medication-logs", HttpMethod.POST,
                body(ctx.token(), Map.of("userMedicationId", "not-a-uuid", "quantityUsed", 1)),
                Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) res.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void concurrent_logs_against_single_unit_only_one_succeeds() throws Exception {
        Ctx ctx = registerUser("race-" + UUID.randomUUID() + "@b.com");
        UUID umId = seedInventory(ctx.userId(), BigDecimal.ONE);

        Callable<HttpStatus> attempt = () -> rest.exchange("/medication-logs", HttpMethod.POST,
                body(ctx.token(), Map.of("userMedicationId", umId.toString(), "quantityUsed", 1)),
                Map.class).getStatusCode().value() == 201 ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<HttpStatus>> results = pool.invokeAll(List.of(attempt, attempt));
            long created = results.stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).filter(HttpStatus.CREATED::equals).count();
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(inventory.findById(umId).orElseThrow().getQuantity())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
