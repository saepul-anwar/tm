package com.studenttask.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** All data is synthetic. Users are created through the real register/login endpoints so hashes are real BCrypt. */
@AutoConfigureMockMvc
class ProfileIT extends MySqlTestBase {

    private static final String PASSWORD = "Passw0rd-Synthetic1";
    private static final String NEW_PASSWORD = "N3w-Synthetic-Pass";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1000);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private record Account(long id, String email, String accessToken, Cookie refreshCookie) {
    }

    private static MockHttpServletRequestBuilder freshIp(MockHttpServletRequestBuilder builder) {
        int n = IP_COUNTER.incrementAndGet();
        return builder.with(request -> {
            request.setRemoteAddr("10." + (n / 250) + "." + (n % 250 + 1) + ".7");
            return request;
        });
    }

    private static String newEmail() {
        return "profile-" + UUID.randomUUID() + "@example.test";
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(freshIp(post("/api/auth/login")).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
    }

    private Account newAccount() throws Exception {
        String email = newEmail();
        MvcResult reg = mvc.perform(freshIp(post("/api/auth/register")).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("fullName", "Budi Santoso", "email", email,
                                "password", PASSWORD, "confirmPassword", PASSWORD))))
                .andReturn();
        assertThat(reg.getResponse().getStatus()).isEqualTo(201);
        MvcResult login = login(email, PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String token = body(login).at("/data/accessToken").asText();
        long id = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        return new Account(id, email, token, login.getResponse().getCookie("refresh_token"));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult send(String token, MockHttpServletRequestBuilder builder, Object payload) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (payload != null) {
            builder.contentType("application/json").content(json.writeValueAsString(payload));
        }
        return mvc.perform(builder).andReturn();
    }

    private MvcResult putProfile(Account account, Map<String, Object> payload) throws Exception {
        return send(account.accessToken(), put("/api/profile"), payload);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String errorCode(MvcResult r) throws Exception {
        return body(r).at("/error/code").asText();
    }

    private boolean hasFieldError(MvcResult r, String field) throws Exception {
        for (JsonNode d : body(r).at("/error/details")) {
            if (field.equals(d.get("field").asText())) {
                return true;
            }
        }
        return false;
    }

    private String dbEmail(long id) {
        return jdbc.queryForObject("select email from users where id = ?", String.class, id);
    }

    private String dbHash(long id) {
        return jdbc.queryForObject("select password_hash from users where id = ?", String.class, id);
    }

    // ---- named tests from the plan ----

    @Test
    void email_change_requires_current_password() throws Exception {
        Account a = newAccount();
        String target = newEmail();

        MvcResult missing = putProfile(a, map("email", target));
        assertThat(status(missing)).isEqualTo(400);
        assertThat(errorCode(missing)).isEqualTo("VALIDATION_ERROR");
        assertThat(hasFieldError(missing, "currentPassword")).isTrue();
        assertThat(dbEmail(a.id())).isEqualTo(a.email());

        MvcResult done = putProfile(a, map("email", target.toUpperCase(), "currentPassword", PASSWORD));
        assertThat(status(done)).isEqualTo(200);
        assertThat(body(done).at("/data/email").asText()).isEqualTo(target);
        assertThat(dbEmail(a.id())).isEqualTo(target);

        MvcResult me = send(a.accessToken(), get("/api/auth/me"), null);
        assertThat(body(me).at("/data/email").asText()).isEqualTo(target);
        assertThat(status(login(target, PASSWORD))).isEqualTo(200);
    }

    @Test
    void new_password_requires_current_password_and_revokes_refresh_tokens() throws Exception {
        Account a = newAccount();

        MvcResult missing = putProfile(a, map("newPassword", NEW_PASSWORD));
        assertThat(status(missing)).isEqualTo(400);
        assertThat(hasFieldError(missing, "currentPassword")).isTrue();
        assertThat(status(login(a.email(), PASSWORD))).isEqualTo(200);

        String oldHash = dbHash(a.id());
        MvcResult ok = putProfile(a, map("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD));
        assertThat(status(ok)).isEqualTo(200);
        assertThat(dbHash(a.id())).isNotEqualTo(oldHash).startsWith("$2");
        assertThat(jdbc.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Integer.class, a.id()))
                .isZero();

        MvcResult refresh = mvc.perform(freshIp(post("/api/auth/refresh")).cookie(a.refreshCookie())).andReturn();
        assertThat(status(refresh)).isEqualTo(401);
        assertThat(status(login(a.email(), PASSWORD))).isEqualTo(401);
        assertThat(status(login(a.email(), NEW_PASSWORD))).isEqualTo(200);
    }

    @Test
    void duplicate_email_returns_409() throws Exception {
        Account a = newAccount();
        Account b = newAccount();

        MvcResult same = putProfile(a, map("email", b.email(), "currentPassword", PASSWORD));
        assertThat(status(same)).isEqualTo(409);
        assertThat(errorCode(same)).isEqualTo("CONFLICT");

        MvcResult otherCase = putProfile(a, map("email", b.email().toUpperCase(), "currentPassword", PASSWORD));
        assertThat(status(otherCase)).isEqualTo(409);
        assertThat(dbEmail(a.id())).isEqualTo(a.email());
        assertThat(dbEmail(b.id())).isEqualTo(b.email());
    }

    @Test
    void invalid_iana_timezone_returns_400() throws Exception {
        Account a = newAccount();
        for (String bad : new String[] {"+07:00", "UTC+7", "Mars/Olympus", "asia/jakarta", "", "   ", "GMT+7", "Z"}) {
            MvcResult r = putProfile(a, map("timezone", bad));
            assertThat(status(r)).as("timezone=%s", bad).isEqualTo(400);
            assertThat(errorCode(r)).isEqualTo("VALIDATION_ERROR");
            assertThat(hasFieldError(r, "timezone")).as("timezone=%s", bad).isTrue();
        }
        assertThat(jdbc.queryForObject("select timezone from users where id = ?", String.class, a.id()))
                .isEqualTo("Asia/Jakarta");

        MvcResult ok = putProfile(a, map("timezone", "America/New_York"));
        assertThat(status(ok)).isEqualTo(200);
        assertThat(body(ok).at("/data/timezone").asText()).isEqualTo("America/New_York");
        assertThat(body(send(a.accessToken(), get("/api/auth/me"), null)).at("/data/timezone").asText())
                .isEqualTo("America/New_York");
    }

    @Test
    void settings_toggle_persists() throws Exception {
        Account a = newAccount();
        MvcResult initial = send(a.accessToken(), get("/api/reminders/settings"), null);
        assertThat(status(initial)).isEqualTo(200);
        assertThat(body(initial).at("/data").toString()).isEqualTo(
                "{\"reminder24Hours\":true,\"reminder6Hours\":true,\"reminder1Hour\":true,\"reminderAtDeadline\":true}");

        MvcResult upd = send(a.accessToken(), put("/api/reminders/settings"),
                map("reminder24Hours", false, "reminder6Hours", true, "reminder1Hour", false,
                        "reminderAtDeadline", true));
        assertThat(status(upd)).isEqualTo(200);

        JsonNode data = body(send(a.accessToken(), get("/api/reminders/settings"), null)).get("data");
        assertThat(data.get("reminder24Hours").asBoolean()).isFalse();
        assertThat(data.get("reminder6Hours").asBoolean()).isTrue();
        assertThat(data.get("reminder1Hour").asBoolean()).isFalse();
        assertThat(data.get("reminderAtDeadline").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select reminder_24_hours from reminder_settings where user_id = ?",
                Boolean.class, a.id())).isFalse();
    }

    @Test
    void profile_never_returns_password_hash_or_chat_id() throws Exception {
        Account a = newAccount();
        jdbc.update("update users set telegram_chat_id = 987654321, telegram_connected_at = '2026-11-01 03:00:00.000' "
                + "where id = ?", a.id());
        String hash = dbHash(a.id());

        for (MvcResult r : new MvcResult[] {
                send(a.accessToken(), get("/api/profile"), null),
                putProfile(a, map("fullName", "Siti Aminah"))}) {
            assertThat(status(r)).isEqualTo(200);
            String raw = r.getResponse().getContentAsString();
            assertThat(raw).doesNotContain(hash).doesNotContain("987654321").doesNotContainIgnoringCase("password")
                    .doesNotContainIgnoringCase("chatId").doesNotContainIgnoringCase("chat_id");
            JsonNode data = body(r).get("data");
            assertThat(data.get("telegram").get("connected").asBoolean()).isTrue();
            assertThat(data.get("telegram").get("connectedAt").asText()).startsWith("2026-11-01T03:00:00");
        }
    }

    // ---- additional coverage ----

    @Test
    void get_returns_profile_and_disconnected_telegram_by_default() throws Exception {
        Account a = newAccount();
        JsonNode data = body(send(a.accessToken(), get("/api/profile"), null)).get("data");
        assertThat(data.get("fullName").asText()).isEqualTo("Budi Santoso");
        assertThat(data.get("email").asText()).isEqualTo(a.email());
        assertThat(data.get("timezone").asText()).isEqualTo("Asia/Jakarta");
        assertThat(data.get("telegram").get("connected").asBoolean()).isFalse();
        assertThat(data.get("telegram").get("connectedAt").isNull()).isTrue();
    }

    @Test
    void wrong_current_password_returns_400_not_401_and_changes_nothing() throws Exception {
        Account a = newAccount();
        String oldHash = dbHash(a.id());
        for (Map<String, Object> payload : new Map[] {
                map("email", newEmail(), "fullName", "Changed Name", "currentPassword", "Wr0ng-Password-1"),
                map("newPassword", NEW_PASSWORD, "fullName", "Changed Name", "currentPassword", "Wr0ng-Password-1"),
                map("email", newEmail(), "currentPassword", "x".repeat(80))}) {
            MvcResult r = putProfile(a, payload);
            assertThat(status(r)).isEqualTo(400);
            assertThat(errorCode(r)).isEqualTo("VALIDATION_ERROR");
            assertThat(hasFieldError(r, "currentPassword")).isTrue();
        }
        assertThat(dbEmail(a.id())).isEqualTo(a.email());
        assertThat(dbHash(a.id())).isEqualTo(oldHash);
        assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, a.id()))
                .isEqualTo("Budi Santoso");
        assertThat(status(login(a.email(), PASSWORD))).isEqualTo(200);
    }

    @Test
    void changing_to_own_current_email_is_a_noop_without_password() throws Exception {
        Account a = newAccount();
        MvcResult r = putProfile(a, map("email", a.email().toUpperCase()));
        assertThat(status(r)).isEqualTo(200);
        assertThat(body(r).at("/data/email").asText()).isEqualTo(a.email());
        assertThat(dbEmail(a.id())).isEqualTo(a.email());
    }

    @Test
    void new_password_is_validated_against_the_password_policy() throws Exception {
        Account a = newAccount();
        String oldHash = dbHash(a.id());
        String emoji30 = "😀".repeat(30) + "a1"; // 122 bytes in UTF-8
        for (String weak : new String[] {emoji30, "short1", "onlyletters", "12345678", "a1".repeat(37)}) {
            MvcResult r = putProfile(a, map("currentPassword", PASSWORD, "newPassword", weak));
            assertThat(status(r)).as("len=%d", weak.length()).isEqualTo(400);
            assertThat(errorCode(r)).isEqualTo("VALIDATION_ERROR");
            assertThat(hasFieldError(r, "newPassword")).isTrue();
        }
        assertThat(dbHash(a.id())).isEqualTo(oldHash);
        assertThat(status(login(a.email(), PASSWORD))).isEqualTo(200);
    }

    @Test
    void full_name_is_trimmed_and_validated() throws Exception {
        Account a = newAccount();
        MvcResult ok = putProfile(a, map("fullName", "  Siti Aminah  "));
        assertThat(status(ok)).isEqualTo(200);
        assertThat(body(ok).at("/data/fullName").asText()).isEqualTo("Siti Aminah");

        for (String bad : new String[] {"", "   ", "x".repeat(101)}) {
            MvcResult r = putProfile(a, map("fullName", bad));
            assertThat(status(r)).isEqualTo(400);
            assertThat(hasFieldError(r, "fullName")).isTrue();
        }
        assertThat(body(send(a.accessToken(), get("/api/profile"), null)).at("/data/fullName").asText())
                .isEqualTo("Siti Aminah");
    }

    @Test
    void invalid_email_format_and_length_are_rejected() throws Exception {
        Account a = newAccount();
        for (String bad : new String[] {"not-an-email", "a".repeat(250) + "@example.test"}) {
            MvcResult r = putProfile(a, map("email", bad, "currentPassword", PASSWORD));
            assertThat(status(r)).isEqualTo(400);
            assertThat(hasFieldError(r, "email")).isTrue();
        }
        assertThat(dbEmail(a.id())).isEqualTo(a.email());
    }

    @Test
    void empty_update_changes_nothing_and_unknown_properties_are_ignored() throws Exception {
        Account a = newAccount();
        Account b = newAccount();
        String chatBefore = String.valueOf(jdbc.queryForObject(
                "select telegram_chat_id from users where id = ?", Object.class, a.id()));

        MvcResult r = putProfile(a, map("fullName", "Siti Aminah", "userId", b.id(), "id", b.id(),
                "telegramChatId", 555111, "passwordHash", "hacked", "createdAt", "2000-01-01T00:00:00Z"));
        assertThat(status(r)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, a.id()))
                .isEqualTo("Siti Aminah");
        assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, b.id()))
                .isEqualTo("Budi Santoso");
        assertThat(String.valueOf(jdbc.queryForObject(
                "select telegram_chat_id from users where id = ?", Object.class, a.id()))).isEqualTo(chatBefore);
        assertThat(dbHash(a.id())).isNotEqualTo("hacked");
        assertThat(status(putProfile(a, map()))).isEqualTo(200);
    }

    @Test
    void timezone_change_affects_deadline_category_on_task_responses() throws Exception {
        Account a = newAccount();
        ZoneId east = ZoneId.of("Etc/GMT-6"); // UTC+6, no DST
        ZoneId west = ZoneId.of("Etc/GMT+6"); // UTC-6, no DST: time of day is 12h apart from east
        Instant now = Instant.now();
        Instant deadline = now.plus(Duration.ofHours(36));
        MvcResult created = send(a.accessToken(), post("/api/tasks"),
                map("title", "Laporan", "deadline", deadline.toString(), "priority", "LOW"));
        assertThat(status(created)).isEqualTo(201);
        long taskId = body(created).at("/data/id").asLong();

        String eastExpected = expectedCategory(deadline, now, east);
        String westExpected = expectedCategory(deadline, now, west);
        assertThat(eastExpected).isNotEqualTo(westExpected);

        assertThat(status(putProfile(a, map("timezone", east.getId())))).isEqualTo(200);
        assertThat(categoryOf(a, taskId)).isEqualTo(eastExpected);
        assertThat(status(putProfile(a, map("timezone", west.getId())))).isEqualTo(200);
        assertThat(categoryOf(a, taskId)).isEqualTo(westExpected);
    }

    private static String expectedCategory(Instant deadline, Instant now, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate due = deadline.atZone(zone).toLocalDate();
        return due.equals(today.plusDays(1)) ? "DUE_TOMORROW" : "UPCOMING";
    }

    private String categoryOf(Account a, long taskId) throws Exception {
        return body(send(a.accessToken(), get("/api/tasks/" + taskId), null)).at("/data/deadlineCategory").asText();
    }

    @Test
    void unauthenticated_requests_return_401() throws Exception {
        for (MockHttpServletRequestBuilder b : new MockHttpServletRequestBuilder[] {
                get("/api/profile"), put("/api/profile"), get("/api/reminders/settings"),
                put("/api/reminders/settings")}) {
            MvcResult r = send(null, b, map("fullName", "X"));
            assertThat(status(r)).isEqualTo(401);
            assertThat(errorCode(r)).isEqualTo("UNAUTHENTICATED");
        }
    }

    @Test
    void settings_belong_to_the_token_owner_and_unknown_properties_are_ignored() throws Exception {
        Account a = newAccount();
        Account b = newAccount();
        MvcResult r = send(a.accessToken(), put("/api/reminders/settings"),
                map("reminder24Hours", false, "reminder6Hours", false, "reminder1Hour", false,
                        "reminderAtDeadline", false, "userId", b.id(), "id", 1));
        assertThat(status(r)).isEqualTo(200);
        JsonNode other = body(send(b.accessToken(), get("/api/reminders/settings"), null)).get("data");
        assertThat(other.get("reminder24Hours").asBoolean()).isTrue();
        assertThat(other.get("reminderAtDeadline").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from reminder_settings where user_id = ?", Integer.class,
                a.id())).isEqualTo(1);
    }

    @Test
    void settings_put_requires_all_four_flags() throws Exception {
        Account a = newAccount();
        MvcResult r = send(a.accessToken(), put("/api/reminders/settings"),
                map("reminder24Hours", false, "reminder6Hours", true, "reminder1Hour", true));
        assertThat(status(r)).isEqualTo(400);
        assertThat(hasFieldError(r, "reminderAtDeadline")).isTrue();
        assertThat(body(send(a.accessToken(), get("/api/reminders/settings"), null)).at("/data/reminder24Hours")
                .asBoolean()).isTrue();
    }

    @Test
    void settings_get_creates_defaults_when_the_row_is_missing() throws Exception {
        Account a = newAccount();
        jdbc.update("delete from reminder_settings where user_id = ?", a.id());
        MvcResult r = send(a.accessToken(), get("/api/reminders/settings"), null);
        assertThat(status(r)).isEqualTo(200);
        assertThat(body(r).at("/data/reminder1Hour").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from reminder_settings where user_id = ?", Integer.class,
                a.id())).isEqualTo(1);
    }

    @Test
    void settings_put_creates_the_row_when_missing() throws Exception {
        Account a = newAccount();
        jdbc.update("delete from reminder_settings where user_id = ?", a.id());
        MvcResult r = send(a.accessToken(), put("/api/reminders/settings"),
                map("reminder24Hours", false, "reminder6Hours", true, "reminder1Hour", true,
                        "reminderAtDeadline", true));
        assertThat(status(r)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select reminder_24_hours from reminder_settings where user_id = ?",
                Boolean.class, a.id())).isFalse();
    }

    @Test
    void profile_put_11th_request_per_minute_returns_429_per_user() throws Exception {
        Account a = newAccount();
        for (int i = 0; i < 10; i++) {
            assertThat(status(putProfile(a, map("fullName", "Budi " + i)))).isEqualTo(200);
        }

        MvcResult eleventh = putProfile(a, map("fullName", "Budi 11"));

        assertThat(status(eleventh)).isEqualTo(429);
        assertThat(errorCode(eleventh)).isEqualTo("RATE_LIMITED");
        // Per user, not per IP: a different account is unaffected; reads are not limited.
        assertThat(status(putProfile(newAccount(), map("fullName", "Citra")))).isEqualTo(200);
        assertThat(status(send(a.accessToken(), get("/api/profile"), null))).isEqualTo(200);
    }

    @Test
    void sixth_wrong_current_password_returns_429_even_for_the_right_password_and_is_per_user() throws Exception {
        Account a = newAccount();
        String target = newEmail();
        for (int i = 0; i < 5; i++) {
            MvcResult wrong = putProfile(a, map("email", target, "currentPassword", "Wr0ng-Password-" + i));
            assertThat(status(wrong)).isEqualTo(400);
            assertThat(hasFieldError(wrong, "currentPassword")).isTrue();
        }

        MvcResult sixth = putProfile(a, map("email", target, "currentPassword", PASSWORD));

        assertThat(status(sixth)).isEqualTo(429);
        assertThat(errorCode(sixth)).isEqualTo("RATE_LIMITED");
        assertThat(dbEmail(a.id())).isEqualTo(a.email());
        // Updates that do not need the password still work; another user is unaffected.
        assertThat(status(putProfile(a, map("fullName", "Budi Baru")))).isEqualTo(200);
        Account b = newAccount();
        assertThat(status(putProfile(b, map("email", newEmail(), "currentPassword", PASSWORD)))).isEqualTo(200);
    }

    @Test
    void email_change_revokes_all_refresh_tokens() throws Exception {
        Account a = newAccount();

        assertThat(status(putProfile(a, map("email", newEmail(), "currentPassword", PASSWORD)))).isEqualTo(200);

        assertThat(jdbc.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Integer.class, a.id()))
                .isZero();
        MvcResult refresh = mvc.perform(freshIp(post("/api/auth/refresh")).cookie(a.refreshCookie())).andReturn();
        assertThat(status(refresh)).isEqualTo(401);
    }
}
