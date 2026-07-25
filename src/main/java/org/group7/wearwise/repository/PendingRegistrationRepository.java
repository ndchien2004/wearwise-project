package org.group7.wearwise.repository;

import org.group7.wearwise.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    /** Bản đăng ký chờ mới nhất, chưa dùng, cho một email. */
    Optional<PendingRegistration> findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(String email);

    /** Số lần yêu cầu OTP gần đây cho một email — dùng để chặn spam. */
    long countByEmailAndCreatedAtAfter(String email, LocalDateTime createdAt);

    @Modifying
    @Query("update PendingRegistration p set p.usedAt = :now where p.email = :email and p.usedAt is null")
    int markAllUsedForEmail(@Param("email") String email, @Param("now") LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
