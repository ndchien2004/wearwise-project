package org.group7.wearwise.exception;

public class OutfitNotFoundException extends AppException {

    public OutfitNotFoundException(Long id) {
        super(ErrorCode.OUTFIT_NOT_FOUND, "Không tìm thấy outfit với id " + id + ".");
    }
}
