package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.studenttask.MySqlTestBase;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Encoded / non-canonical spellings of the login path must not escape the rate limiter. */
@AutoConfigureMockMvc
class RateLimitBypassIT extends MySqlTestBase {

    private static final String BODY = "{\"email\":\"nobody@example.test\",\"password\":\"Wr0ngPassword-1\"}";

    @Autowired
    MockMvc mvc;

    private MockHttpServletRequestBuilder login(String path, String ip) {
        return post(URI.create(path)).contentType("application/json").content(BODY).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private void assertEleventhIsNotUnthrottled(String variant, String ip) throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(mvc.perform(login("/api/auth/login", ip)).andReturn().getResponse().getStatus()).isEqualTo(401);
        }
        int status = mvc.perform(login(variant, ip)).andReturn().getResponse().getStatus();
        assertThat(status).as(variant).isIn(429, 400, 404);
    }

    @Test
    void percent_encoded_login_is_rate_limited() throws Exception {
        assertEleventhIsNotUnthrottled("/api/auth/%6Cogin", "10.88.0.1");
    }

    @Test
    void trailing_slash_login_is_rate_limited_or_rejected() throws Exception {
        assertEleventhIsNotUnthrottled("/api/auth/login/", "10.88.0.2");
    }

    @Test
    void upper_case_prefix_login_is_rate_limited_or_rejected() throws Exception {
        assertEleventhIsNotUnthrottled("/API/auth/login", "10.88.0.3");
    }

    @Test
    void encoded_refresh_is_rate_limited_or_rejected() throws Exception {
        String ip = "10.88.0.4";
        for (int i = 0; i < 10; i++) {
            mvc.perform(login("/api/auth/refresh", ip));
        }
        int status = mvc.perform(login("/api/auth/%72efresh", ip)).andReturn().getResponse().getStatus();
        assertThat(status).isIn(429, 400, 404);
    }
}
