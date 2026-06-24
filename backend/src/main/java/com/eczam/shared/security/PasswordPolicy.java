package com.eczam.shared.security;

import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces a password policy beyond @Size:
 *  - At least 8 characters (also validated by Bean Validation)
 *  - At least one uppercase letter
 *  - At least one lowercase letter
 *  - At least one digit
 *  - At least one special character
 *  - Not a common/trivial pattern (basic blocklist)
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 100;

    private static final List<String> COMMON_PATTERNS = List.of(
            "password", "12345678", "qwerty", "letmein", "admin123",
            "welcome1", "iloveyou", "sunshine", "monkey", "dragon",
            "football", "baseball", "abc12345", "111111", "123456789"
    );

    /**
     * Validates the password and throws 422 with a field-level message if it fails.
     */
    public void validate(String password) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < MIN_LENGTH) {
            violations.add("Must be at least " + MIN_LENGTH + " characters");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            violations.add("Must be at most " + MAX_LENGTH + " characters");
        }
        if (password != null) {
            if (!password.chars().anyMatch(Character::isUpperCase)) {
                violations.add("Must contain at least one uppercase letter");
            }
            if (!password.chars().anyMatch(Character::isLowerCase)) {
                violations.add("Must contain at least one lowercase letter");
            }
            if (!password.chars().anyMatch(Character::isDigit)) {
                violations.add("Must contain at least one digit");
            }
            if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
                violations.add("Must contain at least one special character");
            }
            String lower = password.toLowerCase();
            if (COMMON_PATTERNS.stream().anyMatch(lower::contains)) {
                violations.add("Password is too common or easily guessable");
            }
        }

        if (!violations.isEmpty()) {
            throw ApiException.unprocessable(ErrorCode.WEAK_PASSWORD,
                    "Password does not meet requirements",
                    "password", String.join("; ", violations));
        }
    }
}
