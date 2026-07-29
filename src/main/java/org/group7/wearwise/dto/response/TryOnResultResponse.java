package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.TryOnResult;

import java.time.LocalDateTime;

public record TryOnResultResponse(
        Long id,
        Long clothingItemId,
        String clothingItemName,
        Long outfitId,
        String outfitName,
        String garmentImageUrl,
        String resultImageUrl,
        /** Ảnh nền của lần ghép này — giao diện đặt cạnh ảnh kết quả để so sánh trước/sau. */
        String baseImageUrl,
        /** Khác NULL nghĩa là ảnh này được mặc chồng lên một kết quả trước đó. */
        Long baseResultId,
        LocalDateTime createdAt
) {

    public static TryOnResultResponse from(TryOnResult result) {
        return new TryOnResultResponse(
                result.getId(),
                result.getClothingItemId(),
                result.getClothingItemName(),
                result.getOutfitId(),
                result.getOutfitName(),
                result.getGarmentImageUrl(),
                result.getResultImageUrl(),
                result.getBaseImageUrl(),
                result.getBaseResultId(),
                result.getCreatedAt()
        );
    }
}
