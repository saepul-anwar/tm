package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.telegram.dto.LinkResponse;
import com.studenttask.telegram.dto.LinkResult;
import com.studenttask.telegram.service.TelegramLinkService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real MySQL, synthetic users and chat ids only. */
class TelegramLinkServiceIT extends MySqlTestBase {

    private static final String PRIVATE = "private";

    @Autowired
    TelegramLinkService service;

    @Autowired
    JdbcTemplate jdbc;

    private long newUser() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into users (full_name, email, password_hash, timezone, created_at, updated_at)"
                + " values (?, ?, ?, 'Asia/Jakarta', ?, ?)", "Sinta Synthetic",
                "tg-" + UUID.randomUUID() + "@example.test", "$2a$10$syntheticsyntheticsyntheticsyntheticsyntheticsyn", now, now);
        return jdbc.queryForObject("select max(id) from users", Long.class);
    }

    private static long newChatId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000_000L);
    }

    private static String tokenOf(LinkResponse link) {
        String marker = "?start=";
        return link.deepLink().substring(link.deepLink().indexOf(marker) + marker.length());
    }

    private Long chatOf(long userId) {
        return jdbc.queryForObject("select telegram_chat_id from users where id = ?", Long.class, userId);
    }

    private boolean connectedAtSet(long userId) {
        return jdbc.queryForObject("select telegram_connected_at is not null from users where id = ?", Boolean.class, userId);
    }

    private boolean tokenUsed(String rawToken) {
        return jdbc.queryForObject("select used_at is not null from telegram_link_tokens where token_hash = ?",
                Boolean.class, com.studenttask.common.TokenHasher.sha256Hex(rawToken));
    }

    @Test
    void start_with_valid_token_links_chat_and_marks_used() {
        long user = newUser();
        long chat = newChatId();
        String token = tokenOf(service.createLink(user));

        assertThat(service.handleStart(chat, PRIVATE, token)).isEqualTo(LinkResult.LINKED);

        assertThat(chatOf(user)).isEqualTo(chat);
        assertThat(connectedAtSet(user)).isTrue();
        assertThat(tokenUsed(token)).isTrue();
    }

    @Test
    void create_link_returns_deep_link_with_43_char_base64url_token_expiring_in_10_minutes() {
        long user = newUser();
        Instant before = Instant.now();

        LinkResponse link = service.createLink(user);

        assertThat(link.deepLink()).startsWith("https://t.me/test_only_bot?start=");
        assertThat(tokenOf(link)).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(link.expiresAt()).isBetween(before.plusSeconds(600).minusMillis(1), Instant.now().plusSeconds(600));
        Long minutes = jdbc.queryForObject("select timestampdiff(SECOND, created_at, expires_at) from telegram_link_tokens"
                + " where user_id = ?", Long.class, user);
        assertThat(minutes).isEqualTo(600L);
    }

    @Test
    void stored_token_is_hash_not_raw() {
        long user = newUser();
        String token = tokenOf(service.createLink(user));

        String stored = jdbc.queryForObject("select token_hash from telegram_link_tokens where user_id = ?", String.class, user);

        assertThat(stored).isNotEqualTo(token).doesNotContain(token).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(stored).isEqualTo(com.studenttask.common.TokenHasher.sha256Hex(token));
    }

    @Test
    void token_expires_after_10_minutes() {
        long user = newUser();
        long chat = newChatId();
        String token = tokenOf(service.createLink(user));
        // Move the expiry into the past (exactly "now" counts as expired too).
        jdbc.update("update telegram_link_tokens set expires_at = ? where user_id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), user);

        assertThat(service.handleStart(chat, PRIVATE, token)).isEqualTo(LinkResult.EXPIRED);

        assertThat(chatOf(user)).isNull();
        assertThat(tokenUsed(token)).isFalse();
    }

    @Test
    void token_second_use_returns_INVALID() {
        long user = newUser();
        String token = tokenOf(service.createLink(user));
        assertThat(service.handleStart(newChatId(), PRIVATE, token)).isEqualTo(LinkResult.LINKED);
        Long firstChat = chatOf(user);

        assertThat(service.handleStart(newChatId(), PRIVATE, token)).isEqualTo(LinkResult.INVALID);

        assertThat(chatOf(user)).isEqualTo(firstChat);
    }

    @Test
    void unknown_or_malformed_token_returns_INVALID() {
        assertThat(service.handleStart(newChatId(), PRIVATE, "nonexistent-token")).isEqualTo(LinkResult.INVALID);
        assertThat(service.handleStart(newChatId(), PRIVATE, "")).isEqualTo(LinkResult.INVALID);
        assertThat(service.handleStart(newChatId(), PRIVATE, null)).isEqualTo(LinkResult.INVALID);
    }

    @Test
    void chat_already_linked_to_other_user_returns_CHAT_TAKEN_and_does_not_change_either_user() {
        long owner = newUser();
        long other = newUser();
        long chat = newChatId();
        assertThat(service.handleStart(chat, PRIVATE, tokenOf(service.createLink(owner)))).isEqualTo(LinkResult.LINKED);
        String otherToken = tokenOf(service.createLink(other));

        assertThat(service.handleStart(chat, PRIVATE, otherToken)).isEqualTo(LinkResult.CHAT_TAKEN);

        assertThat(chatOf(owner)).isEqualTo(chat);
        assertThat(chatOf(other)).isNull();
        assertThat(connectedAtSet(other)).isFalse();
        // Documented choice: a refused link does not consume the token (the user may retry from another chat).
        assertThat(tokenUsed(otherToken)).isFalse();
    }

    @Test
    void connect_again_invalidates_previous_token() {
        long user = newUser();
        String first = tokenOf(service.createLink(user));
        String second = tokenOf(service.createLink(user));

        assertThat(first).isNotEqualTo(second);
        assertThat(service.handleStart(newChatId(), PRIVATE, first)).isEqualTo(LinkResult.INVALID);
        assertThat(chatOf(user)).isNull();
        assertThat(service.handleStart(newChatId(), PRIVATE, second)).isEqualTo(LinkResult.LINKED);
    }

    @Test
    void already_connected_user_relinks_to_new_chat_with_new_token() {
        long user = newUser();
        long oldChat = newChatId();
        long newChat = newChatId();
        assertThat(service.handleStart(oldChat, PRIVATE, tokenOf(service.createLink(user)))).isEqualTo(LinkResult.LINKED);

        assertThat(service.handleStart(newChat, PRIVATE, tokenOf(service.createLink(user)))).isEqualTo(LinkResult.LINKED);

        assertThat(chatOf(user)).isEqualTo(newChat);
        // The old chat is free again for another account.
        long other = newUser();
        assertThat(service.handleStart(oldChat, PRIVATE, tokenOf(service.createLink(other)))).isEqualTo(LinkResult.LINKED);
    }

    @Test
    void same_chat_relinking_to_same_user_is_idempotent_LINKED() {
        long user = newUser();
        long chat = newChatId();
        assertThat(service.handleStart(chat, PRIVATE, tokenOf(service.createLink(user)))).isEqualTo(LinkResult.LINKED);

        assertThat(service.handleStart(chat, PRIVATE, tokenOf(service.createLink(user)))).isEqualTo(LinkResult.LINKED);

        assertThat(chatOf(user)).isEqualTo(chat);
    }

    @Test
    void group_chat_start_is_NOT_PRIVATE_and_changes_nothing() {
        long user = newUser();
        String token = tokenOf(service.createLink(user));

        assertThat(service.handleStart(-100123456789L, "group", token)).isEqualTo(LinkResult.NOT_PRIVATE);
        assertThat(service.handleStart(-100123456789L, "supergroup", token)).isEqualTo(LinkResult.NOT_PRIVATE);
        assertThat(service.handleStart(5L, null, token)).isEqualTo(LinkResult.NOT_PRIVATE);

        assertThat(chatOf(user)).isNull();
        assertThat(tokenUsed(token)).isFalse();
    }

    @Test
    void concurrent_handleStart_with_same_token_from_many_chats_yields_exactly_one_LINKED() throws Exception {
        long user = newUser();
        String token = tokenOf(service.createLink(user));
        int threads = 8;
        List<Long> chats = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            chats.add(newChatId());
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<LinkResult>> results = new ArrayList<>();
        for (long chat : chats) {
            results.add(pool.submit(() -> {
                go.await();
                return service.handleStart(chat, PRIVATE, token);
            }));
        }
        go.countDown();
        List<LinkResult> outcomes = new ArrayList<>();
        for (Future<LinkResult> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes.stream().filter(r -> r == LinkResult.LINKED)).hasSize(1);
        assertThat(outcomes.stream().filter(r -> r == LinkResult.INVALID)).hasSize(threads - 1);
        assertThat(chats).contains(chatOf(user));
    }

    @Test
    void disconnect_clears_chat_id_is_idempotent_and_drops_pending_tokens() {
        long user = newUser();
        assertThat(service.handleStart(newChatId(), PRIVATE, tokenOf(service.createLink(user)))).isEqualTo(LinkResult.LINKED);
        String pending = tokenOf(service.createLink(user));

        service.disconnect(user);
        service.disconnect(user);

        assertThat(chatOf(user)).isNull();
        assertThat(connectedAtSet(user)).isFalse();
        assertThat(service.handleStart(newChatId(), PRIVATE, pending)).isEqualTo(LinkResult.INVALID);
    }
}
