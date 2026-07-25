package org.group7.wearwise.exception;

import java.util.Map;

/** Vi phạm ràng buộc nghiệp vụ chung — dùng khi không cần một lớp exception riêng. */
public class BusinessRuleException extends AppException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleException(ErrorCode errorCode, String message, Map<String, String> details) {
        super(errorCode, message, details);
    }
}
