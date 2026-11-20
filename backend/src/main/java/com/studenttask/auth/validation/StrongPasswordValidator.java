package com.studenttask.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    public static final int MIN_BYTES = 8;
    /** BCrypt ignores everything past 72 bytes, so longer input must be rejected rather than truncated. */
    public static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isStrong(value);
    }

    /** The single definition of the password policy, also used where bean validation cannot (optional fields). */
    public static boolean isStrong(String value) {
        if (value == null) {
            return false;
        }
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_BYTES || bytes > MAX_BYTES) {
            return false;
        }
        boolean hasLetter = value.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = value.codePoints().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
