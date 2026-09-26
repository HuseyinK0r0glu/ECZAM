package com.eczam.medications;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * category_path (JSONB) is populated on every catalog row but was write-only —
 * never read back into a DTO, and unusable for browsing/filtering. Covers the
 * new GET /medications/categories endpoint and the GET /medications?category=
 * filter (plans/medications-schema-plan.md category-depth stats; category_path
 * is the cleaned ordered therapeutic-category hierarchy, first element = top level).
 */
class MedicationCategoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;

    // Unique per test run so counts/ordering assertions can't be polluted by any
    // other seeded/real catalog data sharing the same Postgres instance.
    private final String catA = "ZZ-Test-Category-A-" + UUID.randomUUID();
    private final String catB = "ZZ-Test-Category-B-" + UUID.randomUUID();

    private String token() {
        var reg = rest.postForEntity("/auth/register",
                Map.of("email", "cat-" + UUID.randomUUID() + "@b.com", "password", "ValidP@ss1!", "displayName", "T"),
                Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        return (String) data.get("accessToken");
    }

    private Medication seed(String name, List<String> categoryPath) {
        Medication m = new Medication();
        m.setName(name);
        m.setCategoryPath(categoryPath);
        return medications.save(m);
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    @SuppressWarnings("unchecked")
    @Test
    void categories_endpoint_reports_accurate_counts_ordered_by_count_desc_and_excludes_null_or_empty() {
        String token = token();
        // 3 in category A, 2 in category B, plus null/empty categoryPath rows that must never count.
        seed("A Med 1 " + UUID.randomUUID(), List.of(catA, "Sub A1"));
        seed("A Med 2 " + UUID.randomUUID(), List.of(catA, "Sub A2"));
        seed("A Med 3 " + UUID.randomUUID(), List.of(catA));
        seed("B Med 1 " + UUID.randomUUID(), List.of(catB, "Sub B1"));
        seed("B Med 2 " + UUID.randomUUID(), List.of(catB));
        seed("No Category Med " + UUID.randomUUID(), null);
        seed("Empty Category Med " + UUID.randomUUID(), List.of());

        var res = rest.exchange("/medications/categories", HttpMethod.GET, auth(token), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = (List<Map<String, Object>>) res.getBody().get("data");

        Map<String, Object> a = data.stream().filter(e -> catA.equals(e.get("category"))).findFirst().orElseThrow();
        Map<String, Object> b = data.stream().filter(e -> catB.equals(e.get("category"))).findFirst().orElseThrow();
        assertThat(((Number) a.get("count")).longValue()).isEqualTo(3);
        assertThat(((Number) b.get("count")).longValue()).isEqualTo(2);

        // Ordered by count descending: A (3) must come before B (2).
        int idxA = data.indexOf(a);
        int idxB = data.indexOf(b);
        assertThat(idxA).isLessThan(idxB);

        // Null/empty category_path rows never surface as a bucket of their own.
        assertThat(data).noneMatch(e -> e.get("category") == null);
    }

    @SuppressWarnings("unchecked")
    @Test
    void category_filter_only_returns_medications_in_that_top_level_category() {
        String token = token();
        seed("Filter A Med " + UUID.randomUUID(), List.of(catA, "Sub"));
        seed("Filter B Med " + UUID.randomUUID(), List.of(catB, "Sub"));

        var res = rest.exchange("/medications?category=" + enc(catA), HttpMethod.GET, auth(token), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(data).isNotEmpty();
        assertThat(data).allMatch(m -> {
            List<String> cp = (List<String>) m.get("categoryPath");
            return cp != null && !cp.isEmpty() && catA.equals(cp.get(0));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void q_and_category_together_narrow_results() {
        String token = token();
        String uniqueWord = "Narrowzol" + UUID.randomUUID().toString().substring(0, 8);
        seed(uniqueWord + " In Category A", List.of(catA));
        seed(uniqueWord + " In Category B", List.of(catB));
        seed("Other Name In Category A " + UUID.randomUUID(), List.of(catA));

        var res = rest.exchange("/medications?q=" + enc(uniqueWord) + "&category=" + enc(catA),
                HttpMethod.GET, auth(token), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(data).hasSize(1);
        assertThat((String) data.get(0).get("name")).isEqualTo(uniqueWord + " In Category A");
    }

    @SuppressWarnings("unchecked")
    @Test
    void omitting_category_behaves_exactly_as_before_regression_guard() {
        String token = token();
        String uniqueWord = "Regressotab" + UUID.randomUUID().toString().substring(0, 8);
        seed(uniqueWord + " One", List.of(catA));
        seed(uniqueWord + " Two", null);

        var res = rest.exchange("/medications?q=" + enc(uniqueWord), HttpMethod.GET, auth(token), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(data).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void category_with_no_matches_returns_empty_list_not_an_error() {
        String token = token();
        var res = rest.exchange("/medications?category=" + enc("Nonexistent-Category-" + UUID.randomUUID()),
                HttpMethod.GET, auth(token), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> data = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(data).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void null_or_empty_category_path_medication_never_matches_a_category_filter() {
        String token = token();
        String uniqueWord = "Nullcatzol" + UUID.randomUUID().toString().substring(0, 8);
        seed(uniqueWord, null);

        var res = rest.exchange("/medications?category=" + enc(catA), HttpMethod.GET, auth(token), Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(data).noneMatch(m -> uniqueWord.equals(m.get("name")));
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
