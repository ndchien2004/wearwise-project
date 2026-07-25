package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.PasswordResetToken;
import org.group7.wearwise.exception.InvalidPasswordResetTokenException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Luồng "quên mật khẩu": phát mã dùng một lần gửi qua email, rồi đổi mật khẩu bằng mã đó.
 *
 * <p>Endpoint yêu cầu đặt lại luôn trả về cùng một thông điệp dù email có tồn tại hay không,
 * để không lộ danh sách email đã đăng ký (user enumeration).</p>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final AuthMailService authMailService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final long expiresInSeconds;
    private final int maxRequestsPerHour;

    public PasswordResetService(
            AppUserRepository appUserRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            SecureTokenGenerator secureTokenGenerator,
            AuthMailService authMailService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            @Value("${wearwise.auth.password-reset-expires-in-seconds:1800}") long expiresInSeconds,
            @Value("${wearwise.auth.password-reset-max-requests-per-hour:5}") int maxRequestsPerHour
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.authMailService = authMailService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.expiresInSeconds = expiresInSeconds;
        this.maxRequestsPerHour = maxRequestsPerHour;
    }

    /**
     * Gửi link đặt lại mật khẩu nếu email tồn tại. Không báo lỗi khi email không tồn tại —
     * phía gọi luôn trả về thông điệp trung lập.
     */
    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return;
        }

        Optional<AppUser> maybeUser = appUserRepository.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            log.info("Bỏ qua yêu cầu đặt lại mật khẩu cho email chưa đăng ký.");
            return;
        }

        AppUser user = maybeUser.get();
        LocalDateTime now = LocalDateTime.now();

        long recentRequests = passwordResetTokenRepository
                .countByUsernameAndCreatedAtAfter(user.getUsername(), now.minusHours(1));
        if (recentRequests >= maxRequestsPerHour) {
            log.warn("Vượt hạn mức yêu cầu đặt lại mật khẩu cho tài khoản {}", user.getUsername());
            return;
        }

        // Mỗi lần yêu cầu mới sẽ vô hiệu các mã cũ chưa dùng.
        passwordResetTokenRepository.markAllUsedForUser(user.getUsername(), now);
        passwordResetTokenRepository.deleteByExpiresAtBefore(now.minusDays(1));

        String rawToken = secureTokenGenerator.generate();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .tokenHash(secureTokenGenerator.hash(rawToken))
                .username(user.getUsername())
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .createdAt(now)
                .build());

        authMailService.sendPasswordResetEmail(
                normalizedEmail,
                user.getUsername(),
                rawToken,
                Math.max(1, expiresInSeconds / 60)
        );
    }

    /** Đặt mật khẩu mới bằng mã dùng một lần; mọi phiên đang mở đều bị đăng xuất. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidPasswordResetTokenException();
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(secureTokenGenerator.hash(rawToken))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        LocalDateTime now = LocalDateTime.now();
        if (!resetToken.isUsable(now)) {
            throw new InvalidPasswordResetTokenException();
        }

        AppUser user = appUserRepository.findByUsername(resetToken.getUsername())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        appUserRepository.save(user);

        resetToken.setUsedAt(now);
        passwordResetTokenRepository.save(resetToken);

        // Mật khẩu đổi thì mọi phiên cũ phải chấm dứt, kể cả trên thiết bị khác.
        refreshTokenService.revokeAllForUser(user.getUsername());
        log.info("Đã đặt lại mật khẩu cho tài khoản {}", user.getUsername());
    }

    /**
     * Vô hiệu mọi mã đặt lại chưa dùng của một tài khoản. Gọi khi email thay đổi, để mã
     * đã gửi tới hộp thư cũ không còn giá trị.
     */
    @Transactional
    public void invalidatePendingResets(String username) {
        passwordResetTokenRepository.markAllUsedForUser(username, LocalDateTime.now());
    }

    /** Cho phép giao diện kiểm tra link còn hiệu lực trước khi hiển thị form. */
    @Transactional(readOnly = true)
    public boolean isResetTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        return passwordResetTokenRepository.findByTokenHash(secureTokenGenerator.hash(rawToken))
                .map(token -> token.isUsable(LocalDateTime.now()))
                .orElse(false);
    }

    static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
