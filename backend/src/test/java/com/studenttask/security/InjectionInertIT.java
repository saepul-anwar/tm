package com.studenttask.security;

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
import com.studenttask.security.SecurityFixtures.Account;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** SQL metacharacters, wildcards, odd unicode and oversized values must be inert: 200/400/404 only, never 500, no leaks. */
@AutoConfigureMockMvc
class InjectionInertIT extends MySqlTestBase {

    private static final String A_MARK = "ZZ-A-PRIVATE-";
    private static final List<String> PAYLOADS = List.of(
            "' OR '1'='1", "' OR 1=1 --", "1; DROP TABLE tasks; --", "\" OR \"\"=\"", "') UNION SELECT password_hash FROM users --",
            "%", "_", "%%", "\\", "\\%", "' --", "/*", "*/", "a' AND SLEEP(5) --", "${jndi:ldap://x}", "{{7*7}}",
            "<script>alert(1)</script>", "\uD83D\uDE00\uD83D\uDD25", "\u0000", "a\u0000b", "..%2F..%2Fetc%2Fpasswd",
            "A".repeat(10_000), "\n\r", "NULL", "null", "true", "-1", "9223372036854775808", "1e309", "0x41");
    private static final List<String> PARAMS = List.of("q", "subject", "sort", "dir", "status", "priority", "page",
            "size", "deadlineFrom", "deadlineTo");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    private Account a;
    private Account b;
    private long aTask;
    private long bTask;

    @BeforeEach
    void data() {
        SecurityFixtures fx = new SecurityFixtures(jdbc, jwt);
        a = fx.user(true);
        b = fx.user(true);
        aTask = fx.task(a.id(), A_MARK + UUID.randomUUID(), "A-SUBJECT", Instant.now().plus(1, ChronoUnit.DAYS), "TODO");
        bTask = fx.task(b.id(), "B plain title", "B-SUBJECT", Instant.now().plus(1, ChronoUnit.DAYS), "TODO");
    }

