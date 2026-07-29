package org.group7.wearwise.service;

import org.group7.wearwise.exception.TryOnImageException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    @Test
    void acceptsAnOrdinaryPhoto() throws IOException {
        ImageValidator.ValidatedImage validated =
                validator.read(pngFile(picture(640, 480)), "thiếu ảnh");

        assertThat(validated.width()).isEqualTo(640);
        assertThat(validated.height()).isEqualTo(480);
    }

    /**
     * Trọng tâm của bài kiểm tra: tệp chỉ 69 byte nhưng khai báo ảnh 40000×40000 (1,6 tỉ điểm
     * ảnh ≈ 6GB heap khi giải mã). Nếu validator gọi thẳng {@code ImageIO.read()} thì JVM sẽ
     * chết vì OutOfMemoryError; đọc header trước thì nó bị từ chối tức thì.
     */
    @Test
    void rejectsDecompressionBombBeforeDecodingIt() {
        byte[] bomb = pngHeaderDeclaring(40_000, 40_000);
        assertThat(bomb.length).isLessThan(100);

        assertThatThrownBy(() -> validator.read(multipart(bomb, "image/png"), "thiếu ảnh"))
                .isInstanceOf(TryOnImageException.class)
                .hasMessageContaining("độ phân giải quá lớn")
                .hasMessageContaining("40000×40000");
    }

    @Test
    void rejectsAnImageWiderThanTheEdgeLimitEvenWhenPixelCountIsModest() {
        byte[] panorama = pngHeaderDeclaring(ImageValidator.MAX_EDGE_PIXELS + 1, 10);

        assertThatThrownBy(() -> validator.read(multipart(panorama, "image/png"), "thiếu ảnh"))
                .isInstanceOf(TryOnImageException.class)
                .hasMessageContaining("độ phân giải quá lớn");
    }

    /** Content-Type do client tự khai, nên nội dung tệp mới là thứ quyết định. */
    @Test
    void rejectsNonImageBytesDisguisedAsPng() {
        byte[] notAnImage = "<?php system($_GET['c']); ?>".getBytes();

        assertThatThrownBy(() -> validator.read(multipart(notAnImage, "image/png"), "thiếu ảnh"))
                .isInstanceOf(TryOnImageException.class)
                .hasMessageContaining("Không đọc được ảnh");
    }

    @Test
    void rejectsAnUndeclaredContentType() throws IOException {
        byte[] png = toPng(picture(64, 64));

        assertThatThrownBy(() -> validator.read(multipart(png, "application/octet-stream"), "thiếu ảnh"))
                .isInstanceOf(TryOnImageException.class)
                .hasMessageContaining("JPG hoặc PNG");
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() -> validator.read(multipart(new byte[0], "image/png"), "Hãy chọn ảnh."))
                .isInstanceOf(TryOnImageException.class)
                .hasMessage("Hãy chọn ảnh.");
    }

    // ---------- helpers ----------

    private static BufferedImage picture(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private static MockMultipartFile pngFile(BufferedImage image) throws IOException {
        return multipart(toPng(image), "image/png");
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static MockMultipartFile multipart(byte[] content, String contentType) {
        return new MockMultipartFile("file", "upload.png", contentType, content);
    }

    /**
     * Dựng một tệp PNG chỉ gồm chữ ký + chunk IHDR khai báo kích thước cho trước. Không có dữ
     * liệu điểm ảnh nào — đúng như một quả bomb thật, phần khai báo mới là thứ gây hại, và
     * {@code ImageReader.getWidth()} chỉ cần đọc tới đây.
     */
    private static byte[] pngHeaderDeclaring(int width, int height) {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes());
        ihdr.putInt(width);
        ihdr.putInt(height);
        ihdr.put((byte) 8);  // bit depth
        ihdr.put((byte) 2);  // color type: truecolor
        ihdr.put((byte) 0);  // compression
        ihdr.put((byte) 0);  // filter
        ihdr.put((byte) 0);  // interlace

        CRC32 crc = new CRC32();
        crc.update(ihdr.array());

        ByteBuffer png = ByteBuffer.allocate(header.length + 4 + ihdr.capacity() + 4);
        png.put(header);
        png.putInt(ihdr.capacity() - 4); // độ dài phần dữ liệu, không tính 4 byte tên chunk
        png.put(ihdr.array());
        png.putInt((int) crc.getValue());

        return png.array();
    }
}
