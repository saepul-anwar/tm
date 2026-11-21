package com.studenttask.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import com.studenttask.calendar.service.CalendarService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
class CalendarIT extends MySqlTestBase {

    private static final Instant BASE = Instant.parse("2026-12-01T00:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @Autowired
    CalendarService service;

    private long userA;
    private long userB;
    private String tokenA;

    @BeforeEach
    void users() {
        userA = insertUser();
        userB = insertUser();
        tokenA = jwt.issue(userA, "a@example.test");
    }

    /** DATETIME columns hold UTC; bind a LocalDateTime so the JDBC session zone cannot shift it. */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long insertUser() {
        String email = "cal-" + UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users (full_name,email,password_hash,timezone,created_at,updated_at) "
                + "values ('Synthetic User', ?, 'x', 'Asia/Jakarta', now(3), now(3))", email);
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long insertTask(long user, String title, Instant deadline, String status) {
        jdbc.update("insert into tasks (user_id,title,description,subject,deadline,priority,status,version,"
                + "created_at,updated_at) values (?,?,?,?,?,'HIGH',?,0,now(3),now(3))",
                user, title, "secret description", "Fisika", utc(deadline), status);
        return jdbc.queryForObject("select max(id) from tasks where user_id = ?", Long.class, user);
    }

    private MvcResult call(String token, String query) throws Exception {
        MockHttpServletRequestBuilder builder = get(java.net.URI.create("/api/calendar/tasks" + query));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder).andReturn();
    }

    private JsonNode ok(String query) throws Exception {
        MvcResult result = call(tokenA, query);
        assertThat(result.getResponse().getStatus()).as(query).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode badRequest(String query) throws Exception {
        MvcResult result = call(tokenA, query);
        assertThat(result.getResponse().getStatus()).as(query).isEqualTo(400);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("error").path("code").asText()).as(query).isEqualTo("VALIDATION_ERROR");
        return body;
    }

    private static List<String> titles(JsonNode body) {
        List<String> out = new ArrayList<>();
        body.get("data").forEach(n -> out.add(n.get("title").asText()));
        return out;
    }

    private static String q(Instant from, Instant to) {
        return "?from=" + from + "&to=" + to;
    }

    @Test
    void returns_only_own_tasks_in_range_inclusive_start_exclusive_end() throws Exception {
        insertTask(userA, "sebelum", BASE.minusMillis(1), "TODO");
        insertTask(userA, "awal", BASE, "TODO");
        insertTask(userA, "tengah", BASE.plus(Duration.ofDays(1)), "IN_PROGRESS");
        insertTask(userA, "akhir", BASE.plus(Duration.ofDays(2)), "TODO");
        insertTask(userB, "milik-user-lain", BASE.plus(Duration.ofHours(1)), "TODO");

        JsonNode body = ok(q(BASE, BASE.plus(Duration.ofDays(2))));

        assertThat(titles(body)).containsExactly("awal", "tengah");
    }

