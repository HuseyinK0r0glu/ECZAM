package com.eczam.medications;

import com.eczam.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pg_trgm-backed fuzzy search over the medication catalog
 * (MedicationRepository.search(), V7__pg_trgm_search.sql). The real seeded
 * catalog (20,471 Turkish medicine rows) isn't present in this ephemeral test
 * database, so a handful of representative fixture rows are inserted directly.
 *
 * <p>AbstractIntegrationTest uses a singleton Postgres container shared by the
 * whole test run, so the {@code medications} table may already carry rows from
 * other integration test classes by the time this runs. Assertions therefore
 * never rely on the table's total row count — only on our own fixtures' ids —
 * and {@link #cleanupCatalog()} removes them after each test so repeated runs
 * within this class don't accumulate duplicates either.
 */
class MedicationFuzzySearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired MedicationRepository medications;

    /** Comfortably larger than anything this suite could accumulate, so our fixtures are never paged out. */
    private static final PageRequest BIG_PAGE = PageRequest.of(0, 500);

    private Medication ibuprofen;
    private Medication parasetamol;
    private Medication amoksisilin;

    @BeforeEach
    void seedCatalog() {
        ibuprofen = save("IBUPROFEN 400 MG TABLET", "Ibuprofen", "IBUPROFEN");
        parasetamol = save("PARASETAMOL 500 MG TABLET", "Parasetamol", "PARASETAMOL");
        amoksisilin = save("AMOKSISILIN 1000 MG TABLET", null, "AMOKSISILIN TRIHIDRAT");
    }

    @AfterEach
    void cleanupCatalog() {
        // Safe to delete directly: this test never attaches user_medications/
        // leaflet_chunks rows to these fixtures, so no FK references them.
        medications.deleteAll(List.of(ibuprofen, parasetamol, amoksisilin));
    }

    private Medication save(String name, String genericName, String activeIngredient) {
        Medication m = new Medication();
        m.setName(name);
        m.setGenericName(genericName);
        m.setActiveIngredient(activeIngredient);
        return medications.save(m);
    }

    private List<UUID> idsOf(Page<Medication> page) {
        return page.getContent().stream().map(Medication::getId).collect(Collectors.toList());
    }

    @Test
    void exact_substring_query_returns_the_expected_row_first() {
        Page<Medication> page = medications.search("IBUPROFEN", BIG_PAGE);
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().get(0).getId()).isEqualTo(ibuprofen.getId());
    }

    @Test
    void realistic_typo_still_finds_ibuprofen_via_trigram_similarity() {
        // Missing letter.
        assertThat(idsOf(medications.search("IBUPROFEM", BIG_PAGE))).contains(ibuprofen.getId());
        // Transposed middle letters (the specific example from the feature brief).
        assertThat(idsOf(medications.search("IBRUPOFEN", BIG_PAGE))).contains(ibuprofen.getId());
    }

    @Test
    void unrelated_query_returns_no_results() {
        assertThat(medications.search("ZZQXWVPLORBQKQZ", BIG_PAGE).getContent()).isEmpty();
    }

    @Test
    void blank_or_null_query_still_returns_all_rows_ordered_by_name() {
        List<UUID> known = List.of(amoksisilin.getId(), ibuprofen.getId(), parasetamol.getId());

        for (String q : new String[] { null, "" }) {
            List<UUID> ids = idsOf(medications.search(q, BIG_PAGE));
            assertThat(ids).containsAll(known);

            // Restrict to just our 3 known fixtures, preserving the order they
            // came back in. A subsequence of a name-ASC-sorted sequence is
            // itself sorted, so this still proves ordering even with other
            // tests' unrelated rows interleaved in the shared DB.
            List<UUID> knownInReturnedOrder = ids.stream().filter(known::contains).toList();
            assertThat(knownInReturnedOrder).containsExactlyElementsOf(known);
        }
    }

    @Test
    void searching_by_active_ingredient_finds_a_match() {
        // "TRIHIDRAT" only appears in amoksisilin's active_ingredient, not its name/generic_name.
        assertThat(idsOf(medications.search("TRIHIDRAT", BIG_PAGE))).containsExactly(amoksisilin.getId());
    }
}
