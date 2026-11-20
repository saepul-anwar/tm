package com.studenttask.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Synthetic values only. */
class AppPropertiesTest {

    private static final String BOT_TOKEN = "123456:synthetic-bot-token-value";
    private static final String GOOD_SECRET = "synthetic_webhook-secret_0123456789abcdef";
    private static final String JWT_SECRET = "synthetic-jwt-secret-0123456789abcdef";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private static AppProperties.Telegram telegram(String webhookSecret, String apiBase, boolean allowInsecure) {
        return new AppProperties.Telegram(BOT_TOKEN, webhookSecret, "some_bot", AppProperties.Telegram.Mode.POLLING,
                apiBase, "", true, allowInsecure);
    }

    private Set<ConstraintViolation<AppProperties.Telegram>> violations(AppProperties.Telegram telegram) {
        return validator.validate(telegram);
    }

    private ApplicationContextRunner runner(String webhookSecret, String apiBase, String allowInsecure) {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("app.jwt.secret=" + JWT_SECRET, "app.telegram.bot-token=" + BOT_TOKEN,
                        "app.telegram.webhook-secret=" + webhookSecret, "app.telegram.bot-username=some_bot",
                        "app.telegram.api-base=" + apiBase, "app.cors.allowed-origins=http://localhost:3000");
        return allowInsecure == null ? runner : runner.withPropertyValues("app.telegram.allow-insecure-api-base=" + allowInsecure);
    }

    // ---- F2: secrets never appear in toString ----

    @Test
    void toString_of_telegram_and_jwt_redacts_every_secret() {
        AppProperties.Telegram telegram = telegram(GOOD_SECRET, "https://api.telegram.org", false);

        assertThat(telegram.toString()).doesNotContain(BOT_TOKEN).doesNotContain(GOOD_SECRET)
                .contains("some_bot");
        assertThat(new AppProperties.Jwt(JWT_SECRET).toString()).doesNotContain(JWT_SECRET);
        AppProperties all = new AppProperties(new AppProperties.Jwt(JWT_SECRET), telegram, null, null, null);
        assertThat(all.toString()).doesNotContain(JWT_SECRET).doesNotContain(BOT_TOKEN).doesNotContain(GOOD_SECRET);
    }

    // ---- F2: webhook secret format ----

    @Test
    void webhook_secret_with_the_allowed_alphabet_and_length_is_accepted() {
        assertThat(violations(telegram(GOOD_SECRET, "https://api.telegram.org", false))).isEmpty();
        assertThat(violations(telegram("a".repeat(32), "https://api.telegram.org", false))).isEmpty();
        assertThat(violations(telegram("A-_9".repeat(64), "https://api.telegram.org", false))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-secret", "a123456789012345678901234567890", "has space 0123456789012345678901234567890",
            "has.dot.0123456789012345678901234567890", "unicode-é-0123456789012345678901234567890"})
    void webhook_secret_too_short_too_long_or_with_other_characters_is_rejected(String secret) {
        assertThat(violations(telegram(secret, "https://api.telegram.org", false))).isNotEmpty();
    }

    @Test
    void webhook_secret_longer_than_256_characters_is_rejected() {
        assertThat(violations(telegram("a".repeat(257), "https://api.telegram.org", false))).isNotEmpty();
    }

    @Test
    void blank_webhook_secret_is_still_rejected() {
        assertThat(violations(telegram("", "https://api.telegram.org", false))).isNotEmpty();
        assertThat(violations(telegram("   ", "https://api.telegram.org", false))).isNotEmpty();
    }

    @Test
    void startup_fails_for_a_bad_webhook_secret_without_echoing_it() {
        String bad = "short$ecret-value";

        runner(bad, "https://api.telegram.org", null).run(context -> {
            assertThat(context).hasFailed();
            StringBuilder messages = new StringBuilder();
            for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
                messages.append(t).append('\n');
            }
            assertThat(messages.toString()).contains("webhook").doesNotContain(bad).doesNotContain(BOT_TOKEN)
                    .doesNotContain(JWT_SECRET);
        });
    }

    @Test
    void startup_succeeds_with_a_valid_webhook_secret() {
        runner(GOOD_SECRET, "https://api.telegram.org", null).run(context -> assertThat(context).hasNotFailed());
    }

    // ---- F6: api-base scheme ----

    @ParameterizedTest
    @ValueSource(strings = {"https://api.telegram.org", "https://telegram.example.test:8443/base"})
    void https_api_base_with_a_host_is_accepted(String apiBase) {
        assertThat(violations(telegram(GOOD_SECRET, apiBase, false))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:9", "http://api.telegram.org", "ftp://api.telegram.org", "https://",
            "https:///path", "api.telegram.org", "//api.telegram.org", "https://user:pw@api.telegram.org", "not a url", "https:"})
    void http_and_malformed_api_base_is_rejected_by_default(String apiBase) {
        assertThat(violations(telegram(GOOD_SECRET, apiBase, false))).isNotEmpty();
    }

    @Test
    void http_api_base_is_accepted_only_with_the_explicit_test_flag() {
        assertThat(violations(telegram(GOOD_SECRET, "http://127.0.0.1:9", true))).isEmpty();
        assertThat(violations(telegram(GOOD_SECRET, "https://", true))).isNotEmpty(); // flag never excuses a missing host
        assertThat(violations(telegram(GOOD_SECRET, "ftp://127.0.0.1", true))).isNotEmpty();
    }

    @Test
    void startup_fails_for_http_api_base_unless_the_flag_is_set() {
        runner(GOOD_SECRET, "http://127.0.0.1:9", null).run(context -> {
            assertThat(context).hasFailed();
            StringBuilder messages = new StringBuilder();
            for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
                messages.append(t).append('\n');
            }
            assertThat(messages.toString()).doesNotContain(BOT_TOKEN).doesNotContain(GOOD_SECRET).doesNotContain(JWT_SECRET);
        });
        runner(GOOD_SECRET, "http://127.0.0.1:9", "false").run(context -> assertThat(context).hasFailed());
        runner(GOOD_SECRET, "http://127.0.0.1:9", "true").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void default_api_base_and_flag_are_secure() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("app.jwt.secret=" + JWT_SECRET, "app.telegram.bot-token=" + BOT_TOKEN,
                        "app.telegram.webhook-secret=" + GOOD_SECRET, "app.telegram.bot-username=some_bot",
                        "app.cors.allowed-origins=http://localhost:3000");
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AppProperties.Telegram t = context.getBean(AppProperties.class).telegram();
            assertThat(t.apiBase()).isEqualTo("https://api.telegram.org");
            assertThat(t.allowInsecureApiBase()).isFalse();
        });
    }
}
