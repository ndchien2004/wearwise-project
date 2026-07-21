package org.group7.wearwise.dto.response;

/** Kết quả tải ảnh lên Cloudinary — chỉ trả về URL vĩnh viễn để lưu vào DB. */
public record ImageUploadResponse(String url) {
}
