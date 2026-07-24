package org.group7.wearwise.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sinh và băm token ngẫu nhiên dùng cho refresh token và mã đặt lại mật khẩu.
 * DB chỉ lưu bản băm, nên giá trị gốc không thể khôi phục từ dữ liệu bị rò rỉ.
 */
@Component
public class SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] buffer = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(buffer);
        return ENCODER.encodeToString(buffer);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return ENCODER.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash token.", exception);
        }
    }
}
