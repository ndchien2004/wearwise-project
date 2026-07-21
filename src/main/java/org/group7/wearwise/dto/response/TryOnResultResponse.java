package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.TryOnResult;

import java.time.LocalDateTime;

public record TryOnResultResponse(
        Long id,
        Long clothingItemId,
        String clothingItemName,
        String garmentImageUrl,
        String resultImageUrl,
        LocalDateTime createdAt
) {

    public static TryOnResultResponse from(TryOnResult result) {
        return new TryOnResultResponse(
                result.getId(),
                result.getClothingItemId(),
                result.getClothingItemName(),
                result.getGarmentImageUrl(),
                result.getResultImageUrl(),
                result.getCreatedAt()
        );
    }
}
