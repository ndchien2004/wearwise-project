package org.group7.wearwise.exception;

public class TryOnImageException extends AppException {

    public TryOnImageException(String message) {
        super(ErrorCode.IMAGE_INVALID, message);
    }
}
