package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.studenttask.telegram.client.TelegramClient;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Two users with real data; B must never read or change A's data, and must not learn that A's ids exist. */
@AutoConfigureMockMvc
class CrossUserAccessIT extends MySqlTestBase {

    private static final String A_MARK = "ZZ-A-PRIVATE-";
    private static final String B_MARK = "YY-B-OWN-";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    TelegramClient telegram;

    private SecurityFixtures fx;
    private Account a;
    private Account b;
    private long aTask1;
    private long aTask2;
    private long aOverdue;
    private long bTask;
    private final String aTitle1 = A_MARK + UUID.randomUUID();
    private final String aTitle2 = A_MARK + UUID.randomUUID();
    private final String aTitleOverdue = A_MARK + UUID.randomUUID();
    private final String bTitle = B_MARK + UUID.randomUUID();

    @BeforeEach
    void data() {
        fx = new SecurityFixtures(jdbc, jwt);
        a = fx.user(true);
        b = fx.user(true);
        Instant now = Instant.now();
        aTask1 = fx.task(a.id(), aTitle1, "A-SUBJECT-MATH", now.plus(2, ChronoUnit.DAYS), "TODO");
        aTask2 = fx.task(a.id(), aTitle2, "A-SUBJECT-BIO", now.plus(3, ChronoUnit.DAYS), "IN_PROGRESS");
        aOverdue = fx.task(a.id(), aTitleOverdue, "A-SUBJECT-OLD", now.minus(2, ChronoUnit.DAYS), "TODO");
        fx.settings(a.id(), false, false, false, false);
        fx.log(a.id(), aTask1);
        fx.log(a.id(), aTask2);
        bTask = fx.task(b.id(), bTitle, "B-SUBJECT", now.plus(1, ChronoUnit.DAYS), "TODO");
        fx.settings(b.id(), true, true, true, true);
    }

