package com.studenttask.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Seeds rows directly with offsets relative to the real clock (hours/days, never near a boundary the
 * assertions depend on), so results are stable. Boundaries are covered by the pure calculator test.
 */
@AutoConfigureMockMvc
class DashboardIT extends MySqlTestBase {

    private static final DateTimeFormatter UTC_LITERAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @Autowired
    Clock clock;

    private long userA;
    private long userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void users() {
        userA = insertUser("Asia/Jakarta");
        userB = insertUser("Asia/Jakarta");
        tokenA = jwt.issue(userA, "a@example.test");
        tokenB = jwt.issue(userB, "b@example.test");
    }

    private long insertUser(String timezone) {
        String email = "dash-" + UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users (full_name,email,password_hash,timezone,created_at,updated_at) "
                + "values ('Synthetic User', ?, 'x', ?, now(3), now(3))", email, timezone);
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long seed(long user, String title, Duration fromNow, String priority, String status) {
        Instant deadline = clock.instant().plus(fromNow);
        Instant completedAt = status.equals("COMPLETED") ? clock.instant() : null;
        jdbc.update("insert into tasks (user_id,title,deadline,priority,status,completed_at,version,created_at,updated_at) "
                + "values (?,?,?,?,?,?,0,now(3),now(3))", user, title, utc(deadline), priority, status,
                completedAt == null ? null : utc(completedAt));
        return jdbc.queryForObject("select max(id) from tasks where user_id = ?", Long.class, user);
    }

    /** DATETIME(3) holds UTC; pass a literal so the test JVM's default zone cannot shift it. */
    private static String utc(Instant instant) {
        return UTC_LITERAL.format(instant);
    }

    private MvcResult call(String path, String token) throws Exception {
        var builder = get("/api/dashboard" + path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder).andReturn();
    }

    private JsonNode ok(String path, String token) throws Exception {
        MvcResult result = call(path, token);
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isTrue();
        return body.get("data");
    }

