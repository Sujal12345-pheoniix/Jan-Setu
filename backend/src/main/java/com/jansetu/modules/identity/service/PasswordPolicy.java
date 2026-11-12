package com.jansetu.modules.identity.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Password rules: 12-64 characters (at most 72 UTF-8 bytes, BCrypt's input limit), at least one letter and one
 * digit, not a well-known weak password, and not containing the account e-mail's local part.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    private static final int MAX_BYTES = 72;
    private static final Set<String> COMMON = Set.of(
            "password1234", "passw0rd1234", "123456789012", "qwerty123456", "letmein12345",
            "welcome12345", "admin1234567", "iloveyou1234", "changeme1234", "password123!");

    public void validate(String password, String email) {
        String problem = check(password, email);
        if (problem != null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, problem);
        }
    }

    /** Returns a human-readable problem, or null if the password is acceptable. */
    public String check(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters.";
        }
        if (password.length() > MAX_LENGTH || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "Password must be at most " + MAX_LENGTH + " characters.";
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            return "Password must contain at least one letter and one digit.";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return "Password is too common.";
        }
        if (email != null) {
            int at = email.indexOf('@');
            String local = (at > 0 ? email.substring(0, at) : email).toLowerCase(Locale.ROOT);
            if (local.length() >= 4 && lower.contains(local)) {
                return "Password must not contain your e-mail address.";
            }
        }
        return null;
    }
}
