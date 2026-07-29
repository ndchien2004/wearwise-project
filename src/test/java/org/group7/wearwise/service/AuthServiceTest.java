package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.exception.AccountLockedException;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String TEST_TOKEN_SECRET = "test-secret-with-enough-length-32";
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCK_DURATION_SECONDS = 900;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthTokenRevocationService authTokenRevocationService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                appUserRepository,
                passwordEncoder,
                new AuthTokenService(TEST_TOKEN_SECRET, 3600),
                authTokenRevocationService,
                refreshTokenService,
                auditLogService,
                MAX_FAILED_ATTEMPTS,
                LOCK_DURATION_SECONDS
        );

        when(refreshTokenService.issue(any())).thenReturn("refresh-token-value");
        when(refreshTokenService.getExpiresInSeconds()).thenReturn(604800L);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registerNormalizesCredentialsHashesPasswordAndReturnsTokens() {
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        when(appUserRepository.existsByUsername("demo")).thenReturn(false);
        when(appUserRepository.existsByEmail("demo@example.com")).thenReturn(false);

        AuthResponse response = authService.register(" Demo ", "  Demo@Example.com ", "password123");

        verify(appUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("demo");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("demo@example.com");
        assertThat(passwordEncoder.matches("password123", userCaptor.getValue().getPasswordHash())).isTrue();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(response.refreshExpiresInSeconds()).isEqualTo(604800L);
        assertThat(response.username()).isEqualTo("demo");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(appUserRepository.existsByUsername("demo")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("demo", "demo@example.com", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tên đăng nhập này đã có người sử dụng.");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(appUserRepository.existsByUsername("demo")).thenReturn(false);
        when(appUserRepository.existsByEmail("demo@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("demo", "demo@example.com", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email này đã được dùng cho một tài khoản khác.");
    }

    @Test
    void loginRejectsInvalidPassword() {
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user("password123")));

        assertThatThrownBy(() -> authService.login("demo", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Tên đăng nhập/email hoặc mật khẩu không đúng.");
    }

    @Test
    void loginAcceptsTheEmailInsteadOfTheUsername() {
        AppUser user = user("password123");
        when(appUserRepository.findByUsername("demo@example.com")).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login("  Demo@Example.COM ", "password123");

        // Token vẫn phát theo username, dù người dùng đăng nhập bằng email.
        assertThat(response.username()).isEqualTo("demo");
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void loginLooksUpTheUsernameBeforeFallingBackToEmail() {
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user("password123")));

        authService.login("Demo", "password123");

        verify(appUserRepository, never()).findByEmail(any());
    }

    @Test
    void loginRejectsAnIdentifierThatMatchesNeitherUsernameNorEmail() {
        when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "password123"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Tên đăng nhập/email hoặc mật khẩu không đúng.");
    }

    @Test
    void loginCountsFailedAttempts() {
        AppUser user = user("password123");
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("demo", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void loginLocksAccountAfterTooManyFailedAttempts() {
        AppUser user = user("password123");
        user.setFailedLoginAttempts(MAX_FAILED_ATTEMPTS - 1);
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("demo", "wrong-password"))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("bị khóa tạm");

        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void loginRejectsLockedAccountEvenWithCorrectPassword() {
        AppUser user = user("password123");
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("demo", "password123"))
                .isInstanceOf(AccountLockedException.class);

        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void successfulLoginClearsFailedAttempts() {
        AppUser user = user("password123");
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login("demo", "password123");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void refreshRotatesTokensForValidRefreshToken() {
        when(refreshTokenService.consume("refresh-token")).thenReturn("demo");
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user("password123")));

        AuthResponse response = authService.refresh("refresh-token");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isEqualTo("refresh-token-value");
        verify(refreshTokenService).consume("refresh-token");
    }

    @Test
    void refreshFailsWhenUserNoLongerExists() {
        when(refreshTokenService.consume("refresh-token")).thenReturn("ghost");
        when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
    }

    @Test
    void getCurrentUserReturnsUserProfile() {
        AppUser user = user("password123");
        user.setId(1L);
        user.setEmail("demo@example.com");
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.getCurrentUser(" Demo ");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("demo");
        assertThat(response.email()).isEqualTo("demo@example.com");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void logoutRevokesAccessAndRefreshTokens() {
        authService.logout("Bearer token-value", "refresh-token");

        verify(authTokenRevocationService).revoke("token-value");
        verify(refreshTokenService).revoke("refresh-token");
    }

    @Test
    void logoutWithoutRefreshTokenOnlyRevokesAccessToken() {
        authService.logout("Bearer token-value", null);

        verify(authTokenRevocationService).revoke("token-value");
        verify(refreshTokenService, never()).revoke(any());
    }

    @Test
    void changePasswordUpdatesHashAndInvalidatesOtherSessions() {
        AppUser user = user("password123");
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        AuthResponse response = authService.changePassword("demo", "password123", "newPassword456");

        assertThat(passwordEncoder.matches("newPassword456", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        verify(refreshTokenService).revokeAllForUser("demo");
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user("password123")));

        assertThatThrownBy(() -> authService.changePassword("demo", "wrong-password", "newPassword456"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Mật khẩu hiện tại không đúng.");

        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void changePasswordRejectsReusingTheSamePassword() {
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user("password123")));

        assertThatThrownBy(() -> authService.changePassword("demo", "password123", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mật khẩu mới phải khác mật khẩu hiện tại.");
    }

    private AppUser user(String rawPassword) {
        return AppUser.builder()
                .username("demo")
                .email("demo@example.com")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("USER")
                .build();
    }
}
