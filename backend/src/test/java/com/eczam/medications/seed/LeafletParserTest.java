package com.eczam.medications.seed;

import com.eczam.medications.LeafletSections;
import com.eczam.medications.seed.LeafletParser.Block;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Semantic section parsing of the Turkish Kullanma Talimatı structure. */
class LeafletParserTest {

    private static final String FULL = """
            1. ARMANAKS NEDİR VE NE İÇİN KULLANILIR
            Ağrı kesicidir.
            2. ARMANAKS KULLANMADAN ÖNCE DİKKAT EDİLMESİ GEREKENLER
            Alerjiniz varsa kullanmayın.
            3. ARMANAKS NASIL KULLANILIR
            Günde iki kez bir tablet.
            4. OLASI YAN ETKİLER NELERDİR
            Baş ağrısı görülebilir.
            5. ARMANAKS'IN SAKLANMASI
            Oda sıcaklığında saklayın.
            """;

    @Test void parses_five_sections_in_order() {
        List<Block> blocks = LeafletParser.parse(FULL);
        assertThat(blocks).hasSize(5);
        assertThat(blocks).extracting(Block::ordinal).containsExactly(1, 2, 3, 4, 5);
        assertThat(blocks).extracting(Block::key)
                .containsExactly("what_is", "before_use", "how_to_use", "side_effects", "storage");
        assertThat(blocks.get(3).text()).contains("Baş ağrısı");
        // Provenance offsets point back into the raw text.
        assertThat(blocks.get(0).charStart()).isGreaterThanOrEqualTo(0);
        assertThat(blocks.get(0).charLen()).isGreaterThan(0);
    }

    @Test void fewer_than_two_anchors_returns_empty_for_fixed_size_fallback() {
        assertThat(LeafletParser.parse("3. NASIL KULLANILIR\nGünde bir tablet.")).isEmpty();
        assertThat(LeafletParser.parse("Serbest metin, başlık yok.")).isEmpty();
    }

    @Test void null_or_blank_is_empty() {
        assertThat(LeafletParser.parse(null)).isEmpty();
        assertThat(LeafletParser.parse("   ")).isEmpty();
    }

    @Test void projects_blocks_onto_leaflet_sections() {
        LeafletSections s = LeafletParser.toLeafletSections(LeafletParser.parse(FULL));
        assertThat(s.dosage()).contains("Günde iki kez");            // how_to_use → dosage
        assertThat(s.sideEffects()).contains("Baş ağrısı");
        assertThat(s.contraindications()).contains("Alerjiniz");     // before_use → contraindications
        assertThat(s.storage()).contains("Oda sıcaklığında");
        assertThat(s.interactions()).isNull();
        assertThat(s.missedDose()).isNull();
    }
}
