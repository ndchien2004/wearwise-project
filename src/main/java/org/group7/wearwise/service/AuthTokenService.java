package org.group7.wearwise.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expiresInSeconds;

    public AuthTokenService(
            @Value("${wearwise.auth.token-secret:wearwise-local-development-secret-change-me-32chars}") String tokenSecret,
            @Value("${wearwise.auth.token-expires-in-seconds:86400}") long expiresInSeconds
    ) {
        if (tokenSecret == null || tokenSecret.length() < 32) {
            throw new IllegalArgumentException("Auth token secret must be at least 32 characters.");
        }

        if (expiresInSeconds < 1) {
            throw new IllegalArgumentException("Auth token expiration must be at least 1 second.");
        }

        this.objectMapper = new ObjectMapper();
        this.secret = tokenSecret.getBytes(StandardCharsets.UTF_8);
        this.expiresInSeconds = expiresInSeconds;
    }

    public String createToken(String username) {
        long expiresAt = Instant.now().plusSeconds(expiresInSeconds).getEpochSecond();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("exp", expiresAt);

        String headerPart = encodeJson(header);
        String payloadPart = encodeJson(payload);
        String signingInput = headerPart + "." + payloadPart;
        String signaturePart = sign(signingInput);

        return signingInput + "." + signaturePart;
    }

    public Optional<String> validateAndGetUsername(String token) {
        return validateAndGetDetails(token).map(AuthTokenDetails::username);
    }

    public Optional<AuthTokenDetails> validateAndGetDetails(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(signingInput), parts[2])) {
                return Optional.empty();
            }

            byte[] payloadBytes = BASE64_URL_DECODER.decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, new TypeReference<>() {
            });

            Object username = payload.get("sub");
            Object expiresAt = payload.get("exp");
            if (!(username instanceof String) || !(expiresAt instanceof Number)) {
                return Optional.empty();
            }

            if (((Number) expiresAt).longValue() <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            return Optional.of(new AuthTokenDetails(
                    (String) username,
                    Instant.ofEpochSecond(((Number) expiresAt).longValue())
            ));
        } catch (RuntimeException | java.io.IOException exception) {
            return Optional.empty();
        }
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return BASE64_URL_ENCODER.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash auth token.", exception);
        }
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to create auth token.", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign auth token.", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);

        if (leftBytes.length != rightBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            result |= leftBytes[i] ^ rightBytes[i];
        }

        return result == 0;
    }
}
