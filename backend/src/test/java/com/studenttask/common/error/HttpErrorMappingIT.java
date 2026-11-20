package com.studenttask.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studenttask.MySqlTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies framework-level HTTP errors use the API envelope. The probe controller lives in src/test
 * only and is imported explicitly so it never leaks into other test contexts.
 */
@AutoConfigureMockMvc
@Import(HttpErrorMappingIT.ProbeController.class)
class HttpErrorMappingIT extends MySqlTestBase {

    @RestController
    static class ProbeController {
        record Payload(String value) {
        }

        @PostMapping(path = "/api/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
        String login(@RequestBody Payload payload) {
            return "ok";
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void unsupported_method_returns_405_envelope() throws Exception {
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.message").value("Metode HTTP tidak didukung."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void unsupported_media_type_returns_415_envelope() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.error.message").value("Tipe konten tidak didukung."));
    }
}
