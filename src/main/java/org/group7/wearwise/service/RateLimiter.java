package org.group7.wearwise.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bộ đếm token bucket trong bộ nhớ, dùng để chặn lạm dụng ở tầng ứng dụng.
 *
 * <p><b>Đây không phải lớp chống DDoS.</b> Tấn công thể tích làm nghẽn băng thông và bão hòa
 * thread pool từ trước khi request tới được Java. Lớp này chỉ ngăn <i>một người dùng</i> gọi quá
 * nhiều — đủ để bảo vệ quota AI, chặn dò mật khẩu và cào dữ liệu. Chống DDoS thật phải đặt ở
 * tầng trước server (Cloudflare, WAF, load balancer).</p>
 *
 * <p>Chọn token bucket thay vì đếm theo cửa sổ cố định vì cửa sổ cố định cho phép "dồn toa":
 * gọi hết hạn mức ở cuối cửa sổ này rồi gọi tiếp hết hạn mức đầu cửa sổ sau, thành ra gấp đôi
 * mức cho phép trong chốc lát. Bucket nạp lại đều theo thời gian nên không có khe hở đó.</p>
 *
 * <p>Trạng thái nằm trong bộ nhớ của từng tiến trình: chạy nhiều instance thì hạn mức thực tế
 * nhân lên theo số instance. Muốn chính xác khi scale ngang thì phải chuyển sang Redis.</p>
 */
@Component
public class RateLimiter {

    /** Dọn rác khi số bucket vượt ngưỡng này, tránh phình bộ nhớ vì IP lạ. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.nanoTime());

    /**
     * @param capacity số lượt tối đa được phép dồn lại
     * @param window   khoảng thời gian để nạp đầy lại từ 0
     * @return số giây phải chờ, hoặc 0 nếu request được phép đi tiếp
     */
    public long checkAndConsume(String key, int capacity, Duration window) {
        if (capacity <= 0) {
            return 0; // Hạn mức <= 0 nghĩa là tắt giới hạn cho nhóm này.
        }

        cleanupIfNeeded(window);

        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity));
        return bucket.tryConsume(capacity, window.toNanos());
    }

    /** Xóa các bucket đã đầy lại hoàn toàn — chúng không mang thông tin gì nữa. */
    private void cleanupIfNeeded(Duration window) {
        if (buckets.size() < CLEANUP_THRESHOLD) {
            return;
        }

        long now = System.nanoTime();
        long previous = lastCleanup.get();
        if (now - previous < window.toNanos() || !lastCleanup.compareAndSet(previous, now)) {
            return;
        }

        buckets.entrySet().removeIf(entry -> entry.getValue().isIdle(now, window.toNanos()));
    }

    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;

        private Bucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized long tryConsume(int capacity, long windowNanos) {
            refill(capacity, windowNanos);

            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }

            // Còn thiếu bao nhiêu token thì chờ bấy nhiêu thời gian nạp.
            double missing = 1 - tokens;
            double nanosPerToken = (double) windowNanos / capacity;
            return Math.max(1, (long) Math.ceil(missing * nanosPerToken / 1_000_000_000d));
        }

        private void refill(int capacity, long windowNanos) {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            lastRefillNanos = now;

            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + (double) elapsed * capacity / windowNanos);
            }
        }

        private synchronized boolean isIdle(long now, long windowNanos) {
            return now - lastRefillNanos > windowNanos;
        }
    }
}
