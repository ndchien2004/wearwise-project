package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.AuthRequest;
import org.group7.wearwise.dto.request.ChangePasswordRequest;
import org.group7.wearwise.dto.request.ForgotPasswordRequest;
import org.group7.wearwise.dto.request.LogoutRequest;
import org.group7.wearwise.dto.request.RefreshTokenRequest;
import org.group7.wearwise.dto.request.RegisterRequest;
import org.group7.wearwise.dto.request.ResetPasswordRequest;
import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.dto.response.MessageResponse;
import org.group7.wearwise.dto.response.TokenValidationResponse;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Thông điệp trung lập cho /forgot-password — không tiết lộ email nào đã đăng ký. */
    private static final String FORGOT_PASSWORD_MESSAGE =
            "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi link đặt lại mật khẩu tới hộp thư đó.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.username(), request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestBody(required = false) LogoutRequest request
    ) {
        authService.logout(authorizationHeader, request == null ? null : request.refreshToken());
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
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return authService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword()
        );
    }
}
