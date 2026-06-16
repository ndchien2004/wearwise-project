package org.group7.wearwise.service;

import org.group7.wearwise.entity.RevokedAuthToken;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.RevokedAuthTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AuthTokenRevocationService {

    private final RevokedAuthTokenRepository revokedAuthTokenRepository;
    private final AuthTokenService authTokenService;

    public AuthTokenRevocationService(
            RevokedAuthTokenRepository revokedAuthTokenRepository,
            AuthTokenService authTokenService
    ) {
        this.revokedAuthTokenRepository = revokedAuthTokenRepository;
        this.authTokenService = authTokenService;
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        return revokedAuthTokenRepository.existsByTokenHash(authTokenService.hashToken(token));
    }

    @Transactional
    public void revoke(String token) {
        AuthTokenDetails tokenDetails = authTokenService.validateAndGetDetails(token)
                .orElseThrow(AuthenticationFailedException::new);
        String tokenHash = authTokenService.hashToken(token);

        cleanupExpiredTokens();
        if (revokedAuthTokenRepository.existsByTokenHash(tokenHash)) {
            return;
        }

        revokedAuthTokenRepository.save(RevokedAuthToken.builder()
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.ofInstant(tokenDetails.expiresAt(), ZoneOffset.UTC))
                .build());
    }

    @Transactional
    public void cleanupExpiredTokens() {
        revokedAuthTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
    }
}
