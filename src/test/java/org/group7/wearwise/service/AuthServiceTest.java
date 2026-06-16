package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TEST_TOKEN_SECRET = "test-secret-with-enough-length-32";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthTokenRevocationService authTokenRevocationService;

    @Test
    void registerNormalizesUsernameHashesPasswordAndReturnsToken() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);
        AuthService authService = new AuthService(
                appUserRepository,
                passwordEncoder,
                authTokenService,
                authTokenRevocationService
        );
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);

        when(appUserRepository.existsByUsername("demo")).thenReturn(false);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(" Demo ", "password123");

        verify(appUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("demo");
        assertThat(passwordEncoder.matches("password123", userCaptor.getValue().getPasswordHash())).isTrue();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.username()).isEqualTo("demo");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        AuthService authService = new AuthService(
                appUserRepository,
                new BCryptPasswordEncoder(),
                new AuthTokenService(TEST_TOKEN_SECRET, 3600),
                authTokenRevocationService
        );
        when(appUserRepository.existsByUsername("demo")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("demo", "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username is already taken.");
    }

    @Test
    void loginRejectsInvalidPassword() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        AppUser user = AppUser.builder()
                .username("demo")
                .passwordHash(passwordEncoder.encode("password123"))
                .build();
        AuthService authService = new AuthService(
                appUserRepository,
                passwordEncoder,
                new AuthTokenService(TEST_TOKEN_SECRET, 3600),
                authTokenRevocationService
        );
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("demo", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid username or password.");
    }

    @Test
    void getCurrentUserReturnsUserProfile() {
        AppUser user = AppUser.builder()
                .id(1L)
                .username("demo")
                .passwordHash("hash")
                .role("USER")
                .build();
        AuthService authService = new AuthService(
                appUserRepository,
                new BCryptPasswordEncoder(),
                new AuthTokenService(TEST_TOKEN_SECRET, 3600),
                authTokenRevocationService
        );
        when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.getCurrentUser(" Demo ");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("demo");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void logoutRevokesBearerToken() {
        AuthService authService = new AuthService(
                appUserRepository,
                new BCryptPasswordEncoder(),
                new AuthTokenService(TEST_TOKEN_SECRET, 3600),
                authTokenRevocationService
        );

        authService.logout("Bearer token-value");

        verify(authTokenRevocationService).revoke("token-value");
    }
}
