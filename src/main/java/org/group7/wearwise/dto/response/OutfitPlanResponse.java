package org.group7.wearwise.dto.response;

import org.group7.wearwise.entity.OutfitPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record OutfitPlanResponse(
        Long id,
        LocalDate date,
        String note,
        Boolean completed,
        OutfitResponse outfit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static OutfitPlanResponse from(OutfitPlan plan) {
        return new OutfitPlanResponse(
                plan.getId(),
                plan.getPlanDate(),
                plan.getNote(),
                plan.getCompleted(),
                OutfitResponse.from(plan.getOutfit()),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}
