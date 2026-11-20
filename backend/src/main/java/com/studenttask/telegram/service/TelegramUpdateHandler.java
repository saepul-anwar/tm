package com.studenttask.telegram.service;

import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.dto.LinkResult;
import com.studenttask.telegram.dto.TelegramUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The single entry point for updates, shared by the webhook and the polling runner. Only private-chat text
 * messages starting with {@code /start} are acted on; everything else is ignored. The link token and the chat id
 * are never logged and the token is never echoed back in a reply.
 */
@Service
public class TelegramUpdateHandler {

    static final String LINKED = "Akun berhasil terhubung. Kamu akan menerima pengingat deadline tugas di chat ini.";
    static final String INVALID = "Token tidak valid atau sudah dipakai. Buat ulang dari aplikasi "
            + "(Settings > Hubungkan Telegram), lalu buka tautan yang baru.";
    static final String EXPIRED = "Token sudah kedaluwarsa. Buat ulang dari aplikasi "
            + "(Settings > Hubungkan Telegram), lalu buka tautan yang baru.";
    static final String CHAT_TAKEN = "Chat ini sudah terhubung ke akun lain. "
            + "Putuskan sambungan di akun tersebut terlebih dahulu.";
    static final String INSTRUCTIONS = "Untuk menghubungkan akun, buka aplikasi, pilih Settings > Hubungkan Telegram, "
            + "lalu buka tautan yang diberikan dan tekan Start.";

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);
    private static final String PRIVATE_CHAT = "private";

    private final TelegramLinkService links;
    private final TelegramClient client;

    public TelegramUpdateHandler(TelegramLinkService links, TelegramClient client) {
        this.links = links;
        this.client = client;
    }

    public void handle(TelegramUpdate update) {
        if (update == null || update.message() == null) {
            return;
        }
        TelegramUpdate.Message message = update.message();
        if (message.chat() == null || message.chat().id() == null || message.text() == null) {
            return;
        }
        String[] parts = message.text().trim().split("\\s+", 3);
        String command = parts[0];
        int mention = command.indexOf('@');
        if (mention > 0) {
            command = command.substring(0, mention);
        }
        if (!"/start".equalsIgnoreCase(command)) {
            return;
        }
        long chatId = message.chat().id();
        String chatType = message.chat().type();
        if (parts.length < 2) {
            if (PRIVATE_CHAT.equals(chatType)) {
                reply(chatId, INSTRUCTIONS);
            }
            return;
        }
        LinkResult result = links.handleStart(chatId, chatType, parts[1]);
        log.debug("Telegram /start handled: {}", result);
        switch (result) {
            case LINKED -> reply(chatId, LINKED);
            case INVALID -> reply(chatId, INVALID);
            case EXPIRED -> reply(chatId, EXPIRED);
            case CHAT_TAKEN -> reply(chatId, CHAT_TAKEN);
            case NOT_PRIVATE -> { }
        }
    }

    /** A failed reply must never undo or block the link that was just made. */
    private void reply(long chatId, String text) {
        try {
            client.sendMessage(chatId, text);
        } catch (TelegramException e) {
            log.warn("Telegram reply failed ({})", e.kind());
        } catch (RuntimeException e) {
            log.warn("Telegram reply failed ({})", e.getClass().getSimpleName());
        }
    }
}
