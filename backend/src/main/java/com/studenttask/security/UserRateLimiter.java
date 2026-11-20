package com.studenttask.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * In-memory per-user limit (not shared between instances), keyed on the authenticated user id, never on the IP.
 * Each {@code action} (e.g. "telegram-connect") has its own budget per user. Buckets live in a size-bounded cache
 * that drops entries idle for {@link #IDLE_EXPIRY} (longer than the refill window, so eviction never grants extra
 * budget). Callers pass the limit so the policy stays next to the endpoint it protects.
 */
@Component
public class UserRateLimiter {

    static final int DEFAULT_MAX_BUCKETS = 100_000;
    static final Duration IDLE_EXPIRY = Duration.ofMinutes(2);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Cache<String, Bucket> buckets;
    private final TimeMeter timeMeter;

    public UserRateLimiter(Clock clock) {
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAX_BUCKETS)
                .expireAfterAccess(IDLE_EXPIRY)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /** Consumes one token or throws {@code RATE_LIMITED} (429). */
    public void check(String action, long userId, int perMinute) {
        Bucket bucket = buckets.get(action + '|' + userId, k -> newBucket(perMinute));
        if (!bucket.tryConsume(1)) {
            throw new AppException(ErrorCode.RATE_LIMITED, "Terlalu banyak permintaan. Coba lagi nanti.");
        }
    }

    private Bucket newBucket(int perMinute) {
        Bandwidth limit = Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, WINDOW).build();
        return Bucket.builder().addLimit(limit).withCustomTimePrecision(timeMeter).build();
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return TimeUnit.MILLISECONDS.toNanos(clock.millis());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
