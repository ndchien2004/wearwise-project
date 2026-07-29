package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.group7.wearwise.dto.response.ClothingItemSuggestionResponse;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.AiUnavailableException;
import org.group7.wearwise.exception.TryOnImageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nhận diện món quần áo trong ảnh bằng Gemini để điền sẵn form thêm đồ.
 *
 * <p><b>Về chi phí token.</b> Đo thực tế trên gemini-3.1-flash-lite: ảnh luôn bị tính
 * <b>1089 token cố định</b>, không đổi dù gửi ảnh 96px hay 2048px. Nói cách khác thu nhỏ ảnh
 * <i>không</i> tiết kiệm được token trên dòng model này (khác dòng 2.5, vốn tính theo ô 768×768).
 * Mỗi lần gọi tốn khoảng 1089 token ảnh + ~73 prompt + ~63 output ≈ 1225 token.</p>
 *
 * <p>Vì token đã cố định, đòn bẩy tiết kiệm nằm ở <b>số lần gọi</b> chứ không phải kích thước
 * ảnh — nên chỉ tự chạy khi thêm đồ mới, còn lúc sửa thì đợi người dùng bấm nút. Việc thu nhỏ
 * ảnh vẫn giữ lại, nhưng để tiết kiệm băng thông và thời gian chờ: ảnh điện thoại 3-5MB xuống
 * còn khoảng 20KB.</p>
 */
@Service
public class ClothingItemVisionService {

    private static final Logger log = LoggerFactory.getLogger(ClothingItemVisionService.class);

    /**
     * Cạnh dài tối đa khi gửi ảnh cho AI. Chọn 768 vì token không phụ thuộc kích thước — giữ
     * nhiều chi tiết hơn cho họa tiết/chất liệu mà vẫn chỉ khoảng 20KB mỗi ảnh.
     */
    static final int MAX_IMAGE_EDGE = 768;

    /** Đủ cho 6 trường ngắn; chặn trên để một câu trả lời lạc đề không đốt token. */
    private static final int MAX_OUTPUT_TOKENS = 200;

    /** Trần số món nhận từ một ảnh: nhiều hơn thì bảng xác nhận cũng không ai soát nổi. */
    static final int MAX_ITEMS_PER_IMAGE = 12;

    /** Mỗi món tốn khoảng 60 token output, cộng dư ra cho phần dấu ngoặc của mảng. */
    private static final int MAX_BATCH_OUTPUT_TOKENS = 60 * MAX_ITEMS_PER_IMAGE + 100;


    /**
     * Prompt cố tình ngắn: mọi ràng buộc giá trị hợp lệ đã nằm trong responseSchema nên không
     * cần liệt kê lại bằng lời. Chỉ giữ hai thứ schema không diễn đạt được là ngôn ngữ và độ dài.
     */
    private static final String PROMPT = """
            Nhận diện món quần áo trong ảnh.
            name: tên gọi ngắn bằng tiếng Việt, tối đa 6 từ, gồm loại đồ và màu (vd "Áo thun trắng").
            color: tên màu chủ đạo bằng tiếng Việt, 1-2 từ.
            Nếu ảnh có nhiều món, mô tả món chiếm nhiều diện tích nhất.
            """;

    /**
     * Nhấn mạnh "từng món riêng biệt" vì mặc định model hay gộp cả bộ thành một mô tả duy nhất.
     */
    private static final String BATCH_PROMPT = """
            Liệt kê TỪNG món quần áo nhìn thấy trong ảnh thành một phần tử riêng của mảng.
            Bỏ qua móc treo, hộp, người mẫu và phông nền — chỉ lấy quần áo, giày, phụ kiện.
            Không gộp nhiều món thành một; không lặp lại cùng một món hai lần.
            name: tên gọi ngắn bằng tiếng Việt, tối đa 6 từ, gồm loại đồ và màu (vd "Áo thun trắng").
            color: tên màu chủ đạo bằng tiếng Việt, 1-2 từ.
            """;

    private final GeminiClient geminiClient;
    private final ImageValidator imageValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonNode responseSchema = buildResponseSchema();
    private final JsonNode batchResponseSchema = buildBatchResponseSchema();

    public ClothingItemVisionService(GeminiClient geminiClient, ImageValidator imageValidator) {
        this.geminiClient = geminiClient;
        this.imageValidator = imageValidator;
    }

    public boolean isConfigured() {
        return geminiClient.isConfigured();
    }

    public ClothingItemSuggestionResponse analyze(MultipartFile file) {
        String raw = callGemini(file, PROMPT, responseSchema, MAX_OUTPUT_TOKENS);
        return parse(readJson(raw));
    }

