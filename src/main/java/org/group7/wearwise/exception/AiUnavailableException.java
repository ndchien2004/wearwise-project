package org.group7.wearwise.exception;

/**
 * Tính năng gợi ý AI tạm thời không dùng được: chưa cấu hình API key Gemini,
 * hoặc dịch vụ Gemini gặp sự cố / trả về dữ liệu không đọc được. → HTTP 503.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
