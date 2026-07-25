package org.group7.wearwise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.group7.wearwise.enums.ShareTargetType;

import java.time.LocalDateTime;

/**
 * Một lượt chia sẻ outfit hoặc món đồ: chủ sở hữu sinh ra mã, người khác nhập mã để chép
 * bản sao về tủ đồ của mình. Bản sao là độc lập — sửa hay xóa bên này không ảnh hưởng bên kia.
 *
 * <p>Mã được lưu nguyên văn (không băm như refresh token) vì chủ sở hữu phải xem lại và
 * gửi cho người khác được. Đổi lại, mã chỉ cho phép chép một bản sao chỉ-đọc của trang phục,
 * và có thể thu hồi bất cứ lúc nào.</p>
 */
@Entity
@Table(
        name = "shares",
        indexes = @Index(name = "idx_shares_owner", columnList = "owner_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private ShareTargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    /** Khác NULL khi targetType = OUTFIT. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_id")
    private Outfit outfit;

    /** Khác NULL khi targetType = CLOTHING_ITEM. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clothing_item_id")
    private ClothingItem clothingItem;

    /** NULL nghĩa là không hết hạn. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Khác NULL nghĩa là chủ sở hữu đã thu hồi, mã không dùng được nữa. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** Số lần người khác đã chép về tủ đồ của họ. */
    @Column(name = "import_count", nullable = false)
    @Builder.Default
    private int importCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
