package org.group7.wearwise.dto.response;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        int status,
        String message,
        Map<String, String> errors,
        Instant timestamp
) {

    public static ApiErrorResponse of(int status, String message, Map<String, String> errors) {
        return new ApiErrorResponse(status, message, errors, Instant.now());
    }
}
