package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.AppUser;

import java.time.LocalDateTime;

public record CurrentUserResponse(
        Long id,
        String username,
        String role,
        LocalDateTime createdAt
) {

    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
