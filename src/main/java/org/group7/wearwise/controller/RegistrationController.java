package org.group7.wearwise.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.group7.wearwise.config.RefreshTokenCookie;
import org.group7.wearwise.dto.request.RegisterRequest;
import org.group7.wearwise.dto.request.VerifyOtpRequest;
import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.RegistrationOtpResponse;
import org.group7.wearwise.service.RegistrationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đăng ký có xác thực email bằng OTP: xin mã, rồi xác nhận mã để tạo tài khoản. */
@RestController
@RequestMapping("/api/auth/register")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final RefreshTokenCookie refreshTokenCookie;

    public RegistrationController(
            RegistrationService registrationService,
            RefreshTokenCookie refreshTokenCookie
    ) {
        this.registrationService = registrationService;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    /** Bước 1: kiểm tra thông tin, gửi mã OTP tới email. Chưa tạo tài khoản ở bước này. */
    @PostMapping("/request-otp")
    public RegistrationOtpResponse requestOtp(@Valid @RequestBody RegisterRequest request) {
        return registrationService.requestOtp(request.username(), request.email(), request.password());
    }

    /** Bước 2: nhập đúng OTP thì tạo tài khoản và trả về token đăng nhập luôn. */
    @PostMapping("/verify")
    public AuthResponse verify(@Valid @RequestBody VerifyOtpRequest request, HttpServletResponse response) {
        AuthResponse tokens = registrationService.verifyOtp(request.email(), request.otp());
        refreshTokenCookie.write(response, tokens.refreshToken());
        return tokens;
    }
}
