package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UserRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-20T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final UserRateLimiter limiter = new UserRateLimiter(clock);

    private void consume(String action, long user, int times, int limit) {
        for (int i = 0; i < times; i++) {
            limiter.check(action, user, limit);
        }
    }

    @Test
    void allows_up_to_the_limit_then_rejects_with_RATE_LIMITED() {
        consume("a", 1, 3, 3);

        assertThatThrownBy(() -> limiter.check("a", 1, 3)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void budgets_are_separate_per_user_and_per_action() {
        consume("a", 1, 3, 3);

        assertThatCode(() -> limiter.check("a", 2, 3)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("b", 1, 3)).doesNotThrowAnyException();
    }

    @Test
    void budget_refills_after_the_window() {
        consume("a", 1, 3, 3);
        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> consume("a", 1, 3, 3)).doesNotThrowAnyException();
    }
}
