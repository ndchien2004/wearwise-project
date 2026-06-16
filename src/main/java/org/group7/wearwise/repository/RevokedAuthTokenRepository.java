package org.group7.wearwise.repository;

import org.group7.wearwise.entity.RevokedAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface RevokedAuthTokenRepository extends JpaRepository<RevokedAuthToken, Long> {

    boolean existsByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
