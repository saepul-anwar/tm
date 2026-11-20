package com.studenttask.telegram.service;

import com.studenttask.common.TokenHasher;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.config.AppProperties;
import com.studenttask.telegram.dto.LinkResponse;
import com.studenttask.telegram.dto.LinkResult;
import com.studenttask.telegram.entity.TelegramLinkToken;
import com.studenttask.telegram.repository.TelegramLinkTokenRepository;
import com.studenttask.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Account linking (spec section 9). The raw token only exists in the deep link; the database holds its SHA-256.
 *
 * <p>Decisions: a link refused with CHAT_TAKEN does not consume the token (nothing is changed, the user may retry
 * from another chat before it expires); the token is consumed with one conditional UPDATE, so of any number of
 * concurrent callers exactly one wins; the unique key on {@code users.telegram_chat_id} is the final guard, and a
 * violation rolls the whole transaction back (token stays unused) and is reported as CHAT_TAKEN.
 */
@Service
public class TelegramLinkService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 64;
    private static final String PRIVATE_CHAT = "private";

    private final UserRepository users;
    private final TelegramLinkTokenRepository tokens;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public TelegramLinkService(UserRepository users, TelegramLinkTokenRepository tokens, TransactionTemplate tx,
            Clock clock, AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.tx = tx;
        this.clock = clock;
        this.props = props;
    }

    public LinkResponse createLink(long userId) {
        String botUsername = requireConfiguredBotUsername();
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = now();
        Instant expiresAt = now.plus(TOKEN_TTL);
        tx.executeWithoutResult(status -> {
            tokens.deleteUnusedByUserId(userId);
            tokens.save(new TelegramLinkToken(userId, TokenHasher.sha256Hex(token), expiresAt, now));
        });
        return new LinkResponse("https://t.me/" + botUsername + "?start=" + token, expiresAt);
    }

    public LinkResult handleStart(long chatId, String chatType, String token) {
        if (!PRIVATE_CHAT.equals(chatType)) {
            return LinkResult.NOT_PRIVATE;
        }
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return LinkResult.INVALID;
        }
        String hash = TokenHasher.sha256Hex(token);
        try {
            return tx.execute(status -> link(chatId, hash, status));
        } catch (DataIntegrityViolationException e) {
            return LinkResult.CHAT_TAKEN; // unique key on telegram_chat_id lost a race; transaction rolled back
        }
    }

    /** Clears the chat link and drops any pending link token. Idempotent. */
    public void disconnect(long userId) {
        tx.executeWithoutResult(status -> {
            users.unlinkTelegram(userId, now());
            tokens.deleteUnusedByUserId(userId);
        });
    }

    private LinkResult link(long chatId, String hash, TransactionStatus status) {
        Instant now = now();
        TelegramLinkToken token = tokens.findByTokenHash(hash).orElse(null);
        if (token == null || token.getUsedAt() != null) {
            return LinkResult.INVALID;
        }
        if (!token.getExpiresAt().isAfter(now)) {
            return LinkResult.EXPIRED;
        }
        Optional<Long> holder = users.findIdByTelegramChatId(chatId);
        if (holder.isPresent() && !holder.get().equals(token.getUserId())) {
            return LinkResult.CHAT_TAKEN;
        }
        if (tokens.claim(token.getId(), now) != 1) {
            return LinkResult.INVALID; // lost the race: someone else consumed it first
        }
        if (users.linkTelegram(token.getUserId(), chatId, now) != 1) {
            status.setRollbackOnly();
            return LinkResult.INVALID;
        }
        return LinkResult.LINKED;
    }

    private String requireConfiguredBotUsername() {
        AppProperties.Telegram telegram = props.telegram();
        if (telegram == null || isBlank(telegram.botToken()) || isBlank(telegram.botUsername())) {
            throw new AppException(ErrorCode.TELEGRAM_UNAVAILABLE, "Bot Telegram belum dikonfigurasi");
        }
        String username = telegram.botUsername().trim();
        return username.startsWith("@") ? username.substring(1) : username;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS); // DATETIME(3)
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
