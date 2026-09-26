package com.eczam.medications;

import com.eczam.shared.web.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedicationSearchCursorTest {

    @Test void round_trips_an_alpha_cursor() {
        UUID id = UUID.randomUUID();
        String encoded = MedicationSearchCursor.forAlpha("PAROL 500 MG TABLET", id).encode();
        MedicationSearchCursor decoded = MedicationSearchCursor.decodeAlpha(encoded);
        assertThat(decoded.afterName()).isEqualTo("PAROL 500 MG TABLET");
        assertThat(decoded.afterId()).isEqualTo(id);
    }

    @Test void round_trips_a_similarity_cursor() {
        UUID id = UUID.randomUUID();
        String encoded = MedicationSearchCursor.forSimilarity(0.482391, id).encode();
        MedicationSearchCursor decoded = MedicationSearchCursor.decodeSimilarity(encoded);
        assertThat(decoded.afterScore()).isEqualTo(0.482391);
        assertThat(decoded.afterId()).isEqualTo(id);
    }

    @Test void a_name_with_reserved_delimiter_looking_characters_still_round_trips() {
        UUID id = UUID.randomUUID();
        String tricky = "ASPIRIN COMPLEX 500/30 MG — %ÖZEL İSİM";
        String encoded = MedicationSearchCursor.forAlpha(tricky, id).encode();
        assertThat(MedicationSearchCursor.decodeAlpha(encoded).afterName()).isEqualTo(tricky);
    }

    @Test void null_or_blank_decodes_to_null_for_either_mode() {
        assertThat(MedicationSearchCursor.decodeAlpha(null)).isNull();
        assertThat(MedicationSearchCursor.decodeAlpha("")).isNull();
        assertThat(MedicationSearchCursor.decodeAlpha("   ")).isNull();
        assertThat(MedicationSearchCursor.decodeSimilarity(null)).isNull();
    }

    @Test void malformed_cursor_is_a_validation_error_not_500() {
        assertThatThrownBy(() -> MedicationSearchCursor.decodeAlpha("%%%not-base64%%%"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MedicationSearchCursor.decodeAlpha("YWJj")) // base64("abc"), wrong shape
                .isInstanceOf(ApiException.class);
    }

    @Test void decoding_a_cursor_from_the_wrong_mode_is_rejected() {
        UUID id = UUID.randomUUID();
        String alphaCursor = MedicationSearchCursor.forAlpha("PAROL", id).encode();
        String simCursor = MedicationSearchCursor.forSimilarity(0.5, id).encode();

        assertThatThrownBy(() -> MedicationSearchCursor.decodeSimilarity(alphaCursor))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MedicationSearchCursor.decodeAlpha(simCursor))
                .isInstanceOf(ApiException.class);
    }
}
