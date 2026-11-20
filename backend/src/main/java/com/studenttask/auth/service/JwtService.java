package com.studenttask.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.config.AppProperties;
import com.studenttask.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** HS256 access tokens, 15 minutes, {@code sub} = user id. */
@Service
public class JwtService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    static final int MIN_SECRET_BYTES = 32;
    private static final String ISSUER = "studenttask";
    private static final String EMAIL_CLAIM = "email";

    private final Clock clock;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtService(AppProperties properties, Clock clock) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            // Message intentionally omits the secret and its length.
            throw new IllegalStateException("app.jwt.secret must be at least 256 bits (32 bytes)");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtClaimValidator<String>("iss", ISSUER::equals));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(validator);
    }

    public String issue(long userId, String email) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(Long.toString(userId))
                .claim(EMAIL_CLAIM, email)
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public AuthenticatedUser parse(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return new AuthenticatedUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString(EMAIL_CLAIM));
        } catch (JwtException | NumberFormatException | NullPointerException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan.");
        }
    }
}
