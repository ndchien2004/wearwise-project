package org.group7.wearwise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một đợt kế hoạch mặc do AI sinh ra từ yêu cầu của người dùng.
 *
 * <p>Đây là <b>cái vỏ</b> gom nhiều {@link OutfitPlan} lại: từng ngày vẫn nằm ở bảng cũ nên lịch
 * tháng, việc đánh dấu đã mặc và nhật ký mặc chạy y như kế hoạch tự đặt tay, không cần biết ngày
 * đó đến từ đâu. Cái vỏ giữ những thứ mà một dòng lịch lẻ không mang được: người dùng đã yêu cầu
 * gì, AI hiểu thành mục tiêu nào, và đợt này gồm những ngày nào — nhờ vậy mới hiển thị được cả
 * đợt ở trang chủ và xóa được trọn gói.</p>
 *
 * <p>Giữ nguyên {@link #userRequest} người dùng gõ, không chỉ lưu kết quả: khi kế hoạch ra không
 * ưng ý, so lại yêu cầu gốc với lịch nhận được là cách duy nhất biết AI hiểu sai chỗ nào.</p>
 */
@Entity
@Table(
        name = "wear_plans",
        indexes = @Index(name = "idx_wear_plans_owner_start", columnList = "owner_id, start_date")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WearPlan {

    /** Trần số ngày mỗi đợt — đủ cho một chuyến đi hoặc hai tuần công sở. */
    public static final int MAX_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    /** Tên ngắn do AI đặt theo mục đích, người dùng sửa được trước khi lưu. */
    @Column(nullable = false, length = 120)
    private String title;

    /** Nguyên văn yêu cầu người dùng gõ. */
    @Column(name = "user_request", nullable = false, length = 1000)
    private String userRequest;

    /** Một hai câu AI tóm tắt hướng phối của cả đợt. */
    @Column(length = 1000)
    private String summary;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
