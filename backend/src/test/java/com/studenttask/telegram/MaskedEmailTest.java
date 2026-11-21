package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.telegram.service.MaskedEmail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaskedEmailTest {

    @ParameterizedTest
    @CsvSource({
        "alice@example.test,a***@example.test",
        "budi.santoso+tag@mail.example.test,b***@mail.example.test",
        "abc@example.test,a***@example.test",
        "ab@example.test,***@example.test",
        "a@example.test,***@example.test",
        "a@b@example.test,a***@example.test",
    })
    void masks_the_local_part_and_keeps_the_domain(String email, String expected) {
        assertThat(MaskedEmail.of(email)).isEqualTo(expected);
    }

    @Test
    void odd_input_never_throws_and_never_returns_the_input() {
        for (String odd : new String[] {null, "", "   ", "no-at-sign", "@example.test", "alice@", "@", "alice@ "}) {
            String masked = MaskedEmail.of(odd);
            assertThat(masked).isNotNull();
            if (odd != null && !odd.isBlank()) {
                assertThat(masked).isNotEqualTo(odd);
            }
            assertThat(masked).doesNotContain("alice").doesNotContain("no-at-sign");
        }
        assertThat(MaskedEmail.of("@example.test")).isEqualTo("***@example.test");
    }

    @Test
    void multi_code_unit_first_character_is_not_split() {
        assertThat(MaskedEmail.of("😀smile@example.test")).isEqualTo("😀***@example.test");
    }
}
