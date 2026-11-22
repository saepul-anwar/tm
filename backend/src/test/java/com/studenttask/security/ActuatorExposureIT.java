package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Only /actuator/health is reachable, and it reveals nothing beyond the status. */
@AutoConfigureMockMvc
class ActuatorExposureIT extends MySqlTestBase {

    private static final List<String> SENSITIVE = List.of("env", "beans", "heapdump", "metrics", "info", "loggers",
            "threaddump", "mappings", "configprops", "prometheus", "conditions", "caches", "scheduledtasks",
            "flyway", "liquibase", "auditevents", "httpexchanges", "sessions", "shutdown", "startup", "quartz",
            "env/app.jwt.secret", "metrics/jvm.memory.used", "loggers/ROOT", "health/db", "health/diskSpace",
            "health/liveness", "health/readiness", "health/ping", "../actuator/env", "env;a=b", "ENV", "Env/");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @Test
    void health_is_public_and_shows_status_only() throws Exception {
        MvcResult result = mvc.perform(get("/actuator/health")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.fieldNames()).toIterable().containsExactly("status");
        assertThat(body.get("status").asText()).isEqualTo("UP");
    }

    @Test
    void health_shows_no_details_even_to_an_authenticated_user() throws Exception {
        String token = new SecurityFixtures(jdbc, jwt).user(false).token();
        JsonNode body = json.readTree(mvc.perform(get("/actuator/health").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString());
        assertThat(body.fieldNames()).toIterable().containsExactly("status");
    }

    @Test
    void sensitive_actuator_endpoints_are_not_reachable_unauthenticated() throws Exception {
        for (String endpoint : SENSITIVE) {
            MvcResult result = mvc.perform(get("/actuator/" + endpoint)).andReturn();
            assertThat(result.getResponse().getStatus()).as(endpoint).isIn(400, 401, 404);
            assertThat(result.getResponse().getContentAsString()).as(endpoint)
                    .doesNotContain("jdbc:", "password", "activeProfiles", "propertySources");
        }
        assertThat(mvc.perform(get("/actuator")).andReturn().getResponse().getStatus()).isIn(401, 404);
        assertThat(mvc.perform(get("/actuator/")).andReturn().getResponse().getStatus()).isIn(401, 404);
        assertThat(mvc.perform(post("/actuator/shutdown")).andReturn().getResponse().getStatus()).isIn(401, 404, 405);
        assertThat(mvc.perform(post("/actuator/loggers/ROOT").contentType("application/json")
                .content("{\"configuredLevel\":\"TRACE\"}")).andReturn().getResponse().getStatus()).isIn(401, 404, 405);
    }

    @Test
    void sensitive_actuator_endpoints_are_not_exposed_at_all_even_with_a_valid_token() throws Exception {
        String token = new SecurityFixtures(jdbc, jwt).user(false).token();
        for (String endpoint : SENSITIVE) {
            MvcResult result = mvc.perform(get("/actuator/" + endpoint).header("Authorization", "Bearer " + token))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as(endpoint).isIn(400, 404);
            assertThat(result.getResponse().getContentAsString()).as(endpoint)
                    .doesNotContain("jdbc:", "password", "activeProfiles", "propertySources");
        }
    }

    @Test
    void only_health_is_exposed_by_configuration() {
        assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.exclude")).isNull();
    }

    @Autowired
    ApplicationContext context;
}
