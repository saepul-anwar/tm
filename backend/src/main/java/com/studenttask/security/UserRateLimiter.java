package com.studenttask.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
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
 * and are dropped only after being idle for their window plus {@link #IDLE_MARGIN}, so eviction never grants extra
 * budget. Callers pass the limit so the policy stays next to the endpoint it protects.
 *
 * <p>Two styles: {@link #check} / {@link #tryAcquire} are a per-minute rate (tokens trickle back); {@link #reserve}
 * / {@link #refund} are a fixed-window budget (all tokens return at once, one window after the bucket was
 * created) used for failed-attempt limits. Ids are only cache keys, so non-user ids (Telegram chat ids, which may
 * be negative) can be used with a distinct action name.
 */
@Component
public class UserRateLimiter {

    static final int DEFAULT_MAX_BUCKETS = 100_000;
    static final Duration IDLE_MARGIN = Duration.ofMinutes(1);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Cache<String, Limited> buckets;
    private final TimeMeter timeMeter;

    public UserRateLimiter(Clock clock) {
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAX_BUCKETS)
                .expireAfter(new IdleExpiry())
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /** Consumes one token or throws {@code RATE_LIMITED} (429). */
    public void check(String action, long userId, int perMinute) {
        if (!tryAcquire(action, userId, perMinute)) {
            throw rateLimited();
        }
    }

    /** Consumes one token; false when the budget is exhausted (never throws). */
    public boolean tryAcquire(String action, long id, int perMinute) {
        Limited limited = buckets.get(key(action, id), k -> new Limited(
                newBucket(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, WINDOW).build()), WINDOW));
        return limited.bucket().tryConsume(1);
    }

    /**
     * Takes one token of a fixed-window budget of {@code max} per {@code window} or throws {@code RATE_LIMITED}.
     * Taking the token BEFORE the guarded work keeps concurrent attempts from overshooting the budget.
     */
    public void reserve(String action, long id, int max, Duration window) {
        Limited limited = buckets.get(key(action, id), k -> new Limited(
                newBucket(Bandwidth.builder().capacity(max).refillIntervally(max, window).build()), window));
        if (!limited.bucket().tryConsume(1)) {
            throw rateLimited();
        }
    }

    /** Gives back a token taken by {@link #reserve} (the attempt turned out to be legitimate). */
    public void refund(String action, long id) {
        Limited limited = buckets.getIfPresent(key(action, id));
        if (limited != null) {
            limited.bucket().addTokens(1); // capped at the capacity
        }
    }

    private static String key(String action, long id) {
        return action + '|' + id;
    }

    private static AppException rateLimited() {
        return new AppException(ErrorCode.RATE_LIMITED, "Terlalu banyak permintaan. Coba lagi nanti.");
    }

    private Bucket newBucket(Bandwidth limit) {
        return Bucket.builder().addLimit(limit).withCustomTimePrecision(timeMeter).build();
    }

    private record Limited(Bucket bucket, Duration window) {
    }

    /** Idle time before eviction: the bucket's own window plus a margin (never shorter than a full refill). */
    private static final class IdleExpiry implements Expiry<String, Limited> {

        private static long nanos(Limited limited) {
            return limited.window().plus(IDLE_MARGIN).toNanos();
        }

        @Override
        public long expireAfterCreate(String key, Limited value, long currentTime) {
            return nanos(value);
        }

        @Override
        public long expireAfterUpdate(String key, Limited value, long currentTime, long currentDuration) {
            return nanos(value);
        }

        @Override
        public long expireAfterRead(String key, Limited value, long currentTime, long currentDuration) {
            return nanos(value);
        }
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
