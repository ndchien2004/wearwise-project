package org.group7.wearwise.exception;

public class TryOnResultNotFoundException extends RuntimeException {

    public TryOnResultNotFoundException(Long id) {
        super("Không tìm thấy ảnh thử đồ với id " + id + ".");
    }
}
