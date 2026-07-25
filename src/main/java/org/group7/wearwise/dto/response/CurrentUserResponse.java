package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.AppUser;

import java.time.LocalDateTime;

public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String role,
        String avatarUrl,
        LocalDateTime createdAt,
        LocalDateTime passwordChangedAt
) {

    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getAvatarUrl(),
                user.getCreatedAt(),
                user.getPasswordChangedAt()
        );
    }
}
