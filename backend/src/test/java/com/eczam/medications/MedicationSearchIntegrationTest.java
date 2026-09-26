package com.eczam.medications;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /medications?q= : trigram-similarity ranked/typo-tolerant search,
 * alphabetical browse when q is blank, and keyset cursor pagination
 * (plans/medications-schema-plan.md; CLAUDE.md §5 cursor-pagination convention).
 */
class MedicationSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired MedicationRepository medications;
    @Autowired JdbcTemplate jdbc;

    private String token() {
        var reg = rest.postForEntity("/auth/register", Map.of(
                "email", "search-" + UUID.randomUUID() + "@b.com",
                "password", "ValidP@ss1!", "displayName", "T"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) reg.getBody().get("data");
        return (String) data.get("accessToken");
    }

    private HttpEntity<Void> auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    private Medication save(String name) {
        Medication m = new Medication();
        m.setName(name);
        return medications.save(m);
    }

    private Medication save(String name, String activeIngredient) {
        Medication m = new Medication();
        m.setName(name);
        m.setActiveIngredient(activeIngredient);
        return medications.save(m);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> search(String token, String q, String cursor, Integer limit) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/medications");
        if (q != null) uri.queryParam("q", q);
        if (cursor != null) uri.queryParam("cursor", cursor);
        if (limit != null) uri.queryParam("limit", limit);
        return rest.exchange(uri.build().toUriString(), HttpMethod.GET, auth(token), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> data(ResponseEntity<Map> res) {
        return (List<Map<String, Object>>) res.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> meta(ResponseEntity<Map> res) {
        return (Map<String, Object>) res.getBody().get("meta");
    }

    // ---- Ranking -----------------------------------------------------

    @Test
    void exact_match_ranks_first_among_similarly_named_real_medicines() {
        String token = token();
        // Real names from the seeded Turkish catalog (Tip-Atlası ilac dataset) —
        // deliberately near-duplicates differing only by dose/form/pack size.
        save("ASPIRIN 100 MG 20 TABLET");
        save("ASPIRIN 500 MG TABLET, 20 ADET");
        save("ASPIRIN 500 MG TABLET, 1000 ADET");
        save("ASPIRIN PLUS C 400 MG/240 MG EFERVESAN TABLET, 10 ADET");

        var res = search(token, "ASPIRIN 100 MG 20 TABLET", null, 10);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> items = data(res);
        assertThat(items).isNotEmpty();
        assertThat(items.get(0).get("name")).isEqualTo("ASPIRIN 100 MG 20 TABLET");
    }

    @Test
    void a_one_or_two_character_typo_on_a_real_name_still_finds_it() {
        String token = token();
        save("ASPIRIN 100 MG 20 TABLET");
        save("PAROL 500 MG TABLET, 20 ADET");   // unrelated real decoy
        save("LEPTOL 5 MG TABLET");             // unrelated real decoy

        // "asprin" — a real, plausible one-character-missing typo of "aspirin".
        var res = search(token, "asprin", null, 20);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(data(res)).extracting(m -> m.get("name"))
                .contains("ASPIRIN 100 MG 20 TABLET");
    }

    @Test
    void a_query_matching_only_the_active_ingredient_still_finds_the_row() {
        String token = token();
        String tag = "ACTIVEINGTEST" + UUID.randomUUID().toString().replace("-", "");
        // Deliberately a generic-sounding brand name so only the active-ingredient
        // column, not the name/generic_name columns, can plausibly match "tag".
        save("BRAND " + UUID.randomUUID() + " 400 MG", tag);
        save("PAROL 500 MG TABLET, 20 ADET");   // unrelated real decoy

        var res = search(token, tag, null, 20);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(data(res)).isNotEmpty();
    }

    @Test
    void a_completely_unrelated_query_returns_no_results_rather_than_noise() {
        String token = token();
        save("PAROL 500 MG TABLET, 20 ADET");
        save("LEPTOL 5 MG TABLET");

        String gibberish = "qzxjkvwplonghand" + UUID.randomUUID().toString().replace("-", "");
        var res = search(token, gibberish, null, 20);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(data(res)).isEmpty();
        assertThat(meta(res).get("nextCursor")).isNull();
    }

    // ---- Backward compatibility ---------------------------------------

    @Test
    void blank_query_still_lists_everything_alphabetically() {
        String token = token();
        save("Zeta compat check " + UUID.randomUUID());
        save("Alpha compat check " + UUID.randomUUID());

        var res = search(token, null, null, 100);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        List<String> names = data(res).stream().map(m -> (String) m.get("name")).toList();
        assertThat(names).isNotEmpty();
        // Ordering invariant (rows from every test sharing this DB may be present,
        // so we assert the ordering property rather than an exact fixed set).
        // Compare against Postgres's OWN ORDER BY name ASC (the same clause
        // browseAlphabetical() uses) rather than re-deriving the expected order
        // in Java: Postgres's default locale collation disagrees with Java's
        // String.CASE_INSENSITIVE_ORDER often enough (e.g. how a space compares
        // against a letter at the same position) that the two aren't
        // interchangeable once enough varied fixture names from other tests
        // share this DB — this endpoint only promises to match the database's
        // own ordering, not any particular Java comparator's opinion of it.
        List<String> expected = jdbc.queryForList(
                "SELECT name FROM medications ORDER BY name ASC, id ASC LIMIT ?", String.class, 100);
        assertThat(names).isEqualTo(expected);
    }

    @Test
    void blank_query_with_no_q_param_at_all_behaves_the_same_as_before() {
        String token = token();
        var res = search(token, null, null, 5);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(meta(res).get("limit")).isEqualTo(5);
    }

    // ---- limit clamp ----------------------------------------------------

    @Test
    void limit_is_clamped_to_the_documented_maximum_of_100() {
        String token = token();
        String tag = "QQPLIMTEST" + UUID.randomUUID().toString().replace("-", "");
        for (int i = 0; i < 105; i++) {
            save(tag + " 10 MG TABLET");
        }

        var res = search(token, tag, null, 1000);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(data(res)).hasSize(100);
        assertThat(meta(res).get("limit")).isEqualTo(100);
        assertThat(meta(res).get("nextCursor")).isNotNull();
    }

    // ---- Keyset pagination ------------------------------------------------

    @Test
    void keyset_pagination_pages_through_tied_scores_without_duplicates_or_gaps() {
        String token = token();
        // Identical name ⇒ identical word_similarity score for every row, so
        // the page boundaries can only be decided by the id ASC tiebreak.
        String tag = "ZZQVEXMED" + UUID.randomUUID().toString().replace("-", "");
        String name = tag + " 500 MG TABLET";
        Set<String> seededIds = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            seededIds.add(save(name).getId().toString());
        }

        Set<String> seenIds = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            var res = search(token, tag, cursor, 10);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            List<Map<String, Object>> items = data(res);
            assertThat(items).isNotEmpty();
            for (Map<String, Object> item : items) {
                String id = (String) item.get("id");
                assertThat(seenIds.add(id)).as("id %s must not repeat across pages", id).isTrue();
            }
            cursor = (String) meta(res).get("nextCursor");
            pages++;
            assertThat(pages).isLessThan(10); // guard against an infinite loop on a bug
        } while (cursor != null);

        assertThat(pages).isEqualTo(3); // 25 rows at page size 10 ⇒ 10 + 10 + 5
        assertThat(seenIds).isEqualTo(seededIds); // no duplicates, no skipped rows
    }
}
