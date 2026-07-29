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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "outfit_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutfitPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outfit_id", nullable = false)
    private Outfit outfit;

    /** Thời điểm đánh dấu đã mặc; nhật ký mặc của kế hoạch được tìm theo {@link #id}. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /**
     * Đợt kế hoạch đã sinh ra ngày này, NULL nếu người dùng tự đặt tay. Nhờ nó mà xóa cả đợt gỡ
     * đúng những ngày của đợt đó, không đụng vào lịch người dùng tự thêm xen kẽ.
     *
     * <p>Lý do AI chọn bộ cho ngày này nằm ở {@link #note} chứ không phải một cột riêng: nó vốn
     * là ghi chú của ngày, và để ở đó thì lịch tháng hiện sẵn mà không cần sửa gì.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wear_plan_id")
    private WearPlan wearPlan;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.completed == null) {
            this.completed = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
