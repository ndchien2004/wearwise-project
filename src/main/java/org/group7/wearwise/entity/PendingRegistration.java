package org.group7.wearwise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Đăng ký đang chờ xác thực OTP. Tài khoản chỉ được tạo thật sau khi người dùng nhập đúng
 * mã OTP gửi qua email. Mật khẩu đã được băm sẵn (không lưu mật khẩu thô), OTP cũng chỉ lưu
 * bản băm SHA-256 — giá trị gốc chỉ nằm trong email.
 */
@Entity
@Table(
        name = "pending_registrations",
        indexes = {
                @Index(name = "idx_pending_registrations_email", columnList = "email"),
                @Index(name = "idx_pending_registrations_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Khác NULL nghĩa là OTP đã dùng để tạo tài khoản, không dùng lại được. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** Số lần nhập OTP sai — vượt ngưỡng thì bản ghi bị vô hiệu để chặn dò mã. */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
