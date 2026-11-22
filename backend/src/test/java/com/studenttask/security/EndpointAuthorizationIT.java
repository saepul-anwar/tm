package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every route the application registers is discovered from RequestMappingHandlerMapping (not hand-listed). All of
 * them must answer 401 without a valid token except an explicit, asserted allow-list of public routes.
 */
@AutoConfigureMockMvc
class EndpointAuthorizationIT extends MySqlTestBase {

    /** Deliberately public routes (METHOD path). A new public route must be added here on purpose. */
    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "POST /api/auth/register", "POST /api/auth/login", "POST /api/auth/refresh", "POST /api/auth/logout",
            "POST /api/telegram/webhook");
    private static final String PUBLIC_ACTUATOR = "/actuator/health";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private record Route(String method, String template) {
        String concretePath() {
            return template.replaceAll("\\{[^/}]+}", "1");
        }

        @Override
        public String toString() {
            return method + " " + template;
        }
    }

    private List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (methods.isEmpty()) {
                    routes.add(new Route("GET", pattern)); // method-less mapping: any method is accepted
                }
                methods.forEach(m -> routes.add(new Route(m.name(), pattern)));
            }
        }
        return routes;
    }

    private MockHttpServletRequestBuilder build(Route route) {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(route.method()), route.concretePath());
        return builder.contentType("application/json").content("{}");
    }

    private void assertUnauthenticatedEnvelope(MvcResult result, String what) throws Exception {
        assertThat(result.getResponse().getStatus()).as(what).isEqualTo(401);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("success").asBoolean(true)).as(what).isFalse();
        assertThat(body.at("/error/code").asText()).as(what).isEqualTo("UNAUTHENTICATED");
        assertThat(result.getResponse().getContentAsString()).as(what).doesNotContain("Exception", "\tat ");
    }

    @Test
    void public_allow_list_matches_the_security_configuration_and_existing_routes() {
        Set<String> configured = new TreeSet<>(List.of(SecurityConfig.PUBLIC_PATHS));
        Set<String> allowListPaths = new TreeSet<>();
        PUBLIC_ROUTES.forEach(r -> allowListPaths.add(r.substring(r.indexOf(' ') + 1)));
        allowListPaths.add(PUBLIC_ACTUATOR);
        // A route made public in SecurityConfig without being added here (or the reverse) fails this test.
        assertThat(configured).isEqualTo(allowListPaths);

        Set<String> discovered = new TreeSet<>();
        routes().forEach(r -> discovered.add(r.toString()));
        assertThat(discovered).containsAll(PUBLIC_ROUTES);
        assertThat(discovered).as("sanity: routes were really enumerated").hasSizeGreaterThan(20);
    }

    @Test
    void every_authenticated_endpoint_returns_401_without_token() throws Exception {
        int checked = 0;
        for (Route route : routes()) {
            if (PUBLIC_ROUTES.contains(route.toString())) {
                continue;
            }
            assertUnauthenticatedEnvelope(mvc.perform(build(route)).andReturn(), route.toString());
            checked++;
        }
        assertThat(checked).isGreaterThan(15);
    }

    @Test
    void every_authenticated_endpoint_rejects_malformed_garbage_expired_and_non_bearer_credentials() throws Exception {
        long now = Instant.now().getEpochSecond();
        String expired = SecurityFixtures.hs256(SecurityFixtures.claims("1", "studenttask", now - 7200, now - 3600, null));
        List<String> credentials = List.of(
                "Bearer", "Bearer ", "Bearer garbage", "Bearer a.b.c", "Bearer ..", "Bearer " + expired,
                "Basic dXNlcjpwYXNz", "Token abc", "Bearer null", "Bearer undefined", expired);
        for (Route route : routes()) {
            if (PUBLIC_ROUTES.contains(route.toString())) {
                continue;
            }
            for (String credential : credentials) {
                assertUnauthenticatedEnvelope(
                        mvc.perform(build(route).header("Authorization", credential)).andReturn(),
                        route + " with Authorization=" + credential.substring(0, Math.min(18, credential.length())));
            }
        }
    }

    @Test
    void every_public_route_is_reachable_without_a_token_and_never_answers_5xx() throws Exception {
        for (String route : PUBLIC_ROUTES) {
            String method = route.substring(0, route.indexOf(' '));
            String path = route.substring(route.indexOf(' ') + 1);
            MvcResult result = mvc.perform(request(HttpMethod.valueOf(method), path).with(SecurityFixtures.freshIp())
                    .contentType("application/json").content("{}")).andReturn();
            assertThat(result.getResponse().getStatus()).as(route).isLessThan(500);
        }
    }

    @Test
    void head_and_method_override_tricks_do_not_bypass_authentication_or_change_the_method() throws Exception {
        for (Route route : routes()) {
            if (PUBLIC_ROUTES.contains(route.toString()) || !route.method().equals("GET")) {
                continue;
            }
            assertThat(mvc.perform(request(HttpMethod.HEAD, route.concretePath())).andReturn().getResponse().getStatus())
                    .as("HEAD " + route.template()).isEqualTo(401);
            assertThat(mvc.perform(request(HttpMethod.TRACE, route.concretePath())).andReturn().getResponse().getStatus())
                    .as("TRACE " + route.template()).isIn(400, 401, 403, 405); // 400 = rejected by the request firewall
        }
        // Overriding the method must not turn a read into a write for an authenticated caller either.
        SecurityFixtures.Account user = new SecurityFixtures(jdbc, jwt).user(false);
        long task = new SecurityFixtures(jdbc, jwt).task(user.id(), "keep-me", "S", Instant.now().plusSeconds(3600), "TODO");
        String bearer = "Bearer " + user.token();
        mvc.perform(request(HttpMethod.POST, "/api/tasks/" + task + "?_method=DELETE").header("Authorization", bearer));
        mvc.perform(request(HttpMethod.GET, "/api/tasks/" + task).header("Authorization", bearer)
                .header("X-HTTP-Method-Override", "DELETE").header("X-Method-Override", "DELETE"));
        assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, task)).isEqualTo(1);
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    com.studenttask.auth.service.JwtService jwt;
}
