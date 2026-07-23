package org.group7.wearwise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ảnh thử đồ ảo do dịch vụ thử đồ tạo ra (ghép trang phục lên ảnh của người dùng),
 * đã được lưu vĩnh viễn trên Cloudinary. Chỉ lưu URL trong DB.
 *
 * <p>Tham chiếu tới món đồ là "mềm" ({@code clothingItemId} + snapshot tên/ảnh)
 * để lịch sử thử đồ vẫn còn khi món đồ gốc bị xoá.</p>
 */
@Entity
@Table(name = "try_on_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TryOnResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Món đồ đã thử (tham chiếu mềm — có thể null nếu đồ đã bị xoá, hoặc khi thử cả outfit). */
    private Long clothingItemId;

    @Column(length = 255)
    private String clothingItemName;

    /** Outfit đã thử (tham chiếu mềm — chỉ có khi thử nguyên bộ). */
    private Long outfitId;

    @Column(length = 255)
    private String outfitName;

    /** Ảnh trang phục đã dùng để ghép (snapshot). */
    @Column(length = 512)
    private String garmentImageUrl;

    /** URL ảnh kết quả (đã lưu trên Cloudinary). */
    @Column(nullable = false, length = 512)
    private String resultImageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
