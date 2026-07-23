package org.group7.wearwise.controller;

import org.group7.wearwise.dto.response.ImageUploadResponse;
import org.group7.wearwise.service.ImageUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Tải ảnh minh họa cho món đồ trong tủ lên Cloudinary, trả về URL để lưu vào DB. */
@RestController
@RequestMapping("/api/images")
public class ImageUploadController {

    private final ImageUploadService imageUploadService;

    public ImageUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping("/clothing")
    public ImageUploadResponse uploadClothingImage(@RequestParam("file") MultipartFile file) {
        return new ImageUploadResponse(imageUploadService.uploadClothingImage(file));
    }

    @PostMapping("/outfit")
    public ImageUploadResponse uploadOutfitImage(@RequestParam("file") MultipartFile file) {
        return new ImageUploadResponse(imageUploadService.uploadOutfitImage(file));
    }
}
