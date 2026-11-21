package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.studenttask.telegram.service.TelegramUpdateHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The webhook controller answers first and processes later; the secret check still comes before anything else. */
class TelegramWebhookAsyncTest {

    private static final String SECRET = "synthetic_webhook-secret_0123456789abcdef";
    private static final String HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final String PAYLOAD =
            "{\"update_id\":1,\"message\":{\"chat\":{\"id\":9,\"type\":\"private\"},\"text\":\"/stop\"}}";

    private final TelegramUpdateHandler handler = mock(TelegramUpdateHandler.class);
    private final List<Runnable> queued = new ArrayList<>();

    private MockMvc mvc(TelegramUpdateDispatcher dispatcher) {
        AppProperties props = new AppProperties(null, new AppProperties.Telegram("synthetic-token", SECRET, "some_bot",
                AppProperties.Telegram.Mode.WEBHOOK, "https://api.telegram.org", "", true, false), null, null, null);
        TelegramController controller = new TelegramController(props, mock(TelegramLinkService.class), dispatcher,
                mock(TelegramNotificationService.class), mock(UserRateLimiter.class), new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void valid_update_gets_an_empty_200_and_is_handled_later_by_the_worker() throws Exception {
        MockMvc mvc = mvc(new TelegramUpdateDispatcher(handler, queued::add));

        var response = mvc.perform(post("/api/telegram/webhook").header(HEADER, SECRET)
                .contentType("application/json").content(PAYLOAD)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
        verify(handler, never()).handle(any());
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        verify(handler).handle(any());
    }

    @Test
    void a_saturated_executor_still_answers_200_and_drops_the_update() throws Exception {
        MockMvc mvc = mvc(new TelegramUpdateDispatcher(handler, task -> {
            throw new RejectedExecutionException("full");
        }));

        var response = mvc.perform(post("/api/telegram/webhook").header(HEADER, SECRET)
                .contentType("application/json").content(PAYLOAD)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(handler);
    }

    @Test
    void a_failing_handler_in_the_worker_does_not_change_the_response() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(handler).handle(any());
        MockMvc mvc = mvc(new TelegramUpdateDispatcher(handler, Runnable::run));

        var response = mvc.perform(post("/api/telegram/webhook").header(HEADER, SECRET)
                .contentType("application/json").content(PAYLOAD)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void wrong_or_missing_secret_is_401_and_nothing_is_parsed_or_enqueued() throws Exception {
        MockMvc mvc = mvc(new TelegramUpdateDispatcher(handler, queued::add));

        for (String secret : new String[] {null, "", "wrong", SECRET + "x"}) {
            var request = post("/api/telegram/webhook").contentType("application/json").content(PAYLOAD);
            if (secret != null) {
                request.header(HEADER, secret);
            }
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).as("secret=%s", secret)
                    .isEqualTo(401);
        }
        // Not even a malformed or oversized body is looked at before authentication.
        assertThat(mvc.perform(post("/api/telegram/webhook").header(HEADER, "wrong").content("x".repeat(100_000)))
                .andReturn().getResponse().getStatus()).isEqualTo(401);

        assertThat(queued).isEmpty();
        verifyNoInteractions(handler);
    }

    @Test
    void oversized_authenticated_body_is_still_rejected_without_enqueueing() throws Exception {
        MockMvc mvc = mvc(new TelegramUpdateDispatcher(handler, queued::add));

        var response = mvc.perform(post("/api/telegram/webhook").header(HEADER, SECRET)
                .contentType("application/json").content("x".repeat(70_000))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(queued).isEmpty();
    }
}
