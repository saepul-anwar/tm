package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studenttask.MySqlTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SecurityConfigIT extends MySqlTestBase {

    private static final String ALLOWED = "http://localhost:3000";

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Test
    void unauthenticated_request_to_protected_path_returns_401_in_api_envelope() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    void invalid_bearer_token_on_protected_path_still_returns_envelope_401() throws Exception {
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/telegram/webhook"})
    void public_post_paths_are_not_blocked_by_authentication(String path) throws Exception {
        // No controller exists yet, so a permitted request reaches routing and yields 404 (never 401/403).
        mvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void actuator_health_is_public() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void cors_allows_configured_origin_only() throws Exception {
        mvc.perform(options("/api/tasks")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(options("/api/tasks")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void security_headers_are_present() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void no_default_generated_user_details_service_exists() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }
}
