package org.group7.wearwise.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Xác định địa chỉ IP thật của người gọi. Dùng chung cho giới hạn tần suất và nhật ký kiểm toán —
 * hai nơi này bắt buộc phải hiểu "người gọi" giống hệt nhau, nếu không sẽ có tình huống bị chặn
 * theo một IP nhưng lại ghi log một IP khác.
 *
 * <p>Header {@code X-Forwarded-For} chỉ được tin khi bật tường minh
 * ({@code wearwise.ratelimit.trust-forwarded-header}). Server phơi thẳng ra Internet mà tin
 * header này thì kẻ tấn công chỉ cần đổi giá trị mỗi request là thoát mọi giới hạn và bơm dữ
 * liệu rác vào nhật ký.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedHeader;

    public ClientIpResolver(
            @Value("${wearwise.ratelimit.trust-forwarded-header:false}") boolean trustForwardedHeader
    ) {
        this.trustForwardedHeader = trustForwardedHeader;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeader) {
            String forwarded = request.getHeader("CF-Connecting-IP");
            if (forwarded == null || forwarded.isBlank()) {
                forwarded = request.getHeader("X-Forwarded-For");
            }
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Lấy request đang xử lý từ ThreadLocal của Spring MVC. Cần cho tầng service — nơi không nhận
     * {@code HttpServletRequest} qua tham số nhưng vẫn muốn ghi nhật ký kèm IP.
     *
     * @return rỗng khi không chạy trong ngữ cảnh HTTP (tác vụ nền, unit test)
     */
    public Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }
}