    private MvcResult as(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + b.token())).andReturn();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Never a 5xx; never user A's data; error bodies never repeat the raw payload. */
    private void assertInert(MvcResult result, String payload, String where) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        assertThat(status).as(where + " body=" + abbreviate(body)).isIn(200, 201, 204, 400, 404);
        assertThat(body).as(where).doesNotContain(A_MARK, "A-SUBJECT", a.email(), "password_hash");
        if (status >= 400 && payload.length() >= 6) { // shorter ones (null, true, -1) are legitimate JSON/words
            assertThat(body).as(where + ": error must not echo the raw input").doesNotContain(payload);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    @Test
    void sql_meta_chars_in_filters_are_inert() throws Exception {
        for (String param : PARAMS) {
            for (String payload : PAYLOADS) {
                MvcResult result = as(get("/api/tasks?" + param + "=" + enc(payload)));
                assertInert(result, payload, param + "=" + abbreviate(payload));
                if (result.getResponse().getStatus() == 200) {
                    JsonNode data = json.readTree(result.getResponse().getContentAsString()).at("/data");
                    for (JsonNode task : data) {
                        assertThat(task.at("/title").asText()).as(param).isEqualTo("B plain title");
                    }
                }
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from tasks", Integer.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from users where id in (?,?)", Integer.class, a.id(), b.id()))
                .isEqualTo(2);
    }

    @Test
    void wildcards_in_search_are_literal_and_do_not_match_other_text() throws Exception {
        for (String wildcard : List.of("%", "_", "%%", "B%", "B_lain", "\\")) {
            JsonNode result = json.readTree(as(get("/api/tasks?q=" + enc(wildcard))).getResponse().getContentAsString());
            assertThat(result.at("/meta/totalElements").asLong()).as("q=" + wildcard).isZero();
        }
        assertThat(json.readTree(as(get("/api/tasks?q=plain")).getResponse().getContentAsString())
                .at("/meta/totalElements").asLong()).isEqualTo(1);
        assertThat(json.readTree(as(get("/api/tasks?subject=" + enc("B%"))).getResponse().getContentAsString())
                .at("/meta/totalElements").asLong()).isZero();
    }

    @Test
    void sort_and_direction_are_allow_listed_not_passed_to_the_database() throws Exception {
        for (String sort : List.of("id", "user_id", "userId", "title; drop table tasks", "password_hash", "deadline desc",
                "(select 1)", "DEADLINE", "priorityRank", "user.password")) {
            MvcResult result = as(get("/api/tasks?sort=" + enc(sort)));
            assertThat(result.getResponse().getStatus()).as(sort).isEqualTo(400);
        }
        for (String dir : List.of("sideways", "asc; drop", "ASCENDING", "1", "")) {
            int status = as(get("/api/tasks?dir=" + enc(dir))).getResponse().getStatus();
            assertThat(status).as("dir=" + dir).isIn(200, 400);
        }
    }

    @Test
    void sql_meta_chars_in_calendar_and_date_filters_are_inert() throws Exception {
        for (String payload : PAYLOADS) {
            assertInert(as(get("/api/calendar/tasks?from=" + enc(payload) + "&to=" + enc(payload))), payload, "calendar");
            assertInert(as(get("/api/tasks?deadlineFrom=" + enc(payload))), payload, "deadlineFrom");
        }
    }

    @Test
    void path_variables_with_sql_fragments_are_rejected_before_any_query() throws Exception {
        List<String> ids = List.of("1%20OR%201=1", "1%27%20--", "%27%20--", "1;DROP%20TABLE%20tasks", "1%00", "%00",
                "-1", "0", "1.0", "1e3", "0x1", "%F0%9F%98%80", "9223372036854775808", "NaN", "%27", "%22",
                "1%0d%0aSet-Cookie:%20x=y", "a".repeat(5000), "%252e%252e");
        for (String id : ids) {
            for (MockHttpServletRequestBuilder builder : List.of(get("/api/tasks/" + id), delete("/api/tasks/" + id),
                    put("/api/tasks/" + id).contentType("application/json").content("{}"),
                    patch("/api/tasks/" + id + "/status").contentType("application/json").content("{\"status\":\"TODO\"}"))) {
                MvcResult result = as(builder);
                assertThat(result.getResponse().getStatus()).as(id).isIn(400, 404);
                assertThat(result.getResponse().getContentAsString()).as(id).doesNotContain(A_MARK, "Set-Cookie", "DROP");
                assertThat(result.getResponse().getHeader("Set-Cookie")).as(id).isNull();
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from tasks where id in (?,?)", Integer.class, aTask, bTask))
                .isEqualTo(2);
    }

    @Test
    void json_fields_with_metacharacters_are_stored_literally_and_returned_unchanged() throws Exception {
        List<String> titles = List.of("' OR '1'='1", "Robert'); DROP TABLE tasks;--", "100% sure_thing\\", "<script>alert(1)</script>",
                "\uD83D\uDE00 emoji \uD83D\uDD25", "a\u0000b", "${7*7} {{7*7}}", "line1\nline2", "\u202Ertl-override");
        for (String title : titles) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", title);
            body.put("description", title);
            body.put("subject", title.length() > 100 ? title.substring(0, 100) : title);
            body.put("deadline", "2030-01-01T00:00:00Z");
            body.put("priority", "LOW");
            MvcResult created = as(post("/api/tasks").contentType("application/json").content(json.writeValueAsString(body)));
            assertThat(created.getResponse().getStatus()).as(title).isIn(201, 400);
            if (created.getResponse().getStatus() == 201) {
                long id = json.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();
                assertThat(jdbc.queryForObject("select user_id from tasks where id = ?", Long.class, id)).isEqualTo(b.id());
                JsonNode back = json.readTree(as(get("/api/tasks/" + id)).getResponse().getContentAsString());
                assertThat(back.at("/data/title").asText()).isEqualTo(title.strip());
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from users where id in (?,?)", Integer.class, a.id(), b.id()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, aTask)).startsWith(A_MARK);
    }

    @Test
    void too_long_json_fields_are_rejected_with_field_names_only() throws Exception {
        String payload = "<b>x</b>'; --" + "z".repeat(300);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", payload);
        body.put("subject", payload);
        body.put("description", payload.repeat(20));
        body.put("deadline", "2030-01-01T00:00:00Z");
        body.put("priority", "LOW");
        MvcResult result = as(post("/api/tasks").contentType("application/json").content(json.writeValueAsString(body)));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String text = result.getResponse().getContentAsString();
        assertThat(text).doesNotContain("<b>", "DROP", "'; --");
        List<String> fields = new ArrayList<>();
        json.readTree(text).at("/error/details").forEach(d -> fields.add(d.get("field").asText()));
        assertThat(fields).contains("title", "subject");
    }

    @Test
    void injection_in_auth_and_profile_fields_is_inert() throws Exception {
        for (String payload : PAYLOADS) {
            Map<String, Object> login = Map.of("email", payload, "password", payload);
            MvcResult result = mvc.perform(post("/api/auth/login").with(SecurityFixtures.freshIp())
                    .contentType("application/json").content(json.writeValueAsString(login))).andReturn();
            assertThat(result.getResponse().getStatus()).as(abbreviate(payload)).isIn(400, 401);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");

            MvcResult profile = as(put("/api/profile").contentType("application/json").content(
                    json.writeValueAsString(Map.of("fullName", payload, "timezone", payload))));
            assertThat(profile.getResponse().getStatus()).as(abbreviate(payload)).isIn(200, 400, 429);
        }
        assertThat(jdbc.queryForObject("select count(*) from users where id in (?,?)", Integer.class, a.id(), b.id()))
                .isEqualTo(2);
    }
}
