package org.group7.wearwise.exception;

/**
 * Ảnh do người dùng cung cấp không đủ điều kiện để thử đồ (sai định dạng, quá nhỏ,
 * tỉ lệ không phù hợp, chưa có ảnh...). Lỗi này người dùng có thể tự khắc phục → HTTP 400.
 */
public class TryOnImageException extends RuntimeException {

    public TryOnImageException(String message) {
        super(message);
    }
}
