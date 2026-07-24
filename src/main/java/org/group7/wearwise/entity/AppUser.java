package org.group7.wearwise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /**
     * Email nhận link đặt lại mật khẩu. Cho phép NULL vì các tài khoản tạo trước khi
     * có tính năng quên mật khẩu chưa khai báo email; tài khoản đăng ký mới thì bắt buộc.
     */
    @Column(unique = true, length = 190)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String role = "USER";

    /** Ảnh của người dùng dùng cho thử đồ ảo — chỉ lưu URL (Cloudinary). */
    @Column(length = 512)
    private String bodyPhotoUrl;

    /** Số lần đăng nhập sai liên tiếp; reset về 0 khi đăng nhập thành công. */
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /** Thời điểm hết khóa tạm sau khi nhập sai quá nhiều lần; NULL nghĩa là không bị khóa. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Mọi access token phát hành trước mốc này đều bị coi là hết hiệu lực. */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();

        if (this.role == null || this.role.isBlank()) {
            this.role = "USER";
        }

        if (this.passwordChangedAt == null) {
            this.passwordChangedAt = this.createdAt;
        }
    }
}
