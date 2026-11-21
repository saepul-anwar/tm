package com.studenttask.telegram.service;

import com.studenttask.security.UserRateLimiter;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.dto.LinkResult;
import com.studenttask.telegram.dto.TelegramUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The single entry point for updates, shared by the webhook and the polling runner. Only text messages starting with
 * {@code /start} or {@code /stop} are acted on (both only in private chats, with a per-chat limit); everything else
 * is ignored. The link token and the chat id
 * are never logged and the token is never echoed back in a reply.
 */
@Service
public class TelegramUpdateHandler {

    static final String LINKED_SUFFIX = "berhasil terhubung. Kamu akan menerima pengingat deadline tugas di chat ini. "
            + "Bukan kamu? Kirim /stop";
    static final String STOPPED = "Sambungan diputus. Kamu tidak akan menerima pengingat di chat ini lagi. "
            + "Hubungkan kembali dari aplikasi kapan saja.";
    static final String NOT_LINKED = "Tidak ada akun yang terhubung ke chat ini.";
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
    static final String CHAT_RATE_ACTION = "telegram-chat";
    static final int CHAT_UPDATES_PER_MINUTE = 5;

    private final TelegramLinkService links;
    private final TelegramClient client;
    private final UserRateLimiter rateLimiter;

    public TelegramUpdateHandler(TelegramLinkService links, TelegramClient client, UserRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
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
        boolean start = "/start".equalsIgnoreCase(command);
        boolean stop = "/stop".equalsIgnoreCase(command);
        if (!start && !stop) {
            return;
        }
        long chatId = message.chat().id();
        String chatType = message.chat().type();
        // Before any DB lookup or reply: a chat that floods commands is dropped silently.
        if (!rateLimiter.tryAcquire(CHAT_RATE_ACTION, chatId, CHAT_UPDATES_PER_MINUTE)) {
            log.debug("Telegram update dropped (chat rate limit)");
            return;
        }
        if (stop) {
            handleStop(chatId, chatType);
            return;
        }
        if (parts.length < 2) {
            if (PRIVATE_CHAT.equals(chatType)) {
                reply(chatId, INSTRUCTIONS);
            }
            return;
        }
        LinkResult result = links.handleStart(chatId, chatType, parts[1]);
        log.debug("Telegram /start handled: {}", result);
        switch (result) {
            case LINKED -> reply(chatId, linkedReply(chatId));
            case INVALID -> reply(chatId, INVALID);
            case EXPIRED -> reply(chatId, EXPIRED);
            case CHAT_TAKEN -> reply(chatId, CHAT_TAKEN);
            case NOT_PRIVATE -> { }
        }
    }

    /** Private chats only. Unlinks the chat's account; reveals nothing about it. */
    private void handleStop(long chatId, String chatType) {
        if (!PRIVATE_CHAT.equals(chatType)) {
            return;
        }
        reply(chatId, links.disconnectByChat(chatId) ? STOPPED : NOT_LINKED);
    }

    /** Names the account only by its masked email so a wrong or shared chat can recognise a mistaken link. */
    private String linkedReply(long chatId) {
        String account = links.findLinkedEmail(chatId).map(MaskedEmail::of).map(masked -> " " + masked).orElse("");
        return "Akun" + account + " " + LINKED_SUFFIX;
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
