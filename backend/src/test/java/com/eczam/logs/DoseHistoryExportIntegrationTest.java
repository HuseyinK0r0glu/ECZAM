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
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /medication-logs/export — cross-medication dose history export for a
 * doctor/pharmacist hand-off (CLAUDE.md "intelligent information access" /
 * adherence pillars).
 */
class DoseHistoryExportIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;
    @Autowired UserMedicationRepository inventory;
    @Autowired MedicationLogRepository logs;

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

    private UUID seedInventory(UUID userId, String medName, BigDecimal qty) {
        Medication med = new Medication();
        med.setName(medName);
        med = medications.save(med);
        UserMedication um = new UserMedication();
        um.setUserId(userId);
        um.setMedication(med);
        um.setQuantity(qty);
        return inventory.save(um).getId();
    }

    private void seedLog(UUID umId, OffsetDateTime takenAt, BigDecimal qty, String notes) {
        MedicationLog log = new MedicationLog();
        log.setUserMedicationId(umId);
        log.setQuantityUsed(qty);
        log.setNotes(notes);
        log.setTakenAt(takenAt);
        logs.save(log);
    }

    /**
     * Builds a fully-encoded {@link URI} and hands it to the {@code URI} overload of
     * {@code exchange}, which sends it verbatim with no further template expansion.
     * Two encoding traps to dodge for an {@code OffsetDateTime#toString()} value
     * (e.g. {@code ...T04:01:43.357+03:00}):
     *  1. Passing a hand-built query {@code String} to the {@code String} overload
     *     of {@code exchange} double-processes it — TestRestTemplate treats a
     *     String as a URI *template* and re-encodes it.
     *  2. {@code UriComponentsBuilder(...).build().encode()} leaves a literal `+`
     *     alone (RFC 3986 allows an unescaped `+` in a query), but the servlet
     *     container then decodes that literal `+` as a space (form-encoding
     *     rules), silently turning `+03:00` into ` 03:00`. Pre-encoding the value
     *     with {@link URLEncoder} (which *does* escape `+` to `%2B`) and building
     *     with {@code build(true)} (values already encoded — don't re-touch them)
     *     avoids both.
     */
    private ResponseEntity<Map> exportRequest(String token, OffsetDateTime from, OffsetDateTime to) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(rest.getRootUri() + "/medication-logs/export");
        if (from != null) builder.queryParam("from", URLEncoder.encode(from.toString(), StandardCharsets.UTF_8));
        if (to != null) builder.queryParam("to", URLEncoder.encode(to.toString(), StandardCharsets.UTF_8));
        URI uri = builder.build(true).toUri();
        return rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    private ResponseEntity<Map> exportRequest(String token) {
        return exportRequest(token, null, null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dataOf(ResponseEntity<Map> res) {
        return (List<Map<String, Object>>) res.getBody().get("data");
    }

    @Test
    void returns_logs_across_medications_with_names_newest_first() {
        Ctx ctx = register("export-" + UUID.randomUUID() + "@b.com");
        String medAName = "Med A " + UUID.randomUUID();
        String medBName = "Med B " + UUID.randomUUID();
        UUID umA = seedInventory(ctx.userId(), medAName, new BigDecimal("10"));
        UUID umB = seedInventory(ctx.userId(), medBName, new BigDecimal("10"));

        OffsetDateTime now = OffsetDateTime.now();
        seedLog(umA, now.minusDays(2), BigDecimal.ONE, "older dose");
        seedLog(umB, now.minusDays(1), new BigDecimal("2"), "newer dose");

        var res = exportRequest(ctx.token());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = dataOf(res);
        assertThat(data).hasSize(2);

        // Newest first.
        assertThat(data.get(0).get("notes")).isEqualTo("newer dose");
        assertThat(data.get(0).get("medicationName")).isEqualTo(medBName);
        assertThat(Double.parseDouble(data.get(0).get("quantityUsed").toString())).isEqualTo(2.0);

        assertThat(data.get(1).get("notes")).isEqualTo("older dose");
        assertThat(data.get(1).get("medicationName")).isEqualTo(medAName);
    }

    @Test
    void never_returns_another_users_logs() {
        Ctx owner = register("owner-" + UUID.randomUUID() + "@b.com");
        Ctx other = register("other-" + UUID.randomUUID() + "@b.com");

        UUID ownerUm = seedInventory(owner.userId(), "Owner Med " + UUID.randomUUID(), new BigDecimal("10"));
        UUID otherUm = seedInventory(other.userId(), "Other Med " + UUID.randomUUID(), new BigDecimal("10"));

        OffsetDateTime now = OffsetDateTime.now();
        seedLog(ownerUm, now.minusDays(1), BigDecimal.ONE, "mine");
        seedLog(otherUm, now.minusDays(1), BigDecimal.ONE, "not mine");

        var res = exportRequest(owner.token());
        List<Map<String, Object>> data = dataOf(res);
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("notes")).isEqualTo("mine");
    }

    @Test
    void defaults_to_last_90_days_when_no_range_given() {
        Ctx ctx = register("default-window-" + UUID.randomUUID() + "@b.com");
        UUID um = seedInventory(ctx.userId(), "Windowed Med " + UUID.randomUUID(), new BigDecimal("10"));

        OffsetDateTime now = OffsetDateTime.now();
        seedLog(um, now.minusDays(100), BigDecimal.ONE, "too old");
        seedLog(um, now.minusDays(10), BigDecimal.ONE, "within window");

        var res = exportRequest(ctx.token());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = dataOf(res);
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("notes")).isEqualTo("within window");
    }

    @Test
    void rejects_a_requested_range_wider_than_365_days() {
        Ctx ctx = register("wide-range-" + UUID.randomUUID() + "@b.com");
        seedInventory(ctx.userId(), "Wide Range Med " + UUID.randomUUID(), new BigDecimal("10"));

        OffsetDateTime now = OffsetDateTime.now();

        var res = exportRequest(ctx.token(), now.minusDays(400), now);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) res.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void accepts_an_explicit_range_within_365_days() {
        Ctx ctx = register("ok-range-" + UUID.randomUUID() + "@b.com");
        UUID um = seedInventory(ctx.userId(), "Ok Range Med " + UUID.randomUUID(), new BigDecimal("10"));

        OffsetDateTime now = OffsetDateTime.now();
        seedLog(um, now.minusDays(200), BigDecimal.ONE, "in range");

        var res = exportRequest(ctx.token(), now.minusDays(300), now);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = dataOf(res);
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("notes")).isEqualTo("in range");
    }
}
