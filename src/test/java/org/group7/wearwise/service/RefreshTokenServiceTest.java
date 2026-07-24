package org.group7.wearwise.service;

import org.group7.wearwise.entity.RefreshToken;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private SecureTokenGenerator secureTokenGenerator;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        secureTokenGenerator = new SecureTokenGenerator();
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, secureTokenGenerator, 604800);
    }

    @Test
    void issueStoresOnlyTheHashedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        String rawToken = refreshTokenService.issue("demo");

        verify(refreshTokenRepository).save(captor.capture());
        assertThat(rawToken).isNotBlank();
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(secureTokenGenerator.hash(rawToken))
                .isNotEqualTo(rawToken);
        assertThat(captor.getValue().getUsername()).isEqualTo("demo");
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void issuedTokensAreUnique() {
        assertThat(refreshTokenService.issue("demo")).isNotEqualTo(refreshTokenService.issue("demo"));
    }

    @Test
    void consumeReturnsUsernameAndRevokesTheToken() {
        RefreshToken stored = storedToken("demo", LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(secureTokenGenerator.hash("raw-token")))
                .thenReturn(Optional.of(stored));

        String username = refreshTokenService.consume("raw-token");

        assertThat(username).isEqualTo("demo");
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void consumeRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.consume("raw-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Refresh token is invalid or expired.");
    }

    @Test
    void consumeRejectsExpiredToken() {
        when(refreshTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(storedToken("demo", LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> refreshTokenService.consume("raw-token"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void consumeRejectsBlankToken() {
        assertThatThrownBy(() -> refreshTokenService.consume("  "))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void reusingRevokedTokenRevokesEverySessionOfThatUser() {
        RefreshToken revoked = storedToken("demo", LocalDateTime.now().plusDays(1));
        revoked.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.consume("raw-token"))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokenRepository).revokeAllForUser(eq("demo"), any(LocalDateTime.class));
    }

    @Test
    void revokeIgnoresUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.revoke("raw-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeIsANoOpForBlankToken() {
        refreshTokenService.revoke(null);
        refreshTokenService.revoke("  ");

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void expirationMustBePositive() {
        assertThatThrownBy(() -> new RefreshTokenService(refreshTokenRepository, secureTokenGenerator, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token expiration must be at least 1 second.");
    }

    private RefreshToken storedToken(String username, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .tokenHash(secureTokenGenerator.hash("raw-token"))
                .username(username)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
    }
}
