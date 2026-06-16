package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final AuthTokenRevocationService authTokenRevocationService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            AuthTokenRevocationService authTokenRevocationService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.authTokenRevocationService = authTokenRevocationService;
    }

    @Transactional
    public AuthResponse register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);

        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username is already taken.");
        }

        AppUser user = AppUser.builder()
                .username(normalizedUsername)
                .passwordHash(passwordEncoder.encode(password))
                .role("USER")
                .build();

        appUserRepository.save(user);
        return authResponse(normalizedUsername);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        AppUser user = appUserRepository.findByUsername(normalizedUsername)
                .orElseThrow(AuthenticationFailedException::new);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }

        return authResponse(normalizedUsername);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        AppUser user = appUserRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(AuthenticationFailedException::new);

        return CurrentUserResponse.from(user);
    }

    @Transactional
    public void logout(String authorizationHeader) {
        authTokenRevocationService.revoke(extractBearerToken(authorizationHeader));
    }

    private AuthResponse authResponse(String username) {
        return new AuthResponse(
                "Bearer",
                authTokenService.createToken(username),
                authTokenService.getExpiresInSeconds(),
                username
        );
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthenticationFailedException();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new AuthenticationFailedException();
        }

        return token;
    }
}
