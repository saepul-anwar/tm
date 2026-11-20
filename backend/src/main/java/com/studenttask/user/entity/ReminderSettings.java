package com.studenttask.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reminder_settings")
public class ReminderSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "reminder_24_hours", nullable = false)
    private boolean reminder24Hours;

    @Column(name = "reminder_6_hours", nullable = false)
    private boolean reminder6Hours;

    @Column(name = "reminder_1_hour", nullable = false)
    private boolean reminder1Hour;

    @Column(name = "reminder_at_deadline", nullable = false)
    private boolean reminderAtDeadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReminderSettings() {
    }

    /** New settings with every reminder enabled (default for a fresh account). */
    public static ReminderSettings allEnabled(long userId, Instant now) {
        ReminderSettings settings = new ReminderSettings();
        settings.userId = userId;
        settings.reminder24Hours = true;
        settings.reminder6Hours = true;
        settings.reminder1Hour = true;
        settings.reminderAtDeadline = true;
        settings.createdAt = now;
        settings.updatedAt = now;
        return settings;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public boolean isReminder24Hours() {
        return reminder24Hours;
    }

    public boolean isReminder6Hours() {
        return reminder6Hours;
    }

    public boolean isReminder1Hour() {
        return reminder1Hour;
    }

    public boolean isReminderAtDeadline() {
        return reminderAtDeadline;
    }
}
