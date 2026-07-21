package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.group7.wearwise.exception.TryOnImageException;
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

/**
 * Client cho tryon-api.com — dịch vụ ghép trang phục ảo.
 *
 * <p>Xác thực bằng Bearer API key. Gửi ảnh dưới dạng URL (ảnh của ta đã ở Cloudinary) qua JSON.
 * Chạy ở chế độ async: POST tạo job → poll tới khi hoàn tất → trả URL ảnh kết quả.</p>
 *
 * <p>API key và cấu hình điền thủ công vào application.properties (mục {@code wearwise.tryon.*}).</p>
 */
@Service
public class TryOnApiClient {

    private static final Logger log = LoggerFactory.getLogger(TryOnApiClient.class);

    /** Số lần hỏi trạng thái tối đa và khoảng cách giữa các lần (≈ 2 phút). */
    private static final int MAX_POLLS = 40;
    private static final long POLL_INTERVAL_MS = 3000L;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String category;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TryOnApiClient(
            @Value("${wearwise.tryon.api-key:}") String apiKey,
            @Value("${wearwise.tryon.base-url:https://tryon-api.com/api/v1}") String baseUrl,
            @Value("${wearwise.tryon.model:auto}") String model,
            @Value("${wearwise.tryon.category:apparel}") String category
    ) {
        this.apiKey = trim(apiKey);
        String normalizedBase = trim(baseUrl);
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        this.baseUrl = normalizedBase.isBlank() ? "https://tryon-api.com/api/v1" : normalizedBase;
        this.model = trim(model).isBlank() ? "auto" : trim(model);
        this.category = trim(category);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * Ghép trang phục ({@code clothImageUrl}) lên ảnh người ({@code humanImageUrl}).
     * @return URL ảnh kết quả (do tryon-api host — nên tải về lưu lại ngay đề phòng hết hạn).
     */
    public String generateTryOn(String humanImageUrl, String clothImageUrl) {
        if (!isConfigured()) {
            throw new TryOnUnavailableException(
                    "Dịch vụ thử đồ chưa được cấu hình. Hãy điền wearwise.tryon.api-key vào application.properties.");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.putArray("person_images").add(humanImageUrl);
        payload.putArray("garment_images").add(clothImageUrl);
        if (!category.isBlank()) {
            payload.put("category", category);
        }
        payload.put("mode", "async");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tryon"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        JsonNode json = send(request, "gửi yêu cầu thử đồ");
        log.info("tryon-api create response: {}", json);

        // Nếu server trả kết quả ngay (đồng bộ) thì lấy luôn.
        String directUrl = extractImageUrl(json);
        if (directUrl != null) {
            return directUrl;
        }

        // Ngược lại là job async — poll tới khi hoàn tất.
        String jobId = firstNonBlank(json.path("jobId").asText(null), json.path("id").asText(null));
        String statusUrl = json.path("statusUrl").asText(null);
        if (jobId == null && (statusUrl == null || statusUrl.isBlank())) {
            throw new TryOnUnavailableException("Dịch vụ thử đồ không trả về mã tác vụ hợp lệ.");
        }
        return pollForResult(jobId, statusUrl);
    }

    private String pollForResult(String jobId, String statusUrl) {
        String pollUri = absolutize((statusUrl != null && !statusUrl.isBlank())
                ? statusUrl
                : baseUrl + "/tryon/" + jobId);

        for (int attempt = 0; attempt < MAX_POLLS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pollUri))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            JsonNode json = send(request, "kiểm tra kết quả thử đồ");
            log.info("tryon-api poll response (attempt {}): {}", attempt + 1, json);
            String status = json.path("status").asText("").toLowerCase();

            String url = extractImageUrl(json);
            if (url != null) {
                return url;
            }

            if (status.equals("completed") || status.equals("succeeded") || status.equals("success")) {
                // Báo hoàn tất nhưng không thấy ảnh — coi như lỗi dịch vụ.
                throw new TryOnUnavailableException("Dịch vụ thử đồ báo hoàn tất nhưng không có ảnh kết quả.");
            }

            if (status.equals("failed") || status.equals("error")) {
                String msg = firstNonBlank(json.path("error").asText(null), json.path("message").asText(null));
                throw new TryOnImageException(
                        "Không xử lý được ảnh này"
                                + (msg == null ? "" : " (" + msg + ")")
                                + ". Hãy dùng ảnh chân dung/toàn thân rõ nét, đủ sáng và chỉ có một người.");
            }

            // queued / processing → chờ rồi hỏi lại
            sleepBetweenPolls();
        }

        throw new TryOnUnavailableException("Dịch vụ thử đồ xử lý quá lâu. Vui lòng thử lại sau ít phút.");
    }

