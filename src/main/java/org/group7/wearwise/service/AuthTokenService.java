package org.group7.wearwise.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
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
 *
 * <p><b>Khóa ký phải được cấu hình tường minh.</b> Trước đây {@code application.properties} mang
 * sẵn một khóa mặc định nằm công khai trong git: quên đặt biến môi trường lúc triển khai là ký
 * token bằng chuỗi ai cũng đọc được, và bất kỳ ai cũng tự phát hành được token cho mọi tài khoản.
 * Nay khóa mặc định chỉ tồn tại trong {@code application-dev.properties}, và constructor từ chối
 * khởi động nếu khóa đó bị dùng ngoài profile phát triển — hỏng lúc khởi động rõ ràng hơn nhiều
 * so với một hệ thống chạy êm mà không có xác thực thật.</p>
 */
@Service
public class AuthTokenService {

    public static final String TOKEN_TYPE_ACCESS = "access";

    /** Khóa dùng chung cho máy lập trình viên — khai báo ở {@code application-dev.properties}. */
    static final String DEV_ONLY_SECRET = "wearwise-local-development-secret-change-me-32chars";

    /** Chỉ những profile này mới được phép chạy bằng {@link #DEV_ONLY_SECRET}. */
    private static final Profiles NON_PRODUCTION_PROFILES = Profiles.of("dev", "test", "local");

    private static final String ISSUER = "wearwise";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    private final long expiresInSeconds;

    // Có hai constructor nên phải chỉ rõ cái nào dành cho container.
    @Autowired
    public AuthTokenService(
            Environment environment,
            @Value("${wearwise.auth.token-secret:}") String tokenSecret,
            @Value("${wearwise.auth.token-expires-in-seconds:900}") long expiresInSeconds
    ) {
        if (tokenSecret == null || tokenSecret.isBlank()) {
            throw new IllegalStateException("""
                    Chưa cấu hình wearwise.auth.token-secret nên ứng dụng không khởi động.

                    Đang phát triển cục bộ? Khóa dùng chung nằm trong application-dev.properties, \
                    nhưng file đó CHỈ được đọc khi profile 'dev' đang bật. Hãy chạy bằng một trong các cách:
                      - IntelliJ : chọn cấu hình "WearwiseApplication [dev]" trong danh sách Run
                                   (hoặc thêm VM option -Dspring.profiles.active=dev vào cấu hình đang dùng)
                      - Terminal : ./mvnw spring-boot:run          (đã bật sẵn profile dev)
                      - Jar      : java -jar app.jar --spring.profiles.active=dev

                    Đang triển khai thật? Đặt biến môi trường WEARWISE_AUTH_TOKEN_SECRET bằng một \
                    chuỗi ngẫu nhiên riêng, tối thiểu 32 ký tự:  openssl rand -base64 48""");
        }

        if (tokenSecret.length() < 32) {
            throw new IllegalArgumentException("Auth token secret must be at least 32 characters.");
        }

        if (DEV_ONLY_SECRET.equals(tokenSecret) && !environment.acceptsProfiles(NON_PRODUCTION_PROFILES)) {
            throw new IllegalStateException(
                    "wearwise.auth.token-secret đang dùng khóa mặc định của môi trường phát triển — "
                            + "khóa này nằm công khai trong mã nguồn nên bất kỳ ai cũng giả mạo được token. "
                            + "Hãy đặt WEARWISE_AUTH_TOKEN_SECRET bằng một chuỗi ngẫu nhiên riêng, ví dụ: "
                            + "openssl rand -base64 48");
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

    /**
     * Lối tắt cho unit test. Dùng {@link StandardEnvironment} rỗng — không profile nào được bật —
     * nên mọi kiểm tra ở constructor chính vẫn có hiệu lực y như khi chạy thật.
     */
    AuthTokenService(String tokenSecret, long expiresInSeconds) {
        this(new StandardEnvironment(), tokenSecret, expiresInSeconds);
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
