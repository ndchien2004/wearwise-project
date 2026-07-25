package org.group7.wearwise.exception;

/** Mã chia sẻ không tồn tại, đã bị thu hồi hoặc đã hết hạn. */
public class ShareNotFoundException extends RuntimeException {

    public ShareNotFoundException(String message) {
        super(message);
    }

    public static ShareNotFoundException forCode(String code) {
        return new ShareNotFoundException("Không tìm thấy mã chia sẻ \"" + code + "\".");
    }
}
