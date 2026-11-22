package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Security headers on every kind of API response, token responses, and the CORS policy. */
@AutoConfigureMockMvc
class SecurityHeadersIT extends MySqlTestBase {

    private static final String ALLOWED = "http://localhost:3000";
    private static final String PASSWORD = "Passw0rd-Synthetic1";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    private SecurityFixtures.Account user;

    @BeforeEach
    void user() {
        user = new SecurityFixtures(jdbc, jwt).user(false);
    }

    private void assertHardened(MockHttpServletResponse response, String what) {
        assertThat(response.getHeader("X-Content-Type-Options")).as(what).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).as(what).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).as(what).isEqualTo("no-referrer");
        assertThat(response.getHeader("Content-Security-Policy")).as(what)
                .isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(response.getHeader("Cache-Control")).as(what).contains("no-store");
        assertThat(response.getHeader("Access-Control-Allow-Origin")).as(what).isNotEqualTo("*");
        assertThat(response.getHeader("Server")).as(what).isNull();
        assertThat(response.getHeader("X-Powered-By")).as(what).isNull();
    }

    @Test
    void security_headers_are_present_on_success_and_every_error_status() throws Exception {
        String bearer = "Bearer " + user.token();
        assertHardened(mvc.perform(get("/api/tasks").header("Authorization", bearer)).andReturn().getResponse(), "200");
        assertHardened(mvc.perform(get("/api/tasks")).andReturn().getResponse(), "401");
        assertHardened(mvc.perform(get("/api/tasks/99999999").header("Authorization", bearer)).andReturn().getResponse(), "404");
        assertHardened(mvc.perform(get("/api/nope").header("Authorization", bearer)).andReturn().getResponse(), "404 route");
        assertHardened(mvc.perform(delete("/api/tasks").header("Authorization", bearer)).andReturn().getResponse(), "405");
        assertHardened(mvc.perform(post("/api/tasks").header("Authorization", bearer).contentType("text/plain")
                .content("x")).andReturn().getResponse(), "415");
        assertHardened(mvc.perform(post("/api/tasks").header("Authorization", bearer).contentType("application/json")
                .content("{")).andReturn().getResponse(), "400");
        assertHardened(mvc.perform(get("/actuator/health")).andReturn().getResponse(), "health");
        assertHardened(mvc.perform(post("/api/auth/refresh").with(SecurityFixtures.freshIp())).andReturn().getResponse(),
                "refresh 401");
        assertHardened(mvc.perform(post("/api/auth/refresh").with(SecurityFixtures.freshIp())
                .header("Origin", "http://evil.example")).andReturn().getResponse(), "origin 403");
    }

    @Test
    void hsts_is_sent_over_https() throws Exception {
        MockHttpServletResponse https = mvc.perform(get("/api/tasks").secure(true)).andReturn().getResponse();
        assertThat(https.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000 ; includeSubDomains");
    }

    @Test
    void token_responses_are_not_cacheable_and_the_refresh_cookie_is_locked_down() throws Exception {
        String email = "hdr-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/auth/register").with(SecurityFixtures.freshIp()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("fullName", "Sinta Synthetic", "email", email,
                        "password", PASSWORD, "confirmPassword", PASSWORD)))).andReturn();

        MvcResult login = mvc.perform(post("/api/auth/login").with(SecurityFixtures.freshIp())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getHeader("Cache-Control")).contains("no-store");
        List<String> cookies = login.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
        JsonNode body = json.readTree(login.getResponse().getContentAsString());
        assertThat(body.at("/data/accessToken").asText()).isNotEmpty();
        assertThat(body.toString()).as("refresh token is never in the JSON body").doesNotContain("refreshToken");

        String refreshValue = cookies.get(0).substring(cookies.get(0).indexOf('=') + 1, cookies.get(0).indexOf(';'));
        MvcResult refresh = mvc.perform(post("/api/auth/refresh").with(SecurityFixtures.freshIp())
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshValue))).andReturn();
        assertThat(refresh.getResponse().getStatus()).isEqualTo(200);
        assertThat(refresh.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(refresh.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "Secure", "SameSite=Strict");
    }

    // ---- CORS ----

    // Accepted: Spring compares origins case-insensitively and ignores a trailing slash. Browsers always send the
    // canonical lowercase origin without a slash, and the response echoes the request origin, so nothing widens.
    @Test
    void cors_rejects_unlisted_origins_on_simple_and_preflight_requests() throws Exception {
        String bearer = "Bearer " + user.token();
        List<String> bad = List.of("http://evil.example", "null", "http://localhost:3001", "https://localhost:3000",
                "http://localhost:3000.evil.example", "http://evil.example/http://localhost:3000", "*", "");
        for (String origin : bad) {
            MockHttpServletResponse preflight = mvc.perform(options("/api/tasks").header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET")).andReturn().getResponse();
            assertThat(preflight.getHeader("Access-Control-Allow-Origin")).as("preflight " + origin).isNull();
            assertThat(preflight.getHeader("Access-Control-Allow-Credentials")).as("preflight " + origin).isNull();

            MockHttpServletResponse simple = mvc.perform(get("/api/tasks").header("Origin", origin)
                    .header("Authorization", bearer)).andReturn().getResponse();
            assertThat(simple.getHeader("Access-Control-Allow-Origin")).as("simple " + origin).isNull();
            if (!origin.isEmpty()) { // an empty Origin header is treated as absent
                assertThat(simple.getStatus()).as("simple " + origin).isEqualTo(403);
            }
        }
    }

    @Test
    void cors_preflight_answers_only_the_configured_origin_with_credentials_and_no_wildcard() throws Exception {
        MockHttpServletResponse ok = mvc.perform(options("/api/tasks").header("Origin", ALLOWED)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")).andReturn().getResponse();
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED);
        assertThat(ok.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(ok.getHeader("Access-Control-Allow-Methods")).doesNotContain("*").doesNotContain("TRACE");
        assertThat(ok.getHeader("Access-Control-Allow-Headers")).doesNotContain("*");
        assertThat(ok.getHeaders("Vary")).anyMatch(v -> v.contains("Origin"));

        // Methods and headers outside the allow-list are refused even for the right origin.
        for (String method : List.of("TRACE", "CONNECT", "HEAD-X")) {
            assertThat(mvc.perform(options("/api/tasks").header("Origin", ALLOWED)
                    .header("Access-Control-Request-Method", method)).andReturn().getResponse().getStatus())
                    .as(method).isEqualTo(403);
        }
        assertThat(mvc.perform(options("/api/tasks").header("Origin", ALLOWED)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "X-Evil-Header")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void cors_actual_response_for_the_configured_origin_has_credentials_but_never_a_wildcard() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/tasks").header("Origin", ALLOWED)
                .header("Authorization", "Bearer " + user.token())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED);
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }
}
