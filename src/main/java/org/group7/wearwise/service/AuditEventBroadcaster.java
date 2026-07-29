package org.group7.wearwise.service;

import jakarta.annotation.PreDestroy;
import org.group7.wearwise.dto.response.AuditEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Đẩy sự kiện kiểm toán tới các màn hình quản trị đang mở, qua Server-Sent Events.
 *
 * <h2>Vì sao SSE chứ không phải WebSocket hay polling</h2>
 *
 * <p>Luồng dữ liệu ở đây chỉ có một chiều (server → trình duyệt) và thưa. WebSocket là kênh hai
 * chiều, kéo theo cả một tầng giao thức không dùng tới. Polling thì hoặc trễ, hoặc phải hỏi liên
 * tục cho một thứ hầu như không thay đổi. SSE chạy trên chính HTTP sẵn có, tự động kết nối lại,
 * và giữ nguyên được cơ chế xác thực hiện tại.
 *
 * <h2>Giới hạn cần biết</h2>
 *
 * <p>Danh sách kết nối nằm <b>trong bộ nhớ của từng tiến trình</b>. Chạy nhiều instance thì quản
 * trị viên đang nối vào instance A sẽ không thấy sự kiện phát sinh ở instance B. Muốn đúng khi
 * scale ngang phải đẩy qua một kênh chung (Redis pub/sub) — cùng bài toán với
 * {@link RateLimiter} và bộ đệm hạn mức.
 *
 * <p>Đây là kênh <i>thông báo</i>, không phải nguồn dữ liệu tin cậy: mất kết nối là mất những sự
 * kiện xảy ra trong lúc đó. Nguồn thật vẫn là bảng {@code audit_events}, và giao diện tải lại
 * danh sách mỗi khi kết nối lại.
 */
@Component
public class AuditEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(AuditEventBroadcaster.class);

    /**
     * Thời gian sống tối đa của một kết nối. Trình duyệt tự nối lại sau khi hết hạn, nên đặt hữu
     * hạn để không tích tụ kết nối chết mà tầng mạng đã âm thầm cắt.
     */
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    /**
     * Nhịp gửi tín hiệu giữ kết nối. Nhiều proxy đóng kết nối HTTP im lặng quá 60 giây, nên
     * phải nhắn gì đó thường xuyên hơn thế.
     */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(25);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Bộ hẹn giờ riêng thay vì {@code @Scheduled}: nhịp tim là chuyện nội bộ của lớp này, không
     * đáng để bật cơ chế lập lịch cho toàn ứng dụng.
     */
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "audit-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public AuditEventBroadcaster() {
        heartbeat.scheduleAtFixedRate(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL.toSeconds(),
                HEARTBEAT_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));

        emitters.add(emitter);

        // Gửi ngay một sự kiện mở màn: trình duyệt biết kết nối đã thông, và nếu proxy có đệm
        // phản hồi thì đây là thứ buộc nó phải đẩy dữ liệu đi.
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException exception) {
            emitters.remove(emitter);
            emitter.completeWithError(exception);
        }

        return emitter;
    }

    /** Gọi sau khi bản ghi đã được lưu — xem {@link AuditLogService}. */
    public void publish(AuditEventResponse event) {
        send(SseEmitter.event().name("audit").data(event));
    }

    private void sendHeartbeat() {
        if (!emitters.isEmpty()) {
            send(SseEmitter.event().name("heartbeat").data(""));
        }
    }

    /** Kết nối nào lỗi thì bỏ khỏi danh sách; một client chết không được làm hỏng lượt gửi của client khác. */
    private void send(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (Exception exception) {
                emitters.remove(emitter);
                log.debug("Gỡ kết nối SSE đã đóng: {}", exception.toString());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }
}
