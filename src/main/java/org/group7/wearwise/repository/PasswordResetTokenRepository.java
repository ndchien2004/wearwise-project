package org.group7.wearwise.repository;

import org.group7.wearwise.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Số mã còn hiệu lực đã cấp gần đây — dùng để chặn spam yêu cầu đặt lại mật khẩu. */
    long countByUsernameAndCreatedAtAfter(String username, LocalDateTime createdAt);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.username = :username and t.usedAt is null")
    int markAllUsedForUser(@Param("username") String username, @Param("now") LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
