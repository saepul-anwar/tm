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

    @Test
    void tryAcquire_returns_false_instead_of_throwing_when_exhausted() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("a", 1, 3)).isTrue();
        }

        assertThat(limiter.tryAcquire("a", 1, 3)).isFalse();
        assertThat(limiter.tryAcquire("a", 2, 3)).isTrue();
        assertThat(limiter.tryAcquire("a", -5, 3)).isTrue(); // negative ids (group chats) are valid keys
    }

    private void reserve(long user, int times) {
        for (int i = 0; i < times; i++) {
            limiter.reserve("fail", user, 5, Duration.ofMinutes(15));
        }
    }

    @Test
    void reserve_allows_max_tokens_then_blocks_until_the_whole_window_has_passed() {
        reserve(1, 5);

        assertThatThrownBy(() -> reserve(1, 1)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.RATE_LIMITED));
        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> reserve(1, 1)).isInstanceOf(AppException.class);
        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> reserve(1, 5)).doesNotThrowAnyException();
    }

    @Test
    void reserve_budget_is_not_evicted_while_the_window_is_still_open() {
        reserve(1, 5);
        clock.advance(Duration.ofMinutes(10)); // longer than the 2 minute idle expiry of the per-minute buckets

        assertThatThrownBy(() -> reserve(1, 1)).isInstanceOf(AppException.class);
    }

    @Test
    void reserve_is_per_user_and_refund_returns_a_token() {
        reserve(1, 5);

        assertThatCode(() -> reserve(2, 5)).doesNotThrowAnyException();
        limiter.refund("fail", 1);
        assertThatCode(() -> reserve(1, 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> reserve(1, 1)).isInstanceOf(AppException.class);
    }

    @Test
    void refund_never_grants_more_than_the_capacity() {
        reserve(1, 1);
        for (int i = 0; i < 10; i++) {
            limiter.refund("fail", 1);
        }

        assertThatCode(() -> reserve(1, 5)).doesNotThrowAnyException();
        assertThatThrownBy(() -> reserve(1, 1)).isInstanceOf(AppException.class);
    }
}
