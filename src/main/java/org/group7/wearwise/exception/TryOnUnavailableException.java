package org.group7.wearwise.exception;

/**
 * Tính năng thử đồ ảo tạm thời không dùng được: chưa cấu hình API key, hoặc dịch vụ
 * bên ngoài (Cloudinary / tryon-api.com) gặp sự cố. → HTTP 503.
 */
public class TryOnUnavailableException extends RuntimeException {

    public TryOnUnavailableException(String message) {
        super(message);
    }
}
