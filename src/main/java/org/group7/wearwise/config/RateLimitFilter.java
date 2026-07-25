package org.group7.wearwise.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.group7.wearwise.dto.response.ApiErrorResponse;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.service.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Chặn lạm dụng theo từng người dùng / địa chỉ IP, chia làm ba nhóm hạn mức vì ba nhóm endpoint
 * có mức độ "đắt" rất khác nhau:
 *
 * <ul>
 *   <li><b>AI</b> — mỗi lượt gọi tốn tiền thật và quota có hạn, nên siết chặt nhất.</li>
 *   <li><b>Xác thực</b> — tính theo IP để chặn dò mật khẩu và đăng ký hàng loạt. Khóa tài khoản
 *       sau 5 lần sai đã có sẵn, nhưng nó không ngăn được việc dò <i>nhiều tài khoản khác nhau</i>
 *       từ cùng một nguồn.</li>
 *   <li><b>Còn lại</b> — hạn mức rộng, chỉ để chặn cào dữ liệu và vòng lặp lỗi của client.</li>
 * </ul>
 *
 * <p>Chạy <b>sau</b> {@link BearerTokenAuthenticationFilter} để biết được username; request chưa
 * đăng nhập thì tính theo IP.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    /** Tự tạo thay vì inject: filter chạy ngoài MVC nên không phụ thuộc ObjectMapper của web layer. */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final int aiPerHour;
    private final int authPerMinute;
    private final int generalPerMinute;
    private final boolean trustForwardedHeader;

    public RateLimitFilter(
            RateLimiter rateLimiter,
            @Value("${wearwise.ratelimit.ai-per-hour:40}") int aiPerHour,
            @Value("${wearwise.ratelimit.auth-per-minute:20}") int authPerMinute,
            @Value("${wearwise.ratelimit.general-per-minute:240}") int generalPerMinute,
            @Value("${wearwise.ratelimit.trust-forwarded-header:false}") boolean trustForwardedHeader
    ) {
        this.rateLimiter = rateLimiter;
        this.aiPerHour = aiPerHour;
        this.authPerMinute = authPerMinute;
        this.generalPerMinute = generalPerMinute;
        this.trustForwardedHeader = trustForwardedHeader;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Chỉ giới hạn API; file tĩnh và swagger để yên.
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        Group group = groupOf(path);
        String key = group.name() + ':' + callerKey(request);
        long retryAfterSeconds = rateLimiter.checkAndConsume(key, group.capacity(this), group.window());

        if (retryAfterSeconds > 0) {
            log.warn("Chặn vì vượt hạn mức: nhóm={} khóa={} path={}", group, key, path);
            writeTooManyRequests(response, group, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Group groupOf(String path) {
        if (path.startsWith("/api/ai/")) {
            return Group.AI;
        }
        // /api/auth/me và /logout là thao tác thường của người đã đăng nhập, không phải cửa dò mật khẩu.
        if (path.startsWith("/api/auth/") && !path.startsWith("/api/auth/me") && !path.endsWith("/logout")) {
            return Group.AUTH;
        }
        return Group.GENERAL;
    }

    /**
     * Ưu tiên tính theo tài khoản; chưa đăng nhập thì theo IP.
     *
     * <p>Chỉ đọc header X-Forwarded-For khi được bật tường minh: nếu tin header này trong khi
     * server phơi trực tiếp ra Internet, kẻ tấn công chỉ cần đổi header mỗi request là thoát
     * sạch giới hạn.</p>
     */
    private String callerKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return "user:" + authentication.getName();
        }

        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
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

    private void writeTooManyRequests(HttpServletResponse response, Group group, long retryAfterSeconds)
            throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.RATE_LIMITED.getStatus().value(),
                ErrorCode.RATE_LIMITED,
                group.message(retryAfterSeconds),
                Map.of(),
                Map.of("retryAfterSeconds", String.valueOf(retryAfterSeconds))
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private enum Group {
        AI, AUTH, GENERAL;

        private int capacity(RateLimitFilter filter) {
            return switch (this) {
                case AI -> filter.aiPerHour;
                case AUTH -> filter.authPerMinute;
                case GENERAL -> filter.generalPerMinute;
            };
        }

        private Duration window() {
            return this == AI ? Duration.ofHours(1) : Duration.ofMinutes(1);
        }

        private String message(long retryAfterSeconds) {
            return switch (this) {
                case AI -> "Bạn đã dùng hết lượt AI cho giờ này. Hãy thử lại sau "
                        + Math.max(1, retryAfterSeconds / 60) + " phút, hoặc nhập thông tin thủ công.";
                case AUTH -> "Quá nhiều lần thử. Hãy đợi " + retryAfterSeconds + " giây rồi thử lại.";
                case GENERAL -> "Bạn thao tác hơi nhanh. Hãy đợi " + retryAfterSeconds + " giây rồi thử lại.";
            };
        }
    }
}
