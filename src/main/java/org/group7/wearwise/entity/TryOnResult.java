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

    /**
     * Ảnh người đã dùng làm nền cho lần ghép này. Thường là ảnh cơ thể của người dùng, nhưng khi
     * mặc chồng lớp thì lại là ảnh kết quả của lần ghép trước.
     *
     * <p>Lưu lại thay vì suy ra từ hồ sơ người dùng: người dùng có thể đổi ảnh cơ thể bất cứ lúc
     * nào, và khi đó mọi kết quả cũ sẽ bị so sánh với một ảnh gốc không liên quan.</p>
     */
    @Column(name = "base_image_url", length = 512)
    private String baseImageUrl;

    /**
     * Kết quả trước đó trong chuỗi mặc chồng (tham chiếu mềm). NULL nghĩa là ghép từ ảnh cơ thể
     * gốc — tức là lớp đầu tiên.
     */
    @Column(name = "base_result_id")
    private Long baseResultId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
