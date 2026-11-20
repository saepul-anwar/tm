package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.studenttask.MySqlTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The Secure flag can be switched off only through explicit configuration (local http development). */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.auth.refresh-cookie-secure=false")
class AuthInsecureCookieIT extends MySqlTestBase {

    @Autowired
    MockMvc mvc;

    @Test
    void secure_flag_is_omitted_only_when_configured_off() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(
                "{\"fullName\":\"Budi\",\"email\":\"" + email
                        + "\",\"password\":\"Passw0rd-Synthetic1\",\"confirmPassword\":\"Passw0rd-Synthetic1\"}"));
        String setCookie = mvc.perform(post("/api/auth/login").contentType("application/json").content(
                        "{\"email\":\"" + email + "\",\"password\":\"Passw0rd-Synthetic1\"}"))
                .andReturn().getResponse().getHeader("Set-Cookie");

        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth").doesNotContain("Secure");
    }
}
