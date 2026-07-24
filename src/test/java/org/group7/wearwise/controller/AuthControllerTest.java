package org.group7.wearwise.controller;

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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                .standaloneSetup(new AuthController(authService, passwordResetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator())
                .build();
    }

    @Test
    void registerReturnsCreatedTokens() throws Exception {
        when(authService.register("demo", "demo@example.com", "password123"))
                .thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "email": "demo@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("token-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-value"))
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginReturnsTokens() throws Exception {
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
                .andExpect(jsonPath("$.refreshToken").value("refresh-value"));
    }

    @Test
    void refreshReturnsRotatedTokens() throws Exception {
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
    void meReturnsCurrentUser() throws Exception {
        when(authService.getCurrentUser("demo")).thenReturn(new CurrentUserResponse(
                1L,
                "demo",
                "demo@example.com",
                "USER",
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
    void updateEmailReturnsRefreshedProfile() throws Exception {
        when(authService.updateEmail("demo", "password123", "new@example.com"))
                .thenReturn(new CurrentUserResponse(1L, "demo", "new@example.com", "USER", null, null));

        mockMvc.perform(put("/api/auth/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "email": "new@example.com"
                                }
                                """)
                        .principal(new UsernamePasswordAuthenticationToken("demo", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void updateEmailRejectsMalformedAddress() throws Exception {
        mockMvc.perform(put("/api/auth/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "email": "not-an-email"
                                }
                                """)
                        .principal(new UsernamePasswordAuthenticationToken("demo", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email is not valid."));
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
                .andExpect(jsonPath("$.errors.email").value("Email is not valid."));

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
                        "Password reset link is invalid or has expired. Please request a new one."));
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
                        "Password must be 8-100 characters and include at least one letter and one digit."));
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
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
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
                        "Account is temporarily locked after too many failed sign-in attempts. Try again in 15 minute(s)."));
    }

    @Test
    void registerRejectsWeakPasswordAndMissingEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "ab",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("Username must be between 3 and 100 characters."))
                .andExpect(jsonPath("$.errors.email").value("Email is required."))
                .andExpect(jsonPath("$.errors.password").value(
                        "Password must be 8-100 characters and include at least one letter and one digit."));
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
