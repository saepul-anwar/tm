package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.common.error.GlobalExceptionHandler;
import com.studenttask.config.AppProperties;
import com.studenttask.security.UserRateLimiter;
import com.studenttask.telegram.controller.TelegramController;
import com.studenttask.telegram.service.TelegramLinkService;
import com.studenttask.telegram.service.TelegramNotificationService;
import com.studenttask.telegram.service.TelegramUpdateDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TelegramWebhookSecretTest {

    private final TelegramUpdateDispatcher handler = mock(TelegramUpdateDispatcher.class);

    private MockMvc mvcWithConfiguredSecret(String configured) {
        AppProperties props = new AppProperties(null, new AppProperties.Telegram("synthetic-token", configured, "some_bot",
                AppProperties.Telegram.Mode.WEBHOOK, "https://api.telegram.org", "", true, false), null, null, null);
        TelegramController controller = new TelegramController(props, mock(TelegramLinkService.class), handler,
                mock(TelegramNotificationService.class), mock(UserRateLimiter.class), new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void blank_configured_secret_fails_closed_even_when_the_request_header_is_blank_or_missing() throws Exception {
        for (String configured : new String[] {"", "   ", null}) {
            MockMvc mvc = mvcWithConfiguredSecret(configured);
            String payload = "{\"update_id\":1}";

            assertThat(mvc.perform(post("/api/telegram/webhook").contentType("application/json").content(payload))
                    .andReturn().getResponse().getStatus()).isEqualTo(401);
            assertThat(mvc.perform(post("/api/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token", "")
                    .contentType("application/json").content(payload)).andReturn().getResponse().getStatus()).isEqualTo(401);
            assertThat(mvc.perform(post("/api/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token", "   ")
                    .contentType("application/json").content(payload)).andReturn().getResponse().getStatus()).isEqualTo(401);
        }
        verifyNoInteractions(handler);
    }
}
