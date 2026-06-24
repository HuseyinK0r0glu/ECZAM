package com.eczam.shared.security;

import com.eczam.shared.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    // ---------------------------------------------------------------- accept
    @Test
    void accepts_strong_password() {
        assertThatNoException().isThrownBy(() -> policy.validate("ValidP@ss1!"));
    }

    @Test
    void accepts_minimum_length_strong_password() {
        assertThatNoException().isThrownBy(() -> policy.validate("Ab1!abcd"));
    }

    // ---------------------------------------------------------------- reject: length
    @Test
    void rejects_null() {
        assertThatThrownBy(() -> policy.validate(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejects_too_short() {
        assertThatThrownBy(() -> policy.validate("Ab1!"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejects_too_long() {
        String pw = "Ab1!" + "a".repeat(100); // 104 chars
        assertThatThrownBy(() -> policy.validate(pw))
                .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------------- reject: character class
    @Test
    void rejects_no_uppercase() {
        assertThatThrownBy(() -> policy.validate("validp@ss1!"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejects_no_lowercase() {
        assertThatThrownBy(() -> policy.validate("VALIDP@SS1!"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejects_no_digit() {
        assertThatThrownBy(() -> policy.validate("ValidP@ssword!"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejects_no_special_char() {
        assertThatThrownBy(() -> policy.validate("ValidPass1"))
                .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------------- reject: common patterns
    @ParameterizedTest
    @ValueSource(strings = {
            "Password1!",   // contains "password"
            "12345678Aa!",  // contains "12345678"
            "Qwerty12!",    // contains "qwerty"
            "Letmein1!",    // contains "letmein"
    })
    void rejects_common_patterns(String pw) {
        assertThatThrownBy(() -> policy.validate(pw))
                .isInstanceOf(ApiException.class);
    }
}