    private static List<String> titles(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get("title").asText()));
        return out;
    }

    @Test
    void summary_counts_match_seeded_tasks_and_overdue_is_subset_of_open() throws Exception {
        seed(userA, "todo-overdue", Duration.ofDays(-3), "LOW", "TODO");
        seed(userA, "progress-overdue", Duration.ofHours(-5), "HIGH", "IN_PROGRESS");
        seed(userA, "todo-soon", Duration.ofDays(2), "MEDIUM", "TODO");
        seed(userA, "progress-in-6d", Duration.ofDays(6), "URGENT", "IN_PROGRESS");
        seed(userA, "todo-later", Duration.ofDays(30), "LOW", "TODO");
        seed(userA, "done-old", Duration.ofDays(-10), "LOW", "COMPLETED");
        seed(userA, "done-future", Duration.ofDays(3), "LOW", "COMPLETED");

        JsonNode data = ok("/summary", tokenA);
        JsonNode counts = data.get("counts");
        assertThat(counts.get("total").asInt()).isEqualTo(7);
        assertThat(counts.get("todo").asInt()).isEqualTo(3);
        assertThat(counts.get("inProgress").asInt()).isEqualTo(2);
        assertThat(counts.get("completed").asInt()).isEqualTo(2);
        assertThat(counts.get("overdue").asInt()).isEqualTo(2); // open only; completed-old excluded
        assertThat(counts.get("upcoming").asInt()).isEqualTo(2); // todo-soon, progress-in-6d
        assertThat(counts.get("overdue").asInt())
                .isLessThanOrEqualTo(counts.get("todo").asInt() + counts.get("inProgress").asInt());
        assertThat(data.get("completionPercent").asInt()).isEqualTo(29); // 2/7 = 28.57 -> 29

        assertThat(titles(data.get("dueSoon"))).containsExactly("todo-soon", "progress-in-6d", "todo-later");
        assertThat(titles(data.get("topPriority")))
                .containsExactly("progress-in-6d", "progress-overdue", "todo-soon", "todo-overdue", "todo-later");
        assertThat(data.get("dueSoon").get(0).get("deadlineCategory").isNull()).isFalse();
    }

    @Test
    void completion_percent_is_zero_when_no_tasks() throws Exception {
        JsonNode data = ok("/summary", tokenA);
        assertThat(data.get("completionPercent").asInt()).isZero();
        assertThat(data.get("counts").get("total").asInt()).isZero();
        assertThat(data.get("counts").get("overdue").asInt()).isZero();
        assertThat(data.get("dueSoon")).isEmpty();
        assertThat(data.get("topPriority")).isEmpty();
        assertThat(ok("/upcoming", tokenA)).isEmpty();
        assertThat(ok("/overdue", tokenA)).isEmpty();
    }

    @Test
    void completion_percent_rounds_half_up_and_reaches_100() throws Exception {
        seed(userA, "a", Duration.ofDays(1), "LOW", "COMPLETED");
        seed(userA, "b", Duration.ofDays(1), "LOW", "TODO");
        seed(userA, "c", Duration.ofDays(1), "LOW", "TODO");
        assertThat(ok("/summary", tokenA).get("completionPercent").asInt()).isEqualTo(33); // 33.3
        seed(userA, "d", Duration.ofDays(1), "LOW", "COMPLETED");
        seed(userA, "e", Duration.ofDays(1), "LOW", "COMPLETED");
        seed(userA, "f", Duration.ofDays(1), "LOW", "COMPLETED");
        seed(userA, "g", Duration.ofDays(1), "LOW", "COMPLETED");
        assertThat(ok("/summary", tokenA).get("completionPercent").asInt()).isEqualTo(71); // 5/7 = 71.4
        jdbc.update("update tasks set status='COMPLETED', completed_at=now(3) where user_id=?", userA);
        assertThat(ok("/summary", tokenA).get("completionPercent").asInt()).isEqualTo(100);
        jdbc.update("delete from tasks where user_id=?", userA);
        seed(userA, "h", Duration.ofDays(1), "LOW", "COMPLETED");
        seed(userA, "i", Duration.ofDays(1), "LOW", "TODO");
        assertThat(ok("/summary", tokenA).get("completionPercent").asInt()).isEqualTo(50);
    }

    @Test
    void dashboard_excludes_other_users_tasks() throws Exception {
        seed(userB, "b-overdue", Duration.ofDays(-1), "URGENT", "TODO");
        seed(userB, "b-soon", Duration.ofDays(1), "URGENT", "TODO");
        seed(userB, "b-done", Duration.ofDays(1), "URGENT", "COMPLETED");
        seed(userA, "a-soon", Duration.ofDays(1), "LOW", "TODO");

        JsonNode summary = ok("/summary", tokenA);
        assertThat(summary.get("counts").get("total").asInt()).isEqualTo(1);
        assertThat(summary.get("counts").get("overdue").asInt()).isZero();
        assertThat(summary.get("counts").get("completed").asInt()).isZero();
        assertThat(titles(summary.get("dueSoon"))).containsExactly("a-soon");
        assertThat(titles(summary.get("topPriority"))).containsExactly("a-soon");
        assertThat(titles(ok("/upcoming", tokenA))).containsExactly("a-soon");
        assertThat(ok("/overdue", tokenA)).isEmpty();
        assertThat(titles(ok("/overdue", tokenB))).containsExactly("b-overdue");
    }

    @Test
    void upcoming_lists_open_tasks_within_seven_days_sorted_by_deadline() throws Exception {
        seed(userA, "in-5d", Duration.ofDays(5), "LOW", "TODO");
        seed(userA, "in-1h", Duration.ofHours(1), "LOW", "IN_PROGRESS");
        seed(userA, "in-8d", Duration.ofDays(8), "LOW", "TODO");
        seed(userA, "past", Duration.ofMinutes(-5), "LOW", "TODO");
        seed(userA, "done-soon", Duration.ofDays(1), "LOW", "COMPLETED");
        seed(userA, "in-2d", Duration.ofDays(2), "LOW", "TODO");

        assertThat(titles(ok("/upcoming", tokenA))).containsExactly("in-1h", "in-2d", "in-5d");
    }

    @Test
    void overdue_lists_open_tasks_most_overdue_first() throws Exception {
        seed(userA, "late-1d", Duration.ofDays(-1), "LOW", "TODO");
        seed(userA, "late-9d", Duration.ofDays(-9), "LOW", "IN_PROGRESS");
        seed(userA, "late-done", Duration.ofDays(-4), "LOW", "COMPLETED");
        seed(userA, "future", Duration.ofDays(1), "LOW", "TODO");
        seed(userA, "late-3h", Duration.ofHours(-3), "LOW", "TODO");

        JsonNode overdue = ok("/overdue", tokenA);
        assertThat(titles(overdue)).containsExactly("late-9d", "late-1d", "late-3h");
        overdue.forEach(n -> assertThat(n.get("deadlineCategory").asText()).isEqualTo("OVERDUE"));
    }

    @Test
    void caps_of_5_and_50_are_respected() throws Exception {
        for (int i = 0; i < 60; i++) {
            seed(userA, "late-" + i, Duration.ofHours(-1 - i), "LOW", "TODO");
            seed(userA, "soon-" + i, Duration.ofMinutes(10 + i), "LOW", "TODO");
        }
        JsonNode summary = ok("/summary", tokenA);
        assertThat(summary.get("counts").get("total").asInt()).isEqualTo(120);
        assertThat(summary.get("counts").get("overdue").asInt()).isEqualTo(60);
        assertThat(summary.get("counts").get("upcoming").asInt()).isEqualTo(60);
        assertThat(summary.get("dueSoon")).hasSize(5);
        assertThat(summary.get("topPriority")).hasSize(5);
        assertThat(ok("/upcoming", tokenA)).hasSize(50);
        assertThat(ok("/overdue", tokenA)).hasSize(50);
        assertThat(titles(ok("/overdue", tokenA)).get(0)).isEqualTo("late-59");
        assertThat(titles(ok("/upcoming", tokenA)).get(0)).isEqualTo("soon-0");
    }

    @Test
    void top_priority_orders_by_rank_then_earliest_deadline_and_skips_completed() throws Exception {
        seed(userA, "low-early", Duration.ofDays(1), "LOW", "TODO");
        seed(userA, "high-late", Duration.ofDays(9), "HIGH", "TODO");
        seed(userA, "high-early", Duration.ofDays(2), "HIGH", "IN_PROGRESS");
        seed(userA, "urgent-done", Duration.ofDays(1), "URGENT", "COMPLETED");
        seed(userA, "medium", Duration.ofDays(1), "MEDIUM", "TODO");

        assertThat(titles(ok("/summary", tokenA).get("topPriority")))
                .containsExactly("high-early", "high-late", "medium", "low-early");
    }

    @Test
    void user_with_invalid_timezone_falls_back_and_still_gets_a_category() throws Exception {
        long odd = insertUser("Not/AZone");
        String token = jwt.issue(odd, "odd@example.test");
        seed(odd, "x", Duration.ofDays(-2), "LOW", "TODO");

        JsonNode overdue = ok("/overdue", token);
        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).get("deadlineCategory").asText()).isEqualTo("OVERDUE");
    }

    @Test
    void task_responses_carry_deadline_category_on_get_and_list_and_null_when_completed() throws Exception {
        long open = seed(userA, "open", Duration.ofDays(30), "LOW", "TODO");
        long done = seed(userA, "done", Duration.ofDays(30), "LOW", "COMPLETED");
        long late = seed(userA, "late", Duration.ofDays(-2), "LOW", "TODO");

        JsonNode openTask = taskGet(open);
        assertThat(openTask.get("deadlineCategory").asText()).isEqualTo("LATER");
        assertThat(taskGet(done).get("deadlineCategory").isNull()).isTrue();
        assertThat(taskGet(late).get("deadlineCategory").asText()).isEqualTo("OVERDUE");

        MvcResult list = mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + tokenA)).andReturn();
        JsonNode rows = json.readTree(list.getResponse().getContentAsString()).get("data");
        assertThat(rows).hasSize(3);
        rows.forEach(n -> {
            assertThat(n.has("deadlineCategory")).isTrue();
            String title = n.get("title").asText();
            assertThat(n.get("deadlineCategory").isNull()).isEqualTo(title.equals("done"));
        });
    }

    private JsonNode taskGet(long id) throws Exception {
        MvcResult result = mvc.perform(get("/api/tasks/" + id).header("Authorization", "Bearer " + tokenA))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @Test
    void unauthenticated_requests_get_401() throws Exception {
        for (String path : new String[] {"/summary", "/upcoming", "/overdue"}) {
            MvcResult result = call(path, null);
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(401);
            assertThat(json.readTree(result.getResponse().getContentAsString()).at("/error/code").asText())
                    .isEqualTo("UNAUTHENTICATED");
        }
    }
}
