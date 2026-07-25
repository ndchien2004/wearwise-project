package org.group7.wearwise.exception;

import java.util.List;
import java.util.Map;

/**
 * Không xóa cứng được món đồ vì nó còn nằm trong outfit hoặc đã có lịch sử mặc.
 * Giao diện bắt mã {@link ErrorCode#CLOTHING_ITEM_IN_USE} để mời người dùng ẩn thay vì xóa.
 */
public class ClothingItemInUseException extends AppException {

    public ClothingItemInUseException(String itemName, List<String> outfitNames, int wearCount) {
        super(
                ErrorCode.CLOTHING_ITEM_IN_USE,
                buildMessage(itemName, outfitNames, wearCount),
                Map.of(
                        "outfitCount", String.valueOf(outfitNames.size()),
                        "outfits", String.join(", ", outfitNames),
                        "wearCount", String.valueOf(wearCount)
                )
        );
    }

    private static String buildMessage(String itemName, List<String> outfitNames, int wearCount) {
        StringBuilder message = new StringBuilder("Không thể xóa \"").append(itemName).append("\" vì ");

        if (!outfitNames.isEmpty()) {
            message.append("đang nằm trong ").append(outfitNames.size()).append(" outfit (")
                    .append(String.join(", ", outfitNames)).append(")");
            if (wearCount > 0) {
                message.append(" và ");
            }
        }

        if (wearCount > 0) {
            message.append("đã có ").append(wearCount).append(" lượt mặc được ghi nhận");
        }

        return message.append(". Hãy ẩn món đồ này thay vì xóa để giữ nguyên lịch sử.").toString();
    }
}
