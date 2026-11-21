package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.config.AppProperties;
import com.studenttask.telegram.repository.TelegramLinkTokenRepository;
import com.studenttask.telegram.service.TelegramLinkService;
import com.studenttask.user.repository.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** Bean validation (NotBlank) normally stops the app from starting; the service still fails closed on its own. */
class TelegramLinkServiceConfigTest {

    private final UserRepository users = mock(UserRepository.class);
    private final TelegramLinkTokenRepository tokens = mock(TelegramLinkTokenRepository.class);

    private TelegramLinkService serviceWith(String botToken, String botUsername) {
        AppProperties props = new AppProperties(null, new AppProperties.Telegram(botToken, "secret", botUsername,
                AppProperties.Telegram.Mode.POLLING, "https://api.telegram.org", "", true, false), null, null, null);
        return new TelegramLinkService(users, tokens, mock(TransactionTemplate.class), Clock.systemUTC(), props);
    }

    @Test
    void connect_with_blank_bot_username_returns_502_and_creates_no_token() {
        assertThatThrownBy(() -> serviceWith("synthetic-token", " ").createLink(1L))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.TELEGRAM_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo("Bot Telegram belum dikonfigurasi");
                });
        verifyNoInteractions(tokens);
    }

    @Test
    void connect_with_blank_bot_token_returns_502_and_creates_no_token() {
        assertThatThrownBy(() -> serviceWith("", "some_bot").createLink(1L))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.TELEGRAM_UNAVAILABLE));
        verifyNoInteractions(tokens);
    }
}
