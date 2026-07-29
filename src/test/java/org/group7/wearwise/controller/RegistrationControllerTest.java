package org.group7.wearwise.controller;

import org.group7.wearwise.config.RefreshTokenCookie;
import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.RegistrationOtpResponse;
import org.group7.wearwise.exception.GlobalExceptionHandler;
import org.group7.wearwise.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationControllerTest {

    private RegistrationService registrationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registrationService = mock(RegistrationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(
                        registrationService, new RefreshTokenCookie(false, "Lax", 604800)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator())
                .build();
    }

    @Test
    void requestOtpReturnsMessage() throws Exception {
        when(registrationService.requestOtp("demo", "demo@example.com", "password123"))
                .thenReturn(new RegistrationOtpResponse("Đã gửi mã.", "demo@example.com", 600));

        mockMvc.perform(post("/api/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "email": "demo@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã gửi mã."))
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));
    }

    @Test
    void requestOtpRejectsWeakPasswordAndMissingEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "ab",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("Tên đăng nhập phải từ 3 đến 100 ký tự."))
                .andExpect(jsonPath("$.errors.email").value("Hãy nhập email."))
                .andExpect(jsonPath("$.errors.password").value(
                        "Mật khẩu phải từ 8 đến 100 ký tự, có cả chữ và số, không chứa khoảng trắng."));
    }

    @Test
    void verifyReturnsTokens() throws Exception {
        when(registrationService.verifyOtp("demo@example.com", "123456"))
                .thenReturn(new AuthResponse("Bearer", "token-value", 900, "refresh-value", 604800, "demo", "USER"));

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "demo@example.com",
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"))
                .andExpect(jsonPath("$.username").value("demo"))
                // Như mọi endpoint phát hành token khác: refresh token chỉ đi bằng cookie HttpOnly.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("wearwise_refresh=refresh-value")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void verifyRejectsMalformedOtp() throws Exception {
        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "demo@example.com",
                                  "otp": "12ab"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.otp").value("Mã OTP gồm 6 chữ số."));
    }

    private static Validator validator() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.afterPropertiesSet();
        return validatorFactoryBean;
    }
}
