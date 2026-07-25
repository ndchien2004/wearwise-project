package org.group7.wearwise.exception;

public class ClothingItemNotFoundException extends AppException {

    public ClothingItemNotFoundException(Long id) {
        super(ErrorCode.CLOTHING_ITEM_NOT_FOUND, "Không tìm thấy món đồ với id " + id + ".");
    }
}
