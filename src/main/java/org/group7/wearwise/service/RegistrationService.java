package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.RegistrationOtpResponse;
import org.group7.wearwise.entity.PendingRegistration;
import org.group7.wearwise.exception.AppException;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.PendingRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Đăng ký hai bước có xác thực email bằng OTP:
 * <ol>
 *   <li>{@link #requestOtp} kiểm tra tên/email còn trống, băm mật khẩu, sinh OTP 6 số và gửi email.</li>
 *   <li>{@link #verifyOtp} nhập đúng OTP thì tạo tài khoản thật và đăng nhập luôn.</li>
 * </ol>
 * Mật khẩu thô không bao giờ được lưu (chỉ lưu bản băm), OTP cũng chỉ lưu bản băm.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final AuthMailService authMailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final long expiresInSeconds;
    private final int maxRequestsPerHour;
    private final int maxAttempts;

    public RegistrationService(
            AppUserRepository appUserRepository,
            PendingRegistrationRepository pendingRegistrationRepository,
            SecureTokenGenerator secureTokenGenerator,
            AuthMailService authMailService,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            @Value("${wearwise.auth.registration-otp-expires-in-seconds:600}") long expiresInSeconds,
            @Value("${wearwise.auth.registration-otp-max-requests-per-hour:5}") int maxRequestsPerHour,
            @Value("${wearwise.auth.registration-otp-max-attempts:5}") int maxAttempts
    ) {
        this.appUserRepository = appUserRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.authMailService = authMailService;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.expiresInSeconds = expiresInSeconds;
        this.maxRequestsPerHour = maxRequestsPerHour;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public RegistrationOtpResponse requestOtp(String username, String email, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);

        LocalDateTime now = LocalDateTime.now();
        assertUsernameFree(normalizedUsername, normalizedEmail, now);

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException(
                    ErrorCode.EMAIL_TAKEN, "Email này đã được dùng cho một tài khoản khác.");
        }

        long recentRequests = pendingRegistrationRepository
                .countByEmailAndCreatedAtAfter(normalizedEmail, now.minusHours(1));
        if (maxRequestsPerHour > 0 && recentRequests >= maxRequestsPerHour) {
            throw new IllegalArgumentException("Bạn đã yêu cầu mã quá nhiều lần. Vui lòng thử lại sau ít phút.");
        }

        // Vô hiệu các yêu cầu cũ chưa dùng của email này và dọn bản ghi hết hạn cũ.
        pendingRegistrationRepository.markAllUsedForEmail(normalizedEmail, now);
        pendingRegistrationRepository.deleteByExpiresAtBefore(now.minusDays(1));

        String otp = generateOtp();
        pendingRegistrationRepository.save(PendingRegistration.builder()
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .otpHash(secureTokenGenerator.hash(otp))
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .createdAt(now)
                .build());

        long expiresInMinutes = Math.max(1, expiresInSeconds / 60);
        authMailService.sendRegistrationOtpEmail(normalizedEmail, normalizedUsername, otp, expiresInMinutes);

        return new RegistrationOtpResponse(
                "Đã gửi mã xác nhận (OTP) tới email của bạn. Mã có hiệu lực trong " + expiresInMinutes + " phút.",
                normalizedEmail,
                expiresInSeconds
        );
    }

    /**
     * {@code noRollbackFor} để lần nhập sai OTP vẫn ghi nhận được số lần thử — nếu không, việc
     * ném lỗi sẽ cuốn theo cả bản ghi attempts vừa tăng, và bộ đếm mãi bằng 0. Lý do tương tự
     * với {@link AppException}: yêu cầu bị đánh dấu đã dùng phải nằm lại được trong DB.
     */
    @Transactional(noRollbackFor = {IllegalArgumentException.class, AppException.class})
    public AuthResponse verifyOtp(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException("Mã OTP gồm 6 chữ số.");
        }

        PendingRegistration pending = pendingRegistrationRepository
                .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Chưa có yêu cầu đăng ký nào đang chờ cho email này, hoặc mã đã hết hạn. Hãy đăng ký lại."));

        LocalDateTime now = LocalDateTime.now();
        if (!pending.isUsable(now)) {
            throw new IllegalArgumentException("Mã xác nhận đã hết hạn. Hãy yêu cầu mã mới.");
        }

        if (maxAttempts > 0 && pending.getAttempts() >= maxAttempts) {
            pending.setUsedAt(now);
            pendingRegistrationRepository.save(pending);
            throw new IllegalArgumentException("Bạn đã nhập sai mã quá nhiều lần. Hãy yêu cầu mã mới.");
        }

        if (!pending.getOtpHash().equals(secureTokenGenerator.hash(otp))) {
            pending.setAttempts(pending.getAttempts() + 1);
            pendingRegistrationRepository.save(pending);
            throw new IllegalArgumentException("Mã xác nhận không đúng. Vui lòng thử lại.");
        }

        // Someone else may have taken the username while this OTP was in flight. Burn the request
        // so the user is not left retrying an OTP that can never succeed.
        if (appUserRepository.existsByUsername(pending.getUsername())) {
            pending.setUsedAt(now);
            pendingRegistrationRepository.save(pending);
            throw new BusinessRuleException(
                    ErrorCode.USERNAME_TAKEN,
                    "Tên đăng nhập \"" + pending.getUsername() + "\" vừa có người khác sử dụng. "
                            + "Hãy đăng ký lại với một tên đăng nhập khác."
            );
        }

        // Đúng OTP → tạo tài khoản từ mật khẩu đã băm sẵn và cấp token đăng nhập luôn.
        AuthResponse response = authService.registerWithHashedPassword(
                pending.getUsername(), pending.getEmail(), pending.getPasswordHash());

        pending.setUsedAt(now);
        pendingRegistrationRepository.save(pending);
        log.info("Đã tạo tài khoản {} sau khi xác thực OTP.", pending.getUsername());
        return response;
    }

    /**
     * A username is free only when no account holds it and no other email is sitting on an
     * unexpired OTP for it — otherwise both people would pass registration and the slower one
     * would be rejected after already receiving a code.
     */
    private void assertUsernameFree(String username, String email, LocalDateTime now) {
        boolean taken = appUserRepository.existsByUsername(username)
                || pendingRegistrationRepository
                        .existsByUsernameAndEmailNotAndUsedAtIsNullAndExpiresAtAfter(username, email, now);

        if (taken) {
            throw new BusinessRuleException(
                    ErrorCode.USERNAME_TAKEN, "Tên đăng nhập này đã có người sử dụng.");
        }
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            throw new IllegalArgumentException("Hãy nhập tên đăng nhập.");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        String normalized = PasswordResetService.normalizeEmail(email);
        if (normalized == null) {
            throw new IllegalArgumentException("Hãy nhập email.");
        }
        return normalized;
    }
}
