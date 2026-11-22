package com.studenttask.security;

import com.studenttask.auth.service.JwtService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Synthetic users, rows and hand-built JWTs for the security tests (plain SQL, independent of the code under test). */
final class SecurityFixtures {

    static final String JWT_SECRET = "test-only-synthetic-jwt-secret-0123456789abcdef";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    /** A distinct client address per call so the per-IP auth rate limit never leaks between tests. */
    static RequestPostProcessor freshIp() {
        int n = IP_COUNTER.incrementAndGet();
        return request -> {
            request.setRemoteAddr("172.20." + (n / 250) + "." + (n % 250 + 1));
            return request;
        };
    }

    record Account(long id, String email, String token) {
    }

    private final JdbcTemplate jdbc;
    private final JwtService jwt;

    SecurityFixtures(JdbcTemplate jdbc, JwtService jwt) {
        this.jdbc = jdbc;
        this.jwt = jwt;
    }

    Account user(boolean telegramLinked) {
        String email = "sec-" + UUID.randomUUID() + "@example.test";
        Long chat = telegramLinked ? ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000_000L) : null;
        jdbc.update("insert into users (full_name,email,password_hash,telegram_chat_id,timezone,created_at,updated_at) "
                + "values ('Synthetic User', ?, 'x', ?, 'Asia/Jakarta', now(3), now(3))", email, chat);
        long id = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        return new Account(id, email, jwt.issue(id, email));
    }

    Long chatOf(long userId) {
        return jdbc.queryForObject("select telegram_chat_id from users where id = ?", Long.class, userId);
    }

    long task(long userId, String title, String subject, Instant deadline, String status) {
        jdbc.update("insert into tasks (user_id,title,description,subject,deadline,priority,status,created_at,updated_at) "
                + "values (?,?,?,?,?,'HIGH',?,now(3),now(3))", userId, title, "desc of " + title, subject,
                Timestamp.from(deadline), status);
        return jdbc.queryForObject("select max(id) from tasks where user_id = ?", Long.class, userId);
    }

    void settings(long userId, boolean h24, boolean h6, boolean h1, boolean atDeadline) {
        jdbc.update("insert into reminder_settings (user_id,reminder_24_hours,reminder_6_hours,reminder_1_hour,"
                + "reminder_at_deadline,created_at,updated_at) values (?,?,?,?,?,now(3),now(3))",
                userId, h24, h6, h1, atDeadline);
    }

    void log(long userId, long taskId) {
        jdbc.update("insert into notification_logs (user_id,task_id,reminder_type,scheduled_at,status,attempts,"
                + "created_at,updated_at) values (?,?,'H24',now(3),'SENT',1,now(3),now(3))", userId, taskId);
    }

    int logs(long userId) {
        return jdbc.queryForObject("select count(*) from notification_logs where user_id = ?", Integer.class, userId);
    }

    // ---- hand-built JWTs: full control over header, claims and signature ----

    static String jwt(String headerJson, String payloadJson, String hmacAlgorithm, byte[] key) {
        String signingInput = b64(headerJson) + "." + b64(payloadJson);
        if (hmacAlgorithm == null) {
            return signingInput + ".";
        }
        return signingInput + "." + B64.encodeToString(hmac(hmacAlgorithm, key, signingInput));
    }

    static String hs256(String payloadJson) {
        return jwt("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", payloadJson, "HmacSHA256",
                JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    static String claims(String sub, String iss, Long iat, Long exp, Long nbf) {
        StringBuilder sb = new StringBuilder("{");
        if (sub != null) {
            sb.append("\"sub\":\"").append(sub).append("\",");
        }
        if (iss != null) {
            sb.append("\"iss\":\"").append(iss).append("\",");
        }
        if (iat != null) {
            sb.append("\"iat\":").append(iat).append(',');
        }
        if (exp != null) {
            sb.append("\"exp\":").append(exp).append(',');
        }
        if (nbf != null) {
            sb.append("\"nbf\":").append(nbf).append(',');
        }
        sb.append("\"email\":\"forged@example.test\"}");
        return sb.toString();
    }

    static String b64(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] hmac(String algorithm, byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
