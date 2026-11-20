package com.studenttask.telegram.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.common.api.ApiResponse;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.config.AppProperties;
import com.studenttask.security.SecurityUtils;
import com.studenttask.security.UserRateLimiter;
import com.studenttask.telegram.dto.LinkResponse;
import com.studenttask.telegram.dto.TelegramUpdate;
import com.studenttask.telegram.service.TelegramLinkService;
import com.studenttask.telegram.service.TelegramNotificationService;
import com.studenttask.telegram.service.TelegramUpdateHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    static final int CONNECT_PER_MINUTE = 5;
    static final int TEST_PER_MINUTE = 3;
    private static final int MAX_WEBHOOK_BODY_BYTES = 64 * 1024;
    private static final Logger log = LoggerFactory.getLogger(TelegramController.class);

    private final AppProperties props;
    private final TelegramLinkService links;
    private final TelegramUpdateHandler handler;
    private final TelegramNotificationService notifications;
    private final UserRateLimiter rateLimiter;
    private final ObjectMapper mapper;

    public TelegramController(AppProperties props, TelegramLinkService links, TelegramUpdateHandler handler,
            TelegramNotificationService notifications, UserRateLimiter rateLimiter, ObjectMapper mapper) {
        this.props = props;
        this.links = links;
        this.handler = handler;
        this.notifications = notifications;
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
    }

    @PostMapping("/connect")
    public ApiResponse<LinkResponse> connect() {
        long userId = SecurityUtils.currentUserId();
        rateLimiter.check("telegram-connect", userId, CONNECT_PER_MINUTE);
        return ApiResponse.ok(links.createLink(userId));
    }

    /**
     * Public endpoint (trust boundary). The secret header is checked first and fails closed: a missing, wrong or
     * unconfigured secret is 401 and nothing is parsed. Once authenticated, every outcome (including ignored or
     * unparsable updates) is an empty 200 so Telegram does not keep redelivering the same update. The body is read
     * directly (bounded) so content negotiation cannot answer before the secret check.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret, HttpServletRequest request)
            throws IOException {
        if (!secretMatches(secret)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan.");
        }
        byte[] body = request.getInputStream().readNBytes(MAX_WEBHOOK_BODY_BYTES + 1);
        if (body.length > MAX_WEBHOOK_BODY_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Permintaan terlalu besar.");
        }
        TelegramUpdate update = parse(body);
        if (update != null) {
            try {
                handler.handle(update);
            } catch (RuntimeException e) {
                log.error("Telegram update handling failed ({})", e.getClass().getSimpleName());
            }
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect() {
        links.disconnect(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Boolean>> test() {
        long userId = SecurityUtils.currentUserId();
        rateLimiter.check("telegram-test", userId, TEST_PER_MINUTE);
        notifications.sendTest(userId);
        return ApiResponse.ok(Map.of("sent", true));
    }

    /** Constant-time: compares fixed-length digests so neither content nor length leaks through timing. */
    private boolean secretMatches(String supplied) {
        AppProperties.Telegram telegram = props.telegram();
        String configured = telegram == null ? null : telegram.webhookSecret();
        if (configured == null || configured.isBlank() || supplied == null || supplied.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(sha256(configured), sha256(supplied));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private TelegramUpdate parse(byte[] body) {
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                return null;
            }
            return mapper.treeToValue(node, TelegramUpdate.class);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Ignoring unreadable Telegram update");
            return null;
        }
    }
}
