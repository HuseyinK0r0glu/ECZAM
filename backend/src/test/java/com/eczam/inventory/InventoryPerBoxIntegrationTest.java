package com.eczam.inventory;

import com.eczam.AbstractIntegrationTest;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-box (GS1 batch/serial) inventory: one physical box = one row. Two boxes of
 * the same product are distinct rows; re-scanning the same serial is rejected
 * (plans/testing-plan.md §4.4).
 */
class InventoryPerBoxIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;

    private record Ctx(String token) {}

    private Ctx register(String email) {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", email, "password", "ValidP@ss1!", "displayName", "T"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        return new Ctx((String) data.get("accessToken"));
    }

    private UUID seedCatalog() {
        Medication med = new Medication();
        med.setName("Box Med " + UUID.randomUUID());
        med.setStrength("400 mg");
        return medications.save(med).getId();
    }

    private HttpEntity<Map<String, Object>> body(String token, Map<String, Object> payload) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, h);
    }

    @SuppressWarnings("unchecked")
    @Test
    void distinct_serials_create_distinct_rows_and_round_trip_batch_serial() {
        Ctx ctx = register("box-" + UUID.randomUUID() + "@b.com");
        String medId = seedCatalog().toString();

        var box1 = rest.exchange("/user-medications", HttpMethod.POST, body(ctx.token(), Map.of(
                "medicationId", medId, "quantity", 30, "unit", "pill",
                "batch", "LOT123", "serialNumber", "SER-A")), Map.class);
        assertThat(box1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> item = (Map<String, Object>) box1.getBody().get("data");
        assertThat(item.get("batch")).isEqualTo("LOT123");
        assertThat(item.get("serialNumber")).isEqualTo("SER-A");

        // Second physical box, same product, different serial → second row.
        var box2 = rest.exchange("/user-medications", HttpMethod.POST, body(ctx.token(), Map.of(
                "medicationId", medId, "quantity", 30, "serialNumber", "SER-B")), Map.class);
        assertThat(box2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var list = rest.exchange("/user-medications", HttpMethod.GET, body(ctx.token(), Map.of()), Map.class);
        List<Object> items = (List<Object>) list.getBody().get("data");
        assertThat(items).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void rescanning_the_same_serial_is_rejected() {
        Ctx ctx = register("dup-" + UUID.randomUUID() + "@b.com");
        String medId = seedCatalog().toString();

        var first = rest.exchange("/user-medications", HttpMethod.POST, body(ctx.token(), Map.of(
                "medicationId", medId, "quantity", 10, "serialNumber", "SER-DUP")), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var dup = rest.exchange("/user-medications", HttpMethod.POST, body(ctx.token(), Map.of(
                "medicationId", medId, "quantity", 10, "serialNumber", "SER-DUP")), Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> error = (Map<String, Object>) dup.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("INVENTORY_BATCH_EXISTS");
    }

    @Test
    void manual_box_without_a_serial_is_allowed() {
        Ctx ctx = register("manual-" + UUID.randomUUID() + "@b.com");
        String medId = seedCatalog().toString();

        var a = rest.exchange("/user-medications", HttpMethod.POST, body(ctx.token(), Map.of(
                "medicationId", medId, "quantity", 10)), Map.class);
        var b = rest.exchange("/user-medications", HttpMethod.POST, body(ctx.token(), Map.of(
                "medicationId", medId, "quantity", 10)), Map.class);
        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(b.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
