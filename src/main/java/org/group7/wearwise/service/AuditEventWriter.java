package org.group7.wearwise.service;

import org.group7.wearwise.config.ClientIpResolver;
import org.group7.wearwise.entity.AuditEvent;
import org.group7.wearwise.enums.AuditAction;
import org.group7.wearwise.repository.AuditEventRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Bean riêng chỉ để làm một việc: ghi bản ghi kiểm toán trong <b>transaction độc lập</b>.
 *
 * <p>Phải tách khỏi {@link AuditLogService} chứ không gộp thành một phương thức private: Spring
 * hiện thực {@code @Transactional} bằng proxy, nên một phương thức được gọi từ chính bên trong
 * class sẽ không đi qua proxy và {@code REQUIRES_NEW} sẽ âm thầm không có tác dụng. Đi qua một
 * bean khác mới đảm bảo proxy được kích hoạt.
 *
 * <p>Vì sao cần transaction riêng: phần lớn sự kiện đáng ghi nhất xảy ra ngay trước một exception
 * (đăng nhập sai, phát hiện refresh token bị dùng lại). Nếu nằm chung transaction nghiệp vụ,
 * chúng sẽ bị rollback cuốn theo và biến mất đúng lúc cần nhất.
 */
@Component
class AuditEventWriter {

    /** Cắt cho vừa cột; giá trị dài hơn hầu như luôn là dữ liệu rác do client gửi lên. */
    private static final int MAX_USERNAME_LENGTH = 190;
    private static final int MAX_DETAIL_LENGTH = 500;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final AuditEventRepository auditEventRepository;
    private final ClientIpResolver clientIpResolver;

    AuditEventWriter(AuditEventRepository auditEventRepository, ClientIpResolver clientIpResolver) {
        this.auditEventRepository = auditEventRepository;
        this.clientIpResolver = clientIpResolver;
    }

    /** @return bản ghi đã lưu (đã có id và mốc thời gian) để phía gọi phát đi cho màn hình quản trị */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent write(AuditAction action, String actorUsername, String targetUsername, String detail) {
        AuditEvent event = AuditEvent.builder()
                .action(action)
                .occurredAt(LocalDateTime.now())
                .actorUsername(truncate(actorUsername, MAX_USERNAME_LENGTH))
                .targetUsername(truncate(targetUsername, MAX_USERNAME_LENGTH))
                .detail(truncate(detail, MAX_DETAIL_LENGTH))
                .build();

        // Không có ngữ cảnh HTTP (tác vụ nền, unit test) thì để trống IP thay vì bịa giá trị.
        clientIpResolver.currentRequest().ifPresent(request -> {
            event.setIpAddress(clientIpResolver.resolve(request));
            event.setUserAgent(truncate(request.getHeader(HttpHeaders.USER_AGENT), MAX_USER_AGENT_LENGTH));
        });

        return auditEventRepository.save(event);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
