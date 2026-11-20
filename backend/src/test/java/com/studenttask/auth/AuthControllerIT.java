package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.AuthService;
import com.studenttask.auth.service.JwtService;
import com.studenttask.common.TokenHasher;
import com.studenttask.config.AppProperties;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerIT extends MySqlTestBase {

    private static final String PASSWORD = "Passw0rd-Synthetic1";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthService authService;

    @Autowired
    AppProperties properties;

    /** Every test uses its own remote address so the shared in-memory rate limiter never couples tests. */
    private MockHttpServletRequestBuilder withFreshIp(MockHttpServletRequestBuilder builder) {
        int n = IP_COUNTER.incrementAndGet();
        return fixedIp(builder, "10." + (n / 250) + "." + (n % 250 + 1) + ".1");
    }

    private static MockHttpServletRequestBuilder fixedIp(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    private String registerBody(String email, String password, String confirm) throws Exception {
        return json.writeValueAsString(Map.of("fullName", "Budi Santoso", "email", email,
                "password", password, "confirmPassword", confirm));
    }

    private MvcResult register(String email, String password, String confirm) throws Exception {
        return mvc.perform(withFreshIp(post("/api/auth/register")).contentType("application/json")
                        .content(registerBody(email, password, confirm)))
                .andReturn();
    }

    private void registerOk(String email) throws Exception {
        assertThat(register(email, PASSWORD, PASSWORD).getResponse().getStatus()).isEqualTo(201);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(withFreshIp(post("/api/auth/login")).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
    }

    private static Cookie refreshCookie(MvcResult result) {
        return result.getResponse().getCookie("refresh_token");
    }

    private MvcResult refresh(String rawRefresh) throws Exception {
        return mvc.perform(withFreshIp(post("/api/auth/refresh")).cookie(new Cookie("refresh_token", rawRefresh)))
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private long userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private int activeRefreshTokens(long userId) {
        return jdbc.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Integer.class, userId);
    }

    @Test
    void register_creates_user_and_default_reminder_settings_all_true() throws Exception {
        String email = email();
        MvcResult result = register(email, PASSWORD, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = body(result).get("data");
        assertThat(data.get("email").asText()).isEqualTo(email);
        assertThat(data.get("fullName").asText()).isEqualTo("Budi Santoso");
        assertThat(data.get("timezone").asText()).isEqualTo("Asia/Jakarta");
        assertThat(result.getResponse().getContentAsString().toLowerCase()).doesNotContain("password", "hash");

        Map<String, Object> row = jdbc.queryForMap(
                "select reminder_24_hours a, reminder_6_hours b, reminder_1_hour c, reminder_at_deadline d "
                        + "from reminder_settings where user_id = ?", userId(email));
        assertThat(row.values()).containsOnly(true);
        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void register_lowercases_email_and_rejects_duplicate_with_409() throws Exception {
        String lower = email();
        assertThat(register(lower.toUpperCase(), PASSWORD, PASSWORD)
                .getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, lower)).isEqualTo(1);

        MvcResult duplicate = register(lower, PASSWORD, PASSWORD);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicate).at("/error/code").asText()).isEqualTo("CONFLICT");
    }

    @Test
    void register_rejects_mismatched_confirm() throws Exception {
        MvcResult result = register(email(), PASSWORD, PASSWORD + "x");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body(result).at("/error/details/0/field").asText()).isEqualTo("confirmPassword");
    }

    @Test
    void register_rejects_password_over_72_bytes_even_if_under_72_chars() throws Exception {
        String emojiPassword = "Passw0rd" + "😀".repeat(30); // 38 characters, 128 bytes
        String email = email();
        MvcResult result = register(email, emojiPassword, emojiPassword);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body(result).at("/error/details/0/field").asText()).isEqualTo("password");
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isZero();
    }

    @Test
    void register_accepts_password_of_exactly_72_bytes_and_login_works_with_it() throws Exception {
        String email = email();
        String exactly72 = "a1" + "x".repeat(70);
        assertThat(register(email, exactly72, exactly72).getResponse().getStatus()).isEqualTo(201);
        assertThat(login(email, exactly72).getResponse().getStatus()).isEqualTo(200);
        // bytes past 72 must never be silently ignored
        assertThat(login(email, exactly72 + "y").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void register_rejects_password_without_digit() throws Exception {
        MvcResult result = register(email(), "OnlyLettersHere", "OnlyLettersHere");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).at("/error/details/0/field").asText()).isEqualTo("password");
    }

    @Test
    void register_rejects_blank_name_bad_email_and_missing_fields() throws Exception {
        MvcResult result = mvc.perform(withFreshIp(post("/api/auth/register")).contentType("application/json")
                        .content("{\"fullName\":\" \",\"email\":\"not-an-email\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        List<String> fields = body(result).at("/error/details").findValuesAsText("field");
        assertThat(fields).contains("fullName", "email", "password");
    }

    @Test
    void login_wrong_password_and_unknown_email_return_identical_401() throws Exception {
        String email = email();
        registerOk(email);

        MvcResult wrongPassword = login(email, "Wr0ngPassword-x");
        MvcResult unknownEmail = login(email(), PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
        assertThat(body(wrongPassword).at("/error/code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(wrongPassword.getResponse().getCookie("refresh_token")).isNull();
    }

    @Test
    void login_returns_access_token_and_httponly_refresh_cookie() throws Exception {
        String email = email();
        registerOk(email);
        MvcResult result = login(email.toUpperCase(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = body(result).get("data");
        assertThat(data.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(data.has("refreshToken")).isFalse();
        String accessToken = data.get("accessToken").asText();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refresh_token=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");

        // only the hash is stored
        String raw = refreshCookie(result).getValue();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                TokenHasher.sha256Hex(raw))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw))
                .isZero();
        assertThat(raw).hasSize(43); // 32 random bytes, base64url without padding

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.id").value(userId(email)));
    }

    @Test
    void me_without_or_with_garbage_token_returns_401_envelope() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void expired_access_token_returns_401() throws Exception {
        String email = email();
        registerOk(email);
        long id = userId(email);
        Clock past = Clock.offset(Clock.systemUTC(), Duration.ofHours(-1));
        String expired = new JwtService(properties, past).issue(id, email);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refresh_rotates_token_and_old_token_reuse_revokes_all_user_tokens() throws Exception {
        String email = email();
        registerOk(email);
        long id = userId(email);
        String first = refreshCookie(login(email, PASSWORD)).getValue();
        String otherDevice = refreshCookie(login(email, PASSWORD)).getValue();
        assertThat(activeRefreshTokens(id)).isEqualTo(2);

        MvcResult rotated = refresh(first);
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(rotated).at("/data/accessToken").asText()).isNotBlank();
        String second = refreshCookie(rotated).getValue();
        assertThat(second).isNotEqualTo(first);
        assertThat(activeRefreshTokens(id)).isEqualTo(2); // first revoked, second added

        // reuse of the already-rotated token => theft assumed, everything revoked
        MvcResult reuse = refresh(first);
        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(activeRefreshTokens(id)).isZero();
        assertThat(refresh(second).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(otherDevice).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_without_cookie_or_with_unknown_token_returns_401_and_revokes_nothing() throws Exception {
        String email = email();
        registerOk(email);
        login(email, PASSWORD);

        mvc.perform(withFreshIp(post("/api/auth/refresh"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        assertThat(refresh("unknown-token-value").getResponse().getStatus()).isEqualTo(401);
        assertThat(activeRefreshTokens(userId(email))).isEqualTo(1);
    }

    @Test
    void expired_refresh_token_is_rejected() throws Exception {
        String email = email();
        registerOk(email);
        String raw = refreshCookie(login(email, PASSWORD)).getValue();
        // expires_at is stored in UTC; compute it in SQL to stay independent of the JVM/session time zone
        jdbc.update("update refresh_tokens set expires_at = UTC_TIMESTAMP(3) - interval 60 second where token_hash = ?",
                TokenHasher.sha256Hex(raw));

        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logout_revokes_refresh_token() throws Exception {
        String email = email();
        registerOk(email);
        String raw = refreshCookie(login(email, PASSWORD)).getValue();

        MvcResult logout = mvc.perform(withFreshIp(post("/api/auth/logout")).cookie(new Cookie("refresh_token", raw)))
                .andReturn();

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(logout.getResponse().getHeader("Set-Cookie")).contains("refresh_token=", "Max-Age=0", "Path=/api/auth");
        assertThat(activeRefreshTokens(userId(email))).isZero();
        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logout_without_cookie_is_204() throws Exception {
        mvc.perform(withFreshIp(post("/api/auth/logout"))).andExpect(status().isNoContent());
    }

    @Test
    void revokeAll_revokes_every_refresh_token_of_user() throws Exception {
        String email = email();
        String bystander = email();
        registerOk(email);
        registerOk(bystander);
        for (int i = 0; i < 3; i++) {
            login(email, PASSWORD);
        }
        login(bystander, PASSWORD);
        assertThat(activeRefreshTokens(userId(email))).isEqualTo(3);

        authService.revokeAll(userId(email));

        assertThat(activeRefreshTokens(userId(email))).isZero();
        assertThat(activeRefreshTokens(userId(bystander))).isEqualTo(1);
    }

    @Test
    void login_11th_request_per_minute_from_same_ip_returns_429() throws Exception {
        String ip = "203.0." + (IP_COUNTER.incrementAndGet() % 250) + ".9";
        String payload = json.writeValueAsString(Map.of("email", email(), "password", PASSWORD));
        for (int i = 0; i < 10; i++) {
            mvc.perform(fixedIp(post("/api/auth/login"), ip).contentType("application/json").content(payload))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(fixedIp(post("/api/auth/login"), ip).contentType("application/json").content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void password_never_appears_in_logs_or_responses(CapturedOutput output) throws Exception {
        String email = email();
        String secretPassword = "Synth3tic-Secret-Pw-" + UUID.randomUUID();
        MvcResult registered = register(email, secretPassword, secretPassword);
        MvcResult loggedIn = login(email, secretPassword);
        MvcResult failed = login(email, secretPassword + "-wrong");
        MvcResult invalid = register(email(), "short", "other");
        String refreshRaw = refreshCookie(loggedIn).getValue();
        String accessRaw = body(loggedIn).at("/data/accessToken").asText();
        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        refresh(refreshRaw);

        for (MvcResult r : List.of(registered, loggedIn, failed, invalid)) {
            assertThat(r.getResponse().getContentAsString()).doesNotContain(secretPassword).doesNotContain(hash);
        }
        assertThat(output.getAll())
                .doesNotContain(secretPassword)
                .doesNotContain(hash)
                .doesNotContain(refreshRaw)
                .doesNotContain(accessRaw)
                .doesNotContain(TokenHasher.sha256Hex(refreshRaw));
    }
}
