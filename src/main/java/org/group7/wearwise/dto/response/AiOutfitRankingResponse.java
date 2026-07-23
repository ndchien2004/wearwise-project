package org.group7.wearwise.dto.response;

/** Một outfit CÓ SẴN được Gemini xếp hạng phù hợp với thời tiết, kèm lý do tiếng Việt. */
public record AiOutfitRankingResponse(
        OutfitResponse outfit,
        String reason
) {
}
