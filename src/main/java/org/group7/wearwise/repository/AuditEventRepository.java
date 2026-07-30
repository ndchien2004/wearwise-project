package org.group7.wearwise.repository;

import org.group7.wearwise.entity.AuditEvent;
import org.group7.wearwise.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chỉ có thao tác đọc và thêm. Không khai báo phương thức xóa/sửa nào — nhật ký kiểm toán mà
 * chỉnh được thì không còn giá trị làm bằng chứng.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Truy vấn cho màn hình quản trị: lọc theo loại sự kiện và/hoặc tài khoản, cả hai đều tùy chọn.
     * Dùng một câu duy nhất với điều kiện "tham số NULL thì bỏ qua" để khỏi phải dựng
     * Specification cho một trường hợp đơn giản như thế này.
     */
    @Query("""
            select event from AuditEvent event
            where (:action is null or event.action = :action)
              and (:username is null
                   or event.actorUsername = :username
                   or event.targetUsername = :username)
            """)
    Page<AuditEvent> search(AuditAction action, String username, Pageable pageable);

    @Query("select event.action, count(event) from AuditEvent event "
            + "where event.occurredAt >= :since group by event.action")
    List<Object[]> countByActionSince(LocalDateTime since);
}
