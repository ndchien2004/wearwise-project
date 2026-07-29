package org.group7.wearwise.service;

import org.group7.wearwise.exception.TryOnUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tải ảnh minh họa cho món đồ trong tủ lên Cloudinary rồi trả về URL.
 * Kiểm tra nhẹ hơn ảnh thử đồ (ảnh sản phẩm có thể mọi tỉ lệ): định dạng, dung lượng và
 * độ phân giải do {@link ImageValidator} lo, ở đây không thêm ràng buộc gì nữa.
 */
@Service
public class ImageUploadService {

    private final CloudinaryService cloudinaryService;
    private final ImageValidator imageValidator;

    public ImageUploadService(CloudinaryService cloudinaryService, ImageValidator imageValidator) {
        this.cloudinaryService = cloudinaryService;
        this.imageValidator = imageValidator;
    }

    /** Tải ảnh món đồ lên thư mục wearwise/items và trả về secure URL. */
    public String uploadClothingImage(MultipartFile file) {
        return upload(file, "items");
    }

    /** Tải ảnh đại diện outfit lên thư mục wearwise/outfits và trả về secure URL. */
    public String uploadOutfitImage(MultipartFile file) {
        return upload(file, "outfits");
    }

    /** Tải ảnh đại diện người dùng lên thư mục wearwise/avatars và trả về secure URL. */
    public String uploadAvatarImage(MultipartFile file) {
        return upload(file, "avatars");
    }

    private String upload(MultipartFile file, String subFolder) {
        if (!cloudinaryService.isConfigured()) {
            throw new TryOnUnavailableException(
                    "Cloudinary chưa được cấu hình. Hãy điền cloud-name, api-key và api-secret vào application.properties.");
        }

        byte[] bytes = imageValidator.read(file, "Vui lòng chọn một ảnh để tải lên.").bytes();
        return cloudinaryService.uploadImage(bytes, file.getContentType(), subFolder);
    }
}
