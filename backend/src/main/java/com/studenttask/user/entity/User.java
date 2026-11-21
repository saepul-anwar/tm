package com.studenttask.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    public static final String DEFAULT_TIMEZONE = "Asia/Jakarta";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_connected_at")
    private Instant telegramConnectedAt;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String fullName, String email, String passwordHash, Instant now) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.timezone = DEFAULT_TIMEZONE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getTimezone() {
        return timezone;
    }

    /** Whether a Telegram chat is linked; the chat id itself is deliberately not exposed by this class. */
    public boolean isTelegramConnected() {
        return telegramChatId != null;
    }

    public Instant getTelegramConnectedAt() {
        return telegramConnectedAt;
    }

    public void changeProfile(String fullName, String email, String timezone, Instant now) {
        this.fullName = fullName;
        this.email = email;
        this.timezone = timezone;
        this.updatedAt = now;
    }

    public void changePasswordHash(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
