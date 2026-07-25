package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.Share;
import org.group7.wearwise.enums.ShareTargetType;

import java.time.LocalDateTime;

/**
 * Nội dung một mã chia sẻ nhìn từ phía người nhận: xem trước trang phục trước khi
 * quyết định chép về tủ đồ của mình.
 */
public record SharePreviewResponse(
        String code,
        ShareTargetType targetType,
        String ownerUsername,
        /** True khi người xem chính là chủ sở hữu — không cho tự chép của mình. */
        boolean mine,
        int importCount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        OutfitResponse outfit,
        ClothingItemResponse item
) {

    public static SharePreviewResponse from(Share share, String viewerUsername) {
        boolean isOutfit = share.getTargetType() == ShareTargetType.OUTFIT;

        return new SharePreviewResponse(
                share.getCode(),
                share.getTargetType(),
                share.getOwner().getUsername(),
                share.getOwner().getUsername().equals(viewerUsername),
                share.getImportCount(),
                share.getExpiresAt(),
                share.getCreatedAt(),
                isOutfit ? OutfitResponse.from(share.getOutfit()) : null,
                isOutfit ? null : ClothingItemResponse.from(share.getClothingItem())
        );
    }
}
