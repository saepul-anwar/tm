package com.studenttask.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
class TaskListIT extends MySqlTestBase {

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
        String email = "list-" + UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users (full_name,email,password_hash,timezone,created_at,updated_at) "
                + "values ('Synthetic User', ?, 'x', 'Asia/Jakarta', now(3), now(3))", email);
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long create(String token, String title, String subject, String deadline, String priority,
            String status) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("subject", subject);
        payload.put("deadline", deadline);
        payload.put("priority", priority);
        payload.put("status", status);
        MvcResult result = mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(payload))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return read(result).get("data").get("id").asLong();
    }

    private long create(String title, String subject, String deadline, String priority, String status)
            throws Exception {
        return create(tokenA, title, subject, deadline, priority, status);
    }

    private MvcResult list(String token, String query) throws Exception {
        MockHttpServletRequestBuilder builder = get(java.net.URI.create("/api/tasks" + query));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder).andReturn();
    }

    private JsonNode listOk(String query) throws Exception {
        MvcResult result = list(tokenA, query);
        assertThat(result.getResponse().getStatus()).as(query).isEqualTo(200);
        return read(result);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> titles(JsonNode body) {
        List<String> out = new ArrayList<>();
        body.get("data").forEach(n -> out.add(n.get("title").asText()));
        return out;
    }

    private static List<Long> ids(JsonNode body) {
        List<Long> out = new ArrayList<>();
        body.get("data").forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    private void assertValidationError(String query) throws Exception {
        MvcResult result = list(tokenA, query);
        assertThat(result.getResponse().getStatus()).as(query).isEqualTo(400);
        assertThat(read(result).path("error").path("code").asText()).as(query).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void filters_by_each_field_and_combinations() throws Exception {
        create("Laporan Fisika", "Fisika", "2026-12-01T10:00:00Z", "HIGH", "TODO");
        create("Soal Kalkulus", "Kalkulus", "2026-12-02T10:00:00Z", "LOW", "IN_PROGRESS");
        create("Esai Sejarah", "Sejarah", "2026-12-03T10:00:00Z", "URGENT", "COMPLETED");
        create("Laporan Kalkulus", "Kalkulus", "2026-12-04T10:00:00Z", "HIGH", "TODO");

        assertThat(titles(listOk("?q=laporan"))).containsExactly("Laporan Fisika", "Laporan Kalkulus");
        assertThat(titles(listOk("?subject=Kalkulus"))).containsExactly("Soal Kalkulus", "Laporan Kalkulus");
        assertThat(titles(listOk("?status=COMPLETED"))).containsExactly("Esai Sejarah");
        assertThat(titles(listOk("?priority=HIGH"))).containsExactly("Laporan Fisika", "Laporan Kalkulus");
        assertThat(titles(listOk("?deadlineFrom=2026-12-02T00:00:00Z&deadlineTo=2026-12-04T00:00:00Z")))
                .containsExactly("Soal Kalkulus", "Esai Sejarah");
        assertThat(titles(listOk("?q=laporan&subject=Kalkulus&status=TODO&priority=HIGH"
                + "&deadlineFrom=2026-12-01T00:00:00Z&deadlineTo=2026-12-31T00:00:00Z")))
                .containsExactly("Laporan Kalkulus");
        assertThat(titles(listOk("?q=laporan&status=COMPLETED"))).isEmpty();
        assertThat(listOk("").get("meta").get("totalElements").asLong()).isEqualTo(4);
    }

    @Test
    void deadline_from_is_inclusive_and_to_is_exclusive() throws Exception {
        create("Awal", null, "2026-12-01T00:00:00Z", "LOW", "TODO");
        create("Tengah", null, "2026-12-01T12:00:00Z", "LOW", "TODO");
        create("Akhir", null, "2026-12-02T00:00:00Z", "LOW", "TODO");

        assertThat(titles(listOk("?deadlineFrom=2026-12-01T00:00:00Z&deadlineTo=2026-12-02T00:00:00Z")))
                .containsExactly("Awal", "Tengah");
        assertThat(titles(listOk("?deadlineFrom=2026-12-02T00:00:00Z"))).containsExactly("Akhir");
        assertThat(titles(listOk("?deadlineTo=2026-12-01T00:00:00Z"))).isEmpty();
        // an offset form is accepted and converted to the same instant (encoded + as %2B)
        assertThat(titles(listOk("?deadlineFrom=2026-12-01T07:00:00%2B07:00&deadlineTo=2026-12-01T12:00:00Z")))
                .containsExactly("Awal");
    }

    @Test
    void search_percent_and_underscore_are_literal() throws Exception {
        create("Diskon 100% lunas", null, "2026-12-01T10:00:00Z", "LOW", "TODO");
        create("Diskon 100 lunas", null, "2026-12-02T10:00:00Z", "LOW", "TODO");
        create("file_a", null, "2026-12-03T10:00:00Z", "LOW", "TODO");
        create("fileXa", null, "2026-12-04T10:00:00Z", "LOW", "TODO");
        create("path\\to", null, "2026-12-05T10:00:00Z", "LOW", "TODO");
        create("pathXto", null, "2026-12-06T10:00:00Z", "LOW", "TODO");

        assertThat(titles(listOk("?q=100%25"))).containsExactly("Diskon 100% lunas");
        assertThat(titles(listOk("?q=%25"))).containsExactly("Diskon 100% lunas");
        assertThat(titles(listOk("?q=_"))).containsExactly("file_a");
        assertThat(titles(listOk("?q=file_a"))).containsExactly("file_a");
        assertThat(titles(listOk("?q=path%5Cto"))).containsExactly("path\\to");
    }

    @Test
    void search_is_case_insensitive() throws Exception {
        create("Laporan PRAKTIKUM", null, "2026-12-01T10:00:00Z", "LOW", "TODO");
        assertThat(titles(listOk("?q=praktikum"))).containsExactly("Laporan PRAKTIKUM");
        assertThat(titles(listOk("?q=LAPORAN%20prak"))).containsExactly("Laporan PRAKTIKUM");
    }

    @Test
    void title_with_emoji_and_script_tag_roundtrips_verbatim() throws Exception {
        String title = "Tugas 🚀 <script>alert(1)</script>";
        String subject = "Mata Kuliah 📚 <b>x</b>";
        long id = create(title, subject, "2026-12-01T10:00:00Z", "LOW", "TODO");

        JsonNode body = listOk("?q=%3Cscript%3E");
        assertThat(ids(body)).containsExactly(id);
        assertThat(body.get("data").get(0).get("title").asText()).isEqualTo(title);
        assertThat(body.get("data").get(0).get("subject").asText()).isEqualTo(subject);
        assertThat(titles(listOk("?q=%F0%9F%9A%80"))).containsExactly(title);
    }

    @Test
    void sql_metacharacters_in_q_and_subject_are_inert() throws Exception {
        create("Tugas biasa", "Fisika", "2026-12-01T10:00:00Z", "LOW", "TODO");
        String evil = "%27%20OR%201%3D1%3B%20DROP%20TABLE%20tasks%3B%20--";

        assertThat(titles(listOk("?q=" + evil))).isEmpty();
        assertThat(titles(listOk("?subject=" + evil))).isEmpty();
        assertThat(titles(listOk("?q=%27"))).isEmpty();
        assertThat(titles(listOk(""))).containsExactly("Tugas biasa");
        assertThat(jdbc.queryForObject("select count(*) from tasks where user_id = ?", Integer.class, userA))
                .isEqualTo(1);
    }

    @Test
    void sort_by_priority_uses_urgent_first_then_deadline() throws Exception {
        create("low-early", null, "2026-12-01T10:00:00Z", "LOW", "TODO");
        create("urgent-late", null, "2026-12-09T10:00:00Z", "URGENT", "TODO");
        create("urgent-early", null, "2026-12-02T10:00:00Z", "URGENT", "TODO");
        create("high", null, "2026-12-03T10:00:00Z", "HIGH", "TODO");
        create("medium", null, "2026-12-04T10:00:00Z", "MEDIUM", "TODO");

        assertThat(titles(listOk("?sort=priority&dir=desc")))
                .containsExactly("urgent-early", "urgent-late", "high", "medium", "low-early");
        assertThat(titles(listOk("?sort=priority&dir=asc")))
                .containsExactly("low-early", "medium", "high", "urgent-early", "urgent-late");
    }

    @Test
    void default_sort_is_deadline_ascending_and_dir_desc_reverses() throws Exception {
        create("b", null, "2026-12-02T10:00:00Z", "LOW", "TODO");
        create("a", null, "2026-12-01T10:00:00Z", "LOW", "TODO");
        create("c", null, "2026-12-03T10:00:00Z", "LOW", "TODO");

        assertThat(titles(listOk(""))).containsExactly("a", "b", "c");
        assertThat(titles(listOk("?sort=deadline&dir=desc"))).containsExactly("c", "b", "a");
        assertThat(titles(listOk("?sort=createdAt&dir=desc"))).containsExactly("c", "a", "b");
    }

    @Test
    void completed_page_query_sorts_by_completed_at_desc() throws Exception {
        long first = create("selesai-1", null, "2026-12-01T10:00:00Z", "LOW", "COMPLETED");
        long second = create("selesai-2", null, "2026-12-02T10:00:00Z", "LOW", "COMPLETED");
        long third = create("selesai-3", null, "2026-12-03T10:00:00Z", "LOW", "COMPLETED");
        create("belum", null, "2026-12-04T10:00:00Z", "LOW", "TODO");
        jdbc.update("update tasks set completed_at = '2026-11-01 08:00:00.000' where id = ?", second);
        jdbc.update("update tasks set completed_at = '2026-11-03 08:00:00.000' where id = ?", first);
        jdbc.update("update tasks set completed_at = '2026-11-02 08:00:00.000' where id = ?", third);

        JsonNode body = listOk("?status=COMPLETED&sort=completedAt&dir=desc");
        assertThat(titles(body)).containsExactly("selesai-1", "selesai-3", "selesai-2");
        assertThat(body.get("meta").get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void size_101_returns_400_and_default_size_is_10() throws Exception {
        for (int i = 0; i < 12; i++) {
            create("t" + i, null, "2026-12-01T10:%02d:00Z".formatted(i), "LOW", "TODO");
        }

        assertValidationError("?size=101");
        assertValidationError("?size=51");
        assertValidationError("?size=0");
        assertValidationError("?size=-1");
        assertValidationError("?size=abc");
        assertValidationError("?page=-1");
        assertValidationError("?page=abc");

        JsonNode defaults = listOk("");
        assertThat(defaults.get("data")).hasSize(10);
        assertThat(defaults.get("meta").get("size").asInt()).isEqualTo(10);
        assertThat(defaults.get("meta").get("page").asInt()).isZero();
        assertThat(defaults.get("meta").get("totalElements").asLong()).isEqualTo(12);
        assertThat(defaults.get("meta").get("totalPages").asInt()).isEqualTo(2);
        assertThat(listOk("?size=50").get("data")).hasSize(12);
        assertThat(listOk("?size=1").get("data")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?sort=title", "?sort=deadline;drop", "?sort=DEADLINE", "?sort=priority_rank",
            "?sort=", "?dir=up", "?dir=ASC", "?status=DONE", "?status=todo", "?priority=CRITICAL",
            "?deadlineFrom=garbage", "?deadlineTo=2026-12-01"})
    void invalid_sort_dir_enum_or_date_returns_400(String query) throws Exception {
        assertValidationError(query);
    }

    @Test
    void page_beyond_last_returns_empty_data_with_correct_meta() throws Exception {
        for (int i = 0; i < 3; i++) {
            create("t" + i, null, "2026-12-01T10:0%d:00Z".formatted(i), "LOW", "TODO");
        }
        JsonNode body = listOk("?page=5&size=2");
        assertThat(body.get("data")).isEmpty();
        assertThat(body.get("meta").get("page").asInt()).isEqualTo(5);
        assertThat(body.get("meta").get("size").asInt()).isEqualTo(2);
        assertThat(body.get("meta").get("totalElements").asLong()).isEqualTo(3);
        assertThat(body.get("meta").get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void paging_is_stable_across_pages_with_equal_deadlines_and_priorities() throws Exception {
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            created.add(create("same-" + i, null, "2026-12-01T10:00:00Z", "HIGH", "TODO"));
        }
        for (String sort : new String[] {"deadline", "priority", "createdAt"}) {
            List<Long> seen = new ArrayList<>();
            for (int page = 0; page < 3; page++) {
                seen.addAll(ids(listOk("?sort=" + sort + "&dir=desc&size=3&page=" + page)));
            }
            assertThat(seen).as(sort).containsExactlyInAnyOrderElementsOf(created).doesNotHaveDuplicates();
            assertThat(seen).as(sort).isEqualTo(ids(listOk("?sort=" + sort + "&dir=desc&size=50")));
        }
    }

    @Test
    void list_only_returns_own_tasks() throws Exception {
        create("milik-a", "Fisika", "2026-12-01T10:00:00Z", "LOW", "TODO");
        create(tokenB, "milik-b", "Fisika", "2026-12-01T10:00:00Z", "LOW", "TODO");

        assertThat(titles(listOk(""))).containsExactly("milik-a");
        assertThat(titles(listOk("?q=milik-b"))).isEmpty();
        JsonNode bodyB = read(list(tokenB, ""));
        assertThat(titles(bodyB)).containsExactly("milik-b");
        assertThat(bodyB.get("meta").get("totalElements").asLong()).isEqualTo(1);
        // a userId parameter is ignored: identity only comes from the token
        assertThat(titles(listOk("?userId=" + userB))).containsExactly("milik-a");
    }

    @Test
    void subjects_returns_distinct_sorted_own_subjects() throws Exception {
        create("1", "Kalkulus", "2026-12-01T10:00:00Z", "LOW", "TODO");
        create("2", "Fisika", "2026-12-01T10:00:00Z", "LOW", "TODO");
        create("3", "Kalkulus", "2026-12-01T10:00:00Z", "LOW", "TODO");
        create("4", null, "2026-12-01T10:00:00Z", "LOW", "TODO");
        create("5", "Biologi", "2026-12-01T10:00:00Z", "LOW", "COMPLETED");
        create(tokenB, "6", "Rahasia-B", "2026-12-01T10:00:00Z", "LOW", "TODO");

        MvcResult result = mvc.perform(get("/api/tasks/subjects").header("Authorization", "Bearer " + tokenA))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = read(result).get("data");
        List<String> subjects = new ArrayList<>();
        data.forEach(n -> subjects.add(n.asText()));
        assertThat(subjects).containsExactly("Biologi", "Fisika", "Kalkulus");
    }

    @Test
    void unauthenticated_list_and_subjects_return_401() throws Exception {
        for (String path : new String[] {"/api/tasks", "/api/tasks/subjects"}) {
            MvcResult result = mvc.perform(get(path)).andReturn();
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(401);
            assertThat(read(result).path("error").path("code").asText()).isEqualTo("UNAUTHENTICATED");
        }
    }
}
