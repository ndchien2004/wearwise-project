package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.CurrentUserResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * Cập nhật hồ sơ người dùng (ảnh đại diện). Tách khỏi AuthService để không đụng tới luồng
 * đăng nhập/đăng ký và bộ test dựng AuthService bằng constructor.
 */
@Service
public class UserProfileService {

    private final AppUserRepository appUserRepository;
    private final ImageUploadService imageUploadService;

    public UserProfileService(AppUserRepository appUserRepository, ImageUploadService imageUploadService) {
        this.appUserRepository = appUserRepository;
        this.imageUploadService = imageUploadService;
    }

    /** Tải ảnh đại diện mới lên Cloudinary rồi lưu URL vào tài khoản. */
    @Transactional
    public CurrentUserResponse updateAvatar(String username, MultipartFile file) {
        AppUser user = getUser(username);
        String url = imageUploadService.uploadAvatarImage(file);
        user.setAvatarUrl(url);
        appUserRepository.save(user);
        return CurrentUserResponse.from(user);
    }

    /** Gỡ ảnh đại diện, quay về chữ cái đầu tên đăng nhập. */
    @Transactional
    public CurrentUserResponse removeAvatar(String username) {
        AppUser user = getUser(username);
        user.setAvatarUrl(null);
        appUserRepository.save(user);
        return CurrentUserResponse.from(user);
    }

    private AppUser getUser(String username) {
        if (username == null || username.trim().isBlank()) {
            throw new AuthenticationFailedException();
        }
        return appUserRepository.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(AuthenticationFailedException::new);
    }
}
