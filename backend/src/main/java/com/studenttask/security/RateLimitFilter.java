package com.studenttask.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.common.api.ApiResponse;
import com.studenttask.common.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-memory per-IP limit (not shared between instances) on register/login/refresh: 10 requests per minute.
 * The client key is {@code request.getRemoteAddr()} only; X-Forwarded-For is deliberately ignored because it is
 * client-controlled. Behind a reverse proxy this must be revisited (trusted-proxy handling) or all users share
 * the proxy's address. Not a Spring bean on purpose (see JwtAuthenticationFilter).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final int LIMIT_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh");
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long nowMillis = clock.millis();
        evictIdleEntries(nowMillis);
        Entry entry = buckets.computeIfAbsent(request.getRemoteAddr() + '|' + request.getRequestURI(), k -> newEntry());
        entry.lastUsedMillis = nowMillis;
        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ErrorCode.RATE_LIMITED, "Terlalu banyak permintaan. Coba lagi nanti.", null));
    }

    /** An entry idle for a full window has refilled completely, so dropping it loses nothing. */
    private void evictIdleEntries(long nowMillis) {
        if (buckets.size() > CLEANUP_THRESHOLD) {
            buckets.values().removeIf(e -> nowMillis - e.lastUsedMillis > WINDOW.toMillis());
        }
    }

    private static Entry newEntry() {
        Bandwidth limit = Bandwidth.builder().capacity(LIMIT_PER_MINUTE).refillGreedy(LIMIT_PER_MINUTE, WINDOW).build();
        return new Entry(Bucket.builder().addLimit(limit).build());
    }

    private static final class Entry {
        private final Bucket bucket;
        private volatile long lastUsedMillis;

        private Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }
}
