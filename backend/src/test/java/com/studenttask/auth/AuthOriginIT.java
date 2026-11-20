package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Origin allow-list on the cookie-authenticated endpoints (defence in depth next to SameSite=Strict). */
@AutoConfigureMockMvc
class AuthOriginIT extends MySqlTestBase {

    private static final String PASSWORD = "Passw0rd-Synthetic1";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder freshIp(MockHttpServletRequestBuilder builder) {
        int n = IP_COUNTER.incrementAndGet();
        return builder.with(request -> {
            request.setRemoteAddr("10.77." + (n / 250) + "." + (n % 250 + 1));
            return request;
        });
    }

    /** Registers a user and returns the raw refresh token of a fresh login. */
    private String loginAndGetRefreshToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.test";
        mvc.perform(freshIp(post("/api/auth/register")).contentType("application/json").content(json.writeValueAsString(
                Map.of("fullName", "Budi Santoso", "email", email, "password", PASSWORD, "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated());
        return mvc.perform(freshIp(post("/api/auth/login")).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getCookie("refresh_token").getValue();
    }

    private int revokedCount(String raw) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ? and revoked_at is not null",
                Integer.class, com.studenttask.common.TokenHasher.sha256Hex(raw));
    }

    @Test
    void refresh_with_foreign_origin_is_403_envelope_and_does_not_rotate() throws Exception {
        String raw = loginAndGetRefreshToken();

        mvc.perform(freshIp(post("/api/auth/refresh")).header("Origin", "http://evil.test")
                        .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(revokedCount(raw)).isZero();
        mvc.perform(freshIp(post("/api/auth/refresh")).cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_with_null_origin_is_403() throws Exception {
        String raw = loginAndGetRefreshToken();
        mvc.perform(freshIp(post("/api/auth/refresh")).header("Origin", "null")
                        .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(revokedCount(raw)).isZero();
    }

    @Test
    void refresh_with_allowed_origin_succeeds() throws Exception {
        String raw = loginAndGetRefreshToken();
        mvc.perform(freshIp(post("/api/auth/refresh")).header("Origin", ALLOWED_ORIGIN)
                        .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isOk());
        assertThat(revokedCount(raw)).isEqualTo(1);
    }

    @Test
    void refresh_without_origin_succeeds() throws Exception {
        String raw = loginAndGetRefreshToken();
        mvc.perform(freshIp(post("/api/auth/refresh")).cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isOk());
    }

    @Test
    void logout_with_foreign_origin_is_403_and_does_not_revoke() throws Exception {
        String raw = loginAndGetRefreshToken();
        mvc.perform(freshIp(post("/api/auth/logout")).header("Origin", "http://evil.test")
                        .cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(revokedCount(raw)).isZero();
    }

    @Test
    void logout_with_allowed_or_no_origin_revokes() throws Exception {
        String withOrigin = loginAndGetRefreshToken();
        mvc.perform(freshIp(post("/api/auth/logout")).header("Origin", ALLOWED_ORIGIN)
                        .cookie(new Cookie("refresh_token", withOrigin)))
                .andExpect(status().isNoContent());
        assertThat(revokedCount(withOrigin)).isEqualTo(1);

        String withoutOrigin = loginAndGetRefreshToken();
        mvc.perform(freshIp(post("/api/auth/logout")).cookie(new Cookie("refresh_token", withoutOrigin)))
                .andExpect(status().isNoContent());
        assertThat(revokedCount(withoutOrigin)).isEqualTo(1);
    }

    @Test
    void foreign_origin_on_encoded_path_is_still_checked() throws Exception {
        String raw = loginAndGetRefreshToken();
        int status = mvc.perform(freshIp(post(java.net.URI.create("/api/auth/%72efresh")))
                        .header("Origin", "http://evil.test").cookie(new Cookie("refresh_token", raw)))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(400, 403, 404);
        assertThat(revokedCount(raw)).isZero();
    }
}
