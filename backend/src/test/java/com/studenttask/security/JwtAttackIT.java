package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import com.studenttask.security.SecurityFixtures.Account;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Forged and malformed access tokens against a protected endpoint. Every attack must end in a 401 envelope. */
@AutoConfigureMockMvc
class JwtAttackIT extends MySqlTestBase {

    private static final String PROTECTED = "/api/tasks";
    private static final byte[] KEY = SecurityFixtures.JWT_SECRET.getBytes(StandardCharsets.UTF_8);
    private static final String HEADER_HS256 = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    private Account victim;
    private long now;

    @BeforeEach
    void victim() {
        victim = new SecurityFixtures(jdbc, jwt).user(false);
        now = Instant.now().getEpochSecond();
    }

    private String validClaims() {
        return SecurityFixtures.claims(Long.toString(victim.id()), "studenttask", now, now + 900, null);
    }

    private MvcResult call(String authorization) throws Exception {
        return mvc.perform(get(PROTECTED).header("Authorization", authorization)).andReturn();
    }

    private void assertRejected(String token, String what) throws Exception {
        MvcResult result = call("Bearer " + token);
        assertThat(result.getResponse().getStatus()).as(what).isEqualTo(401);
        String body = result.getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.at("/error/code").asText()).as(what).isEqualTo("UNAUTHENTICATED");
        assertThat(node.at("/error/message").asText()).as(what).isEqualTo("Autentikasi diperlukan.");
        assertThat(body).as(what).doesNotContain("Exception", "nimbus", "org.springframework", "\tat ", "signature");
    }

    @Test
    void control_a_correctly_built_token_is_accepted() throws Exception {
        // Proves the hand-built token helper matches the server: otherwise every rejection below would be vacuous.
        assertThat(call("Bearer " + SecurityFixtures.hs256(validClaims())).getResponse().getStatus()).isEqualTo(200);
        assertThat(call("Bearer " + victim.token()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void jwt_with_wrong_signature_or_alg_none_rejected() throws Exception {
        byte[] otherKey = "another-synthetic-key-0123456789-0123456789".getBytes(StandardCharsets.UTF_8);
        assertRejected(SecurityFixtures.jwt(HEADER_HS256, validClaims(), "HmacSHA256", otherKey), "different key");
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"none\"}", validClaims(), null, null), "alg none, empty signature");
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"none\",\"typ\":\"JWT\"}", validClaims(), null, null) + "AAAA",
                "alg none with junk signature");
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"None\"}", validClaims(), null, null), "alg None");
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"NONE\"}", validClaims(), null, null), "alg NONE");
        // algorithm confusion: right key, but a different HMAC algorithm than the server pins (HS256)
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"HS384\"}", validClaims(), "HmacSHA384", KEY), "HS384 with real key");
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"HS512\"}", validClaims(), "HmacSHA512", KEY), "HS512 with real key");
        // asymmetric alg header with an HMAC signature (classic RS256->HS256 confusion)
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"RS256\"}", validClaims(), "HmacSHA256", KEY), "RS256 header");
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"ES256\"}", validClaims(), "HmacSHA256", KEY), "ES256 header");
        // header injection of key material must not be honoured
        assertRejected(SecurityFixtures.jwt(
                "{\"alg\":\"HS256\",\"jku\":\"http://127.0.0.1:9/jwks\",\"kid\":\"../../dev/null\"}",
                validClaims(), "HmacSHA256", otherKey), "jku/kid injection with other key");
        assertRejected(SecurityFixtures.jwt("{\"alg\":\"HS256\",\"crit\":[\"exp\"]}", validClaims(), "HmacSHA256", otherKey),
                "crit header, other key");
        // empty signature on an HS256 token
        assertRejected(SecurityFixtures.jwt(HEADER_HS256, validClaims(), null, null), "HS256 without signature");
    }

    @Test
    void tampered_payload_with_original_signature_is_rejected() throws Exception {
        String[] parts = victim.token().split("\\.");
        long otherUserId = victim.id() + 1;
        String tamperedPayload = SecurityFixtures.b64(SecurityFixtures.claims(
                Long.toString(otherUserId), "studenttask", now, now + 900, null));
        assertRejected(parts[0] + "." + tamperedPayload + "." + parts[2], "sub changed, old signature");
        String extended = SecurityFixtures.b64(SecurityFixtures.claims(
                Long.toString(victim.id()), "studenttask", now, now + 99_999_999, null));
        assertRejected(parts[0] + "." + extended + "." + parts[2], "exp extended, old signature");
        // flipped last signature char and truncated signature
        char last = parts[2].charAt(parts[2].length() - 1);
        assertRejected(parts[0] + "." + parts[1] + "." + parts[2].substring(0, parts[2].length() - 1)
                + (last == 'A' ? 'B' : 'A'), "flipped signature");
        assertRejected(parts[0] + "." + parts[1] + "." + parts[2].substring(0, 10), "truncated signature");
        assertRejected(parts[0] + "." + parts[1], "signature part removed");
        assertRejected(victim.token() + ".extra", "extra segment");
    }

    @Test
    void expired_not_yet_valid_and_wrong_issuer_tokens_are_rejected() throws Exception {
        String id = Long.toString(victim.id());
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, "studenttask", now - 1800, now - 900, null)),
                "expired 15 minutes ago");
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, "studenttask", now - 1800, now - 1, null)),
                "expired 1 second ago");
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, "studenttask", now, now + 900, now + 3600)),
                "nbf in the future");
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, "evil", now, now + 900, null)), "wrong issuer");
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, "STUDENTTASK", now, now + 900, null)),
                "issuer case differs");
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, null, now, now + 900, null)), "missing issuer");
    }

    @Test
    void tokens_missing_exp_sub_or_iat_or_with_odd_subjects_are_rejected() throws Exception {
        String id = Long.toString(victim.id());
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, "studenttask", now, null, null)), "no exp");
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(null, "studenttask", now, now + 900, null)), "no sub");
        assertRejected(SecurityFixtures.hs256(SecurityFixtures.claims(id, "studenttask", null, now + 900, null)), "no iat");
        for (String sub : List.of("", "abc", "1 OR 1=1", "-1", "1.5", "9999999999999999999999", "0x10", " " + id)) {
            MvcResult result = call("Bearer " + SecurityFixtures.hs256(
                    SecurityFixtures.claims(sub, "studenttask", now, now + 900, null)));
            // A validly signed token can only be minted by the server, so odd subjects must never produce a 5xx.
            // Numeric subjects that do not exist (-1) authenticate as a user with no data; everything else is 401.
            assertThat(result.getResponse().getStatus()).as("sub=%s", sub).isIn(200, 401);
            if (result.getResponse().getStatus() == 200) {
                assertThat(json.readTree(result.getResponse().getContentAsString()).at("/meta/totalElements").asLong())
                        .as("sub=%s", sub).isZero();
            }
        }
        // exp as a string / far future numbers / non numeric
        assertRejected(SecurityFixtures.hs256("{\"sub\":\"" + id + "\",\"iss\":\"studenttask\",\"iat\":" + now
                + ",\"exp\":\"soon\"}"), "exp is a string");
        assertRejected(SecurityFixtures.hs256("{\"sub\":[\"" + id + "\"],\"iss\":\"studenttask\",\"iat\":" + now
                + ",\"exp\":" + (now + 900) + "}"), "sub is an array");
        // Documented: a validly signed numeric sub is stringified by the JWT claim converter and accepted. Only the
        // server can sign, so this is not reachable by an attacker; it must still behave like the same user.
        assertThat(call("Bearer " + SecurityFixtures.hs256("{\"sub\":" + id + ",\"iss\":\"studenttask\",\"iat\":"
                + now + ",\"exp\":" + (now + 900) + "}")).getResponse().getStatus()).isEqualTo(200);
        assertRejected(SecurityFixtures.hs256("[]"), "payload is an array");
        assertRejected(SecurityFixtures.hs256("null"), "payload is null");
        assertRejected(SecurityFixtures.hs256("not json"), "payload is not json");
    }

    @Test
    void token_for_a_deleted_user_never_exposes_data_and_never_causes_a_500() throws Exception {
        Account ghost = new SecurityFixtures(jdbc, jwt).user(false);
        long ghostTask = new SecurityFixtures(jdbc, jwt).task(
                ghost.id(), "ghost-task", "G", Instant.now().plusSeconds(86_400), "TODO");
        jdbc.update("delete from users where id = ?", ghost.id()); // cascades the task

        // Documented behavior: the JWT is stateless and valid for up to 15 minutes after the account is gone.
        // Reads see an empty world; /me (which loads the user) refuses; writes cannot create orphans.
        List<MvcResult> results = new ArrayList<>();
        String bearer = "Bearer " + ghost.token();
        results.add(mvc.perform(get("/api/tasks").header("Authorization", bearer)).andReturn());
        results.add(mvc.perform(get("/api/tasks/" + ghostTask).header("Authorization", bearer)).andReturn());
        results.add(mvc.perform(get("/api/dashboard/summary").header("Authorization", bearer)).andReturn());
        results.add(mvc.perform(get("/api/auth/me").header("Authorization", bearer)).andReturn());
        results.add(mvc.perform(get("/api/profile").header("Authorization", bearer)).andReturn());
        results.add(mvc.perform(get("/api/reminders/settings").header("Authorization", bearer)).andReturn());
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("title", "orphan");
        task.put("deadline", "2030-01-01T00:00:00Z");
        task.put("priority", "LOW");
        results.add(mvc.perform(post("/api/tasks").header("Authorization", bearer)
                .contentType("application/json").content(json.writeValueAsString(task))).andReturn());
        results.add(mvc.perform(post("/api/telegram/connect").header("Authorization", bearer)).andReturn());
        results.add(mvc.perform(post("/api/telegram/test").header("Authorization", bearer)).andReturn());

        for (MvcResult r : results) {
            String uri = r.getRequest().getMethod() + " " + r.getRequest().getRequestURI();
            assertThat(r.getResponse().getContentAsString()).as(uri).doesNotContain("ghost-task", ghost.email());
        }
        assertThat(results).as("statuses of %s", results.stream().map(r -> r.getRequest().getMethod() + " "
                + r.getRequest().getRequestURI() + "=" + r.getResponse().getStatus()).toList())
                .allSatisfy(r -> assertThat(r.getResponse().getStatus()).isLessThan(500));
        // Writes that need the user row answer 401 (not 500): reminder settings GET, task create, telegram connect.
        assertThat(results.get(5).getResponse().getStatus()).isEqualTo(401);
        assertThat(results.get(6).getResponse().getStatus()).isEqualTo(401);
        assertThat(results.get(7).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from tasks where title = 'orphan'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reminder_settings where user_id = ?", Integer.class,
                ghost.id())).isZero();
    }

    @Test
    void very_long_authorization_header_is_rejected_without_a_500() throws Exception {
        String huge = "A".repeat(200_000);
        assertRejected(huge, "200 KB garbage token");
        assertRejected(victim.token() + huge, "valid token with 200 KB suffix");
        assertRejected(huge + "." + huge + "." + huge, "three huge segments");
        assertRejected("a".repeat(5000) + "." + "b".repeat(5000) + "." + "c".repeat(5000), "15 KB segments");
        MvcResult noBearerHuge = call("Basic " + huge);
        assertThat(noBearerHuge.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void multiple_authorization_headers_do_not_let_a_bad_token_hide_behind_a_good_one() throws Exception {
        String good = "Bearer " + victim.token();
        // First header is the one the servlet container exposes: garbage first means anonymous.
        MvcResult garbageFirst = mvc.perform(get(PROTECTED).header("Authorization", "Bearer garbage")
                .header("Authorization", good)).andReturn();
        assertThat(garbageFirst.getResponse().getStatus()).isEqualTo(401);
        MvcResult basicFirst = mvc.perform(get(PROTECTED).header("Authorization", "Basic abc")
                .header("Authorization", good)).andReturn();
        assertThat(basicFirst.getResponse().getStatus()).isEqualTo(401);
        // Documented: with a valid first header the request is authenticated as that user (never as a mix of users).
        Account other = new SecurityFixtures(jdbc, jwt).user(false);
        new SecurityFixtures(jdbc, jwt).task(other.id(), "other-users-task", "O", Instant.now().plusSeconds(60), "TODO");
        MvcResult validFirst = mvc.perform(get(PROTECTED).header("Authorization", good)
                .header("Authorization", "Bearer " + other.token())).andReturn();
        assertThat(validFirst.getResponse().getStatus()).isEqualTo(200);
        assertThat(validFirst.getResponse().getContentAsString()).doesNotContain("other-users-task");
        // Comma-joined single header (as a proxy might fold them) is not a valid token.
        assertRejected(victim.token() + ", Bearer " + other.token(), "comma folded headers");
        assertRejected(victim.token() + " " + other.token(), "two tokens separated by space");
    }

    @Test
    void bearer_scheme_is_case_insensitive_and_odd_whitespace_never_breaks_the_filter() throws Exception {
        // RFC 7235: the scheme is case-insensitive, so "bearer" and "BEARER" are accepted (documented behavior).
        assertThat(call("bearer " + victim.token()).getResponse().getStatus()).isEqualTo(200);
        assertThat(call("BEARER " + victim.token()).getResponse().getStatus()).isEqualTo(200);
        // A token that is not a Bearer credential is never accepted.
        for (String header : List.of(victim.token(), "Bearer" + victim.token(), "Token " + victim.token(),
                "Basic " + victim.token(), "Bearer\t" + victim.token())) {
            MvcResult r = call(header);
            assertThat(r.getResponse().getStatus()).as(header.substring(0, 8)).isEqualTo(401);
        }
        // A raw line break in a header value is rejected by the request firewall (400) before any controller runs.
        MvcResult lineBreak = call("Bearer\n" + victim.token());
        assertThat(lineBreak.getResponse().getStatus()).isIn(400, 401);
        assertThat(lineBreak.getResponse().getContentAsString()).doesNotContain("Exception", "\tat ");
        // Extra spaces around the token are trimmed: still the same user, never a different one.
        assertThat(call("Bearer   " + victim.token() + "  ").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void access_token_is_not_accepted_where_only_the_refresh_cookie_counts_and_vice_versa() throws Exception {
        // Public refresh endpoint ignores the Authorization header: an access token is not a refresh token.
        MvcResult refresh = mvc.perform(post("/api/auth/refresh").with(SecurityFixtures.freshIp())
                .header("Authorization", "Bearer " + victim.token())
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", victim.token()))).andReturn();
        assertThat(refresh.getResponse().getStatus()).isEqualTo(401);
        // A refresh-token-looking opaque value is not a valid access token.
        assertRejected("0123456789abcdef0123456789abcdef0123456789abcdef", "opaque refresh-like string");
    }
}
