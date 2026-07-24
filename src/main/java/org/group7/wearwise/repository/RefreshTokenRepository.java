package org.group7.wearwise.repository;

import org.group7.wearwise.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.username = :username and t.revokedAt is null")
    int revokeAllForUser(@Param("username") String username, @Param("now") LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