    /**
     * Nhận diện <b>nhiều món</b> trong cùng một ảnh (vd chụp cả kệ tủ).
     *
     * <p>Đây là lý do tính năng này đáng làm: Gemini tính ảnh 1089 token cố định bất kể ảnh
     * chứa một món hay mười món, nên chi phí mỗi món giảm theo đúng số món nhận ra được.
     * Chỉ phần output dài thêm, mà mỗi món chỉ tốn khoảng 60 token.</p>
     */
    public List<ClothingItemSuggestionResponse> analyzeBatch(MultipartFile file) {
        String raw = callGemini(file, BATCH_PROMPT, batchResponseSchema, MAX_BATCH_OUTPUT_TOKENS);
        JsonNode json = readJson(raw);

        if (!json.isArray()) {
            log.warn("Gemini không trả về mảng khi quét nhiều món: {}", raw);
            throw new AiUnavailableException("AI trả về dữ liệu không hợp lệ. Hãy thử lại hoặc nhập tay.");
        }

        List<ClothingItemSuggestionResponse> suggestions = new ArrayList<>();
        for (JsonNode node : json) {
            ClothingItemSuggestionResponse suggestion = parse(node);
            // Món không đọc được tên lẫn danh mục thì bỏ, đưa lên giao diện chỉ gây nhiễu.
            if (suggestion.name() != null || suggestion.category() != null) {
                suggestions.add(suggestion);
            }
            if (suggestions.size() >= MAX_ITEMS_PER_IMAGE) {
                break;
            }
        }

        return suggestions;
    }

    private String callGemini(MultipartFile file, String prompt, JsonNode schema, int maxOutputTokens) {
        if (!geminiClient.isConfigured()) {
            throw new AiUnavailableException(
                    "Tính năng AI chưa được cấu hình. Hãy điền wearwise.gemini.api-key vào application.properties.");
        }

        byte[] shrunk = shrink(readImage(file));
        return geminiClient.generateJson(
                prompt,
                new GeminiClient.InlineImage("image/jpeg", shrunk),
                schema,
                maxOutputTokens
        );
    }

    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (IOException exception) {
            log.warn("Gemini trả về JSON không đọc được: {}", raw);
            throw new AiUnavailableException("AI trả về dữ liệu không hợp lệ. Hãy thử lại hoặc nhập tay.");
        }
    }

    private ClothingItemSuggestionResponse parse(JsonNode json) {
        // responseSchema đã ép enum hợp lệ, nhưng vẫn parse phòng thủ: giá trị lạ thì bỏ trống
        // để người dùng tự chọn, thay vì làm hỏng cả form.
        return new ClothingItemSuggestionResponse(
                text(json, "name", 255),
                text(json, "color", 100),
                enumValue(ColorTone.class, json, "colorTone"),
                enumValue(ClothingCategory.class, json, "category"),
                enumValue(Season.class, json, "season"),
                enumValue(Style.class, json, "style")
        );
    }

    private String text(JsonNode json, String field, int maxLength) {
        String value = json.path(field).asText("").trim();
        if (value.isBlank()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, JsonNode json, String field) {
        String value = json.path(field).asText("").trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.debug("Gemini trả giá trị lạ cho {}: {}", field, value);
            return null;
        }
    }

    private BufferedImage readImage(MultipartFile file) {
        return imageValidator.read(file, "Vui lòng chọn một ảnh để nhận diện.").image();
    }

    /**
     * Thu ảnh về cạnh dài tối đa {@value #MAX_IMAGE_EDGE}px và mã hóa JPEG.
     *
     * <p>Frontend cũng thu nhỏ trước khi gửi, nhưng dữ liệu từ client không đáng tin: bước này
     * mới là thứ đảm bảo server không phải nhét ảnh 8000px vào request gửi Gemini.</p>
     */
    private byte[] shrink(BufferedImage source) {
        int longestEdge = Math.max(source.getWidth(), source.getHeight());
        double scale = longestEdge > MAX_IMAGE_EDGE ? (double) MAX_IMAGE_EDGE / longestEdge : 1.0;

        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        // JPEG không có kênh alpha: nền trong suốt của PNG phải được tô trắng, nếu không
        // vùng đó thành đen và AI hay đoán nhầm thành món đồ màu đen.
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", buffer);
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new TryOnImageException("Không xử lý được ảnh. Vui lòng thử ảnh khác.");
        }
    }

    /**
     * Schema sinh thẳng từ các enum của ứng dụng nên không bao giờ lệch với model dữ liệu —
     * thêm một giá trị vào {@code Style} là prompt tự biết giá trị đó.
     */
    /** Mảng các món, dùng cho ảnh chụp nhiều món cùng lúc. */
    private JsonNode buildBatchResponseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "ARRAY");
        schema.put("maxItems", MAX_ITEMS_PER_IMAGE);
        schema.set("items", buildResponseSchema());
        return schema;
    }

    private JsonNode buildResponseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "OBJECT");

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("name").put("type", "STRING");
        properties.putObject("color").put("type", "STRING");
        properties.set("colorTone", enumProperty(ColorTone.values()));
        properties.set("category", enumProperty(ClothingCategory.values()));
        properties.set("season", enumProperty(Season.values()));
        properties.set("style", enumProperty(Style.values()));

        ArrayNode required = schema.putArray("required");
        required.add("name").add("color").add("colorTone").add("category").add("season").add("style");

        // Giữ thứ tự trường cố định để output không đổi giữa các lần gọi.
        ArrayNode ordering = schema.putArray("propertyOrdering");
        ordering.add("name").add("color").add("colorTone").add("category").add("season").add("style");

        return schema;
    }

    private ObjectNode enumProperty(Enum<?>[] values) {
        ObjectNode property = objectMapper.createObjectNode();
        property.put("type", "STRING");
        ArrayNode allowed = property.putArray("enum");
        for (Enum<?> value : values) {
            allowed.add(value.name());
        }
        return property;
    }
}
