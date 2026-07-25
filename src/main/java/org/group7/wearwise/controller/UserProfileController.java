package org.group7.wearwise.controller;

import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.service.UserProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Cập nhật hồ sơ người dùng — hiện chỉ có ảnh đại diện. */
@RestController
@RequestMapping("/api/auth/avatar")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /** Tải ảnh đại diện lên (JPG/PNG, ≤ 10MB) và lưu URL Cloudinary. */
    @PostMapping
    public CurrentUserResponse uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        return userProfileService.updateAvatar(authentication.getName(), file);
    }

    @DeleteMapping
    public CurrentUserResponse deleteAvatar(Authentication authentication) {
        return userProfileService.removeAvatar(authentication.getName());
    }
}
