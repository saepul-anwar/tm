package com.studenttask.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Application settings. Secrets have no defaults: the app refuses to start when they are blank. */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid Telegram telegram,
        @NotNull @Valid @DefaultValue Reminder reminder,
        @NotNull @Valid Cors cors,
        @NotNull @Valid @DefaultValue Auth auth) {

    /**
     * {@code refreshCookieSecure} must stay true outside local plain-http development
     * (property {@code app.auth.refresh-cookie-secure}, env {@code AUTH_REFRESH_COOKIE_SECURE}).
     */
    public record Auth(@DefaultValue("true") boolean refreshCookieSecure) {
    }

    public record Jwt(@NotBlank String secret) {

        /** Never prints the signing secret. */
        @Override
        public String toString() {
            return "Jwt[secret=***]";
        }
    }

    /**
     * {@code apiBase}: Telegram Bot API root (tests point it at a local fake). {@code publicBaseUrl}: public HTTPS
     * origin of this backend, only needed in webhook mode. {@code runnersEnabled=false} keeps the polling runner and
     * webhook registrar from starting (tests, offline work). {@code allowInsecureApiBase} permits an {@code http://}
     * apiBase and exists only for tests (local fake server); never set it outside tests.
     */
    public record Telegram(
            @NotBlank String botToken,
            @NotBlank String webhookSecret,
            @NotBlank String botUsername,
            @DefaultValue("polling") Mode mode,
            @DefaultValue("https://api.telegram.org") @NotBlank String apiBase,
            @DefaultValue("") String publicBaseUrl,
            @DefaultValue("true") boolean runnersEnabled,
            @DefaultValue("false") boolean allowInsecureApiBase) {

        static final String WEBHOOK_SECRET_REGEX = "[A-Za-z0-9_-]{32,256}";
        private static final java.util.regex.Pattern WEBHOOK_SECRET_PATTERN =
                java.util.regex.Pattern.compile(WEBHOOK_SECRET_REGEX);

        public enum Mode { POLLING, WEBHOOK }

        /**
         * The webhook secret travels in an HTTP header, so it is limited to header-safe characters and a minimum
         * length. Deliberately an {@code @AssertTrue} check and not {@code @Pattern}: Spring Boot prints the rejected
         * value of a failed field in the startup error, which would leak the secret; here the rejected value is a
         * boolean.
         */
        @AssertTrue(message = "app.telegram.webhook-secret must match " + WEBHOOK_SECRET_REGEX + " (value not shown)")
        public boolean isWebhookSecretFormatValid() {
            return webhookSecret != null && WEBHOOK_SECRET_PATTERN.matcher(webhookSecret).matches();
        }

        /**
         * The bot token is part of every request URL, so the API base must be {@code https://host} without user
         * info. Plain {@code http://} is only for tests that talk to a local fake, behind the explicit
         * {@code allowInsecureApiBase} flag (default false). Boolean for the same no-echo reason as above.
         */
        @AssertTrue(message = "app.telegram.api-base must be an https:// URL with a host (value not shown)")
        public boolean isApiBaseAllowed() {
            if (apiBase == null) {
                return false;
            }
            try {
                java.net.URI uri = new java.net.URI(apiBase);
                boolean hostPresent = uri.getHost() != null && !uri.getHost().isBlank();
                boolean schemeAllowed = "https".equalsIgnoreCase(uri.getScheme())
                        || (allowInsecureApiBase && "http".equalsIgnoreCase(uri.getScheme()));
                return hostPresent && schemeAllowed && uri.getUserInfo() == null;
            } catch (java.net.URISyntaxException e) {
                return false;
            }
        }

        /** Never prints the bot token or the webhook secret. */
        @Override
        public String toString() {
            return "Telegram[botToken=***, webhookSecret=***, botUsername=" + botUsername + ", mode=" + mode
                    + ", apiBase=" + apiBase + ", publicBaseUrl=" + publicBaseUrl + ", runnersEnabled=" + runnersEnabled
                    + ", allowInsecureApiBase=" + allowInsecureApiBase + "]";
        }
    }

    /**
     * {@code enabled=false} (env {@code REMINDER_SCHEDULER_ENABLED}) switches off the scheduled triggers of the
     * reminder tick and the cleanup job; the beans stay available so tests can call them directly.
     * {@code sendPause} is the pause between two Telegram sends inside one tick. {@code cleanupCron} is evaluated in
     * UTC; {@code cleanupBatchSize} bounds each DELETE so no long locks are held.
     */
    public record Reminder(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT60S") @NotNull Duration pollInterval,
            @DefaultValue("PT10M") @NotNull Duration catchUpWindow,
            @DefaultValue("200") @Positive int batchSize,
            @DefaultValue("PT5M") @NotNull Duration sendingTimeout,
            @DefaultValue("4") @Positive int maxAttempts,
            @DefaultValue({"PT1M", "PT5M", "PT15M"}) @NotEmpty List<Duration> retryBackoff,
            @DefaultValue("90") @Positive int logRetentionDays,
            @DefaultValue("PT0.04S") @NotNull Duration sendPause,
            @DefaultValue("0 30 3 * * *") @NotBlank String cleanupCron,
            @DefaultValue("1000") @Positive int cleanupBatchSize) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
