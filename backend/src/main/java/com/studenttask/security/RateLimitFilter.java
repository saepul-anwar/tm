package com.studenttask.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.common.api.ApiResponse;
import com.studenttask.common.error.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-memory per-client limit (not shared between instances) on register/login/refresh: 10 requests per minute.
 * The client key is {@code request.getRemoteAddr()} only; X-Forwarded-For is deliberately ignored because it is
 * client-controlled. IPv6 clients are keyed by their /64 (a single subscriber controls the whole prefix).
 * The path is matched in canonical form (see {@link RequestPaths}) so encoded spellings share the budget.
 * Buckets live in a size-bounded cache that drops entries idle for {@link #IDLE_EXPIRY} (a full refill period).
 * Behind a reverse proxy this must be revisited (trusted-proxy handling) or all users share the proxy's
 * address. Not a Spring bean on purpose (see JwtAuthenticationFilter).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final int LIMIT_PER_MINUTE = 10;
    static final int DEFAULT_MAX_CLIENTS = 100_000;
    static final Duration IDLE_EXPIRY = Duration.ofMinutes(2);
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh");
    private static final int IPV6_PREFIX_BYTES = 8;

    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets;

    public RateLimitFilter(ObjectMapper objectMapper, Clock clock) {
        this(objectMapper, clock, DEFAULT_MAX_CLIENTS);
    }

    RateLimitFilter(ObjectMapper objectMapper, Clock clock, long maxClients) {
        this.objectMapper = objectMapper;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maxClients)
                .expireAfterAccess(IDLE_EXPIRY)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(RequestPaths.canonical(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = clientKey(request.getRemoteAddr()) + '|' + RequestPaths.canonical(request);
        ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
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

    /** Visible for tests. */
    long trackedClients() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    static String clientKey(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.indexOf(':') < 0) {
            return String.valueOf(remoteAddr);
        }
        try {
            // A literal address: no DNS lookup happens. IPv4-mapped IPv6 literals come back as Inet4Address.
            InetAddress address = InetAddress.getByName(remoteAddr);
            if (address instanceof Inet6Address) {
                return HexFormat.of().formatHex(address.getAddress(), 0, IPV6_PREFIX_BYTES) + "::/64";
            }
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return remoteAddr;
        }
    }

    private static Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder().capacity(LIMIT_PER_MINUTE).refillGreedy(LIMIT_PER_MINUTE, WINDOW).build();
        return Bucket.builder().addLimit(limit).build();
    }
}
