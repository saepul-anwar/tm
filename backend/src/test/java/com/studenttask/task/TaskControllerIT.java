package com.studenttask.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class TaskControllerIT extends MySqlTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    private long userA;
    private long userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void users() {
        userA = insertUser();
        userB = insertUser();
        tokenA = jwt.issue(userA, "a@example.test");
        tokenB = jwt.issue(userB, "b@example.test");
    }

    private long insertUser() {
        String email = "task-" + UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users (full_name,email,password_hash,timezone,created_at,updated_at) "
                + "values ('Synthetic User', ?, 'x', 'Asia/Jakarta', now(3), now(3))", email);
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private static Map<String, Object> body() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", "Laporan Praktikum");
        map.put("description", "Bab 1-3");
        map.put("subject", "Fisika Dasar");
        map.put("deadline", "2026-12-01T23:59:00+07:00");
        map.put("priority", "HIGH");
        return map;
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder builder, Object payload)
            throws Exception {
        return builder.header("Authorization", "Bearer " + token).contentType("application/json")
                .content(payload instanceof String s ? s : json.writeValueAsString(payload));
    }

    private MvcResult send(String token, MockHttpServletRequestBuilder builder, Object payload) throws Exception {
        return mvc.perform(as(token, builder, payload)).andReturn();
    }

    private MvcResult send(String token, MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createOk(String token, Map<String, Object> payload) throws Exception {
        MvcResult result = send(token, post("/api/tasks"), payload);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return read(result).get("data");
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private String errorCode(MvcResult result) throws Exception {
        return read(result).path("error").path("code").asText();
    }

    @Test
    void create_returns_201_with_utc_deadline_and_all_fields() throws Exception {
        JsonNode data = createOk(tokenA, body());

        assertThat(data.get("id").asLong()).isPositive();
        assertThat(data.get("title").asText()).isEqualTo("Laporan Praktikum");
        assertThat(data.get("deadline").asText()).isEqualTo("2026-12-01T16:59:00Z");
        assertThat(data.get("priority").asText()).isEqualTo("HIGH");
        assertThat(data.get("status").asText()).isEqualTo("TODO");
        assertThat(data.get("completedAt").isNull()).isTrue();
        assertThat(data.get("version").asLong()).isZero();
        assertThat(data.has("deadlineCategory")).isTrue();
        // open task: category present (value depends on the real clock, so only presence is asserted)
        assertThat(data.get("deadlineCategory").isNull()).isFalse();
        assertThat(data.get("createdAt").asText()).endsWith("Z");
        assertThat(data.get("updatedAt").asText()).endsWith("Z");
        assertThat(data.has("userId")).isFalse();
        assertThat(jdbc.queryForObject("select user_id from tasks where id = ?", Long.class, data.get("id").asLong()))
                .isEqualTo(userA);
    }

    @Test
    void create_requires_title_and_deadline() throws Exception {
        Map<String, Object> noTitle = body();
        noTitle.remove("title");
        Map<String, Object> noDeadline = body();
        noDeadline.remove("deadline");
        Map<String, Object> noPriority = body();
        noPriority.remove("priority");

        for (Map<String, Object> payload : new Map[] {noTitle, noDeadline, noPriority}) {
            MvcResult result = send(tokenA, post("/api/tasks"), payload);
            assertThat(status(result)).isEqualTo(400);
            assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
        }
        assertThat(jdbc.queryForObject("select count(*) from tasks where user_id = ?", Integer.class, userA)).isZero();
    }

    @Test
    void create_allows_past_deadline() throws Exception {
        Map<String, Object> payload = body();
        payload.put("deadline", "2020-01-01T00:00:00Z");
        assertThat(createOk(tokenA, payload).get("deadline").asText()).isEqualTo("2020-01-01T00:00:00Z");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void blank_title_is_rejected(String title) throws Exception {
        Map<String, Object> payload = body();
        payload.put("title", title);
        MvcResult result = send(tokenA, post("/api/tasks"), payload);
        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void title_is_trimmed() throws Exception {
        Map<String, Object> payload = body();
        payload.put("title", "  Esai  ");
        assertThat(createOk(tokenA, payload).get("title").asText()).isEqualTo("Esai");
    }

    @Test
    void title_over_200_chars_rejected() throws Exception {
        Map<String, Object> payload = body();
        payload.put("title", "a".repeat(201));
        MvcResult result = send(tokenA, post("/api/tasks"), payload);
        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");

        payload.put("title", "a".repeat(200));
        assertThat(createOk(tokenA, payload).get("title").asText()).hasSize(200);
    }

    @Test
    void description_over_5000_and_subject_over_100_rejected_at_limits_accepted() throws Exception {
        Map<String, Object> longDescription = body();
        longDescription.put("description", "d".repeat(5001));
        assertThat(status(send(tokenA, post("/api/tasks"), longDescription))).isEqualTo(400);
        Map<String, Object> longSubject = body();
        longSubject.put("subject", "s".repeat(101));
        assertThat(status(send(tokenA, post("/api/tasks"), longSubject))).isEqualTo(400);

        Map<String, Object> atLimits = body();
        atLimits.put("description", "d".repeat(5000));
        atLimits.put("subject", "s".repeat(100));
        JsonNode data = createOk(tokenA, atLimits);
        assertThat(data.get("description").asText()).hasSize(5000);
        assertThat(data.get("subject").asText()).hasSize(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"priority\":\"CRITICAL\"", "\"priority\":\"high\"", "\"status\":\"DONE\"",
            "\"status\":42", "\"deadline\":\"2026-12-01\"", "\"deadline\":\"not-a-date\""})
    void invalid_enum_or_deadline_format_returns_400_not_500(String badField) throws Exception {
        String key = badField.substring(0, badField.indexOf(':'));
        String raw = "{\"title\":\"T\",\"deadline\":\"2026-12-01T10:00:00Z\",\"priority\":\"LOW\","
                + key + ":0," + badField + "}";
        // The duplicate key keeps the last (invalid) value; this stays valid JSON for Jackson.
        MvcResult result = send(tokenA, post("/api/tasks"), raw);
        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void invalid_status_in_patch_returns_400_and_missing_status_returns_400() throws Exception {
        long id = createOk(tokenA, body()).get("id").asLong();
        MvcResult bad = send(tokenA, patch("/api/tasks/" + id + "/status"), "{\"status\":\"DONE\"}");
        assertThat(status(bad)).isEqualTo(400);
        assertThat(errorCode(bad)).isEqualTo("VALIDATION_ERROR");
        MvcResult missing = send(tokenA, patch("/api/tasks/" + id + "/status"), "{}");
        assertThat(status(missing)).isEqualTo(400);
        assertThat(errorCode(missing)).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void non_numeric_id_returns_400_envelope() throws Exception {
        MvcResult result = send(tokenA, get("/api/tasks/abc"));
        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void emoji_and_script_tag_roundtrip_verbatim() throws Exception {
        String title = "Tugas 😀 <script>alert(1)</script>";
        String subject = "Mata 📚 <b>kuliah</b>";
        Map<String, Object> payload = body();
        payload.put("title", title);
        payload.put("subject", subject);
        payload.put("description", "<img src=x onerror=alert(1)> 🚀");
        long id = createOk(tokenA, payload).get("id").asLong();

        JsonNode data = read(send(tokenA, get("/api/tasks/" + id))).get("data");
        assertThat(data.get("title").asText()).isEqualTo(title);
        assertThat(data.get("subject").asText()).isEqualTo(subject);
        assertThat(data.get("description").asText()).isEqualTo("<img src=x onerror=alert(1)> 🚀");
    }

    @Test
    void get_returns_own_task_and_put_replaces_fields_and_bumps_version() throws Exception {
        JsonNode created = createOk(tokenA, body());
        long id = created.get("id").asLong();

        Map<String, Object> change = body();
        change.put("title", "Judul Baru");
        change.put("priority", "URGENT");
        change.put("subject", "");
        change.put("version", created.get("version").asLong());
        MvcResult result = send(tokenA, put("/api/tasks/" + id), change);

        assertThat(status(result)).isEqualTo(200);
        JsonNode data = read(result).get("data");
        assertThat(data.get("title").asText()).isEqualTo("Judul Baru");
        assertThat(data.get("priority").asText()).isEqualTo("URGENT");
        assertThat(data.get("subject").isNull()).isTrue();
        assertThat(data.get("version").asLong()).isEqualTo(1);
        assertThat(read(send(tokenA, get("/api/tasks/" + id))).get("data").get("title").asText())
                .isEqualTo("Judul Baru");
    }

    @Test
    void put_validates_like_post() throws Exception {
        long id = createOk(tokenA, body()).get("id").asLong();
        Map<String, Object> payload = body();
        payload.put("title", " ");
        assertThat(status(send(tokenA, put("/api/tasks/" + id), payload))).isEqualTo(400);
    }

    @Test
    void user_b_gets_404_on_get_update_patch_delete_of_user_a_task() throws Exception {
        long id = createOk(tokenA, body()).get("id").asLong();
        long missing = id + 100000;

        MvcResult[] attempts = {
            send(tokenB, get("/api/tasks/" + id)),
            send(tokenB, put("/api/tasks/" + id), body()),
            send(tokenB, patch("/api/tasks/" + id + "/status"), Map.of("status", "COMPLETED")),
            send(tokenB, delete("/api/tasks/" + id)),
        };
        MvcResult nonexistent = send(tokenB, get("/api/tasks/" + missing));

        for (MvcResult attempt : attempts) {
            assertThat(status(attempt)).isEqualTo(404);
            assertThat(errorCode(attempt)).isEqualTo("NOT_FOUND");
            assertThat(attempt.getResponse().getContentAsString()).doesNotContain("Laporan Praktikum");
            // Identical to a task that does not exist: no ownership oracle.
            assertThat(read(attempt).get("error").get("message"))
                    .isEqualTo(read(nonexistent).get("error").get("message"));
        }
        Map<String, Object> row = jdbc.queryForMap("select title, status from tasks where id = ?", id);
        assertThat(row.get("title")).isEqualTo("Laporan Praktikum");
        assertThat(row.get("status")).isEqualTo("TODO");
        assertThat(status(send(tokenA, get("/api/tasks/" + id)))).isEqualTo(200);
    }

    @Test
    void responses_never_contain_another_users_data() throws Exception {
        Map<String, Object> secret = body();
        secret.put("title", "RAHASIA-A-" + UUID.randomUUID());
        long idA = createOk(tokenA, secret).get("id").asLong();
        JsonNode b = createOk(tokenB, body());

        String bodyB = send(tokenB, get("/api/tasks/" + b.get("id").asLong())).getResponse().getContentAsString();
        assertThat(bodyB).doesNotContain((String) secret.get("title"));
        assertThat(send(tokenB, get("/api/tasks/" + idA)).getResponse().getContentAsString())
                .doesNotContain((String) secret.get("title"));
        assertThat(jdbc.queryForObject("select user_id from tasks where id = ?", Long.class, b.get("id").asLong()))
                .isEqualTo(userB);
    }

    @Test
    void status_completed_sets_completed_at_and_reopen_clears_it() throws Exception {
        long id = createOk(tokenA, body()).get("id").asLong();

        JsonNode done = read(send(tokenA, patch("/api/tasks/" + id + "/status"), Map.of("status", "COMPLETED")))
                .get("data");
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("completedAt").asText()).endsWith("Z");
        assertThat(jdbc.queryForObject("select completed_at from tasks where id = ?", Object.class, id)).isNotNull();

        JsonNode reopened = read(send(tokenA, patch("/api/tasks/" + id + "/status"), Map.of("status", "IN_PROGRESS")))
                .get("data");
        assertThat(reopened.get("completedAt").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select completed_at from tasks where id = ?", Object.class, id)).isNull();
    }

    @Test
    void update_with_stale_version_returns_409() throws Exception {
        JsonNode created = createOk(tokenA, body());
        long id = created.get("id").asLong();
        long v0 = created.get("version").asLong();

        Map<String, Object> first = body();
        first.put("title", "Edit pertama");
        first.put("version", v0);
        assertThat(status(send(tokenA, put("/api/tasks/" + id), first))).isEqualTo(200);

        Map<String, Object> stale = body();
        stale.put("title", "Edit basi");
        stale.put("version", v0);
        MvcResult result = send(tokenA, put("/api/tasks/" + id), stale);
        assertThat(status(result)).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("CONFLICT");
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, id))
                .isEqualTo("Edit pertama");
    }

    @Test
    void put_without_version_skips_the_check() throws Exception {
        long id = createOk(tokenA, body()).get("id").asLong();
        assertThat(status(send(tokenA, put("/api/tasks/" + id), body()))).isEqualTo(200);
        assertThat(status(send(tokenA, put("/api/tasks/" + id), body()))).isEqualTo(200);
    }

    @Test
    void delete_removes_task_and_cascades_notification_logs() throws Exception {
        long id = createOk(tokenA, body()).get("id").asLong();
        jdbc.update("insert into notification_logs (user_id,task_id,reminder_type,scheduled_at,status,created_at,"
                + "updated_at) values (?,?,'H24','2026-11-30 00:00:00.000','SENT',now(3),now(3))", userA, id);
        assertThat(jdbc.queryForObject("select count(*) from notification_logs where task_id = ?", Integer.class, id))
                .isEqualTo(1);

        assertThat(status(send(tokenA, delete("/api/tasks/" + id)))).isEqualTo(204);

        assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_logs where task_id = ?", Integer.class, id))
                .isZero();
        assertThat(status(send(tokenA, get("/api/tasks/" + id)))).isEqualTo(404);
        assertThat(status(send(tokenA, delete("/api/tasks/" + id)))).isEqualTo(404);
    }

    @Test
    void userId_in_request_body_is_ignored() throws Exception {
        Map<String, Object> payload = body();
        payload.put("userId", userB);
        payload.put("user_id", userB);
        payload.put("id", 1);
        payload.put("version", null);
        payload.put("completedAt", "2020-01-01T00:00:00Z");
        JsonNode created = createOk(tokenA, payload);
        long id = created.get("id").asLong();

        assertThat(jdbc.queryForObject("select user_id from tasks where id = ?", Long.class, id)).isEqualTo(userA);
        assertThat(created.get("completedAt").isNull()).isTrue();
        assertThat(status(send(tokenB, get("/api/tasks/" + id)))).isEqualTo(404);

        assertThat(status(send(tokenA, put("/api/tasks/" + id), payload))).isEqualTo(200);
        assertThat(jdbc.queryForObject("select user_id from tasks where id = ?", Long.class, id)).isEqualTo(userA);
    }

    @Test
    void unauthenticated_requests_return_401() throws Exception {
        for (MockHttpServletRequestBuilder builder : new MockHttpServletRequestBuilder[] {
            post("/api/tasks"), get("/api/tasks/1"), put("/api/tasks/1"), patch("/api/tasks/1/status"),
            delete("/api/tasks/1")}) {
            MvcResult result = mvc.perform(builder.contentType("application/json").content("{}")).andReturn();
            assertThat(status(result)).isEqualTo(401);
            assertThat(errorCode(result)).isEqualTo("UNAUTHENTICATED");
        }
    }
}
