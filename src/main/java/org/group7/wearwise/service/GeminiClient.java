package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.group7.wearwise.exception.AiUnavailableException;
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

/**
 * Client cho Google Gemini (generateContent REST API) — không cần SDK.
 * API key điền thủ công vào application.properties (mục {@code wearwise.gemini.*}).
 *
 * <p>Luôn yêu cầu Gemini trả về JSON ({@code responseMimeType: application/json})
 * để phía gọi parse được kết quả có cấu trúc.</p>
 */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiClient(
            @Value("${wearwise.gemini.api-key:}") String apiKey,
            @Value("${wearwise.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${wearwise.gemini.model:gemini-2.5-flash}") String model
    ) {
        this.apiKey = trim(apiKey);
        String normalizedBase = trim(baseUrl);
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        this.baseUrl = normalizedBase.isBlank() ? "https://generativelanguage.googleapis.com" : normalizedBase;
        this.model = trim(model).isBlank() ? "gemini-2.5-flash" : trim(model);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** Ảnh gửi kèm prompt, nhúng thẳng vào request dưới dạng base64. */
    public record InlineImage(String mimeType, byte[] data) {
    }

    /**
     * Gửi prompt và trả về nội dung text của câu trả lời (đã yêu cầu ở dạng JSON).
     */
    public String generateJson(String prompt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", prompt);
        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.7);

        return send(payload);
    }

    /**
     * Gửi prompt kèm một ảnh và ép câu trả lời theo {@code responseSchema}.
     *
     * <p>Cấu hình ở đây tối ưu cho tác vụ phân loại ngắn, tiết kiệm token:</p>
     * <ul>
     *   <li>{@code thinkingBudget = 0} — Gemini 2.5 mặc định bật "thinking", với việc nhận diện
     *       một món quần áo thì phần suy luận đó chỉ đốt token vô ích.</li>
     *   <li>{@code responseSchema} — model buộc phải trả đúng enum hợp lệ, không cần prompt dài
     *       để liệt kê luật, cũng không tốn lượt gọi lại khi output sai định dạng.</li>
     *   <li>{@code temperature = 0} — kết quả ổn định, cùng một ảnh cho cùng một đáp án.</li>
     * </ul>
     */
    public String generateJson(String prompt, InlineImage image, JsonNode responseSchema, int maxOutputTokens) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode parts = payload.putArray("contents").addObject().putArray("parts");
        parts.addObject().put("text", prompt);
        ObjectNode inlineData = parts.addObject().putObject("inlineData");
        inlineData.put("mimeType", image.mimeType());
        inlineData.put("data", Base64.getEncoder().encodeToString(image.data()));

        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseSchema", responseSchema);
        generationConfig.put("temperature", 0);
        generationConfig.put("maxOutputTokens", maxOutputTokens);

        // thinkingConfig chỉ tồn tại ở dòng 2.5; gửi cho model cũ hơn sẽ bị trả về HTTP 400.
        if (model.startsWith("gemini-2.5")) {
            generationConfig.putObject("thinkingConfig").put("thinkingBudget", 0);
        }

        return send(payload);
    }

    /**
     * Gửi prompt thuần text nhưng ép câu trả lời theo {@code responseSchema}.
     *
     * <p>Dùng cho những tác vụ mà cấu trúc câu trả lời chính là ràng buộc nghiệp vụ — ví dụ mỗi
     * bộ đồ chỉ được một áo một quần. Diễn đạt luật đó bằng lời trong prompt thì model vẫn có
     * thể phá; đặt vào schema thì nó không có chỗ để phá.</p>
     */
    public String generateJson(String prompt, JsonNode responseSchema, int maxOutputTokens) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", prompt);

        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseSchema", responseSchema);
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", maxOutputTokens);

        return send(payload);
    }

    private String send(ObjectNode payload) {
        if (!isConfigured()) {
            throw new AiUnavailableException(
                    "Gợi ý AI chưa được cấu hình. Hãy điền wearwise.gemini.api-key vào application.properties.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status == 400 || status == 401 || status == 403) {
                log.warn("Gemini auth/request error {}: {}", status, response.body());
                throw new AiUnavailableException("Gemini từ chối yêu cầu (HTTP " + status + "). Hãy kiểm tra lại API key.");
            }
            if (status == 429) {
                throw new AiUnavailableException("Gemini đang quá tải hoặc hết hạn mức (HTTP 429). Thử lại sau ít phút.");
            }
            if (status / 100 != 2) {
                log.warn("Gemini error {}: {}", status, response.body());
                throw new AiUnavailableException("Gemini gặp sự cố (HTTP " + status + "). Thử lại sau.");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String text = json.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText(null);

            if (text == null || text.isBlank()) {
                log.warn("Gemini empty response: {}", response.body());
                throw new AiUnavailableException("Gemini không trả về nội dung. Thử lại sau.");
            }
            return text;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AiUnavailableException("Không kết nối được tới Gemini: " + exception.getMessage());
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
