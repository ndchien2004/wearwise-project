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
import org.springframework.http.HttpMethod;
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
 *   <li><b>AI</b> — mỗi lượt gọi Gemini tốn tiền thật và quota có hạn, nên siết chặt nhất.</li>
 *   <li><b>EXTERNAL</b> — thử đồ ảo và tải ảnh: cũng gọi ra dịch vụ trả tiền (tryon-api.com,
 *       Cloudinary), chỉ khác nhà cung cấp. Trước đây nhóm này rơi nhầm vào hạn mức GENERAL
 *       nên một tài khoản có thể đốt sạch quota trong vài phút.</li>
 *   <li><b>Xác thực</b> — tính theo IP để chặn dò mật khẩu và đăng ký hàng loạt. Khóa tài khoản
 *       sau 5 lần sai đã có sẵn, nhưng nó không ngăn được việc dò <i>nhiều tài khoản khác nhau</i>
 *       từ cùng một nguồn.</li>
 *   <li><b>Còn lại</b> — hạn mức rộng, chỉ để chặn cào dữ liệu và vòng lặp lỗi của client.</li>
 * </ul>
 *
 * <p>Hai nhóm đắt tiền chỉ tính trên request <b>POST</b>: chỉ POST mới kích hoạt lời gọi ra
 * ngoài, còn GET chỉ đọc lại kết quả đã lưu trong DB nên không có lý do gì tiêu quota.</p>
 *
 * <p>Chạy <b>sau</b> {@link BearerTokenAuthenticationFilter} để biết được username; request chưa
 * đăng nhập thì tính theo IP.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String USER_KEY_PREFIX = "user:";

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final UserRateLimitOverrides userRateLimitOverrides;
    /** Tự tạo thay vì inject: filter chạy ngoài MVC nên không phụ thuộc ObjectMapper của web layer. */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final int aiPerHour;
    private final int externalPerHour;
    private final int authPerMinute;
    private final int generalPerMinute;

    public RateLimitFilter(
            RateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            UserRateLimitOverrides userRateLimitOverrides,
            @Value("${wearwise.ratelimit.ai-per-hour:40}") int aiPerHour,
            @Value("${wearwise.ratelimit.external-per-hour:30}") int externalPerHour,
            @Value("${wearwise.ratelimit.auth-per-minute:20}") int authPerMinute,
            @Value("${wearwise.ratelimit.general-per-minute:240}") int generalPerMinute
    ) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.userRateLimitOverrides = userRateLimitOverrides;
        this.aiPerHour = aiPerHour;
        this.externalPerHour = externalPerHour;
        this.authPerMinute = authPerMinute;
        this.generalPerMinute = generalPerMinute;
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

        Group group = groupOf(request, path);
        String caller = callerKey(request);
        String key = group.name() + ':' + caller;
        long retryAfterSeconds = rateLimiter.checkAndConsume(key, capacityFor(group, caller), group.window());

        if (retryAfterSeconds > 0) {
            log.warn("Chặn vì vượt hạn mức: nhóm={} khóa={} path={}", group, key, path);
            writeTooManyRequests(response, group, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Group groupOf(HttpServletRequest request, String path) {
        // Chỉ POST mới thực sự gọi ra dịch vụ ngoài; GET chỉ đọc lại kết quả đã lưu.
        if (HttpMethod.POST.matches(request.getMethod())) {
            if (path.startsWith("/api/ai/")) {
                return Group.AI;
            }
            if (isPaidExternalCall(path)) {
                return Group.EXTERNAL;
            }
        }

        // /api/auth/me, /logout, /avatar là thao tác thường của người đã đăng nhập, không phải cửa dò mật khẩu.
        if (path.startsWith("/api/auth/")
                && !path.startsWith("/api/auth/me")
                && !path.startsWith("/api/auth/avatar")
                && !path.endsWith("/logout")) {
            return Group.AUTH;
        }
        return Group.GENERAL;
    }

    /**
     * Các đường dẫn mà một request POST sẽ kéo theo lời gọi có tính phí ra bên ngoài:
     * ghép ảnh thử đồ (tryon-api.com) và tải ảnh lên Cloudinary.
     */
    private static boolean isPaidExternalCall(String path) {
        return path.startsWith("/api/try-on/items")
                || path.startsWith("/api/try-on/outfits")
                || path.startsWith("/api/try-on/body-photo")
                || path.startsWith("/api/images")
                || path.startsWith("/api/auth/avatar");
    }

    /** Ưu tiên tính theo tài khoản; chưa đăng nhập thì theo IP (xem {@link ClientIpResolver}). */
    private String callerKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return "user:" + authentication.getName();
        }

        return "ip:" + clientIpResolver.resolve(request);
    }

    /**
     * Hạn mức mặc định của nhóm, trừ khi quản trị viên đã đặt riêng cho tài khoản này.
     *
     * <p>Chỉ áp dụng cho hai nhóm đắt tiền và chỉ với người đã đăng nhập — hạn mức tính theo IP
     * thì không gắn được với tài khoản nào để mà nới.</p>
     */
    private int capacityFor(Group group, String callerKey) {
        if (!callerKey.startsWith(USER_KEY_PREFIX)) {
            return group.capacity(this);
        }

        String username = callerKey.substring(USER_KEY_PREFIX.length());
        return switch (group) {
            case AI -> userRateLimitOverrides.aiPerHour(username).orElse(aiPerHour);
            case EXTERNAL -> userRateLimitOverrides.externalPerHour(username).orElse(externalPerHour);
            default -> group.capacity(this);
        };
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
        AI, EXTERNAL, AUTH, GENERAL;

        private int capacity(RateLimitFilter filter) {
            return switch (this) {
                case AI -> filter.aiPerHour;
                case EXTERNAL -> filter.externalPerHour;
                case AUTH -> filter.authPerMinute;
                case GENERAL -> filter.generalPerMinute;
            };
        }

        private Duration window() {
            return (this == AI || this == EXTERNAL) ? Duration.ofHours(1) : Duration.ofMinutes(1);
        }

        private String message(long retryAfterSeconds) {
            return switch (this) {
                case AI -> "Bạn đã dùng hết lượt AI cho giờ này. Hãy thử lại sau "
                        + Math.max(1, retryAfterSeconds / 60) + " phút, hoặc nhập thông tin thủ công.";
                case EXTERNAL -> "Bạn đã dùng hết lượt thử đồ / tải ảnh cho giờ này. Hãy thử lại sau "
                        + Math.max(1, retryAfterSeconds / 60) + " phút.";
                case AUTH -> "Quá nhiều lần thử. Hãy đợi " + retryAfterSeconds + " giây rồi thử lại.";
                case GENERAL -> "Bạn thao tác hơi nhanh. Hãy đợi " + retryAfterSeconds + " giây rồi thử lại.";
            };
        }
    }
}
