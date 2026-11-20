package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(String password, String confirm) {
        return validator.validate(new RegisterRequest("Budi", "budi@example.test", password, confirm)).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void valid_password_passes() {
        assertThat(invalidFields("Passw0rd", "Passw0rd")).isEmpty();
    }

    @Test
    void exactly_72_bytes_is_accepted_and_73_is_rejected() {
        String at72 = "a1" + "x".repeat(70);
        assertThat(invalidFields(at72, at72)).isEmpty();
        String at73 = at72 + "x";
        assertThat(invalidFields(at73, at73)).containsExactly("password");
    }

    @Test
    void multibyte_password_is_measured_in_bytes_not_characters() {
        // 8 + 30 code points = 38 characters (well under 72) but 8 + 120 = 128 bytes.
        String emoji = "Passw0rd" + "😀".repeat(30);
        assertThat(emoji.codePointCount(0, emoji.length())).isLessThan(72);
        assertThat(invalidFields(emoji, emoji)).containsExactly("password");
        // 36 two-byte letters = 72 bytes (+ digit makes 73): borderline in bytes, 37 chars
        String twoByte = "é".repeat(35) + "12"; // 70 + 2 = 72 bytes
        assertThat(invalidFields(twoByte, twoByte)).isEmpty();
        String twoByteOver = "é".repeat(36) + "12"; // 74 bytes
        assertThat(invalidFields(twoByteOver, twoByteOver)).containsExactly("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Passwordx", "12345678", "Pa1", "        "})
    void rejects_missing_letter_missing_digit_or_too_short(String password) {
        assertThat(invalidFields(password, password)).containsExactly("password");
    }

    @Test
    void mismatched_confirm_is_reported_on_confirmPassword() {
        assertThat(invalidFields("Passw0rd", "Passw0rd2")).containsExactly("confirmPassword");
    }
}
