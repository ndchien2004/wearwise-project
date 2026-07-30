package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.group7.wearwise.dto.response.AiOutfitRankingResponse;
import org.group7.wearwise.dto.response.AiOutfitSuggestionResponse;
import org.group7.wearwise.dto.response.ClothingItemResponse;
import org.group7.wearwise.dto.response.OutfitResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gợi ý phối đồ bằng Gemini: đưa toàn bộ tủ đồ (kèm thuộc tính: loại, màu, tone,
 * mùa, phong cách, trạng thái) + thời tiết hiện tại + tone màu người dùng muốn,
 * yêu cầu AI chọn ra 2-3 bộ với lý do bằng tiếng Việt.
 */
@Service
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);
    private static final int MAX_SUGGESTIONS = 3;

    /** 3 bộ x (2 câu lý do + 5 ô id) — dư dả nhưng vẫn chặn câu trả lời lan man. */
    private static final int MAX_SUGGESTION_OUTPUT_TOKENS = 900;

    /** Áo + quần là xương sống của một bộ; các loại còn lại là tùy chọn. */
    private static final Set<ClothingCategory> CORE_CATEGORIES =
            java.util.EnumSet.of(ClothingCategory.SHIRT, ClothingCategory.PANTS);

    private final ClothingItemService clothingItemService;
    private final OutfitService outfitService;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiSuggestionService(
            ClothingItemService clothingItemService,
            OutfitService outfitService,
            GeminiClient geminiClient
    ) {
        this.clothingItemService = clothingItemService;
        this.outfitService = outfitService;
        this.geminiClient = geminiClient;
    }

    public boolean isConfigured() {
        return geminiClient.isConfigured();
    }

    /**
     * Các bộ mặc được ngay, kèm thông báo phân biệt "chưa có bộ nào" với "có nhưng đang vướng".
     * Hai tình huống này cần hai hành động khác nhau, gộp chung một câu thì người dùng đi tạo
     * thêm outfit trong khi thứ họ cần làm chỉ là bấm "Giặt xong".
     */
    private List<Outfit> wearableOutfits(String username, String action) {
        List<Outfit> all = outfitService.findOutfits(username, null, null, null, null);

        if (all.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bạn chưa có outfit nào để AI " + action + ". Hãy tạo vài bộ ở mục Outfit trước nhé!");
        }

        List<Outfit> wearable = all.stream().filter(OutfitService::isWearableNow).toList();

        if (wearable.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mọi outfit của bạn đang vướng đồ giặt, đồ hỏng hoặc thiếu món nên chưa "
                            + action + " được. Hãy đánh dấu \"Giặt xong\" hoặc sửa lại các bộ ở mục Outfit.");
        }

        return wearable;
    }

    @Transactional(readOnly = true)
    public List<AiOutfitSuggestionResponse> suggest(
            String username,
            double temperature,
            boolean raining,
            String weatherDescription,
            ColorTone tone
    ) {
        // Chỉ đưa cho AI những món mặc được ngay. Trước đây đồ đang giặt / chưa dùng được vẫn
        // lọt vào danh sách kèm lời dặn "tránh dùng", nhưng gợi ý ra món không mặc được thì cũng
        // vô dụng như gợi ý hai cái quần.
        List<ClothingItem> wardrobe = clothingItemService.getAllItems(username)
                .stream()
                .filter(item -> item.getCondition() != ClothingCondition.DAMAGED)
                .filter(item -> item.getStatus() == ClothingStatus.AVAILABLE)
                .toList();

        if (wardrobe.size() < 2) {
            throw new IllegalArgumentException(
                    "Tủ đồ chưa đủ món sẵn sàng để phối. Hãy thêm đồ mới, hoặc đánh dấu \"Giặt xong\" "
                            + "cho những món đang giặt.");
        }

        Map<Long, ClothingItem> itemsById = new LinkedHashMap<>();
        wardrobe.forEach(item -> itemsById.put(item.getId(), item));

        String prompt = buildPrompt(wardrobe, temperature, raining, weatherDescription, tone);
        Set<ClothingCategory> availableCategories = wardrobe.stream()
                .map(ClothingItem::getCategory)
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(ClothingCategory.class)));
        String rawJson = geminiClient.generateJson(
                prompt, buildSuggestionSchema(availableCategories), MAX_SUGGESTION_OUTPUT_TOKENS);

        return parseSuggestions(rawJson, itemsById);
    }

    /**
     * Nhờ Gemini xếp hạng các outfit ĐÃ CÓ SẴN của người dùng theo mức phù hợp với thời tiết,
     * trả về tối đa 3 bộ tốt nhất kèm lý do. Khác với {@link #suggest} (bốc món lẻ tạo bộ mới).
     */
    @Transactional(readOnly = true)
    public List<AiOutfitRankingResponse> rankOutfits(
            String username,
            double temperature,
            boolean raining,
            String weatherDescription,
            ColorTone tone
    ) {
        // Xếp hạng "mặc gì hôm nay" nên chỉ đưa AI những bộ mặc được ngay — xếp hạng cao cho một
        // bộ đang giặt là gợi ý người dùng làm việc họ không làm được.
        List<Outfit> outfits = wearableOutfits(username, "xếp hạng");

        Map<Long, Outfit> outfitsById = new LinkedHashMap<>();
        outfits.forEach(outfit -> outfitsById.put(outfit.getId(), outfit));

        String prompt = buildRankingPrompt(outfits, temperature, raining, weatherDescription, tone);
        String rawJson = geminiClient.generateJson(prompt);

        return parseRankings(rawJson, outfitsById);
    }

    private String buildRankingPrompt(
            List<Outfit> outfits,
            double temperature,
            boolean raining,
            String weatherDescription,
            ColorTone tone
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là một stylist thời trang. Hãy chọn trong số các BỘ ĐỒ CÓ SẴN dưới đây ")
                .append("những bộ phù hợp nhất với thời tiết.\n\n");

        prompt.append("Thời tiết hiện tại: ").append(Math.round(temperature)).append("°C");
        if (weatherDescription != null && !weatherDescription.isBlank()) {
            prompt.append(", ").append(weatherDescription.trim());
        }
        prompt.append(raining ? ", đang mưa" : ", không mưa").append(".\n");

        if (tone != null) {
            prompt.append("Người dùng thiên về tone màu: ").append(toneLabel(tone)).append(".\n");
        }

        prompt.append("\nDanh sách outfit có sẵn (id | tên | mùa | phong cách | yêu thích | các món [loại-màu-tone]):\n");
        for (Outfit outfit : outfits) {
            prompt.append(outfit.getId()).append(" | ")
                    .append(outfit.getName()).append(" | ")
                    .append(outfitSeasonLabel(outfit)).append(" | ")
                    .append(outfitStyleLabel(outfit)).append(" | ")
                    .append(Boolean.TRUE.equals(outfit.getFavorite()) ? "có" : "không").append(" | ");
            String items = outfit.getClothingItems().stream()
                    .map(item -> categoryLabel(item) + "-"
                            + (item.getColor() == null ? "?" : item.getColor()) + "-"
                            + (item.getColorTone() == null ? "?" : toneLabel(item.getColorTone())))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(trống)");
            prompt.append(items).append("\n");
        }

        prompt.append("""

                Yêu cầu:
                - Chọn tối đa 3 bộ phù hợp nhất với thời tiết, xếp từ hợp nhất tới ít hợp hơn.
                - Chỉ dùng id có trong danh sách, không bịa.
                - Cân nhắc: hợp mùa, có áo khoác khi lạnh/mưa, tránh đồ dày khi nóng, phối màu hài hòa.
                - "reason": 1-2 câu tiếng Việt giải thích vì sao bộ đó hợp thời tiết hôm nay.

                Chỉ trả về JSON đúng cấu trúc sau, không thêm chữ nào khác:
                {"rankings":[{"outfitId":1,"reason":"..."}]}
                """);

        return prompt.toString();
    }

    private List<AiOutfitRankingResponse> parseRankings(String rawJson, Map<Long, Outfit> outfitsById) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (IOException exception) {
            log.warn("Gemini returned non-JSON payload: {}", rawJson);
            throw new AiUnavailableException("AI trả về dữ liệu không đọc được. Vui lòng thử lại.");
        }

        JsonNode rankings = root.path("rankings");
        List<AiOutfitRankingResponse> result = new ArrayList<>();
        Set<Long> used = new LinkedHashSet<>();

        if (rankings.isArray()) {
            for (JsonNode ranking : rankings) {
                if (result.size() >= MAX_SUGGESTIONS) {
                    break;
                }
                JsonNode idNode = ranking.path("outfitId");
                if (!idNode.canConvertToLong()) {
                    continue;
                }
                Long id = idNode.asLong();
                Outfit outfit = outfitsById.get(id);
                // Bỏ id AI bịa hoặc trùng lặp.
                if (outfit == null || !used.add(id)) {
                    continue;
                }
                String reason = ranking.path("reason").asText("");
                result.add(new AiOutfitRankingResponse(OutfitResponse.from(outfit), reason));
            }
        }

        if (result.isEmpty()) {
            log.warn("Gemini returned no usable rankings: {}", rawJson);
            throw new AiUnavailableException("AI chưa xếp hạng được outfit nào. Vui lòng thử lại.");
        }

        return result;
    }

    private String outfitSeasonLabel(Outfit outfit) {
        return switch (outfit.getSeason()) {
            case SUMMER -> "Mùa hè";
            case WINTER -> "Mùa đông";
            case ALL_SEASON -> "Quanh năm";
        };
    }

    private String outfitStyleLabel(Outfit outfit) {
        return switch (outfit.getStyle()) {
            case CASUAL -> "Thường ngày";
            case FORMAL -> "Trang trọng";
            case STREETWEAR -> "Đường phố";
            case SPORT -> "Thể thao";
        };
    }

    private String buildPrompt(
            List<ClothingItem> wardrobe,
            double temperature,
            boolean raining,
            String weatherDescription,
            ColorTone tone
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là một stylist thời trang. Hãy phối đồ từ tủ đồ của người dùng.\n\n");

        prompt.append("Thời tiết hiện tại: ").append(Math.round(temperature)).append("°C");
        if (weatherDescription != null && !weatherDescription.isBlank()) {
            prompt.append(", ").append(weatherDescription.trim());
        }
        prompt.append(raining ? ", đang mưa" : ", không mưa").append(".\n");

        if (tone != null) {
            prompt.append("Người dùng muốn mặc theo tone màu: ").append(toneLabel(tone)).append(" — ưu tiên các món thuộc tone này làm chủ đạo.\n");
        }

        prompt.append("\nDanh sách món đồ (id | tên | loại | màu | tone màu | mùa | phong cách | trạng thái):\n");
        for (ClothingItem item : wardrobe) {
            prompt.append(item.getId()).append(" | ")
                    .append(item.getName()).append(" | ")
                    .append(categoryLabel(item)).append(" | ")
                    .append(item.getColor() == null ? "không rõ" : item.getColor()).append(" | ")
                    .append(item.getColorTone() == null ? "chưa phân loại" : toneLabel(item.getColorTone())).append(" | ")
                    .append(seasonLabel(item)).append(" | ")
                    .append(styleLabel(item)).append(" | ")
                    .append(statusLabel(item)).append("\n");
        }

        prompt.append("""

                Yêu cầu:
                - Chọn 2-3 bộ trang phục phù hợp nhất với thời tiết trên.
                - Mỗi ô chỉ điền id của món ĐÚNG loại của ô đó; không có món hợp thì để null.
                - Mỗi bộ phải có ít nhất 2 món, và nên có đủ áo + quần nếu tủ đồ có.
                - Trời mưa hoặc lạnh thì nên có áo khoác; trời nóng thì tránh đồ dày.
                - Phối màu hài hòa giữa các món (dựa vào màu và tone màu).
                - "name": tên bộ đồ ngắn gọn, gợi nhớ, tiếng Việt.
                - "reason": 1-2 câu tiếng Việt giải thích vì sao hợp thời tiết và màu sắc phối với nhau ra sao.
                """);

        return prompt.toString();
    }

    /**
     * Mỗi danh mục một ô riêng thay vì một mảng id tự do.
     *
     * <p>Đây mới là thứ chặn được lỗi "một bộ hai quần": prompt chỉ là lời khuyên, model vẫn có
     * quyền phá; còn schema chỉ có đúng một ô {@code pantsId} thì nó không có chỗ để nhét cái
     * quần thứ hai. Cùng kỹ thuật đã dùng cho phần nhận diện ảnh.</p>
     *
     * <p>Sinh thẳng từ {@link ClothingCategory} nên thêm danh mục mới vào enum là có ô mới,
     * không phải sửa hai nơi.</p>
     */
    private JsonNode buildSuggestionSchema(Set<ClothingCategory> availableCategories) {
        ObjectNode suggestion = objectMapper.createObjectNode();
        suggestion.put("type", "OBJECT");

        ObjectNode properties = suggestion.putObject("properties");
        properties.putObject("name").put("type", "STRING");
        properties.putObject("reason").put("type", "STRING");

        ArrayNode required = suggestion.putArray("required");
        required.add("name").add("reason");

        for (ClothingCategory category : ClothingCategory.values()) {
            // Tủ không có loại này thì bỏ hẳn ô đi, đừng để model phải bịa ra một id.
            if (!availableCategories.contains(category)) {
                continue;
            }

            ObjectNode slot = properties.putObject(slotName(category));
            slot.put("type", "INTEGER");

            // Áo và quần là xương sống của một bộ đồ. Đo thực tế: để hai ô này nullable thì
            // model rất hay trả về bộ chỉ có áo + giày, thiếu quần. Bắt buộc điền là hết.
            if (CORE_CATEGORIES.contains(category)) {
                required.add(slotName(category));
            } else {
                slot.put("nullable", true);
            }
        }

        ObjectNode list = objectMapper.createObjectNode();
        list.put("type", "ARRAY");
        list.put("maxItems", MAX_SUGGESTIONS);
        list.set("items", suggestion);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "OBJECT");
        schema.putObject("properties").set("suggestions", list);
        schema.putArray("required").add("suggestions");
        return schema;
    }

    /** SHIRT -> shirtId, PANTS -> pantsId, ... */
    private static String slotName(ClothingCategory category) {
        return category.name().toLowerCase(java.util.Locale.ROOT) + "Id";
    }

    private List<AiOutfitSuggestionResponse> parseSuggestions(String rawJson, Map<Long, ClothingItem> itemsById) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (IOException exception) {
            log.warn("Gemini returned non-JSON payload: {}", rawJson);
            throw new AiUnavailableException("AI trả về dữ liệu không đọc được. Vui lòng thử lại.");
        }

        JsonNode suggestions = root.path("suggestions");
        List<AiOutfitSuggestionResponse> result = new ArrayList<>();

        if (suggestions.isArray()) {
            for (JsonNode suggestion : suggestions) {
                if (result.size() >= MAX_SUGGESTIONS) {
                    break;
                }

                // Schema đã lo phần "mỗi loại một ô", nhưng vẫn phải kiểm lại: model có thể bịa
                // id không có thật, hoặc nhét id cái áo vào ô quần. Món sai loại thì bỏ, không
                // sửa hộ — đoán ý AI dễ tạo ra bộ đồ mà nó không hề định gợi ý.
                List<ClothingItemResponse> items = new ArrayList<>();
                for (ClothingCategory category : ClothingCategory.values()) {
                    JsonNode idNode = suggestion.path(slotName(category));
                    if (!idNode.canConvertToLong()) {
                        continue;
                    }

                    ClothingItem item = itemsById.get(idNode.asLong());
                    if (item == null) {
                        log.debug("AI trả id không có trong tủ đồ: {}", idNode.asLong());
                        continue;
                    }
                    if (item.getCategory() != category) {
                        log.debug("AI xếp \"{}\" ({}) vào ô {}", item.getName(), item.getCategory(), category);
                        continue;
                    }

                    items.add(ClothingItemResponse.from(item));
                }

                if (items.size() < 2) {
                    continue;
                }

                String name = suggestion.path("name").asText("Bộ đồ AI gợi ý");
                String reason = suggestion.path("reason").asText("");
                result.add(new AiOutfitSuggestionResponse(name, reason, items));
            }
        }

        if (result.isEmpty()) {
            log.warn("Gemini returned no usable suggestions: {}", rawJson);
            throw new AiUnavailableException("AI chưa đưa ra được gợi ý hợp lệ. Vui lòng thử lại.");
        }

        return result;
    }

    private String toneLabel(ColorTone tone) {
        return tone.getLabel();
    }

    private String categoryLabel(ClothingItem item) {
        return switch (item.getCategory()) {
            case SHIRT -> "Áo";
            case PANTS -> "Quần";
            case SHOES -> "Giày";
            case JACKET -> "Áo khoác";
            case ACCESSORY -> "Phụ kiện";
        };
    }

    private String seasonLabel(ClothingItem item) {
        return switch (item.getSeason()) {
            case SUMMER -> "Mùa hè";
            case WINTER -> "Mùa đông";
            case ALL_SEASON -> "Quanh năm";
        };
    }

    private String styleLabel(ClothingItem item) {
        return switch (item.getStyle()) {
            case CASUAL -> "Thường ngày";
            case FORMAL -> "Trang trọng";
            case STREETWEAR -> "Đường phố";
            case SPORT -> "Thể thao";
        };
    }

    private String statusLabel(ClothingItem item) {
        ClothingStatus status = item.getStatus();
        return switch (status) {
            case AVAILABLE -> "Sẵn sàng";
            case LAUNDRY -> "Đang giặt";
            case UNAVAILABLE -> "Chưa dùng được";
        };
    }
}
