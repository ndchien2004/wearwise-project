package org.group7.wearwise.exception;

public class OutfitPlanNotFoundException extends AppException {

    public OutfitPlanNotFoundException(Long id) {
        super(ErrorCode.OUTFIT_PLAN_NOT_FOUND, "Không tìm thấy kế hoạch mặc với id " + id + ".");
    }
}
