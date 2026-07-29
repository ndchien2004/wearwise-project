package org.group7.wearwise.service;

import org.group7.wearwise.entity.RefreshToken;
import org.group7.wearwise.enums.AuditAction;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Quản lý vòng đời refresh token với chính sách xoay vòng (rotation):
 * mỗi lần dùng, token cũ bị thu hồi và một token mới được cấp.
 *
 * <p>Nếu một token đã bị thu hồi lại được dùng lần nữa thì rất có thể nó đã bị đánh cắp,
 * nên toàn bộ refresh token của tài khoản đó sẽ bị thu hồi (reuse detection).</p>
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final AuditLogService auditLogService;
    private final long expiresInSeconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            SecureTokenGenerator secureTokenGenerator,
            AuditLogService auditLogService,
            @Value("${wearwise.auth.refresh-token-expires-in-seconds:604800}") long expiresInSeconds
    ) {
        if (expiresInSeconds < 1) {
            throw new IllegalArgumentException("Refresh token expiration must be at least 1 second.");
        }

        this.refreshTokenRepository = refreshTokenRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.auditLogService = auditLogService;
        this.expiresInSeconds = expiresInSeconds;
    }

    /** Cấp refresh token mới và trả về giá trị gốc — đây là lần duy nhất giá trị này tồn tại. */
    @Transactional
    public String issue(String username) {
        String rawToken = secureTokenGenerator.generate();

        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(secureTokenGenerator.hash(rawToken))
                .username(username)
                .expiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds))
                .build());

        return rawToken;
    }

    /**
     * Đổi refresh token lấy tên tài khoản, đồng thời thu hồi token vừa dùng.
     * Phía gọi có trách nhiệm cấp token mới bằng {@link #issue(String)}.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public String consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationFailedException("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
        }

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(secureTokenGenerator.hash(rawToken))
                .orElseThrow(() -> new AuthenticationFailedException("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."));

        LocalDateTime now = LocalDateTime.now();

        if (storedToken.getRevokedAt() != null) {
            // Token đã thu hồi mà vẫn được dùng lại: coi như bị lộ, hủy toàn bộ phiên.
            refreshTokenRepository.revokeAllForUser(storedToken.getUsername(), now);
            // Sự kiện đáng chú ý nhất trong toàn bộ nhật ký: hoặc token đã bị đánh cắp thật, hoặc
            // có một client đang gọi /refresh song song sai cách. Cả hai đều cần người xem xét.
            auditLogService.record(AuditAction.REFRESH_TOKEN_REUSE_DETECTED, storedToken.getUsername(),
                    "Refresh token đã thu hồi lại được dùng; toàn bộ phiên của tài khoản bị hủy");
            throw new AuthenticationFailedException("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
        }

        if (!storedToken.isUsable(now)) {
            throw new AuthenticationFailedException("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
        }

        storedToken.setRevokedAt(now);
        refreshTokenRepository.save(storedToken);
        deleteExpiredTokens();

        return storedToken.getUsername();
    }

    /** Thu hồi một refresh token cụ thể; bỏ qua trong im lặng nếu không tìm thấy. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(secureTokenGenerator.hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    /** Đăng xuất khỏi mọi thiết bị — dùng sau khi đổi hoặc đặt lại mật khẩu. */
    @Transactional
    public void revokeAllForUser(String username) {
        refreshTokenRepository.revokeAllForUser(username, LocalDateTime.now());
    }

    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
