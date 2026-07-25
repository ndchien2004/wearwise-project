package org.group7.wearwise.exception;

/** Mã chia sẻ không tồn tại, đã bị thu hồi hoặc đã hết hạn. */
public class ShareNotFoundException extends AppException {

    public ShareNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ShareNotFoundException forCode(String code) {
        return new ShareNotFoundException(
                ErrorCode.SHARE_NOT_FOUND, "Không tìm thấy mã chia sẻ \"" + code + "\".");
    }

    public static ShareNotFoundException revoked() {
        return new ShareNotFoundException(
                ErrorCode.SHARE_REVOKED, "Mã chia sẻ này đã bị người tạo thu hồi.");
    }

    public static ShareNotFoundException expired() {
        return new ShareNotFoundException(ErrorCode.SHARE_EXPIRED, "Mã chia sẻ này đã hết hạn.");
    }
}
