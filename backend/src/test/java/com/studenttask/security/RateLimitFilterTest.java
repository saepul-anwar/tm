package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(new ObjectMapper(), Clock.systemUTC());

    private MockHttpServletResponse call(String method, String path, String ip, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void eleventh_request_per_minute_from_same_ip_is_429_with_envelope_and_retry_after() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("POST", "/api/auth/login", "10.0.0.1", null).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse limited = call("POST", "/api/auth/login", "10.0.0.1", null);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentAsString())
                .contains("\"success\":false")
                .contains("\"code\":\"RATE_LIMITED\"");
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1L, 61L);
    }

    @Test
    void other_ips_and_other_paths_have_their_own_budget() throws Exception {
        for (int i = 0; i < 10; i++) {
            call("POST", "/api/auth/login", "10.0.0.2", null);
        }
        assertThat(call("POST", "/api/auth/login", "10.0.0.2", null).getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/auth/login", "10.0.0.3", null).getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/register", "10.0.0.2", null).getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/refresh", "10.0.0.2", null).getStatus()).isEqualTo(200);
    }

    @Test
    void spoofed_x_forwarded_for_does_not_bypass_or_split_the_limit() throws Exception {
        for (int i = 0; i < 10; i++) {
            call("POST", "/api/auth/login", "10.0.0.4", "198.51.100." + i);
        }
        assertThat(call("POST", "/api/auth/login", "10.0.0.4", "203.0.113.99").getStatus()).isEqualTo(429);
    }

    @Test
    void unlimited_paths_and_methods_are_never_limited() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertThat(call("GET", "/api/auth/login", "10.0.0.5", null).getStatus()).isEqualTo(200);
            assertThat(call("GET", "/api/auth/me", "10.0.0.5", null).getStatus()).isEqualTo(200);
            assertThat(call("POST", "/api/auth/logout", "10.0.0.5", null).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void encoded_or_non_canonical_login_paths_share_the_login_budget() throws Exception {
        String[] variants = {
            "/api/auth/%6Cogin", "/api/auth/lo%67in", "/api/auth/login/", "/API/auth/login",
            "/api/auth/login;x=y", "/api/auth//login", "/api/auth/./login"
        };
        for (String variant : variants) {
            String ip = "10.1.0." + (java.util.Arrays.asList(variants).indexOf(variant) + 1);
            for (int i = 0; i < 10; i++) {
                assertThat(call("POST", "/api/auth/login", ip, null).getStatus()).as(variant).isEqualTo(200);
            }
            assertThat(call("POST", variant, ip, null).getStatus()).as(variant).isEqualTo(429);
        }
    }

    @Test
    void ipv6_addresses_in_the_same_64_share_one_bucket() throws Exception {
        for (int i = 0; i < 10; i++) {
            call("POST", "/api/auth/login", "2001:db8:1:2:0:0:0:" + (i + 1), null);
        }
        assertThat(call("POST", "/api/auth/login", "2001:db8:1:2:ffff:eeee:dddd:cccc", null).getStatus())
                .isEqualTo(429);
        assertThat(call("POST", "/api/auth/login", "2001:db8:1:3::1", null).getStatus()).isEqualTo(200);
    }

    /** Clock the test can advance, to drive cache expiry deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-20T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static void hit(RateLimitFilter f, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        f.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void idle_clients_expire_from_the_cache() throws Exception {
        MutableClock clock = new MutableClock();
        RateLimitFilter f = new RateLimitFilter(new ObjectMapper(), clock);
        hit(f, "10.2.0.1");
        hit(f, "10.2.0.2");
        assertThat(f.trackedClients()).isEqualTo(2);

        clock.advance(RateLimitFilter.IDLE_EXPIRY.plusSeconds(1));

        assertThat(f.trackedClients()).isZero();
    }

    @Test
    void cache_size_is_bounded() throws Exception {
        RateLimitFilter f = new RateLimitFilter(new ObjectMapper(), Clock.systemUTC(), 50);
        for (int i = 0; i < 500; i++) {
            hit(f, "10.3." + (i / 250) + "." + (i % 250 + 1));
        }
        assertThat(f.trackedClients()).isLessThanOrEqualTo(50);
    }

    @Test
    void client_key_groups_ipv6_by_64_and_leaves_ipv4_alone() {
        assertThat(RateLimitFilter.clientKey("2001:db8:1:2::1"))
                .isEqualTo(RateLimitFilter.clientKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd"))
                .isNotEqualTo(RateLimitFilter.clientKey("2001:db8:1:3::1"));
        assertThat(RateLimitFilter.clientKey("10.0.0.9")).isEqualTo("10.0.0.9");
        assertThat(RateLimitFilter.clientKey("::ffff:10.0.0.9")).isEqualTo("10.0.0.9");
    }
}
