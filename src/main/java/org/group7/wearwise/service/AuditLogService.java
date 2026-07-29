package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.AuditEventResponse;
import org.group7.wearwise.entity.AuditEvent;
import org.group7.wearwise.enums.AuditAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cổng vào duy nhất để ghi nhật ký kiểm toán.
 *
 * <p>Nhiệm vụ của lớp này là <b>nuốt mọi lỗi</b>: nhật ký hỏng không được phép làm hỏng việc người
 * dùng đang làm — không ai muốn đăng nhập thất bại chỉ vì bảng log đầy đĩa. Lỗi vẫn được ghi ra
 * logger thường để còn phát hiện được. Phần transaction độc lập nằm ở {@link AuditEventWriter}.
 *
 * <p><b>Tuyệt đối không</b> đưa mật khẩu, token, hay dữ liệu cá nhân vào {@code detail}: bảng này
 * được quản trị viên đọc và thường bị sao chép ra ngoài khi điều tra sự cố.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditEventWriter writer;
    private final AuditEventBroadcaster broadcaster;

    AuditLogService(AuditEventWriter writer, AuditEventBroadcaster broadcaster) {
        this.writer = writer;
        this.broadcaster = broadcaster;
    }

    /** Sự kiện tự thực hiện: người gây ra cũng là người bị tác động. */
    public void record(AuditAction action, String username, String detail) {
        record(action, username, username, detail);
    }

    public void record(AuditAction action, String actorUsername, String targetUsername, String detail) {
        AuditEvent saved;
        try {
            saved = writer.write(action, actorUsername, targetUsername, detail);
        } catch (RuntimeException exception) {
            log.error("Không ghi được nhật ký kiểm toán: action={} actor={} target={}",
                    action, actorUsername, targetUsername, exception);
            return;
        }

        // Phát sau khi writer trả về, tức là transaction REQUIRES_NEW đã commit — không đẩy đi
        // một sự kiện mà database rốt cuộc lại không lưu.
        try {
            broadcaster.publish(AuditEventResponse.from(saved));
        } catch (RuntimeException exception) {
            log.warn("Không phát được sự kiện kiểm toán tới màn hình quản trị.", exception);
        }
    }
}
