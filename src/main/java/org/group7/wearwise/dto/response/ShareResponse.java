package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.Share;
import org.group7.wearwise.enums.ShareTargetType;

import java.time.LocalDateTime;

/** Một mã chia sẻ nhìn từ phía người tạo ra nó. */
public record ShareResponse(
        Long id,
        String code,
        ShareTargetType targetType,
        Long targetId,
        String targetName,
        String targetImageUrl,
        int itemCount,
        int importCount,
        boolean active,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {

    public static ShareResponse from(Share share, LocalDateTime now) {
        Outfit outfit = share.getOutfit();
        ClothingItem item = share.getClothingItem();

        boolean isOutfit = share.getTargetType() == ShareTargetType.OUTFIT;
        Long targetId = isOutfit ? outfit.getId() : item.getId();
        String targetName = isOutfit ? outfit.getName() : item.getName();

        return new ShareResponse(
                share.getId(),
                share.getCode(),
                share.getTargetType(),
                targetId,
                targetName,
                isOutfit ? coverImageOf(outfit) : item.getImageUrl(),
                isOutfit ? outfit.getClothingItems().size() : 1,
                share.getImportCount(),
                share.isUsable(now),
                share.getExpiresAt(),
                share.getCreatedAt()
        );
    }

    /** Ảnh đại diện của bộ; chưa có thì lấy tạm ảnh của món đầu tiên có ảnh. */
    private static String coverImageOf(Outfit outfit) {
        if (outfit.getImageUrl() != null) {
            return outfit.getImageUrl();
        }

        return outfit.getClothingItems().stream()
                .map(ClothingItem::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }
}
