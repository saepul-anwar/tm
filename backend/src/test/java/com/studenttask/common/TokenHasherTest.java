package com.studenttask.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    void sha256Hex_matches_known_vector() {
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_is_deterministic_and_64_lowercase_hex_chars() {
        assertThat(TokenHasher.sha256Hex("synthetic-token")).matches("[0-9a-f]{64}")
                .isEqualTo(TokenHasher.sha256Hex("synthetic-token"));
    }
}
