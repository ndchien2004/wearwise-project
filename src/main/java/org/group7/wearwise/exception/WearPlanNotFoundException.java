package org.group7.wearwise.exception;

public class WearPlanNotFoundException extends AppException {

    public WearPlanNotFoundException(Long id) {
        super(ErrorCode.WEAR_PLAN_NOT_FOUND, "Không tìm thấy đợt kế hoạch với id " + id + ".");
    }
}
