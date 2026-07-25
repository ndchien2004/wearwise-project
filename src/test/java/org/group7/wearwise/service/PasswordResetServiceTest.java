package org.group7.wearwise.service;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.PasswordResetToken;
import org.group7.wearwise.exception.InvalidPasswordResetTokenException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.PasswordResetTokenRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    private static final long EXPIRES_IN_SECONDS = 1800;
    private static final int MAX_REQUESTS_PER_HOUR = 5;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private AuthMailService authMailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private SecureTokenGenerator secureTokenGenerator;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        secureTokenGenerator = new SecureTokenGenerator();
        passwordEncoder = new BCryptPasswordEncoder();
        passwordResetService = new PasswordResetService(
                appUserRepository,
                passwordResetTokenRepository,
                secureTokenGenerator,
                authMailService,
                refreshTokenService,
                passwordEncoder,
                EXPIRES_IN_SECONDS,
                MAX_REQUESTS_PER_HOUR
        );

        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestResetNormalizesEmailAndSendsTokenThatIsStoredHashed() {
        AppUser user = user("password123");
        when(appUserRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestReset("  DEMO@Example.com ");

        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(authMailService).sendPasswordResetEmail(
                eq("demo@example.com"), eq("demo"), rawTokenCaptor.capture(), anyLong());

        ArgumentCaptor<PasswordResetToken> storedCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(storedCaptor.capture());
        assertThat(storedCaptor.getValue().getTokenHash())
                .isEqualTo(secureTokenGenerator.hash(rawTokenCaptor.getValue()))
                .isNotEqualTo(rawTokenCaptor.getValue());
        assertThat(storedCaptor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void requestResetInvalidatesPreviouslyIssuedTokens() {
        when(appUserRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user("password123")));

        passwordResetService.requestReset("demo@example.com");

        verify(passwordResetTokenRepository).markAllUsedForUser(eq("demo"), any(LocalDateTime.class));
    }

    @Test
    void requestResetStaysSilentForUnknownEmail() {
        when(appUserRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset("ghost@example.com");

        verify(authMailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyLong());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void requestResetIgnoresBlankEmail() {
        passwordResetService.requestReset("   ");

        verify(appUserRepository, never()).findByEmail(any());
    }

    @Test
    void requestResetStopsAfterHourlyLimit() {
        when(appUserRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user("password123")));
        when(passwordResetTokenRepository.countByUsernameAndCreatedAtAfter(eq("demo"), any()))
                .thenReturn((long) MAX_REQUESTS_PER_HOUR);

        passwordResetService.requestReset("demo@example.com");

        verify(authMailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyLong());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordUpdatesHashMarksTokenUsedAndKillsSessions() {
        AppUser user = user("password123");
        PasswordResetToken token = usableToken();
        when(passwordResetTokenRepository.findByTokenHash(secureTokenGenerator.hash("raw-token")))
                .thenReturn(Optional.of(token));
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        passwordResetService.resetPassword("raw-token", "brandNew456");

        assertThat(passwordEncoder.matches("brandNew456", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(token.getUsedAt()).isNotNull();
        verify(refreshTokenService).revokeAllForUser("demo");
    }

    @Test
    void resetPasswordUnlocksAccountLockedByFailedAttempts() {
        AppUser user = user("password123");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usableToken()));
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        passwordResetService.resetPassword("raw-token", "brandNew456");

        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("raw-token", "brandNew456"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordRejectsAlreadyUsedToken() {
        PasswordResetToken token = usableToken();
        token.setUsedAt(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword("raw-token", "brandNew456"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken token = usableToken();
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword("raw-token", "brandNew456"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordRejectsReusingTheCurrentPassword() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usableToken()));
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user("password123")));

        assertThatThrownBy(() -> passwordResetService.resetPassword("raw-token", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mật khẩu mới phải khác mật khẩu hiện tại.");
    }

    @Test
    void isResetTokenValidReflectsTokenState() {
        when(passwordResetTokenRepository.findByTokenHash(secureTokenGenerator.hash("raw-token")))
                .thenReturn(Optional.of(usableToken()));

        assertThat(passwordResetService.isResetTokenValid("raw-token")).isTrue();
        assertThat(passwordResetService.isResetTokenValid("  ")).isFalse();
        assertThat(passwordResetService.isResetTokenValid(null)).isFalse();
    }

    private PasswordResetToken usableToken() {
        return PasswordResetToken.builder()
                .id(1L)
                .tokenHash(secureTokenGenerator.hash("raw-token"))
                .username("demo")
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdAt(LocalDateTime.now())
                .build();
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