    @Test
    void includes_completed_tasks() throws Exception {
        insertTask(userA, "selesai", BASE.plus(Duration.ofHours(1)), "COMPLETED");
        insertTask(userA, "belum", BASE.plus(Duration.ofHours(2)), "TODO");

        JsonNode body = ok(q(BASE, BASE.plus(Duration.ofDays(1))));

        assertThat(titles(body)).containsExactly("selesai", "belum");
        assertThat(body.get("data").get(0).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void range_over_62_days_returns_400() throws Exception {
        JsonNode body = badRequest(q(BASE, BASE.plus(Duration.ofDays(62)).plusMillis(1)));
        assertThat(body.toString()).contains("\"to\"");
        badRequest(q(BASE, BASE.plus(Duration.ofDays(400))));
    }

    @Test
    void exactly_62_days_is_allowed() throws Exception {
        insertTask(userA, "dalam", BASE.plus(Duration.ofDays(61)), "TODO");
        insertTask(userA, "tepat-akhir", BASE.plus(Duration.ofDays(62)), "TODO");

        assertThat(titles(ok(q(BASE, BASE.plus(Duration.ofDays(62)))))).containsExactly("dalam");
    }

    @Test
    void from_after_to_returns_400() throws Exception {
        badRequest(q(BASE.plus(Duration.ofDays(1)), BASE));
    }

    @Test
    void from_equal_to_returns_400() throws Exception {
        badRequest(q(BASE, BASE));
    }

    @Test
    void missing_from_or_to_returns_400_naming_the_field() throws Exception {
        assertThat(badRequest("?to=2026-12-02T00:00:00Z").toString()).contains("\"from\"");
        assertThat(badRequest("?from=2026-12-01T00:00:00Z").toString()).contains("\"to\"");
        badRequest("");
        badRequest("?from=&to=");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-12-01", "garbage", "2026-12-01T00:00:00", "2026-13-45T00:00:00Z", "1700000000"})
    void garbage_or_date_only_returns_400_on_that_field(String bad) throws Exception {
        assertThat(badRequest("?from=" + bad + "&to=2026-12-02T00:00:00Z").toString()).contains("\"from\"");
        assertThat(badRequest("?from=2026-12-01T00:00:00Z&to=" + bad).toString()).contains("\"to\"");
    }

    @Test
    void offsets_encoded_as_percent_2b_select_same_instants_as_utc() throws Exception {
        insertTask(userA, "a", Instant.parse("2026-12-01T16:59:59.999Z"), "TODO");
        insertTask(userA, "b", Instant.parse("2026-12-01T17:00:00Z"), "TODO");
        insertTask(userA, "c", Instant.parse("2026-12-02T16:59:59.999Z"), "TODO");
        insertTask(userA, "d", Instant.parse("2026-12-02T17:00:00Z"), "TODO");

        JsonNode withOffset = ok("?from=2026-12-02T00:00:00%2B07:00&to=2026-12-03T00:00:00%2B07:00");
        JsonNode utc = ok("?from=2026-12-01T17:00:00Z&to=2026-12-02T17:00:00Z");

        assertThat(titles(withOffset)).containsExactly("b", "c");
        assertThat(titles(utc)).isEqualTo(titles(withOffset));
        // deadlines are always returned in UTC
        assertThat(withOffset.get("data").get(0).get("deadline").asText()).isEqualTo("2026-12-01T17:00:00Z");
    }

    @Test
    void results_are_ordered_by_deadline_then_id() throws Exception {
        Instant same = BASE.plus(Duration.ofHours(5));
        long late = insertTask(userA, "terlambat", BASE.plus(Duration.ofHours(9)), "TODO");
        long second = insertTask(userA, "sama-id-lebih-kecil", same, "TODO");
        long first = insertTask(userA, "sama-id-lebih-besar", same, "TODO");
        long early = insertTask(userA, "awal", BASE.plus(Duration.ofHours(1)), "TODO");

        List<Long> ids = new ArrayList<>();
        ok(q(BASE, BASE.plus(Duration.ofDays(1)))).get("data").forEach(n -> ids.add(n.get("id").asLong()));

        assertThat(ids).containsExactly(early, second, first, late);
    }

    @Test
    void other_users_tasks_never_appear() throws Exception {
        insertTask(userB, "rahasia-b", BASE.plus(Duration.ofHours(1)), "TODO");

        assertThat(titles(ok(q(BASE, BASE.plus(Duration.ofDays(1)))))).isEmpty();
    }

    @Test
    void response_has_only_lightweight_fields() throws Exception {
        long id = insertTask(userA, "ringan", BASE.plus(Duration.ofHours(1)), "TODO");

        JsonNode item = ok(q(BASE, BASE.plus(Duration.ofDays(1)))).get("data").get(0);

        List<String> fields = new ArrayList<>();
        item.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "title", "subject", "deadline", "priority", "status");
        assertThat(item.get("id").asLong()).isEqualTo(id);
        assertThat(item.get("priority").asText()).isEqualTo("HIGH");
    }

    @Test
    void unauthenticated_returns_401() throws Exception {
        assertThat(call(null, q(BASE, BASE.plusSeconds(1))).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void result_is_capped_at_the_maximum_and_keeps_the_earliest() {
        int total = 1005;
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            rows.add(new Object[] {userA, "t" + i, utc(BASE.plusSeconds(i))});
        }
        jdbc.batchUpdate("insert into tasks (user_id,title,deadline,priority,status,version,created_at,updated_at) "
                + "values (?,?,?,'LOW','TODO',0,now(3),now(3))", rows);

        var result = service.tasks(userA, BASE, BASE.plus(Duration.ofDays(1)));

        assertThat(result).hasSize(CalendarService.MAX_RESULTS);
        assertThat(result.get(0).title()).isEqualTo("t0");
        assertThat(result.get(result.size() - 1).title()).isEqualTo("t" + (CalendarService.MAX_RESULTS - 1));
    }

    @Test
    void query_uses_the_user_deadline_index() {
        for (int i = 0; i < 200; i++) {
            insertTask(userA, "idx" + i, BASE.plusSeconds(i * 3600L), "TODO");
        }
        jdbc.execute("analyze table tasks");
        Instant to = BASE.plus(Duration.ofDays(1));

        var plan = jdbc.queryForMap("explain select id,title,subject,deadline,priority,status "
                + "from tasks where user_id = ? and deadline >= ? and deadline < ? order by deadline, id",
                userA, utc(BASE), utc(to));

        assertThat(plan.get("key")).isEqualTo("ix_tasks_user_deadline");
    }
}
