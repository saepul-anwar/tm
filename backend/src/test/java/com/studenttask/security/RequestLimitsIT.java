package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import com.studenttask.security.SecurityFixtures.Account;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real embedded Tomcat (MockMvc skips the container limits): body size and header size limits. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.reminder.enabled=false", "app.reminder.send-pause=PT0S"})
class RequestLimitsIT extends MySqlTestBase {

    private static final String TASK_JSON =
            "{\"title\":\"%s\",\"description\":\"%s\",\"deadline\":\"2030-01-01T00:00:00Z\",\"priority\":\"LOW\"}";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    private final HttpClient client = HttpClient.newHttpClient();
    private Account user;

    @BeforeEach
    void user() {
        user = new SecurityFixtures(jdbc, jwt).user(false);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + user.token()).header("Content-Type", "application/json");
    }

    private int tasks() {
        return jdbc.queryForObject("select count(*) from tasks where user_id = ?", Integer.class, user.id());
    }

    @Test
    void normal_sized_task_is_accepted() throws Exception {
        HttpResponse<String> response = client.send(request("/api/tasks")
                .POST(BodyPublishers.ofString(TASK_JSON.formatted("Tugas", "d".repeat(5000)))).build(),
                BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void body_with_declared_length_over_the_limit_is_413_envelope_and_nothing_is_stored() throws Exception {
        String huge = TASK_JSON.formatted("Tugas", "d".repeat(1_000_000));
        HttpResponse<String> response = client.send(
                request("/api/tasks").POST(BodyPublishers.ofString(huge)).build(), BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"success\":false").contains("PAYLOAD_TOO_LARGE")
                .doesNotContain("Exception", "\tat ", "org.apache");
        assertThat(tasks()).isZero();
    }

    @Test
    void oversized_body_is_rejected_before_authentication_is_even_looked_at() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/auth/login")).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"email\":\"" + "a".repeat(500_000) + "\"}")).build(),
                BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void chunked_body_over_the_limit_is_cut_off_and_never_stored() throws Exception {
        byte[] huge = TASK_JSON.formatted("Tugas", "d".repeat(1_000_000)).getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = client.send(request("/api/tasks")
                .POST(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(huge))).build(),
                BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(400, 413);
        assertThat(response.body()).doesNotContain("Exception", "\tat ", "org.apache");
        assertThat(tasks()).isZero();
    }

    @Test
    void multipart_upload_is_not_supported() throws Exception {
        HttpResponse<String> response = client.send(request("/api/tasks")
                .header("Content-Type", "multipart/form-data; boundary=x")
                .POST(BodyPublishers.ofString("--x\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\nb\r\n--x--\r\n"))
                .build(), BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(400, 415);
        assertThat(response.body()).doesNotContain("Exception", "\tat ");
    }

    @Test
    void oversized_request_header_is_refused_by_the_container_without_reaching_the_app() throws Exception {
        HttpRequest request = request("/api/tasks").header("X-Padding", "p".repeat(64 * 1024)).GET().build();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            assertThat(response.statusCode()).isIn(400, 431);
            assertThat(response.body()).doesNotContain("Exception", "\tat ");
        } catch (IOException connectionClosed) {
            // Tomcat may also close the connection for an oversized header: equally a refusal.
            assertThat(connectionClosed).isNotNull();
        }
        assertThat(tasks()).isZero();
    }
}
