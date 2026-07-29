package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.group7.wearwise.exception.TryOnUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tải ảnh lên Cloudinary bằng REST API (signed upload) — không cần thêm SDK.
 * API key được điền thủ công vào application.properties.
 */
@Service
public class CloudinaryService {

    /**
     * Incoming transformation áp lên ảnh <b>trước khi lưu</b>, gồm hai phần:
     *
     * <ul>
     *   <li>{@code a_exif} — xoay ảnh theo thẻ EXIF Orientation. Phải làm trước khi xóa metadata,
     *       nếu không ảnh chụp dọc bằng điện thoại sẽ nằm ngang vĩnh viễn.</li>
     *   <li>{@code fl_force_strip} — xóa toàn bộ EXIF, IPTC, XMP. Ảnh từ điện thoại mang theo
     *       tọa độ GPS nơi chụp và model máy; URL Cloudinary lại công khai, nên giữ metadata
     *       đồng nghĩa công bố chỗ ở của người dùng — đặc biệt với ảnh cơ thể dùng để thử đồ.</li>
     * </ul>
     *
     * <p>Cloudinary chỉ tự bỏ metadata ở ảnh <i>phái sinh</i>; bản gốc thì giữ nguyên, nên bắt
     * buộc phải yêu cầu tường minh ngay lúc tải lên.</p>
     */
    private static final String INCOMING_TRANSFORMATION = "a_exif,fl_force_strip";

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String uploadFolder;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CloudinaryService(
            @Value("${wearwise.cloudinary.cloud-name:}") String cloudName,
            @Value("${wearwise.cloudinary.api-key:}") String apiKey,
            @Value("${wearwise.cloudinary.api-secret:}") String apiSecret,
            @Value("${wearwise.cloudinary.upload-folder:wearwise}") String uploadFolder
    ) {
        this.cloudName = trim(cloudName);
        this.apiKey = trim(apiKey);
        this.apiSecret = trim(apiSecret);
        this.uploadFolder = trim(uploadFolder);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    /**
     * Tải ảnh (bytes) lên Cloudinary, trả về secure URL vĩnh viễn.
     *
     * @param imageBytes  nội dung ảnh
     * @param contentType MIME type (vd image/png)
     * @param subFolder   thư mục con bên trong upload-folder (vd "body-photos")
     */
    public String uploadImage(byte[] imageBytes, String contentType, String subFolder) {
        if (!isConfigured()) {
            throw new TryOnUnavailableException(
                    "Cloudinary chưa được cấu hình. Hãy điền cloud-name, api-key và api-secret vào application.properties.");
        }

        String folder = buildFolder(subFolder);
        long timestamp = Instant.now().getEpochSecond();

        // Chữ ký chỉ gồm các tham số được gửi (trừ file, api_key, resource_type), sắp xếp theo alphabet.
        TreeMap<String, String> signedParams = new TreeMap<>();
        signedParams.put("folder", folder);
        signedParams.put("timestamp", Long.toString(timestamp));
        signedParams.put("transformation", INCOMING_TRANSFORMATION);
        String signature = sign(signedParams);

        String dataUri = "data:" + normalizeContentType(contentType) + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, String> formParams = new LinkedHashMap<>();
        formParams.put("file", dataUri);
        formParams.put("api_key", apiKey);
        formParams.put("timestamp", Long.toString(timestamp));
        formParams.put("folder", folder);
        formParams.put("transformation", INCOMING_TRANSFORMATION);
        formParams.put("signature", signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(urlEncode(formParams), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = readJson(response.body());

            if (response.statusCode() / 100 != 2) {
                String message = json.path("error").path("message").asText("Tải ảnh lên Cloudinary thất bại.");
                throw new TryOnUnavailableException("Cloudinary: " + message);
            }

            String secureUrl = firstNonBlank(json.path("secure_url").asText(null), json.path("url").asText(null));
            if (secureUrl == null) {
                throw new TryOnUnavailableException("Cloudinary không trả về đường dẫn ảnh.");
            }
            return secureUrl;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TryOnUnavailableException("Không kết nối được tới Cloudinary: " + exception.getMessage());
        }
    }

    private JsonNode readJson(String body) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new TryOnUnavailableException("Cloudinary trả về dữ liệu không hợp lệ.");
        }
    }

    private String buildFolder(String subFolder) {
        String base = uploadFolder.isBlank() ? "wearwise" : uploadFolder;
        if (subFolder == null || subFolder.isBlank()) {
            return base;
        }
        return base + "/" + subFolder.trim();
    }

    private String sign(TreeMap<String, String> params) {
        StringBuilder toSign = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (toSign.length() > 0) {
                toSign.append('&');
            }
            toSign.append(entry.getKey()).append('=').append(entry.getValue());
        }
        toSign.append(apiSecret);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(toSign.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 không khả dụng để ký yêu cầu Cloudinary.", exception);
        }
    }

    private static String urlEncode(Map<String, String> params) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
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

    private static String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "image/png" : contentType;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
