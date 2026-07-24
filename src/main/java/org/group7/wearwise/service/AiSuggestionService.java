package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.group7.wearwise.dto.request.AiWeeklyPlanRequest;
import org.group7.wearwise.dto.response.AiOutfitRankingResponse;
import org.group7.wearwise.dto.response.AiOutfitSuggestionResponse;
import org.group7.wearwise.dto.response.AiWeeklyDayPlanResponse;
import org.group7.wearwise.dto.response.ClothingItemResponse;
import org.group7.wearwise.dto.response.OutfitResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
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

    @Transactional(readOnly = true)
    public List<AiOutfitSuggestionResponse> suggest(
            String username,
            double temperature,
            boolean raining,
            String weatherDescription,
            ColorTone tone
    ) {
        List<ClothingItem> wardrobe = clothingItemService.getAllItems(username)
                .stream()
                .filter(item -> item.getCondition() != ClothingCondition.DAMAGED)
                .toList();

        if (wardrobe.size() < 2) {
            throw new IllegalArgumentException("Tủ đồ chưa đủ món để phối. Hãy thêm ít nhất 2 món đồ trước nhé!");
        }

        Map<Long, ClothingItem> itemsById = new LinkedHashMap<>();
        wardrobe.forEach(item -> itemsById.put(item.getId(), item));

        String prompt = buildPrompt(wardrobe, temperature, raining, weatherDescription, tone);
        String rawJson = geminiClient.generateJson(prompt);

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
        List<Outfit> outfits = outfitService.findOutfits(username, null, null, null, null);

        if (outfits.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bạn chưa có outfit nào để AI xếp hạng. Hãy tạo vài bộ ở mục Outfit trước nhé!");
        }

        Map<Long, Outfit> outfitsById = new LinkedHashMap<>();
        outfits.forEach(outfit -> outfitsById.put(outfit.getId(), outfit));

        String prompt = buildRankingPrompt(outfits, temperature, raining, weatherDescription, tone);
        String rawJson = geminiClient.generateJson(prompt);

        return parseRankings(rawJson, outfitsById);
    }

    /**
     * Nhờ Gemini xếp lịch mặc cho nhiều ngày dựa vào dự báo thời tiết,
     * ưu tiên đa dạng (hạn chế lặp lại cùng một bộ). Trả về mỗi ngày một outfit + lý do.
     */
    @Transactional(readOnly = true)
    public List<AiWeeklyDayPlanResponse> planWeek(
            String username,
            List<AiWeeklyPlanRequest.DayForecast> days,
            ColorTone tone
    ) {
        List<Outfit> outfits = outfitService.findOutfits(username, null, null, null, null);

        if (outfits.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bạn chưa có outfit nào để AI lên kế hoạch. Hãy tạo vài bộ ở mục Outfit trước nhé!");
        }
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("Thiếu dữ liệu dự báo thời tiết.");
        }

        Map<Long, Outfit> outfitsById = new LinkedHashMap<>();
        outfits.forEach(outfit -> outfitsById.put(outfit.getId(), outfit));

        String prompt = buildWeeklyPrompt(outfits, days, tone);
        String rawJson = geminiClient.generateJson(prompt);

        return parseWeeklyPlan(rawJson, outfitsById);
    }

    private String buildWeeklyPrompt(
            List<Outfit> outfits,
            List<AiWeeklyPlanRequest.DayForecast> days,
            ColorTone tone
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là một stylist. Hãy lên kế hoạch mặc cho từng ngày dựa vào dự báo thời tiết, ")
                .append("dùng các BỘ ĐỒ CÓ SẴN của người dùng.\n\n");

        if (tone != null) {
            prompt.append("Người dùng thiên về tone màu: ").append(toneLabel(tone)).append(".\n");
        }

        prompt.append("Dự báo các ngày (ngày | nhiệt độ thấp-cao°C | xác suất mưa% | mô tả):\n");
        for (AiWeeklyPlanRequest.DayForecast day : days) {
            prompt.append(day.date()).append(" | ")
                    .append(day.tempMin() == null ? "?" : Math.round(day.tempMin())).append("-")
                    .append(day.tempMax() == null ? "?" : Math.round(day.tempMax())).append("°C | ")
                    .append(day.rainChance() == null ? "?" : day.rainChance()).append("% | ")
                    .append(day.description() == null ? "" : day.description()).append("\n");
        }

        prompt.append("\nDanh sách outfit có sẵn (id | tên | mùa | phong cách | các món [loại-màu]):\n");
        for (Outfit outfit : outfits) {
            prompt.append(outfit.getId()).append(" | ")
                    .append(outfit.getName()).append(" | ")
                    .append(outfitSeasonLabel(outfit)).append(" | ")
                    .append(outfitStyleLabel(outfit)).append(" | ");
            String items = outfit.getClothingItems().stream()
                    .map(item -> categoryLabel(item) + "-" + (item.getColor() == null ? "?" : item.getColor()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(trống)");
            prompt.append(items).append("\n");
        }

        prompt.append("""

                Yêu cầu:
                - Gán cho MỖI ngày đúng 1 outfit có id trong danh sách (không bịa id).
                - Ưu tiên ĐA DẠNG: hạn chế lặp lại cùng một bộ trong tuần nếu còn lựa chọn khác.
                - Chọn bộ hợp thời tiết từng ngày (lạnh/mưa nên có áo khoác; nóng tránh đồ dày).
                - "reason": 1 câu tiếng Việt ngắn gọn giải thích lựa chọn cho ngày đó.

                Chỉ trả về JSON đúng cấu trúc sau, không thêm chữ nào khác:
                {"plan":[{"date":"YYYY-MM-DD","outfitId":1,"reason":"..."}]}
                """);

        return prompt.toString();
    }

    private List<AiWeeklyDayPlanResponse> parseWeeklyPlan(String rawJson, Map<Long, Outfit> outfitsById) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (IOException exception) {
            log.warn("Gemini returned non-JSON payload: {}", rawJson);
            throw new AiUnavailableException("AI trả về dữ liệu không đọc được. Vui lòng thử lại.");
        }

        JsonNode plan = root.path("plan");
        List<AiWeeklyDayPlanResponse> result = new ArrayList<>();

        if (plan.isArray()) {
            for (JsonNode day : plan) {
                String date = day.path("date").asText(null);
                JsonNode idNode = day.path("outfitId");
                if (date == null || !idNode.canConvertToLong()) {
                    continue;
                }
                Outfit outfit = outfitsById.get(idNode.asLong());
                if (outfit == null) {
                    continue;
                }
                String reason = day.path("reason").asText("");
                result.add(new AiWeeklyDayPlanResponse(date, OutfitResponse.from(outfit), reason));
            }
        }

        if (result.isEmpty()) {
            log.warn("Gemini returned no usable weekly plan: {}", rawJson);
            throw new AiUnavailableException("AI chưa lên được kế hoạch. Vui lòng thử lại.");
        }

        return result;
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
                - Mỗi bộ gồm 2-5 món, chỉ dùng id có trong danh sách.
                - Mỗi bộ nên đủ áo + quần (nếu tủ có), thêm giày/áo khoác/phụ kiện khi hợp lý.
                - Trời mưa hoặc lạnh thì nên có áo khoác; trời nóng thì tránh đồ dày.
                - Ưu tiên món "Sẵn sàng"; tránh món "Đang giặt" hoặc "Chưa dùng được" trừ khi không còn lựa chọn.
                - Phối màu hài hòa giữa các món (dựa vào màu và tone màu).
                - "name": tên bộ đồ ngắn gọn, gợi nhớ, tiếng Việt.
                - "reason": 1-2 câu tiếng Việt giải thích vì sao hợp thời tiết và màu sắc phối với nhau ra sao.

                Chỉ trả về JSON đúng cấu trúc sau, không thêm chữ nào khác:
                {"suggestions":[{"name":"...","itemIds":[1,2,3],"reason":"..."}]}
                """);

        return prompt.toString();
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

                Set<Long> ids = new LinkedHashSet<>();
                for (JsonNode idNode : suggestion.path("itemIds")) {
                    if (idNode.canConvertToLong()) {
                        ids.add(idNode.asLong());
                    }
                }

                // Chỉ giữ id thật sự thuộc tủ đồ của người dùng (AI có thể bịa id).
                List<ClothingItemResponse> items = ids.stream()
                        .map(itemsById::get)
                        .filter(item -> item != null)
                        .map(ClothingItemResponse::from)
                        .toList();

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
        return switch (tone) {
            case WARM -> "Tông ấm";
            case COOL -> "Tông lạnh";
            case NEUTRAL -> "Trung tính";
            case PASTEL -> "Pastel";
            case BRIGHT -> "Rực rỡ";
            case DARK -> "Tông tối";
        };
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
