package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.studenttask.config.AppProperties;
import com.studenttask.config.AppProperties.Telegram.Mode;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.runner.WebhookRegistrar;
import org.junit.jupiter.api.Test;

class WebhookRegistrarTest {

    private final TelegramClient client = mock(TelegramClient.class);

    private WebhookRegistrar registrar(Mode mode, String publicBaseUrl, boolean enabled) {
        AppProperties props = new AppProperties(null, new AppProperties.Telegram("synthetic-token", "synthetic-secret",
                "some_bot", mode, "https://api.telegram.org", publicBaseUrl, enabled), null, null, null);
        return new WebhookRegistrar(props, client);
    }

    @Test
    void registers_the_webhook_with_the_secret_in_webhook_mode() {
        registrar(Mode.WEBHOOK, "https://app.example.test/", true).register();

        verify(client).setWebhook("https://app.example.test/api/telegram/webhook", "synthetic-secret");
    }

    @Test
    void does_nothing_in_polling_mode_or_when_runners_are_disabled() {
        registrar(Mode.POLLING, "https://app.example.test", true).register();
        registrar(Mode.WEBHOOK, "https://app.example.test", false).register();

        verifyNoInteractions(client);
    }

    @Test
    void webhook_mode_without_https_public_base_url_fails_fast() {
        assertThatThrownBy(() -> registrar(Mode.WEBHOOK, "", true).register()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registrar(Mode.WEBHOOK, "http://app.example.test", true).register())
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(client);
    }
}
