package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.AuditEvent;
import org.group7.wearwise.enums.AuditAction;

import java.time.LocalDateTime;

public record AuditEventResponse(
        Long id,
        AuditAction action,
        LocalDateTime occurredAt,
        String actorUsername,
        String targetUsername,
        String detail,
        String ipAddress,
        String userAgent
) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getAction(),
                event.getOccurredAt(),
                event.getActorUsername(),
                event.getTargetUsername(),
                event.getDetail(),
                event.getIpAddress(),
                event.getUserAgent()
        );
    }
}
