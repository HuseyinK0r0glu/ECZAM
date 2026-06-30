package com.eczam.shared.web;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test void round_trips_a_timestamp() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 6, 13, 8, 0, 0, 0, ZoneOffset.UTC);
        assertThat(CursorCodec.decode(CursorCodec.encode(ts))).isEqualTo(ts);
    }

    @Test void null_or_blank_decodes_to_null() {
        assertThat(CursorCodec.decode(null)).isNull();
        assertThat(CursorCodec.decode("")).isNull();
        assertThat(CursorCodec.decode("   ")).isNull();
    }

    @Test void malformed_cursor_is_a_validation_error_not_500() {
        // "YWJj" is base64("abc") — decodes cleanly but is not a timestamp.
        assertThatThrownBy(() -> CursorCodec.decode("YWJj"))
                .isInstanceOf(ApiException.class);
        // Not valid base64 at all.
        assertThatThrownBy(() -> CursorCodec.decode("%%%"))
                .isInstanceOf(ApiException.class);
    }
}
