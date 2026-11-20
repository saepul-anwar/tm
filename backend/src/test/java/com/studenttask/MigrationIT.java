package com.studenttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studenttask.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationIT extends MySqlTestBase {

    private static final List<String> EXPECTED_TABLES = List.of(
            "users", "tasks", "reminder_settings", "notification_logs", "telegram_link_tokens", "refresh_tokens");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migration_creates_all_tables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);

        assertThat(tables).map(String::toLowerCase).containsAll(EXPECTED_TABLES);
    }

    @Test
    void notification_logs_rejects_duplicate_task_type_schedule() {
        long userId = insertUser("dup-log@example.test", null);
        long taskId = insertTask(userId);
        insertLog(userId, taskId, "H1", "2026-12-01 10:00:00.000");

        assertThatThrownBy(() -> insertLog(userId, taskId, "H1", "2026-12-01 10:00:00.000"))
                .isInstanceOf(DuplicateKeyException.class);

        // a different schedule for the same task and type is a new reminder, not a duplicate
        insertLog(userId, taskId, "H1", "2026-12-01 11:00:00.000");
    }

    @Test
    void users_rejects_duplicate_email_and_chat_id() {
        insertUser("dup-user@example.test", 1001L);

        assertThatThrownBy(() -> insertUser("dup-user@example.test", null))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> insertUser("other-user@example.test", 1001L))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deleting_user_cascades_tasks_and_logs() {
        long userId = insertUser("cascade@example.test", null);
        long taskId = insertTask(userId);
        insertLog(userId, taskId, "H24", "2026-12-01 10:00:00.000");

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(count("tasks", userId)).isZero();
        assertThat(count("notification_logs", userId)).isZero();
    }

    @Test
    void app_fails_to_start_when_jwt_secret_blank() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withUserConfiguration(PropertiesOnly.class)
                .withPropertyValues(
                        "app.telegram.bot-token=synthetic",
                        "app.telegram.webhook-secret=synthetic",
                        "app.telegram.bot-username=synthetic_bot",
                        "app.cors.allowed-origins=http://localhost:3000");

        runner.withPropertyValues("app.jwt.secret=synthetic-secret")
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("app.jwt.secret=")
                .run(context -> assertThat(context).hasFailed());
        runner.run(context -> assertThat(context).hasFailed());
    }

    // Deliberately not @Configuration: a nested @Configuration would replace the application config in @SpringBootTest.
    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesOnly {
    }

    private long insertUser(String email, Long chatId) {
        jdbc.update("INSERT INTO users (full_name, email, password_hash, telegram_chat_id, created_at, updated_at)"
                + " VALUES ('Synthetic User', ?, 'synthetic-hash', ?, NOW(3), NOW(3))", email, chatId);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertTask(long userId) {
        jdbc.update("INSERT INTO tasks (user_id, title, deadline, priority, created_at, updated_at)"
                + " VALUES (?, 'Synthetic task', '2026-12-02 00:00:00.000', 'HIGH', NOW(3), NOW(3))", userId);
        return jdbc.queryForObject("SELECT MAX(id) FROM tasks WHERE user_id = ?", Long.class, userId);
    }

    private void insertLog(long userId, long taskId, String type, String scheduledAt) {
        jdbc.update("INSERT INTO notification_logs (user_id, task_id, reminder_type, scheduled_at, status, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, 'SENDING', NOW(3), NOW(3))", userId, taskId, type, scheduledAt);
    }

    private int count(String table, long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }
}
