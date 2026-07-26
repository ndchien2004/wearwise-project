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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.group7.wearwise.enums.WearSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một lượt mặc đã xảy ra. Đây là nguồn sự thật của lịch sử: {@code wearCount} và
 * {@code lastWornAt} trên món đồ / outfit chỉ là bản tóm tắt được cập nhật kèm theo, giữ lại
 * để các màn hình cũ và truy vấn sắp xếp không phải đếm lại mỗi lần.
 *
 * <p>Mỗi dòng ghi <b>một</b> thứ: hoặc một món đồ, hoặc một outfit (mặc nguyên bộ sinh ra một
 * dòng cho bộ và một dòng cho mỗi món). Ràng buộc duy nhất theo ngày là chỗ luật "mỗi ngày tính
 * tối đa một lượt" được bảo đảm thật sự, thay vì so {@code lastWornAt} — giá trị mà người dùng
 * sửa tay được ở form món đồ.</p>
 */
@Entity
@Table(
        name = "wear_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_wear_logs_item_day",
                        columnNames = {"owner_id", "clothing_item_id", "worn_on"}
                ),
                @UniqueConstraint(
                        name = "uq_wear_logs_outfit_day",
                        columnNames = {"owner_id", "outfit_id", "worn_on"}
                )
        },
        indexes = {
                @Index(name = "idx_wear_logs_owner_worn_on", columnList = "owner_id, worn_on"),
                @Index(name = "idx_wear_logs_plan", columnList = "outfit_plan_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WearLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clothing_item_id")
    private ClothingItem clothingItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_id")
    private Outfit outfit;

    /** Ngày được tính lượt — khoá của luật một lượt mỗi ngày. */
    @Column(name = "worn_on", nullable = false)
    private LocalDate wornOn;

    /** Thời điểm bấm, dùng để dựng lại {@code lastWornAt} khi hoàn tác. */
    @Column(name = "worn_at", nullable = false)
    private LocalDateTime wornAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WearSource source;

    /**
     * Kế hoạch đã sinh ra dòng này, nếu có. Tham chiếu mềm (không khoá ngoại) để xoá kế hoạch
     * không kéo theo lịch sử — lượt mặc đó vẫn đã xảy ra.
     */
    @Column(name = "outfit_plan_id")
    private Long outfitPlanId;
}
