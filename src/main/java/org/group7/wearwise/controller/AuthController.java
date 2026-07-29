package org.group7.wearwise.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.group7.wearwise.config.RefreshTokenCookie;
import org.group7.wearwise.dto.request.AuthRequest;
import org.group7.wearwise.dto.request.ChangePasswordRequest;
import org.group7.wearwise.dto.request.ForgotPasswordRequest;
import org.group7.wearwise.dto.request.LogoutRequest;
import org.group7.wearwise.dto.request.RefreshTokenRequest;
import org.group7.wearwise.dto.request.ResetPasswordRequest;
import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.dto.response.MessageResponse;
import org.group7.wearwise.dto.response.TokenValidationResponse;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.service.AuthService;
import org.group7.wearwise.service.PasswordResetService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Thông điệp trung lập cho /forgot-password — không tiết lộ email nào đã đăng ký. */
    private static final String FORGOT_PASSWORD_MESSAGE =
            "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi link đặt lại mật khẩu tới hộp thư đó.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            RefreshTokenCookie refreshTokenCookie
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request, HttpServletResponse response) {
        AuthResponse tokens = authService.login(request.username(), request.password());
        refreshTokenCookie.write(response, tokens.refreshToken());
        return tokens;
    }

    /**
     * Refresh token đọc từ cookie {@code HttpOnly}. Vẫn chấp nhận token trong body để phục vụ
     * client không phải trình duyệt (script kiểm thử, curl) — nơi không có cookie jar và cũng
     * không có khái niệm CSRF.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) RefreshTokenRequest body
    ) {
        String refreshToken = refreshTokenCookie.read(request)
                .or(() -> Optional.ofNullable(body).map(RefreshTokenRequest::refreshToken))
                .filter(token -> !token.isBlank())
                .orElseThrow(() -> new AuthenticationFailedException(
                        "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."));

        AuthResponse tokens = authService.refresh(refreshToken);
        refreshTokenCookie.write(response, tokens.refreshToken());
        return tokens;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) LogoutRequest body
    ) {
        String refreshToken = refreshTokenCookie.read(request)
                .orElseGet(() -> body == null ? null : body.refreshToken());

        // Xóa cookie kể cả khi việc thu hồi phía server thất bại: người dùng bấm "đăng xuất"
        // thì thiết bị này phải hết phiên, không phụ thuộc kết quả gọi API.
        refreshTokenCookie.clear(response);
        authService.logout(authorizationHeader, refreshToken);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return new MessageResponse(FORGOT_PASSWORD_MESSAGE);
    }

    @GetMapping("/reset-password/validate")
    public TokenValidationResponse validateResetToken(@RequestParam("token") String token) {
        return new TokenValidationResponse(passwordResetService.isResetTokenValid(token));
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.");
    }

    @PostMapping("/change-password")
    public AuthResponse changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response
    ) {
        AuthResponse tokens = authService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword()
        );

        // Đổi mật khẩu thu hồi mọi refresh token cũ; ghi đè cookie bằng token mới để chính
        // thiết bị đang thao tác không bị đá ra ngoài.
        refreshTokenCookie.write(response, tokens.refreshToken());
        return tokens;
    }
}
