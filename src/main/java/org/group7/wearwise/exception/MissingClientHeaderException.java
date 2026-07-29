package org.group7.wearwise.exception;

/**
 * Ném ra khi một request muốn dùng refresh token đựng trong cookie nhưng không kèm header
 * nhận dạng client. Đây là lớp chống CSRF: cookie được trình duyệt gửi tự động, còn header
 * tự đặt thì chỉ mã JavaScript của chính origin hợp lệ mới thêm được.
 */
public class MissingClientHeaderException extends AppException {

    public MissingClientHeaderException(String headerName) {
        super(ErrorCode.MISSING_CLIENT_HEADER,
                "Yêu cầu thiếu header " + headerName + ". Hãy gọi API từ ứng dụng WearWise.");
    }
}
