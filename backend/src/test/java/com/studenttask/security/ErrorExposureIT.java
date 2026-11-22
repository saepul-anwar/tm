package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import com.studenttask.task.service.TaskService;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Error bodies must be the generic envelope: no stack traces, class names, SQL, framework names or file paths. */
@AutoConfigureMockMvc
class ErrorExposureIT extends MySqlTestBase {

    private static final Pattern LEAK = Pattern.compile(
            "(?i)(\\bat [a-z0-9_.$]+\\(|\\.java|\\.class|stack ?trace|\"trace\"|exception|\\berror:|org\\.springframework|"
                    + "com\\.studenttask|java\\.|javax\\.|jakarta\\.|hibernate|mysql|jdbc|\\bselect\\b.+\\bfrom\\b|"
                    + "\\binsert into\\b|\\bsql\\b|/users/|/var/|/home/|c:\\\\|whitelabel|apache|tomcat|jackson|"
                    + "\\bnull pointer|nullpointer|\\bat line\\b|JsonParseException|\\bsource:)");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @MockitoSpyBean
    TaskService taskService;

    private String bearer;

    @BeforeEach
    void user() {
        bearer = "Bearer " + new SecurityFixtures(jdbc, jwt).user(false).token();
    }

    private void assertEnvelope(MvcResult result, int status, String code) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        JsonNode node = json.readTree(body);
        assertThat(node.path("success").asBoolean(true)).isFalse();
        assertThat(node.at("/error/code").asText()).isEqualTo(code);
        assertThat(node.at("/error/message").asText()).isNotBlank();
        assertThat(LEAK.matcher(body).find()).as("body leaks internals: " + body).isFalse();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", bearer);
    }

    @Test
    void status_400_validation_and_malformed_bodies() throws Exception {
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json").content("{"))
                .andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json").content(""))
                .andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json").content("{}"))
                .andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json")
                .content("{\"title\":\"a\",\"deadline\":\"not-a-date\",\"priority\":\"LOW\"}")).andReturn(),
                400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json")
                .content("{\"title\":\"a\",\"deadline\":\"2030-01-01T00:00:00Z\",\"priority\":\"NOPE\"}")).andReturn(),
                400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json")
                .content("{\"title\":[1,2],\"deadline\":\"2030-01-01T00:00:00Z\",\"priority\":\"LOW\"}")).andReturn(),
                400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json")
                .content("[".repeat(5000))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/json")
                .content(new byte[] {'{', '"', (byte) 0xC3, (byte) 0x28, '"', ':', '1', '}'})).andReturn(),
                400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(get("/api/tasks/abc"))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(get("/api/tasks/99999999999999999999999"))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(get("/api/tasks?page=-1"))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(get("/api/tasks?size=100000"))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(get("/api/tasks?page=abc"))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(get("/api/tasks?status=BOGUS"))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(authed(get("/api/calendar/tasks"))).andReturn(), 400, "VALIDATION_ERROR");
        assertEnvelope(mvc.perform(post("/api/auth/login").with(SecurityFixtures.freshIp())
                .contentType("application/json").content("{\"email\":1}")).andReturn(), 400, "VALIDATION_ERROR");
    }

    @Test
    void status_401_for_missing_bad_and_expired_credentials() throws Exception {
        assertEnvelope(mvc.perform(get("/api/tasks")).andReturn(), 401, "UNAUTHENTICATED");
        assertEnvelope(mvc.perform(get("/api/tasks").header("Authorization", "Bearer x.y.z")).andReturn(), 401,
                "UNAUTHENTICATED");
        assertEnvelope(mvc.perform(post("/api/auth/refresh").with(SecurityFixtures.freshIp())).andReturn(), 401,
                "UNAUTHENTICATED");
        assertEnvelope(mvc.perform(post("/api/auth/login").with(SecurityFixtures.freshIp())
                .contentType("application/json")
                .content("{\"email\":\"nobody@example.test\",\"password\":\"Passw0rd-Synthetic1\"}")).andReturn(), 401,
                "UNAUTHENTICATED");
        assertEnvelope(mvc.perform(post("/api/telegram/webhook").contentType("application/json").content("{}"))
                .andReturn(), 401, "UNAUTHENTICATED");
    }

    @Test
    void status_403_for_a_foreign_origin_on_cookie_endpoints() throws Exception {
        assertEnvelope(mvc.perform(post("/api/auth/refresh").with(SecurityFixtures.freshIp())
                .header("Origin", "http://evil.example")).andReturn(), 403, "FORBIDDEN");
        assertEnvelope(mvc.perform(post("/api/auth/logout").with(SecurityFixtures.freshIp())
                .header("Origin", "http://evil.example")).andReturn(), 403, "FORBIDDEN");
    }

    @Test
    void status_404_for_missing_tasks_and_unknown_routes() throws Exception {
        assertEnvelope(mvc.perform(authed(get("/api/tasks/987654321"))).andReturn(), 404, "NOT_FOUND");
        assertEnvelope(mvc.perform(authed(get("/api/does-not-exist"))).andReturn(), 404, "NOT_FOUND");
        assertEnvelope(mvc.perform(authed(get("/"))).andReturn(), 404, "NOT_FOUND");
        assertEnvelope(mvc.perform(authed(get("/api/tasks/1/unknown"))).andReturn(), 404, "NOT_FOUND");
        assertEnvelope(mvc.perform(authed(get("/WEB-INF/web.xml"))).andReturn(), 404, "NOT_FOUND");
        assertEnvelope(mvc.perform(authed(get("/.env"))).andReturn(), 404, "NOT_FOUND");
        assertEnvelope(mvc.perform(authed(get("/actuator/env"))).andReturn(), 404, "NOT_FOUND");
    }

    @Test
    void the_container_error_route_leaks_nothing_even_when_called_directly_with_a_token() throws Exception {
        // Observation: GET /error (authenticated) answers Boot's default JSON ({timestamp,status,error}), not the
        // envelope. It is only a fallback for container-level failures; it carries no trace, message or path.
        MvcResult result = mvc.perform(authed(get("/error"))).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("trace", "message", "path", "exception");
        assertThat(LEAK.matcher(result.getResponse().getContentAsString()).find()).isFalse();
    }

    @Test
    void unknown_routes_without_a_token_give_the_401_envelope_not_a_framework_page() throws Exception {
        for (String path : List.of("/nope", "/api/nope", "/error", "/WEB-INF/web.xml", "/h2-console", "/swagger-ui.html",
                "/v3/api-docs", "/api-docs", "/.git/config", "/static/x.js", "/favicon.ico", "/index.html")) {
            assertEnvelope(mvc.perform(get(path)).andReturn(), 401, "UNAUTHENTICATED");
        }
    }

    @Test
    void status_405_and_415_and_406() throws Exception {
        assertEnvelope(mvc.perform(authed(delete("/api/tasks"))).andReturn(), 405, "METHOD_NOT_ALLOWED");
        assertEnvelope(mvc.perform(authed(put("/api/dashboard/summary"))).andReturn(), 405, "METHOD_NOT_ALLOWED");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("text/plain").content("hello")).andReturn(),
                415, "UNSUPPORTED_MEDIA_TYPE");
        assertEnvelope(mvc.perform(authed(post("/api/tasks")).contentType("application/xml")
                .content("<task><title>a</title></task>")).andReturn(), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertEnvelope(mvc.perform(authed(post("/api/tasks"))).andReturn(), 400, "VALIDATION_ERROR"); // no body, no type
        // Documented: an unsatisfiable Accept header gets a bare 406 (the envelope itself cannot be rendered as XML).
        MvcResult notAcceptable = mvc.perform(authed(get("/api/tasks")).accept("application/xml")).andReturn();
        assertThat(notAcceptable.getResponse().getStatus()).isEqualTo(406);
        assertThat(LEAK.matcher(notAcceptable.getResponse().getContentAsString()).find()).isFalse();
    }

    @Test
    void status_429_when_the_login_limit_is_exceeded() throws Exception {
        org.springframework.test.web.servlet.request.RequestPostProcessor sameClient = SecurityFixtures.freshIp();
        MvcResult last = null;
        for (int i = 0; i < 11; i++) {
            last = mvc.perform(post("/api/auth/login").with(sameClient).contentType("application/json")
                    .content("{\"email\":\"nobody@example.test\",\"password\":\"Passw0rd-Synthetic1\"}")).andReturn();
        }
        assertEnvelope(last, 429, "RATE_LIMITED");
        assertThat(last.getResponse().getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void status_500_from_a_database_failure_is_generic_and_leaks_nothing() throws Exception {
        doThrow(new org.springframework.jdbc.UncategorizedSQLException("StatementCallback", "select * from tasks where "
                + "secret_column = 'x'", new SQLException("Table 'studenttask_test.tasks' doesn't exist at /var/lib/mysql")))
                .when(taskService).get(anyLong(), eq(666L));
        doThrow(new IllegalStateException("password=hunter2 at com.studenttask.task.service.TaskService.java:42"))
                .when(taskService).get(anyLong(), eq(667L));

        for (String id : List.of("666", "667")) {
            MvcResult result = mvc.perform(authed(get("/api/tasks/" + id))).andReturn();
            assertEnvelope(result, 500, "INTERNAL_ERROR");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("hunter2", "secret_column", "/var/lib");
        }
    }

    @Test
    void error_responses_never_set_a_session_cookie_or_reveal_the_server() throws Exception {
        MvcResult result = mvc.perform(get("/api/tasks")).andReturn();
        assertThat(result.getResponse().getCookies()).isEmpty();
        assertThat(result.getResponse().getHeader("Server")).isNull();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("JSESSIONID");
    }
}