    private MvcResult as(Account who, MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + who.token())).andReturn();
    }

    private MvcResult as(Account who, MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return as(who, builder.contentType("application/json")
                .content(body instanceof String s ? s : json.writeValueAsString(body)));
    }

    private JsonNode read(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private static String raw(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    private Map<String, Object> taskBody(String title) {
        return Map.of("title", title, "subject", "S", "deadline", "2030-01-01T00:00:00Z", "priority", "LOW");
    }

    private void assertNoneOfAHere(String body) {
        assertThat(body).doesNotContain(A_MARK, "A-SUBJECT");
    }

    // ---- task by id: get / put / patch / delete ----

    @Test
    void user_b_gets_404_for_user_a_task_on_get_put_patch_and_delete_and_nothing_changes() throws Exception {
        for (long id : List.of(aTask1, aTask2, aOverdue)) {
            MvcResult get = as(b, get("/api/tasks/" + id));
            MvcResult put = as(b, put("/api/tasks/" + id), taskBody("HIJACKED"));
            MvcResult patch = as(b, patch("/api/tasks/" + id + "/status"), Map.of("status", "COMPLETED"));
            MvcResult del = as(b, delete("/api/tasks/" + id));
            for (MvcResult r : List.of(get, put, patch, del)) {
                assertThat(r.getResponse().getStatus()).isEqualTo(404);
                assertThat(read(r).at("/error/code").asText()).isEqualTo("NOT_FOUND");
                assertNoneOfAHere(raw(r));
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from tasks where user_id = ?", Integer.class, a.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, aTask1)).isEqualTo(aTitle1);
        assertThat(jdbc.queryForObject("select status from tasks where id = ?", String.class, aTask1)).isEqualTo("TODO");
        assertThat(fx.logs(a.id())).as("A's notification logs untouched").isEqualTo(2);
    }

    @Test
    void other_users_task_and_missing_task_are_indistinguishable() throws Exception {
        long missing = aOverdue + 1_000_000;
        List<MockHttpServletRequestBuilder[]> pairs = List.of(
                new MockHttpServletRequestBuilder[] {get("/api/tasks/" + aTask1), get("/api/tasks/" + missing)},
                new MockHttpServletRequestBuilder[] {put("/api/tasks/" + aTask1).contentType("application/json")
                        .content(json.writeValueAsString(taskBody("X"))), put("/api/tasks/" + missing)
                        .contentType("application/json").content(json.writeValueAsString(taskBody("X")))},
                new MockHttpServletRequestBuilder[] {
                    patch("/api/tasks/" + aTask1 + "/status").contentType("application/json").content("{\"status\":\"TODO\"}"),
                    patch("/api/tasks/" + missing + "/status").contentType("application/json").content("{\"status\":\"TODO\"}")},
                new MockHttpServletRequestBuilder[] {delete("/api/tasks/" + aTask1), delete("/api/tasks/" + missing)});
        for (MockHttpServletRequestBuilder[] pair : pairs) {
            MvcResult other = as(b, pair[0]);
            MvcResult none = as(b, pair[1]);
            assertThat(other.getResponse().getStatus()).isEqualTo(none.getResponse().getStatus());
            assertThat(raw(other)).isEqualTo(raw(none));
            assertThat(other.getResponse().getContentType()).isEqualTo(none.getResponse().getContentType());
        }
    }

    @Test
    void sequential_id_enumeration_by_b_only_ever_returns_b_own_task() throws Exception {
        long low = Math.min(aTask1, bTask) - 10;
        long high = Math.max(aOverdue, bTask) + 10;
        List<Long> readable = new ArrayList<>();
        for (long id = Math.max(1, low); id <= high; id++) {
            MvcResult r = as(b, get("/api/tasks/" + id));
            assertNoneOfAHere(raw(r));
            if (r.getResponse().getStatus() == 200) {
                readable.add(id);
            } else {
                assertThat(r.getResponse().getStatus()).isEqualTo(404);
            }
        }
        assertThat(readable).containsExactly(bTask);
    }

    @Test
    void idor_path_variants_never_return_user_a_data_to_b() throws Exception {
        String id = Long.toString(aTask1);
        String encoded = id.chars().mapToObj(c -> String.format("%%%02X", c)).reduce("", String::concat);
        List<String> variants = List.of(
                "/api/tasks/" + id + "/", "/api/tasks/" + id + "//", "/api/tasks//" + id, "/api/tasks/" + encoded,
                "/api/tasks/+" + id, "/api/tasks/0" + id, "/api/tasks/" + id + ".json", "/api/tasks/" + id + "%20",
                "/api/tasks/" + id + "%00", "/api/tasks/" + id + ";x=1", "/api/tasks/./" + id,
                "/api/tasks/x/../" + id, "/api/tasks/%2e/" + id, "/api/tasks/" + id + "/status",
                "/api/TASKS/" + id, "/api/tasks/" + id + "?userId=" + a.id(), "/api/tasks/" + id + "%2F",
                "/api/tasks/" + id + "%252F", "/api/tasks/" + id + "%0d%0a");
        for (String path : variants) {
            for (MockHttpServletRequestBuilder builder : List.of(get(path), delete(path))) {
                MvcResult r = as(b, builder);
                assertThat(r.getResponse().getStatus()).as(path).isBetween(400, 499);
                assertNoneOfAHere(raw(r));
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from tasks where user_id = ?", Integer.class, a.id())).isEqualTo(3);
    }

    @Test
    void idor_via_body_ids_and_user_ids_is_ignored_on_create_and_update() throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>(taskBody("B-created-" + UUID.randomUUID()));
        body.put("id", aTask1);
        body.put("userId", a.id());
        body.put("user_id", a.id());
        body.put("user", Map.of("id", a.id()));
        MvcResult created = as(b, post("/api/tasks"), body);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long newId = read(created).at("/data/id").asLong();
        assertThat(newId).isNotEqualTo(aTask1);
        assertThat(jdbc.queryForObject("select user_id from tasks where id = ?", Long.class, newId)).isEqualTo(b.id());
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, aTask1)).isEqualTo(aTitle1);

        Map<String, Object> update = new java.util.LinkedHashMap<>(taskBody("B-updated"));
        update.put("userId", a.id());
        update.put("id", aTask1);
        MvcResult updated = as(b, put("/api/tasks/" + bTask), update);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select user_id from tasks where id = ?", Long.class, bTask)).isEqualTo(b.id());
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, aTask1)).isEqualTo(aTitle1);
    }

    // ---- lists, dashboard, calendar ----

    @Test
    void b_list_subjects_dashboard_and_calendar_never_include_user_a_data() throws Exception {
        MvcResult list = as(b, get("/api/tasks?size=50"));
        assertThat(read(list).at("/meta/totalElements").asLong()).isEqualTo(1);
        assertThat(read(list).at("/data").size()).isEqualTo(1);
        assertThat(read(list).at("/data/0/title").asText()).isEqualTo(bTitle);
        assertNoneOfAHere(raw(list));

        for (String filter : List.of("?q=" + A_MARK, "?subject=A-SUBJECT-MATH", "?status=IN_PROGRESS", "?q=ZZ&size=50",
                "?userId=" + a.id(), "?user_id=" + a.id() + "&ownerId=" + a.id())) {
            MvcResult r = as(b, get("/api/tasks" + filter));
            assertThat(r.getResponse().getStatus()).as(filter).isEqualTo(200);
            assertNoneOfAHere(raw(r));
        }
        assertThat(read(as(b, get("/api/tasks?q=" + A_MARK))).at("/meta/totalElements").asLong()).isZero();
        assertThat(read(as(b, get("/api/tasks?userId=" + a.id()))).at("/meta/totalElements").asLong())
                .as("userId parameter is ignored: still only B's one task").isEqualTo(1);

        MvcResult subjects = as(b, get("/api/tasks/subjects"));
        assertThat(read(subjects).at("/data")).hasSize(1);
        assertNoneOfAHere(raw(subjects));

        MvcResult summary = as(b, get("/api/dashboard/summary"));
        JsonNode counts = read(summary).at("/data/counts");
        assertThat(counts.at("/total").asInt()).isEqualTo(1);
        assertThat(counts.at("/overdue").asInt()).isZero();
        assertThat(counts.at("/completed").asInt()).isZero();
        assertNoneOfAHere(raw(summary));
        assertThat(raw(as(a, get("/api/dashboard/summary")))).contains(A_MARK);

        MvcResult upcoming = as(b, get("/api/dashboard/upcoming"));
        assertThat(read(upcoming).at("/data")).hasSize(1);
        assertNoneOfAHere(raw(upcoming));
        MvcResult overdue = as(b, get("/api/dashboard/overdue"));
        assertThat(read(overdue).at("/data")).isEmpty();
        assertThat(read(as(a, get("/api/dashboard/overdue"))).at("/data")).hasSize(1);

        MvcResult calendar = as(b, get("/api/calendar/tasks")
                .param("from", "2000-01-01T00:00:00Z").param("to", "2000-02-01T00:00:00Z"));
        assertThat(calendar.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(calendar).at("/data")).isEmpty();
        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        MvcResult window = as(b, get("/api/calendar/tasks")
                .param("from", from.toString()).param("to", from.plus(60, ChronoUnit.DAYS).toString()));
        assertThat(window.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(window).at("/data")).hasSize(1);
        assertNoneOfAHere(raw(window));
        MvcResult aWindow = as(a, get("/api/calendar/tasks")
                .param("from", from.toString()).param("to", from.plus(60, ChronoUnit.DAYS).toString()));
        assertThat(read(aWindow).at("/data")).as("control: A does see their own tasks").hasSize(3);
    }

    // ---- settings, profile ----

    @Test
    void b_reminder_settings_get_and_put_touch_only_b_even_with_a_ids_in_the_body() throws Exception {
        JsonNode bSettings = read(as(b, get("/api/reminders/settings"))).at("/data");
        assertThat(bSettings.at("/reminder24Hours").asBoolean()).isTrue(); // B's row, not A's all-false row

        Long aRowId = jdbc.queryForObject("select id from reminder_settings where user_id = ?", Long.class, a.id());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("reminder24Hours", false);
        body.put("reminder6Hours", false);
        body.put("reminder1Hour", false);
        body.put("reminderAtDeadline", false);
        body.put("userId", a.id());
        body.put("id", aRowId);
        body.put("user_id", a.id());
        MvcResult put = as(b, put("/api/reminders/settings"), body);
        assertThat(put.getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select reminder_24_hours from reminder_settings where user_id = ?",
                Boolean.class, b.id())).isFalse();
        // A's row was all-false from the start; flip A to all-true and prove B's PUT cannot change it back.
        jdbc.update("update reminder_settings set reminder_24_hours = true where user_id = ?", a.id());
        as(b, put("/api/reminders/settings"), body);
        assertThat(jdbc.queryForObject("select reminder_24_hours from reminder_settings where id = ?",
                Boolean.class, aRowId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from reminder_settings where user_id = ?", Integer.class, a.id()))
                .isEqualTo(1);
    }

    @Test
    void b_profile_get_and_put_touch_only_b_and_cannot_set_telegram_or_ids() throws Exception {
        JsonNode profile = read(as(b, get("/api/profile"))).at("/data");
        assertThat(profile.at("/email").asText()).isEqualTo(b.email());
        assertThat(raw(as(b, get("/api/profile")))).doesNotContain(a.email());

        Long bChatBefore = fx.chatOf(b.id());
        Long aChatBefore = fx.chatOf(a.id());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("fullName", "B Renamed");
        body.put("userId", a.id());
        body.put("id", a.id());
        body.put("telegramChatId", 42);
        body.put("telegram_chat_id", 42);
        body.put("passwordHash", "x");
        MvcResult put = as(b, put("/api/profile"), body);
        assertThat(put.getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, b.id()))
                .isEqualTo("B Renamed");
        assertThat(jdbc.queryForObject("select full_name from users where id = ?", String.class, a.id()))
                .isEqualTo("Synthetic User");
        assertThat(fx.chatOf(b.id())).isEqualTo(bChatBefore);
        assertThat(fx.chatOf(a.id())).isEqualTo(aChatBefore);
        assertThat(jdbc.queryForObject("select password_hash from users where id = ?", String.class, b.id()))
                .isEqualTo("x");
    }

    // ---- telegram ----

    @Test
    void b_cannot_disconnect_or_send_test_to_user_a_telegram_chat() throws Exception {
        Long aChat = fx.chatOf(a.id());
        assertThat(aChat).isNotNull();

        // B's own disconnect only clears B.
        MvcResult disconnect = as(b, delete("/api/telegram/disconnect"));
        assertThat(disconnect.getResponse().getStatus()).isEqualTo(204);
        assertThat(fx.chatOf(a.id())).as("A stays linked").isEqualTo(aChat);
        assertThat(fx.chatOf(b.id())).isNull();

        // B is now unlinked: /test must not fall back to any other user's chat and must not call Telegram.
        MvcResult test = as(b, post("/api/telegram/test"));
        assertThat(test.getResponse().getStatus()).isEqualTo(409);
        verifyNoInteractions(telegram);
        assertThat(raw(test)).doesNotContain(String.valueOf(aChat));

        // Connecting creates a link token for B only (no foreign id accepted).
        MvcResult connect = as(b, post("/api/telegram/connect").param("userId", String.valueOf(a.id())));
        assertThat(connect.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from telegram_link_tokens where user_id = ?", Integer.class,
                a.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from telegram_link_tokens where user_id = ?", Integer.class,
                b.id())).isEqualTo(1);
    }

    // ---- structural: nothing accepts a user id ----

    @Test
    void no_endpoint_accepts_a_user_id_in_path_query_header_or_request_body() {
        List<String> offenders = new ArrayList<>();
        for (HandlerMethod method : handlerMapping.getHandlerMethods().values()) {
            if (!method.getBeanType().getPackageName().startsWith("com.studenttask")) {
                continue;
            }
            for (Parameter p : method.getMethod().getParameters()) {
                String name = p.getName();
                if ((p.isAnnotationPresent(PathVariable.class) || p.isAnnotationPresent(RequestParam.class)
                        || p.isAnnotationPresent(RequestHeader.class)) && looksLikeUserId(name)) {
                    offenders.add(method + " param " + name);
                }
                if (p.isAnnotationPresent(RequestBody.class) && p.getType().isRecord()) {
                    for (RecordComponent c : p.getType().getRecordComponents()) {
                        if (looksLikeUserId(c.getName())) {
                            offenders.add(p.getType().getSimpleName() + "." + c.getName());
                        }
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    private static boolean looksLikeUserId(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("_", "");
        return n.equals("userid") || n.equals("ownerid") || n.equals("accountid") || n.equals("user");
    }
}
