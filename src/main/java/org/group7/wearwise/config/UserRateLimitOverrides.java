package org.group7.wearwise.config;

import org.group7.wearwise.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ nhớ đệm các hạn mức riêng mà quản trị viên đã đặt cho từng tài khoản.
 *
 * <p><b>Vì sao phải đệm.</b> {@code RateLimitFilter} chạy trên <i>mọi</i> request. Đọc thẳng
 * database ở đó nghĩa là thêm một truy vấn cho mỗi lần gọi API — trả giá bằng hiệu năng của toàn
 * hệ thống chỉ để phục vụ một tính năng gần như không bao giờ được dùng tới. Thay vào đó, giữ
 * nguyên bảng trong bộ nhớ và cập nhật ngay tại thời điểm quản trị viên bấm nút.
 *
 * <p>Chỉ nạp những tài khoản <i>có</i> hạn mức riêng — thường là vài dòng, không phải toàn bộ
 * người dùng. Tài khoản vắng mặt trong bảng đồng nghĩa "dùng mức mặc định".
 *
 * <p><b>Hạn chế khi chạy nhiều instance:</b> mỗi tiến trình giữ bản sao riêng, nên thay đổi từ
 * instance này không lan sang instance kia cho tới lần khởi động lại. Chấp nhận được vì bản thân
 * {@link org.group7.wearwise.service.RateLimiter} cũng đã là bộ đếm cục bộ theo tiến trình —
 * muốn chính xác khi scale ngang thì cả hai phải chuyển sang Redis cùng lúc.
 */
@Component
public class UserRateLimitOverrides {

    private static final Logger log = LoggerFactory.getLogger(UserRateLimitOverrides.class);

    private final AppUserRepository appUserRepository;
    private final Map<String, Overrides> byUsername = new ConcurrentHashMap<>();

    public UserRateLimitOverrides(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Nạp sau khi ứng dụng sẵn sàng chứ không phải trong constructor: lúc dựng bean thì
     * EntityManagerFactory có thể chưa khởi tạo xong, và một truy vấn hỏng ở đó sẽ làm chết
     * cả tiến trình vì một tính năng phụ.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadFromDatabase() {
        try {
            byUsername.clear();
            appUserRepository.findAllWithRateLimitOverride().forEach(user -> put(
                    user.getUsername(), user.getAiQuotaPerHour(), user.getExternalQuotaPerHour()));

            if (!byUsername.isEmpty()) {
                log.info("Đã nạp hạn mức riêng cho {} tài khoản.", byUsername.size());
            }
        } catch (RuntimeException exception) {
            log.error("Không nạp được hạn mức riêng; mọi tài khoản sẽ dùng mức mặc định.", exception);
        }
    }

    public Optional<Integer> aiPerHour(String username) {
        return Optional.ofNullable(byUsername.get(username)).map(Overrides::aiPerHour);
    }

    public Optional<Integer> externalPerHour(String username) {
        return Optional.ofNullable(byUsername.get(username)).map(Overrides::externalPerHour);
    }

    /** Gọi ngay sau khi quản trị viên lưu thay đổi, để hạn mức mới có hiệu lực tức thì. */
    public void put(String username, Integer aiPerHour, Integer externalPerHour) {
        if (aiPerHour == null && externalPerHour == null) {
            byUsername.remove(username);
            return;
        }

        byUsername.put(username, new Overrides(aiPerHour, externalPerHour));
    }

    /**
     * @param aiPerHour       NULL nghĩa là chỉ nới nhóm còn lại, nhóm này vẫn theo mặc định
     * @param externalPerHour tương tự
     */
    private record Overrides(Integer aiPerHour, Integer externalPerHour) {
    }
}
