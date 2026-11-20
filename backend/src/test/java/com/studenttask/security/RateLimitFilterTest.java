package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
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
}
