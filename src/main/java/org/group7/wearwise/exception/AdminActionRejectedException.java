package org.group7.wearwise.exception;

/** Thao tác quản trị hợp lệ về cú pháp nhưng bị chặn vì một ràng buộc an toàn. */
public class AdminActionRejectedException extends AppException {

    public AdminActionRejectedException(String message) {
        super(ErrorCode.ADMIN_ACTION_REJECTED, message);
    }
}
