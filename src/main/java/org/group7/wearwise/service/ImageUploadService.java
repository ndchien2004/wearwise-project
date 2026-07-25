package org.group7.wearwise.service;

import org.group7.wearwise.exception.TryOnImageException;
import org.group7.wearwise.exception.TryOnUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Tải ảnh minh họa cho món đồ trong tủ lên Cloudinary rồi trả về URL.
 * Kiểm tra nhẹ hơn ảnh thử đồ (ảnh sản phẩm có thể mọi tỉ lệ), chỉ cần đúng định dạng và không quá lớn.
 */
@Service
public class ImageUploadService {

    /** Ảnh minh họa món đồ: JPG/PNG, tối đa 10MB. */
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png");

    private final CloudinaryService cloudinaryService;

    public ImageUploadService(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
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

        byte[] bytes = validateAndReadImage(file);
        return cloudinaryService.uploadImage(bytes, file.getContentType(), subFolder);
    }

    private byte[] validateAndReadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new TryOnImageException("Vui lòng chọn một ảnh để tải lên.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new TryOnImageException("Ảnh phải ở định dạng JPG hoặc PNG.");
        }

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new TryOnImageException("Ảnh quá lớn (tối đa 10MB). Vui lòng chọn ảnh nhẹ hơn.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new TryOnImageException("Không đọc được tệp ảnh. Vui lòng thử lại.");
        }

        if (bytes.length == 0) {
            throw new TryOnImageException("Tệp ảnh rỗng. Vui lòng chọn ảnh khác.");
        }

        // Xác nhận đây thực sự là ảnh giải mã được, tránh tải file hỏng/không phải ảnh.
        BufferedImage image = decodeImage(bytes);
        if (image == null) {
            throw new TryOnImageException("Không đọc được ảnh. Hãy thử một ảnh JPG/PNG rõ nét khác.");
        }

        return bytes;
    }

    private BufferedImage decodeImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            return null;
        }
    }
}
