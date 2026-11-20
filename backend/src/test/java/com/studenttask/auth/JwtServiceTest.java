package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studenttask.auth.service.JwtService;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.config.AppProperties;
import com.studenttask.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "synthetic-secret-for-unit-tests-0123456789";
    private static final Instant T0 = Instant.parse("2026-11-20T00:00:00Z");

    private static JwtService service(String secret, Instant now) {
        AppProperties props = new AppProperties(new AppProperties.Jwt(secret), null, null, null, new AppProperties.Auth(true));
        return new JwtService(props, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void secret_shorter_than_32_bytes_is_rejected_at_startup() {
        assertThatThrownBy(() -> service("short-secret-31-bytes-xxxxxxxxx".substring(0, 31), T0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("short-secret");
        service("x".repeat(32), T0); // exactly 32 bytes is accepted
    }

    @Test
    void issued_token_round_trips_with_user_id_as_subject() {
        JwtService jwt = service(SECRET, T0);
        AuthenticatedUser user = jwt.parse(jwt.issue(99L, "budi@example.test"));
        assertThat(user).isEqualTo(new AuthenticatedUser(99L, "budi@example.test"));
    }

    @Test
    void token_is_valid_for_15_minutes_then_rejected() {
        String token = service(SECRET, T0).issue(1L, "a@example.test");

        service(SECRET, T0.plus(Duration.ofMinutes(15))).parse(token);
        assertThatThrownBy(() -> service(SECRET, T0.plus(Duration.ofMinutes(15)).plusSeconds(1)).parse(token))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void token_signed_with_another_secret_is_rejected() {
        String token = service("another-synthetic-secret-0123456789-abc", T0).issue(1L, "a@example.test");
        assertThatThrownBy(() -> service(SECRET, T0).parse(token)).isInstanceOf(AppException.class);
    }

    @Test
    void alg_none_and_garbage_tokens_are_rejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"iss\":\"studenttask\",\"exp\":4102444800}".getBytes());
        JwtService jwt = service(SECRET, T0);

        assertThatThrownBy(() -> jwt.parse(header + "." + payload + ".")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> jwt.parse("")).isInstanceOf(AppException.class);
    }
}
