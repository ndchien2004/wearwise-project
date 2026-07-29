package org.group7.wearwise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.group7.wearwise.enums.AuditAction;

import java.time.LocalDateTime;

/**
 * Một dòng nhật ký kiểm toán — bản ghi <b>chỉ ghi thêm</b>.
 *
 * <p>Không có API nào sửa hay xóa bảng này, kể cả cho quản trị viên. Nhật ký mà người bị giám sát
 * có quyền chỉnh thì không còn là bằng chứng: mục đích của nó là trả lời được câu "ai đã làm gì,
 * lúc nào" ngay cả khi chính tài khoản quản trị bị chiếm.
 *
 * <p>Bảng này cố tình <b>không có khóa ngoại</b> tới {@code app_users}: sự kiện phải sống sót khi
 * tài khoản bị xóa (đó lại càng là lúc cần tra), và {@link #actorUsername} còn ghi được cả những
 * tên đăng nhập chưa từng tồn tại — chính là dấu vết của một đợt dò tài khoản.
 */
@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_events_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_audit_events_action", columnList = "action"),
                @Index(name = "idx_audit_events_actor", columnList = "actor_username")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * Người thực hiện. NULL với sự kiện không xác định được chủ thể; với LOGIN_FAILED thì đây là
     * chuỗi người dùng đã gõ vào — không đảm bảo là tài khoản có thật.
     */
    @Column(name = "actor_username", length = 190)
    private String actorUsername;

    /** Tài khoản bị tác động. Bằng {@link #actorUsername} với thao tác tự thực hiện. */
    @Column(name = "target_username", length = 190)
    private String targetUsername;

    /** Mô tả ngắn cho người đọc, ví dụ lý do quản trị viên khóa tài khoản. */
    @Column(length = 500)
    private String detail;

    /** Đủ dài cho IPv6 (45 ký tự). NULL khi sự kiện không phát sinh từ một HTTP request. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}
