package org.group7.wearwise.exception;

public class AiUnavailableException extends AppException {

    public AiUnavailableException(String message) {
        super(ErrorCode.AI_UNAVAILABLE, message);
    }
}
