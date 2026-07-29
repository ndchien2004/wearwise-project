package org.group7.wearwise.service;

import org.group7.wearwise.exception.TryOnImageException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Kiểm tra và giải mã ảnh người dùng tải lên. Trước đây ba nơi (tải ảnh món đồ, ảnh cơ thể cho
 * thử đồ, ảnh cho nhận diện AI) mỗi nơi tự viết một bản gần giống nhau; gom về đây để mọi
 * đường vào đều được bảo vệ như nhau.
 *
 * <h2>Vì sao phải đọc kích thước trước khi giải mã</h2>
 *
 * <p>Giới hạn 10MB dung lượng tệp <b>không</b> giới hạn được bộ nhớ khi giải mã. PNG nén rất tốt
 * những vùng màu đồng nhất, nên một tệp 2MB hoàn toàn hợp lệ có thể mô tả ảnh 40000×40000 điểm
 * ảnh — {@code ImageIO.read()} sẽ cấp phát ~6GB heap và giết cả tiến trình. Đây là kiểu tấn công
 * "decompression bomb": chi phí của kẻ tấn công là vài MB, chi phí của server là toàn bộ RAM.
 *
 * <p>Cách chặn: đọc phần <i>header</i> của tệp ({@link ImageReader#getWidth}) — chỉ vài chục byte
 * đầu, không giải nén gì cả — để biết kích thước thật, rồi mới quyết định có giải mã hay không.
 */
@Component
public class ImageValidator {

    /** Dung lượng tệp tối đa; trùng với giới hạn multipart của Spring. */
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    /** Cạnh dài nhất cho phép. Máy ảnh điện thoại cao cấp nhất hiện nay cũng chỉ ~8000px. */
    static final int MAX_EDGE_PIXELS = 10_000;

    /**
     * Tổng số điểm ảnh cho phép (~30MP). Một {@code BufferedImage} 30MP đã chiếm ~120MB heap,
     * nên đây vừa đủ cho ảnh điện thoại thật mà vẫn chặn được ảnh phá hoại.
     */
    static final long MAX_TOTAL_PIXELS = 30_000_000L;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png");

    /** Định dạng thật sự đọc được từ nội dung tệp — không tin phần khai báo của client. */
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png");

    /**
     * Ảnh đã qua kiểm tra: giữ cả byte gốc (để tải lên nguyên bản) lẫn bản đã giải mã
     * (để đo kích thước, thu nhỏ...) nhằm khỏi phải giải mã hai lần.
     */
    public record ValidatedImage(byte[] bytes, BufferedImage image) {

        public int width() {
            return image.getWidth();
        }

        public int height() {
            return image.getHeight();
        }
    }

    /**
     * @param file               tệp người dùng gửi lên
     * @param missingFileMessage thông báo khi chưa chọn tệp — mỗi màn hình có lời văn riêng
     * @throws TryOnImageException với thông điệp tiếng Việt cho mọi trường hợp không hợp lệ
     */
    public ValidatedImage read(MultipartFile file, String missingFileMessage) {
        if (file == null || file.isEmpty()) {
            throw new TryOnImageException(missingFileMessage);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new TryOnImageException("Ảnh phải ở định dạng JPG hoặc PNG.");
        }

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new TryOnImageException("Ảnh quá lớn (tối đa 10MB). Vui lòng chọn ảnh nhẹ hơn.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new TryOnImageException("Không đọc được tệp ảnh. Vui lòng thử lại.");
        }

        if (bytes.length == 0) {
            throw new TryOnImageException("Tệp ảnh rỗng. Vui lòng chọn ảnh khác.");
        }

        return new ValidatedImage(bytes, decodeWithinLimits(bytes));
    }

    /**
     * Đọc kích thước từ header, từ chối ảnh vượt hạn mức, rồi mới giải mã toàn bộ.
     * Thứ tự này là điểm mấu chốt — đảo lại là mở cửa cho decompression bomb.
     */
    private BufferedImage decodeWithinLimits(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new TryOnImageException("Không đọc được ảnh. Hãy thử một ảnh JPG/PNG rõ nét khác.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new TryOnImageException("Không đọc được ảnh. Hãy thử một ảnh JPG/PNG rõ nét khác.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);

                // Nội dung tệp mới là bằng chứng; Content-Type do client tự khai và đổi được tùy ý.
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(format)) {
                    throw new TryOnImageException("Ảnh phải ở định dạng JPG hoặc PNG.");
                }

                checkDimensions(reader.getWidth(0), reader.getHeight(0));

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new TryOnImageException("Không đọc được ảnh. Hãy thử một ảnh JPG/PNG rõ nét khác.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new TryOnImageException("Không đọc được ảnh. Hãy thử một ảnh JPG/PNG rõ nét khác.");
        }
    }

    private void checkDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new TryOnImageException("Không đọc được ảnh. Hãy thử một ảnh JPG/PNG rõ nét khác.");
        }

        if (width > MAX_EDGE_PIXELS || height > MAX_EDGE_PIXELS
                || (long) width * height > MAX_TOTAL_PIXELS) {
            throw new TryOnImageException(
                    "Ảnh có độ phân giải quá lớn (" + width + "×" + height + " điểm ảnh). "
                            + "Vui lòng dùng ảnh nhỏ hơn " + MAX_EDGE_PIXELS + "px mỗi cạnh.");
        }
    }
}