    /** Rút URL ảnh kết quả từ nhiều dạng cấu trúc có thể gặp: images[0].url hoặc result.images[0].url. */
    private String extractImageUrl(JsonNode json) {
        JsonNode images = json.path("images");
        if (!images.isArray() || images.isEmpty()) {
            images = json.path("result").path("images");
        }
        if (images.isArray() && !images.isEmpty()) {
            JsonNode first = images.get(0);
            // Phần tử có thể là chuỗi URL hoặc object { url: ... }.
            String url = first.isTextual() ? first.asText(null) : first.path("url").asText(null);
            if (url != null && !url.isBlank()) {
                return absolutize(url);
            }
        }
        return null;
    }

    /**
     * Chuyển URL tương đối (thiếu scheme, vd "/api/v1/tryon/xxx") thành URL tuyệt đối
     * bằng cách ghép với origin của base-url. URL đã đầy đủ http(s) thì giữ nguyên.
     */
    private String absolutize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed;
        }
        URI base = URI.create(baseUrl);
        String origin = base.getScheme() + "://" + base.getAuthority();
        return origin + (trimmed.startsWith("/") ? trimmed : "/" + trimmed);
    }

    private JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status == 401 || status == 403) {
                throw new TryOnUnavailableException("Dịch vụ thử đồ từ chối xác thực. Hãy kiểm tra lại wearwise.tryon.api-key.");
            }
            if (status == 402) {
                throw new TryOnUnavailableException("Tài khoản tryon-api.com không đủ credit. Vui lòng nạp thêm để tiếp tục.");
            }
            if (status == 429) {
                throw new TryOnUnavailableException("Dịch vụ thử đồ đang quá tải (quá nhiều yêu cầu). Thử lại sau ít phút.");
            }
            if (status / 100 == 5) {
                throw new TryOnUnavailableException("Dịch vụ thử đồ đang gặp sự cố (HTTP " + status + "). Thử lại sau.");
            }

            JsonNode json = readJson(response.body());

            if (status / 100 != 2) {
                String message = firstNonBlank(
                        json.path("error").asText(null),
                        json.path("message").asText(null));
                throw mapError(status, message == null ? "Dịch vụ thử đồ từ chối yêu cầu." : message);
            }

            return json;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TryOnUnavailableException("Không kết nối được tới dịch vụ thử đồ khi " + action + ": " + exception.getMessage());
        }
    }

    private JsonNode readJson(String body) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new TryOnUnavailableException("Dịch vụ thử đồ trả về dữ liệu không hợp lệ.");
        }
    }

    private RuntimeException mapError(int status, String message) {
        // Lỗi liên quan tới nội dung ảnh → 400 để người dùng đổi ảnh; còn lại là lỗi dịch vụ.
        String lower = message.toLowerCase();
        if (lower.contains("image") || lower.contains("resolution") || lower.contains("face")
                || lower.contains("size") || lower.contains("format") || lower.contains("invalid")
                || lower.contains("person") || lower.contains("garment") || lower.contains("body")) {
            return new TryOnImageException(message + ". Hãy dùng ảnh rõ nét, đúng định dạng và đủ điều kiện.");
        }
        return new TryOnUnavailableException("Dịch vụ thử đồ (HTTP " + status + "): " + message);
    }

    private void sleepBetweenPolls() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TryOnUnavailableException("Quá trình thử đồ bị gián đoạn.");
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
