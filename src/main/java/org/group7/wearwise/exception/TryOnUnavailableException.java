package org.group7.wearwise.exception;

public class TryOnUnavailableException extends AppException {

    public TryOnUnavailableException(String message) {
        super(ErrorCode.TRY_ON_UNAVAILABLE, message);
    }
}
