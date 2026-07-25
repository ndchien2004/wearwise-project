package org.group7.wearwise.exception;

public class TryOnResultNotFoundException extends AppException {

    public TryOnResultNotFoundException(Long id) {
        super(ErrorCode.TRY_ON_RESULT_NOT_FOUND, "Không tìm thấy ảnh thử đồ với id " + id + ".");
    }
}
