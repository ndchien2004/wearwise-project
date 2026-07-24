package org.group7.wearwise.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Phát hành và kiểm chứng access token JWT (HS256) bằng thư viện JJWT.
 *
 * <p>Access token cố tình ngắn hạn; phiên dài được duy trì bằng refresh token
 * (xem {@link RefreshTokenService}).</p>
 */
@Service
public class AuthTokenService {

    public static final String TOKEN_TYPE_ACCESS = "access";

    private static final String ISSUER = "wearwise";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    private final long expiresInSeconds;

    public AuthTokenService(
            @Value("${wearwise.auth.token-secret:wearwise-local-development-secret-change-me-32chars}") String tokenSecret,
            @Value("${wearwise.auth.token-expires-in-seconds:900}") long expiresInSeconds
    ) {
        if (tokenSecret == null || tokenSecret.length() < 32) {
            throw new IllegalArgumentException("Auth token secret must be at least 32 characters.");
        }

        if (expiresInSeconds < 1) {
            throw new IllegalArgumentException("Auth token expiration must be at least 1 second.");
        }

        this.secretKey = new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.jwtParser = Jwts.parser()
                .verifyWith(this.secretKey)
                .requireIssuer(ISSUER)
                .build();
        this.expiresInSeconds = expiresInSeconds;
    }

    public String createToken(String username) {
        return createToken(username, "USER");
    }

    public String createToken(String username, String role) {
        Instant issuedAt = Instant.now();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(username)
                .claim(CLAIM_ROLE, role == null || role.isBlank() ? "USER" : role)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expiresInSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<String> validateAndGetUsername(String token) {
        return validateAndGetDetails(token).map(AuthTokenDetails::username);
    }

    /**
     * Kiểm tra chữ ký, issuer, hạn dùng và loại token. Trả về {@link Optional#empty()}
     * cho mọi token không hợp lệ — phía gọi không cần bắt exception.
     */
    public Optional<AuthTokenDetails> validateAndGetDetails(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();

            if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }

            String username = claims.getSubject();
            Date expiration = claims.getExpiration();
            if (username == null || username.isBlank() || expiration == null) {
                return Optional.empty();
            }

            Date issuedAt = claims.getIssuedAt();
            String role = claims.get(CLAIM_ROLE, String.class);

            return Optional.of(new AuthTokenDetails(
                    claims.getId(),
                    username,
                    role == null || role.isBlank() ? "USER" : role,
                    issuedAt == null ? null : issuedAt.toInstant(),
                    expiration.toInstant()
            ));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Băm token để lưu vào bảng thu hồi mà không giữ giá trị gốc. */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return BASE64_URL_ENCODER.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash auth token.", exception);
        }
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
