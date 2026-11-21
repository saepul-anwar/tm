package com.studenttask.telegram.client;

import com.studenttask.telegram.dto.SendResult;
import com.studenttask.telegram.dto.TelegramUpdate;
import java.util.List;

/** Port to the Telegram Bot API. Implementations must never leak the bot token (it is part of the Telegram URL). */
public interface TelegramClient {

    /** Sends plain text (no parse_mode, so user-controlled text cannot inject markup). */
    SendResult sendMessage(long chatId, String text);

    /** Long poll; {@code offset} is the first update id wanted. */
    List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds);

    void setWebhook(String url, String secret);

    void deleteWebhook();
}
