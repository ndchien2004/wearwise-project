package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.group7.wearwise.exception.TryOnUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Sinh ảnh thử đồ bằng model ảnh của Google Gemini (họ "Nano Banana").
 *
 * <h2>Khác gì {@link GeminiClient}</h2>
 *
 * <p>{@code GeminiClient} gọi {@code /v1beta/models/{model}:generateContent} và nhận về <b>chữ</b>.
 * Sinh ảnh là một endpoint khác hẳn — {@code /v1beta/interactions} — và model cũng phải là bản có
 * hậu tố {@code -image}. Model text như {@code gemini-3.1-flash-lite} <b>không</b> sinh được ảnh.
 *
 * <h2>Điều kiện dùng được — đọc trước khi bật</h2>
 *
 * <p><b>Bắt buộc phải có API key đã bật thanh toán.</b> Đã kiểm chứng ngày 27/07/2026 với key gói
 * miễn phí của dự án: cả bốn model ảnh ({@code gemini-3.1-flash-lite-image},
 * {@code gemini-3.1-flash-image}, {@code gemini-2.5-flash-image}, {@code gemini-3-pro-image}) đều
 * trả HTTP 429 kèm {@code limit: 0} — gói free có hạn mức <i>bằng không</i> cho model ảnh, trong
 * khi model text vẫn chạy bình thường (HTTP 200) với đúng key đó.
 *
 * <p>Hệ quả: lớp này <b>chưa được chạy thử trọn vẹn</b>. Phần dựng request bám đúng tài liệu chính
 * thức và endpoint đã xác nhận là tới được model (lỗi trả về là lỗi hạn mức, không phải lỗi model
 * không tồn tại hay sai định dạng). Phần đọc phản hồi thì mới chỉ theo tài liệu. Khi bật billing,
 * hãy chạy thử và đối chiếu lại {@link #extractImage}.
 *
 * <p>Xem thêm {@code docs/TRY_ON_IMAGE_GUIDE.md} để biết cách chuẩn bị ảnh và viết prompt.
 */
@Service
public class GeminiImageClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiImageClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiImageClient(
            @Value("${wearwise.gemini.api-key:}") String apiKey,
            @Value("${wearwise.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${wearwise.gemini.image-model:gemini-3.1-flash-lite-image}") String model
    ) {
        this.apiKey = trim(apiKey);
        String normalizedBase = trim(baseUrl);
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        this.baseUrl = normalizedBase.isBlank() ? "https://generativelanguage.googleapis.com" : normalizedBase;
        this.model = trim(model).isBlank() ? "gemini-3.1-flash-lite-image" : trim(model);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String getModel() {
        return model;
    }

    /** Ảnh đầu vào gửi kèm prompt. */
    public record InputImage(String mimeType, byte[] data) {
    }

    /** Ảnh sinh ra. */
    public record GeneratedImage(String mimeType, byte[] data) {
    }

    /**
     * Gửi prompt kèm các ảnh tham chiếu và nhận về một ảnh mới.
     *
     * @param prompt mô tả việc cần làm; xem {@code docs/TRY_ON_IMAGE_GUIDE.md}
     * @param images ảnh người trước, rồi tới từng món trang phục — thứ tự có ý nghĩa vì prompt
     *               nhắc tới chúng theo thứ tự đó
     */
    public GeneratedImage generateImage(String prompt, List<InputImage> images) {
        if (!isConfigured()) {
            throw new TryOnUnavailableException(
                    "Gemini chưa được cấu hình. Hãy điền wearwise.gemini.api-key vào application-secrets.properties.");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);

        ArrayNode input = payload.putArray("input");
        input.addObject().put("type", "text").put("text", prompt);
        for (InputImage image : images) {
            input.addObject()
                    .put("type", "image")
                    .put("mime_type", image.mimeType() == null ? "image/jpeg" : image.mimeType())
                    .put("data", Base64.getEncoder().encodeToString(image.data()));
        }

        payload.putObject("response_format").put("type", "image");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1beta/interactions"))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                // Sinh ảnh mất vài giây, nhưng để rộng tay phòng lúc dịch vụ quá tải.
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        JsonNode json = send(request);
        return extractImage(json);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = readJson(response.body());

            if (response.statusCode() / 100 != 2) {
                String message = json.path("error").path("message").asText("Gemini trả về lỗi không rõ.");

                // Lỗi hay gặp nhất khi mới bật tính năng — nói thẳng nguyên nhân thay vì để người
                // dùng đi tra mã 429 rồi tưởng là gọi quá nhiều.
                if (response.statusCode() == 429 && message.contains("limit: 0")) {
                    throw new TryOnUnavailableException(
                            "Model sinh ảnh của Gemini không dùng được với API key ở gói miễn phí "
                                    + "(hạn mức bằng 0). Hãy bật thanh toán cho project Google AI, "
                                    + "hoặc chuyển wearwise.tryon.provider về 'tryon-api'.");
                }

                throw new TryOnUnavailableException("Gemini: " + message);
            }

            return json;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TryOnUnavailableException("Không kết nối được tới Gemini: " + exception.getMessage());
        }
    }

    /**
     * Lấy ảnh ra khỏi phản hồi.
     *
     * <p>Thử vài vị trí khác nhau vì cấu trúc phản hồi của endpoint này chưa được kiểm chứng trực
     * tiếp (xem javadoc của lớp). Dò nhiều nhánh an toàn hơn là bám cứng một đường dẫn rồi hỏng
     * lặng lẽ khi Google đổi cách trả về.</p>
     */
    private GeneratedImage extractImage(JsonNode json) {
        JsonNode direct = json.path("output_image");
        if (direct.hasNonNull("data")) {
            return decode(direct.path("mime_type").asText("image/png"), direct.path("data").asText());
        }

        JsonNode nested = json.path("interaction").path("output_image");
        if (nested.hasNonNull("data")) {
            return decode(nested.path("mime_type").asText("image/png"), nested.path("data").asText());
        }

        for (JsonNode node : json.path("output")) {
            if ("image".equals(node.path("type").asText()) && node.hasNonNull("data")) {
                return decode(node.path("mime_type").asText("image/png"), node.path("data").asText());
            }
        }

        log.error("Không tìm thấy ảnh trong phản hồi Gemini: {}", json);
        throw new TryOnUnavailableException(
                "Gemini không trả về ảnh. Có thể prompt bị bộ lọc an toàn chặn, hoặc cấu trúc phản hồi đã đổi.");
    }

    private GeneratedImage decode(String mimeType, String base64) {
        try {
            return new GeneratedImage(mimeType, Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException exception) {
            throw new TryOnUnavailableException("Gemini trả về dữ liệu ảnh không đọc được.");
        }
    }

    private JsonNode readJson(String body) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new TryOnUnavailableException("Gemini trả về dữ liệu không hợp lệ.");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
