package org.group7.wearwise.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTokenServiceTest {

    private static final String TEST_TOKEN_SECRET = "test-secret-with-enough-length-32";
    private static final String OTHER_TOKEN_SECRET = "another-secret-with-enough-len-32";

    @Test
    void createdTokenValidatesBackToUsername() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        String token = authTokenService.createToken("demo");

        assertThat(authTokenService.validateAndGetUsername(token)).contains("demo");
    }

    @Test
    void createdTokenIsAThreePartJwt() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        String token = authTokenService.createToken("demo");

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void createdTokenCarriesIdentityClaims() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        String token = authTokenService.createToken("demo", "ADMIN");

        assertThat(authTokenService.validateAndGetDetails(token))
                .hasValueSatisfying(details -> {
                    assertThat(details.username()).isEqualTo("demo");
                    assertThat(details.role()).isEqualTo("ADMIN");
                    assertThat(details.tokenId()).isNotBlank();
                    assertThat(details.issuedAt()).isNotNull();
                    assertThat(details.expiresAt()).isNotNull().isAfter(details.issuedAt());
                });
    }

    @Test
    void tokenIdIsUniquePerToken() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        String firstId = authTokenService.validateAndGetDetails(authTokenService.createToken("demo"))
                .orElseThrow()
                .tokenId();
        String secondId = authTokenService.validateAndGetDetails(authTokenService.createToken("demo"))
                .orElseThrow()
                .tokenId();

        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    void missingRoleClaimFallsBackToUser() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        String token = authTokenService.createToken("demo", "  ");

        assertThat(authTokenService.validateAndGetDetails(token))
                .hasValueSatisfying(details -> assertThat(details.role()).isEqualTo("USER"));
    }

    @Test
    void hashTokenReturnsStableValueWithoutRawToken() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        String firstHash = authTokenService.hashToken("token-value");
        String secondHash = authTokenService.hashToken("token-value");

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).doesNotContain("token-value");
    }

    @Test
    void tamperedTokenIsRejected() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        String token = authTokenService.createToken("demo") + "tampered";

        assertThat(authTokenService.validateAndGetUsername(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new AuthTokenService(OTHER_TOKEN_SECRET, 3600).createToken("demo");

        assertThat(new AuthTokenService(TEST_TOKEN_SECRET, 3600).validateAndGetUsername(token)).isEmpty();
    }

    @Test
    void garbageTokenIsRejectedWithoutThrowing() {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 3600);

        assertThat(authTokenService.validateAndGetUsername("not-a-jwt")).isEmpty();
        assertThat(authTokenService.validateAndGetUsername("")).isEmpty();
        assertThat(authTokenService.validateAndGetUsername(null)).isEmpty();
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new AuthTokenService("short-secret", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Auth token secret must be at least 32 characters.");
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        AuthTokenService authTokenService = new AuthTokenService(TEST_TOKEN_SECRET, 1);
        String token = authTokenService.createToken("demo");

        Thread.sleep(1100);

        assertThat(authTokenService.validateAndGetUsername(token)).isEmpty();
    }
}
