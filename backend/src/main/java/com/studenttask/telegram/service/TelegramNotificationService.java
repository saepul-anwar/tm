package com.studenttask.telegram.service;

import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramNotificationService {

    static final String TEST_MESSAGE = "[TES] Pengingat tugas\n"
            + "Tugas: Contoh tugas (pesan percobaan)\n"
            + "Ini hanya tes dari StudentTask. Notifikasi Telegram kamu sudah berfungsi.";

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final UserRepository users;
    private final TelegramClient client;

    public TelegramNotificationService(UserRepository users, TelegramClient client) {
        this.users = users;
        this.client = client;
    }

    /** Sends a clearly marked sample reminder (plain text) to the user's linked chat. */
    public void sendTest(long userId) {
        long chatId = users.findTelegramChatIdById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "Telegram belum terhubung."));
        try {
            client.sendMessage(chatId, TEST_MESSAGE);
        } catch (TelegramException e) {
            log.warn("Telegram test message failed ({})", e.kind());
            throw unavailable();
        } catch (RuntimeException e) {
            log.warn("Telegram test message failed ({})", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private static AppException unavailable() {
        return new AppException(ErrorCode.TELEGRAM_UNAVAILABLE, "Gagal mengirim pesan ke Telegram. Coba lagi nanti.");
    }
}
