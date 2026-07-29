package org.group7.wearwise.controller;

import jakarta.servlet.http.Cookie;
import org.group7.wearwise.config.RefreshTokenCookie;
import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.exception.AccountLockedException;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.GlobalExceptionHandler;
import org.group7.wearwise.exception.InvalidPasswordResetTokenException;
import org.group7.wearwise.service.AuthService;
import org.group7.wearwise.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private PasswordResetService passwordResetService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        passwordResetService = mock(PasswordResetService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(
                        authService, passwordResetService, new RefreshTokenCookie(false, "Lax", 604800)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator())
                .build();
    }

    /**
     * Refresh token phải rời khỏi payload JSON hoàn toàn — còn nằm trong body là frontend còn
     * có thể cất vào localStorage, và mọi lợi ích của cookie HttpOnly mất sạch.
     */
    @Test
    void loginReturnsAccessTokenInBodyAndRefreshTokenOnlyInAnHttpOnlyCookie() throws Exception {
        when(authService.login("demo", "password123")).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("wearwise_refresh=refresh-value")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @Test
    void refreshReadsTheTokenFromTheCookie() throws Exception {
        when(authService.refresh("refresh-value")).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookie.NAME, "refresh-value"))
                        .header(RefreshTokenCookie.CLIENT_HEADER, "web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    /**
     * Cốt lõi của lớp chống CSRF: trình duyệt tự gửi cookie kèm mọi request, kể cả request do
     * trang web độc hại kích hoạt — nhưng trang đó không đặt được header tự chế.
     */
    @Test
    void refreshRejectsTheCookieWhenTheClientHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookie.NAME, "refresh-value")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MISSING_CLIENT_HEADER"));

        verify(authService, never()).refresh(anyString());
    }

    /** Client không phải trình duyệt (curl, script kiểm thử) vẫn gửi được token qua body. */
    @Test
    void refreshStillAcceptsTheTokenInTheBody() throws Exception {
        when(authService.refresh("refresh-value")).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-value"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"));
    }

    @Test
    void refreshWithoutAnyTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).refresh(anyString());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        when(authService.getCurrentUser("demo")).thenReturn(new CurrentUserResponse(
                1L,
                "demo",
                "demo@example.com",
                "USER",
                null,
                LocalDateTime.of(2026, 6, 16, 10, 0),
                LocalDateTime.of(2026, 6, 16, 10, 0)
        ));

        mockMvc.perform(get("/api/auth/me")
                        .principal(new UsernamePasswordAuthenticationToken("demo", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void logoutRevokesAccessAndRefreshTokens() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-value"
                                }
                                """)
                        .principal(new UsernamePasswordAuthenticationToken("demo", null)))
                .andExpect(status().isNoContent());

        verify(authService).logout("Bearer token-value", "refresh-value");
    }

    @Test
    void logoutWorksWithoutBody() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer token-value")
                        .principal(new UsernamePasswordAuthenticationToken("demo", null)))
                .andExpect(status().isNoContent());

        verify(authService).logout("Bearer token-value", null);
    }

    @Test
    void logoutRevokesTheCookieTokenAndTellsTheBrowserToDropIt() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer token-value")
                        .header(RefreshTokenCookie.CLIENT_HEADER, "web")
                        .cookie(new Cookie(RefreshTokenCookie.NAME, "refresh-value"))
                        .principal(new UsernamePasswordAuthenticationToken("demo", null)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("wearwise_refresh=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(authService).logout("Bearer token-value", "refresh-value");
    }

    @Test
    void forgotPasswordAlwaysReturnsNeutralMessage() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "demo@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(passwordResetService).requestReset("demo@example.com");
    }

    @Test
    void forgotPasswordRejectsMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email không hợp lệ. Ví dụ đúng: ban@gmail.com"));

        verify(passwordResetService, never()).requestReset(anyString());
    }

    @Test
    void validateResetTokenReportsUsability() throws Exception {
        when(passwordResetService.isResetTokenValid("reset-token")).thenReturn(true);

        mockMvc.perform(get("/api/auth/reset-password/validate").param("token", "reset-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void resetPasswordReturnsSuccessMessage() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "newPassword456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(passwordResetService).resetPassword("reset-token", "newPassword456");
    }

    @Test
    void resetPasswordWithExpiredTokenReturnsBadRequest() throws Exception {
        doThrow(new InvalidPasswordResetTokenException())
                .when(passwordResetService).resetPassword("expired-token", "newPassword456");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "expired-token",
                                  "newPassword": "newPassword456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu link mới."));
    }

    @Test
    void resetPasswordRejectsWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "abcdefgh"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value(
                        "Mật khẩu phải từ 8 đến 100 ký tự, có cả chữ và số, không chứa khoảng trắng."));
    }

    @Test
    void changePasswordReturnsFreshTokens() throws Exception {
        when(authService.changePassword("demo", "password123", "newPassword456")).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "newPassword": "newPassword456"
                                }
                                """)
                        .principal(new UsernamePasswordAuthenticationToken("demo", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"));
    }

    @Test
    void invalidLoginReturnsUnauthorized() throws Exception {
        when(authService.login("demo", "wrong-password")).thenThrow(new AuthenticationFailedException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập/email hoặc mật khẩu không đúng."));
    }

    @Test
    void lockedAccountReturnsLockedStatus() throws Exception {
        when(authService.login("demo", "wrong-password")).thenThrow(new AccountLockedException(900));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value(
                        "Tài khoản đang bị khóa tạm do nhập sai mật khẩu quá nhiều lần. Hãy thử lại sau 15 phút, hoặc dùng \"Quên mật khẩu\" để đặt lại."));
    }

    private static AuthResponse authResponse() {
        return new AuthResponse("Bearer", "token-value", 900, "refresh-value", 604800, "demo", "USER");
    }

    private static Validator validator() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.afterPropertiesSet();
        return validatorFactoryBean;
    }
}
