package com.jansetu.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jansetu.common.error.ApiException;
import com.jansetu.modules.identity.service.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.check("Valid-Passphrase-2026", "someone@example.test")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "Short1, at least 12",
            "OnlyLettersHereNoDigits, letter and one digit",
            "123456789012345, letter and one digit",
            "password1234, too common",
            "my-ramesh-pass-123, e-mail"
    })
    void rejectsWeakPasswords(String password, String expectedMessage) {
        assertThat(policy.check(password, "ramesh@example.test")).contains(expectedMessage);
    }

    @Test
    void rejectsPasswordsBeyondBcryptInputLimit() {
        assertThat(policy.check("a1" + "x".repeat(70), null)).contains("at most");
        // multi-byte characters: 30 chars but > 72 bytes
        assertThat(policy.check("1" + "ह".repeat(29), null)).contains("at most");
    }

    @Test
    void validateThrowsValidationError() {
        assertThatThrownBy(() -> policy.validate("weak", null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode().name())
                .isEqualTo("VALIDATION_ERROR");
    }
}
