package com.studenttask.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.studenttask.MySqlTestBase;
import com.studenttask.auth.service.JwtService;
import com.studenttask.security.SecurityFixtures.Account;
import com.studenttask.telegram.client.TelegramClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Per-user limits (telegram connect 5/min, test 3/min) are keyed on the user, never shared across users or the IP. */
@AutoConfigureMockMvc
class UserRateLimitIsolationIT extends MySqlTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwt;

    @MockitoBean
    TelegramClient telegram;

    private int call(Account who, String path) throws Exception {
        // every call comes from the same client address on purpose
        return mvc.perform(post(path).header("Authorization", "Bearer " + who.token()).with(request -> {
            request.setRemoteAddr("192.0.2.77");
            return request;
        })).andReturn().getResponse().getStatus();
    }

    @Test
    void exhausting_telegram_connect_for_user_a_does_not_throttle_user_b_on_the_same_ip() throws Exception {
        SecurityFixtures fx = new SecurityFixtures(jdbc, jwt);
        Account a = fx.user(false);
        Account b = fx.user(false);

        for (int i = 0; i < 5; i++) {
            assertThat(call(a, "/api/telegram/connect")).as("A call %d", i + 1).isEqualTo(200);
        }
        assertThat(call(a, "/api/telegram/connect")).as("A is over budget").isEqualTo(429);
        assertThat(call(a, "/api/telegram/connect")).isEqualTo(429);

        assertThat(call(b, "/api/telegram/connect")).as("B has an untouched bucket").isEqualTo(200);
    }

    @Test
    void exhausting_telegram_test_for_user_a_does_not_throttle_user_b_and_buckets_are_per_action() throws Exception {
        SecurityFixtures fx = new SecurityFixtures(jdbc, jwt);
        Account a = fx.user(false); // not linked: the call answers 409 once the limit check passed
        Account b = fx.user(false);

        for (int i = 0; i < 3; i++) {
            assertThat(call(a, "/api/telegram/test")).as("A call %d", i + 1).isEqualTo(409);
        }
        assertThat(call(a, "/api/telegram/test")).as("A is over budget").isEqualTo(429);

        assertThat(call(b, "/api/telegram/test")).as("B is not throttled by A").isEqualTo(409);
        assertThat(call(a, "/api/telegram/connect")).as("A's other action has its own bucket").isEqualTo(200);
    }
}
