package org.group7.wearwise.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.group7.wearwise.exception.MissingClientHeaderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Đọc/ghi refresh token dưới dạng cookie {@code HttpOnly}.
 *
 * <h2>Vì sao không để refresh token cho JavaScript giữ</h2>
 *
 * <p>Trước đây frontend cất refresh token trong {@code localStorage}. Chỉ cần một lỗ XSS bất kỳ
 * — kể cả đến từ một gói npm phụ thuộc — là kẻ tấn công đọc được token sống 7 ngày và giữ được
 * quyền truy cập tài khoản kể cả sau khi nạn nhân đóng trình duyệt hay đổi mật khẩu ở thiết bị
 * khác. Cookie {@code HttpOnly} thì {@code document.cookie} không đọc tới được: XSS lúc đó chỉ
 * lợi dụng được phiên hiện tại (access token sống 15 phút), không mang phiên dài đi nơi khác.
 *
 * <h2>Đổi lại phải tự chống CSRF</h2>
 *
 * <p>Cookie được trình duyệt gửi kèm <i>tự động</i>, nên nếu chỉ dựa vào cookie thì một trang web
 * độc hại có thể dụ trình duyệt của nạn nhân gọi {@code /api/auth/refresh} và lấy được cặp token
 * mới. Chốt chặn ở đây là {@link #CLIENT_HEADER}: form HTML hay thẻ {@code <img>} không đặt được
 * header tự chế, còn JavaScript từ origin lạ thì vấp CORS preflight — mà danh sách origin hợp lệ
 * do {@code wearwise.cors.allowed-origins} quy định. Cookie chỉ được chấp nhận khi header có mặt.
 *
 * <p>{@code SameSite} là lớp phòng thủ thứ hai. Mặc định {@code Lax} đủ cho trường hợp frontend và
 * backend cùng site. Khi tách rời (frontend Cloudflare Pages, backend domain khác) trình duyệt sẽ
 * <b>không</b> gửi cookie {@code Lax} nữa, buộc phải đặt {@code None} — và {@code None} bắt buộc
 * đi kèm {@code Secure}, nên constructor từ chối khởi động nếu cấu hình sai cặp này.
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "wearwise_refresh";

    /** Header bắt buộc để cookie được chấp nhận; giá trị là gì không quan trọng, chỉ cần có mặt. */
    public static final String CLIENT_HEADER = "X-Wearwise-Client";

    /**
     * Cookie chỉ được gửi tới các endpoint xác thực. Mọi API còn lại dùng Bearer token nên
     * không cần thấy nó — thu hẹp phạm vi thì bề mặt tấn công cũng hẹp theo.
     */
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public RefreshTokenCookie(
            @Value("${wearwise.auth.refresh-cookie-secure:false}") boolean secure,
            @Value("${wearwise.auth.refresh-cookie-same-site:Lax}") String sameSite,
            @Value("${wearwise.auth.refresh-token-expires-in-seconds:604800}") long expiresInSeconds
    ) {
        String normalized = sameSite == null ? "" : sameSite.trim();
        if (!normalized.equalsIgnoreCase("Lax")
                && !normalized.equalsIgnoreCase("Strict")
                && !normalized.equalsIgnoreCase("None")) {
            throw new IllegalStateException(
                    "wearwise.auth.refresh-cookie-same-site chỉ nhận Lax, Strict hoặc None (đang là: " + sameSite + ").");
        }

        if (normalized.equalsIgnoreCase("None") && !secure) {
            throw new IllegalStateException(
                    "SameSite=None bắt buộc phải đi kèm cookie Secure — trình duyệt sẽ loại bỏ cookie nếu thiếu. "
                            + "Hãy đặt WEARWISE_AUTH_REFRESH_COOKIE_SECURE=true (backend phải chạy HTTPS).");
        }

        this.secure = secure;
        this.sameSite = capitalize(normalized);
        this.maxAge = Duration.ofSeconds(expiresInSeconds);
    }

    public void write(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, maxAge).toString());
    }

    /** Xóa cookie ở phía trình duyệt (max-age=0) — dùng khi đăng xuất. */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    /**
     * @return refresh token trong cookie, hoặc rỗng nếu không có cookie
     * @throws MissingClientHeaderException nếu có cookie nhưng thiếu {@link #CLIENT_HEADER}
     */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (!NAME.equals(cookie.getName())) {
                continue;
            }

            String value = cookie.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }

            // Kiểm tra đặt ngay tại chỗ đọc cookie: không có đường nào lấy được token mà
            // lách qua được bước này, kể cả khi sau này có thêm endpoint mới.
            if (request.getHeader(CLIENT_HEADER) == null) {
                throw new MissingClientHeaderException(CLIENT_HEADER);
            }

            return Optional.of(value);
        }

        return Optional.empty();
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(age)
                .build();
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
