package com.studenttask.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    }

    /**
     * {@code apiBase}: Telegram Bot API root (tests point it at a local fake). {@code publicBaseUrl}: public HTTPS
     * origin of this backend, only needed in webhook mode. {@code runnersEnabled=false} keeps the polling runner and
     * webhook registrar from starting (tests, offline work).
     */
    public record Telegram(
            @NotBlank String botToken,
            @NotBlank String webhookSecret,
            @NotBlank String botUsername,
            @DefaultValue("polling") Mode mode,
            @DefaultValue("https://api.telegram.org") @NotBlank String apiBase,
            @DefaultValue("") String publicBaseUrl,
            @DefaultValue("true") boolean runnersEnabled) {

        public enum Mode { POLLING, WEBHOOK }
    }

    public record Reminder(
            @DefaultValue("PT60S") @NotNull Duration pollInterval,
            @DefaultValue("PT10M") @NotNull Duration catchUpWindow,
            @DefaultValue("200") @Positive int batchSize,
            @DefaultValue("PT5M") @NotNull Duration sendingTimeout,
            @DefaultValue("4") @Positive int maxAttempts,
            @DefaultValue({"PT1M", "PT5M", "PT15M"}) @NotEmpty List<Duration> retryBackoff,
            @DefaultValue("90") @Positive int logRetentionDays) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
