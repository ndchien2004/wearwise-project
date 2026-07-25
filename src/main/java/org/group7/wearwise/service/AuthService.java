package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.exception.AccountLockedException;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final AuthTokenRevocationService authTokenRevocationService;
    private final RefreshTokenService refreshTokenService;
    private final int maxFailedLoginAttempts;
    private final long lockDurationSeconds;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            AuthTokenRevocationService authTokenRevocationService,
            RefreshTokenService refreshTokenService,
            @Value("${wearwise.auth.max-failed-login-attempts:5}") int maxFailedLoginAttempts,
            @Value("${wearwise.auth.lock-duration-seconds:900}") long lockDurationSeconds
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.authTokenRevocationService = authTokenRevocationService;
        this.refreshTokenService = refreshTokenService;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        this.lockDurationSeconds = lockDurationSeconds;
    }

    /**
     * Email được chốt ngay khi tạo tài khoản và không thể đổi về sau — hệ thống không có
     * API cập nhật email. Đây là địa chỉ duy nhất nhận link đặt lại mật khẩu, nên khóa lại
     * để kẻ chiếm được phiên đăng nhập không thể tự trỏ email khôi phục sang hộp thư của mình.
     */
    @Transactional
    public AuthResponse register(String username, String email, String password) {
        return registerWithHashedPassword(username, email, passwordEncoder.encode(password));
    }

    /**
     * Tạo tài khoản từ mật khẩu đã được băm sẵn — dùng cho luồng đăng ký có xác thực OTP,
     * nơi mật khẩu được băm ngay lúc yêu cầu OTP và chỉ lưu bản băm cho tới khi xác nhận.
     */
    @Transactional
    public AuthResponse registerWithHashedPassword(String username, String email, String passwordHash) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);

        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Tên đăng nhập này đã có người sử dụng.");
        }

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email này đã được dùng cho một tài khoản khác.");
        }

        AppUser user = AppUser.builder()
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .role("USER")
                .build();

        appUserRepository.save(user);
        return issueTokens(user);
    }

    /**
     * Đăng nhập bằng tên đăng nhập <em>hoặc</em> email — người dùng hay nhớ email hơn.
     * Không cần đoán người dùng nhập gì: username không chứa được ký tự {@code @}
     * (xem {@code RegisterRequest}) nên hai không gian tên không thể đụng nhau.
     *
     * <p>Kèm chống dò mật khẩu: sai quá {@code max-failed-login-attempts} lần
     * thì tài khoản bị khóa tạm trong {@code lock-duration-seconds}.
     *
     * <p>{@code noRollbackFor} là bắt buộc: nếu không, việc ném lỗi đăng nhập sẽ cuốn theo
     * cả bản ghi số lần sai vừa lưu, và bộ đếm sẽ mãi mãi bằng 0.</p>
     */
    @Transactional(noRollbackFor = {AuthenticationFailedException.class, AccountLockedException.class})
    public AuthResponse login(String usernameOrEmail, String password) {
        AppUser user = findByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(AuthenticationFailedException::new);

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException(Duration.between(now, user.getLockedUntil()).toSeconds());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailedLogin(user, now);
            throw new AuthenticationFailedException();
        }

        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            appUserRepository.save(user);
        }

        return issueTokens(user);
    }

    /**
     * Đổi refresh token còn hiệu lực lấy cặp token mới (access + refresh xoay vòng).
     * Giữ {@code noRollbackFor} để việc thu hồi token khi phát hiện dùng lại không bị hoàn tác.
     */
    @Transactional(noRollbackFor = {AuthenticationFailedException.class, AccountLockedException.class})
    public AuthResponse refresh(String refreshToken) {
        String username = refreshTokenService.consume(refreshToken);
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationFailedException("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(
                    Duration.between(LocalDateTime.now(), user.getLockedUntil()).toSeconds());
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        AppUser user = appUserRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(AuthenticationFailedException::new);

        return CurrentUserResponse.from(user);
    }

    /** Thu hồi access token hiện tại; nếu client gửi kèm refresh token thì thu hồi luôn. */
    @Transactional
    public void logout(String authorizationHeader, String refreshToken) {
        authTokenRevocationService.revoke(extractBearerToken(authorizationHeader));

        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    /**
     * Đổi mật khẩu khi đang đăng nhập. Mọi phiên cũ bị vô hiệu (bao gồm access token
     * đã phát trước đó, nhờ mốc {@code passwordChangedAt}), và trả về cặp token mới
     * để thiết bị hiện tại không bị đăng xuất.
     */
    @Transactional
    public AuthResponse changePassword(String username, String currentPassword, String newPassword) {
        AppUser user = appUserRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(AuthenticationFailedException::new);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Mật khẩu hiện tại không đúng.");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        appUserRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getUsername());
        return issueTokens(user);
    }

    private void registerFailedLogin(AppUser user, LocalDateTime now) {
        int attempts = user.getFailedLoginAttempts() + 1;

        if (maxFailedLoginAttempts > 0 && attempts >= maxFailedLoginAttempts) {
            // Đặt lại bộ đếm để sau khi hết khóa người dùng lại có đủ số lần thử.
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(now.plusSeconds(lockDurationSeconds));
            appUserRepository.save(user);
            throw new AccountLockedException(lockDurationSeconds);
        }

        user.setFailedLoginAttempts(attempts);
        appUserRepository.save(user);
    }

    public AuthResponse issueTokens(AppUser user) {
        return new AuthResponse(
                "Bearer",
                authTokenService.createToken(user.getUsername(), user.getRole()),
                authTokenService.getExpiresInSeconds(),
                refreshTokenService.issue(user.getUsername()),
                refreshTokenService.getExpiresInSeconds(),
                user.getUsername(),
                user.getRole()
        );
    }

    /** Cả username lẫn email đều được lưu ở dạng chữ thường nên chỉ cần chuẩn hóa một kiểu. */
    private Optional<AppUser> findByUsernameOrEmail(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.trim().isBlank()) {
            return Optional.empty();
        }

        String identifier = usernameOrEmail.trim().toLowerCase(Locale.ROOT);

        return appUserRepository.findByUsername(identifier)
                .or(() -> appUserRepository.findByEmail(identifier));
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            throw new IllegalArgumentException("Hãy nhập tên đăng nhập.");
        }

        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = PasswordResetService.normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Hãy nhập email.");
        }

        return normalizedEmail;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthenticationFailedException();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new AuthenticationFailedException();
        }

        return token;
    }
}
