package org.group7.wearwise.dto.response;

/**
 * Trạng thái tính năng thử đồ của người dùng hiện tại: đã tải ảnh bản thân chưa,
 * và các dịch vụ bên ngoài đã được cấu hình chưa (Cloudinary để lưu ảnh, nhà cung cấp thử đồ để ghép ảnh).
 */
public record TryOnProfileResponse(
        String bodyPhotoUrl,
        boolean hasBodyPhoto,
        boolean cloudinaryConfigured,
        boolean providerConfigured,
        boolean ready
) {

    public static TryOnProfileResponse of(String bodyPhotoUrl, boolean cloudinaryConfigured, boolean providerConfigured) {
        boolean hasBodyPhoto = bodyPhotoUrl != null && !bodyPhotoUrl.isBlank();
        boolean ready = hasBodyPhoto && cloudinaryConfigured && providerConfigured;
        return new TryOnProfileResponse(bodyPhotoUrl, hasBodyPhoto, cloudinaryConfigured, providerConfigured, ready);
    }
}
