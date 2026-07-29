package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.group7.wearwise.dto.request.GenerateWearPlanRequest;
import org.group7.wearwise.dto.request.SaveWearPlanRequest;
import org.group7.wearwise.dto.response.OutfitResponse;
import org.group7.wearwise.dto.response.WearPlanPreviewResponse;
import org.group7.wearwise.dto.response.WearPlanResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.OutfitPlan;
import org.group7.wearwise.entity.WearPlan;
import org.group7.wearwise.exception.AiUnavailableException;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.exception.WearPlanNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.OutfitPlanRepository;
import org.group7.wearwise.repository.WearPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lên kế hoạch mặc nhiều ngày từ một câu yêu cầu tự do của người dùng.
 *
 * <p>Chia làm hai bước tách bạch — <b>sinh</b> rồi mới <b>lưu</b> — vì kết quả AI không phải lúc
 * nào cũng dùng được. Đổ thẳng vào lịch thì mỗi lần kế hoạch không ưng ý người dùng lại phải đi
 * dọn từng ngày, và tệ hơn là những ngày họ tự đặt tay đã bị ghi đè mất.</p>
 *
 * <p>Luật xếp lịch nằm ở {@code prompts/wear-plan.md} chứ không nằm trong code: đó là phần cần
 * chỉnh đi chỉnh lại nhiều nhất khi kết quả chưa ưng, và nó là văn bản tiếng Việt thuần chứ không
 * phải logic.</p>
 */
@Service
public class WearPlanService {

    private static final Logger log = LoggerFactory.getLogger(WearPlanService.class);

    private static final String PROMPT_PATH = "prompts/wear-plan.md";
    private static final int MAX_PLAN_OUTPUT_TOKENS = 4096;

    private final WearPlanRepository wearPlanRepository;
    private final OutfitPlanRepository outfitPlanRepository;
    private final OutfitService outfitService;
    private final AppUserRepository appUserRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Nạp một lần lúc dựng bean chứ không đọc lại mỗi request: file nằm trong jar nên không đổi
     * giữa hai lần gọi, và hỏng file thì hỏng ngay lúc khởi động — rõ hơn nhiều so với việc lỗi
     * chỉ hiện ra khi có người bấm nút.
     */
    private final String planRules;

    public WearPlanService(
            WearPlanRepository wearPlanRepository,
            OutfitPlanRepository outfitPlanRepository,
            OutfitService outfitService,
            AppUserRepository appUserRepository,
            GeminiClient geminiClient
    ) {
        this.wearPlanRepository = wearPlanRepository;
        this.outfitPlanRepository = outfitPlanRepository;
        this.outfitService = outfitService;
        this.appUserRepository = appUserRepository;
        this.geminiClient = geminiClient;
        this.planRules = loadPromptRules();
    }

    private static String loadPromptRules() {
        try (var stream = new ClassPathResource(PROMPT_PATH).getInputStream()) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            // Phần trước dấu --- là ghi chú cho lập trình viên về chính file này; gửi cho model
            // chỉ tổ làm loãng prompt bằng chuyện build jar.
            int separator = content.indexOf("\n---\n");
            return (separator < 0 ? content : content.substring(separator + 5)).trim();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không đọc được " + PROMPT_PATH, exception);
        }
    }

    /** Sinh kế hoạch nhưng KHÔNG lưu — người dùng xem trước rồi mới quyết. */
    @Transactional(readOnly = true)
    public WearPlanPreviewResponse generate(String username, GenerateWearPlanRequest request) {
        List<Outfit> outfits = outfitService.findOutfits(username, null, null, null, null).stream()
                .filter(OutfitService::isAvailable)
                .toList();

        if (outfits.isEmpty()) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_REQUEST,
                    "Bạn chưa có outfit nào lành lặn để lên kế hoạch. Hãy tạo vài bộ ở mục Outfit trước nhé!");
        }

        int days = request.days() == null ? 7 : request.days();
        if (days < 1 || days > WearPlan.MAX_DAYS) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_REQUEST, "Mỗi đợt kế hoạch từ 1 tới " + WearPlan.MAX_DAYS + " ngày.");
        }

        LocalDate startDate = request.startDate() == null ? LocalDate.now() : request.startDate();
        LocalDate endDate = startDate.plusDays(days - 1L);

        Map<Long, Outfit> outfitsById = new LinkedHashMap<>();
        outfits.forEach(outfit -> outfitsById.put(outfit.getId(), outfit));

        String rawJson = geminiClient.generateJson(
                buildPrompt(request, outfits, startDate, days),
                buildSchema(days),
                MAX_PLAN_OUTPUT_TOKENS
        );

        return parsePlan(rawJson, outfitsById, username, startDate, endDate, days);
    }

    private String buildPrompt(
            GenerateWearPlanRequest request,
            List<Outfit> outfits,
            LocalDate startDate,
            int days
    ) {
        StringBuilder prompt = new StringBuilder(planRules);

        prompt.append("\n\n## Yêu cầu của người dùng\n\n").append(request.request().trim()).append("\n");

        prompt.append("\n## Khoảng thời gian\n\n")
                .append("Cần đúng ").append(days).append(" ngày, từ ").append(startDate)
                .append(" tới ").append(startDate.plusDays(days - 1L)).append(".\n")
                .append("Các ngày phải liên tiếp và đúng thứ tự.\n");

        if (request.tone() != null) {
            prompt.append("\nNgười dùng thiên về tone màu: ").append(request.tone().getLabel()).append(".\n");
        }

        appendForecast(prompt, request.forecast());
        appendWardrobe(prompt, outfits);

        return prompt.toString();
    }

    private void appendForecast(StringBuilder prompt, List<GenerateWearPlanRequest.DayForecast> forecast) {
        if (forecast == null || forecast.isEmpty()) {
            prompt.append("\n## Thời tiết\n\nKhông có dự báo cho đợt này — bỏ qua tiêu chí thời tiết, ")
                    .append("đừng suy đoán và đừng nhắc tới thời tiết trong phần lý do.\n");
            return;
        }

        prompt.append("\n## Dự báo (ngày | thấp-cao°C | mưa% | mô tả)\n\n");
        for (GenerateWearPlanRequest.DayForecast day : forecast) {
            prompt.append(day.date()).append(" | ")
                    .append(day.tempMin() == null ? "?" : Math.round(day.tempMin())).append("-")
                    .append(day.tempMax() == null ? "?" : Math.round(day.tempMax())).append("°C | ")
                    .append(day.rainChance() == null ? "?" : day.rainChance()).append("% | ")
                    .append(day.description() == null ? "" : day.description()).append("\n");
        }
    }

    private void appendWardrobe(StringBuilder prompt, List<Outfit> outfits) {
        prompt.append("\n## Các bộ có sẵn (id | tên | mùa | phong cách | yêu thích | lần mặc gần nhất | các món)\n\n");

        for (Outfit outfit : outfits) {
            String items = outfit.getClothingItems().stream()
                    .map(item -> item.getCategory() + "-" + (item.getColor() == null ? "?" : item.getColor()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(trống)");

            prompt.append(outfit.getId()).append(" | ")
                    .append(outfit.getName()).append(" | ")
                    .append(outfit.getSeason()).append(" | ")
                    .append(outfit.getStyle()).append(" | ")
                    .append(Boolean.TRUE.equals(outfit.getFavorite()) ? "yêu thích" : "-").append(" | ")
                    .append(outfit.getLastWornAt() == null ? "chưa mặc" : outfit.getLastWornAt().toLocalDate())
                    .append(" | ")
                    // Bộ đang vướng đồ giặt vẫn được chọn: tới ngày mặc rất có thể đã giặt xong,
                    // và loại hẳn thì kế hoạch 7 ngày mất gần hết lựa chọn vì hôm nay có vài món bẩn.
                    .append(items).append("\n");
        }
    }

    /**
     * Ép model trả đúng số ngày bằng schema thay vì dặn bằng lời: {@code minItems}/{@code maxItems}
     * là ràng buộc phía Gemini, còn một câu "hãy trả đủ 7 ngày" thì model bỏ qua lúc nào không hay.
     */
    private JsonNode buildSchema(int days) {
        ObjectNode day = objectMapper.createObjectNode();
        day.put("type", "OBJECT");
        ObjectNode dayProps = day.putObject("properties");
        dayProps.putObject("date").put("type", "STRING");
        dayProps.putObject("outfitId").put("type", "INTEGER");
        dayProps.putObject("reason").put("type", "STRING");
        day.putArray("required").add("date").add("outfitId").add("reason");

        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("type", "ARRAY");
        plan.put("minItems", days);
        plan.put("maxItems", days);
        plan.set("items", day);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "OBJECT");
        ObjectNode rootProps = root.putObject("properties");
        rootProps.putObject("title").put("type", "STRING");
        rootProps.putObject("summary").put("type", "STRING");
        rootProps.set("plan", plan);
        root.putArray("required").add("title").add("summary").add("plan");

        return root;
    }

    private WearPlanPreviewResponse parsePlan(
            String rawJson,
            Map<Long, Outfit> outfitsById,
            String username,
            LocalDate startDate,
            LocalDate endDate,
            int days
    ) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (IOException exception) {
            log.warn("Gemini trả về payload không phải JSON: {}", rawJson);
            throw new AiUnavailableException("AI trả về dữ liệu không đọc được. Vui lòng thử lại.");
        }

        Map<LocalDate, OutfitPlan> existingByDate = new HashMap<>();
        outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetween(username, startDate, endDate)
                .forEach(plan -> existingByDate.put(plan.getPlanDate(), plan));

        List<WearPlanPreviewResponse.Day> parsed = new ArrayList<>();

        for (JsonNode node : root.path("plan")) {
            LocalDate date = readDate(node.path("date").asText(null));
            JsonNode idNode = node.path("outfitId");
            if (date == null || !idNode.canConvertToLong()) {
                continue;
            }

            Outfit outfit = outfitsById.get(idNode.asLong());
            // Model thỉnh thoảng bịa id hoặc trả ngày ngoài khoảng đã dặn. Bỏ dòng đó đi thay vì
            // ném lỗi cả lượt: mất một ngày còn hơn mất cả kế hoạch và một lượt gọi trả tiền.
            if (outfit == null || date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }

            OutfitPlan existing = existingByDate.get(date);
            parsed.add(new WearPlanPreviewResponse.Day(
                    date,
                    OutfitResponse.from(outfit),
                    node.path("reason").asText(""),
                    existing == null ? null : existing.getId(),
                    existing == null ? null : existing.getOutfit().getName()
            ));
        }

        List<WearPlanPreviewResponse.Day> unique = parsed.stream()
                .collect(LinkedHashMap<LocalDate, WearPlanPreviewResponse.Day>::new,
                        (map, item) -> map.putIfAbsent(item.date(), item),
                        Map::putAll)
                .values()
                .stream()
                .sorted(java.util.Comparator.comparing(WearPlanPreviewResponse.Day::date))
                .toList();

        if (unique.isEmpty()) {
            log.warn("Gemini không trả về ngày nào dùng được cho kế hoạch: {}", rawJson);
            throw new AiUnavailableException("AI chưa lên được kế hoạch nào dùng được. Vui lòng thử lại.");
        }

        if (unique.size() < days) {
            log.info("AI chỉ trả về {}/{} ngày dùng được.", unique.size(), days);
        }

        return new WearPlanPreviewResponse(
                trim(root.path("title").asText(""), 120),
                trim(root.path("summary").asText(""), 1000),
                unique.get(0).date(),
                unique.get(unique.size() - 1).date(),
                unique
        );
    }

    /** Lưu kế hoạch: tạo cái vỏ, rồi sinh từng ngày vào lịch như kế hoạch tự đặt tay. */
    @Transactional
    public WearPlanResponse save(String username, SaveWearPlanRequest request) {
        AppUser owner = getOwner(username);

        List<SaveWearPlanRequest.Day> days = request.days().stream()
                .sorted(java.util.Comparator.comparing(SaveWearPlanRequest.Day::date))
                .toList();

        if (days.size() > WearPlan.MAX_DAYS) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_REQUEST, "Mỗi đợt kế hoạch tối đa " + WearPlan.MAX_DAYS + " ngày.");
        }

        LocalDate start = days.get(0).date();
        LocalDate end = days.get(days.size() - 1).date();

        Map<LocalDate, OutfitPlan> existingByDate = new HashMap<>();
        outfitPlanRepository.findAllByOwner_UsernameAndPlanDateBetween(username, start, end)
                .forEach(plan -> existingByDate.put(plan.getPlanDate(), plan));

        WearPlan plan = wearPlanRepository.save(WearPlan.builder()
                .owner(owner)
                .title(request.title().trim())
                .userRequest(request.userRequest().trim())
                .summary(request.summary() == null ? null : request.summary().trim())
                .startDate(start)
                .endDate(end)
                .build());

        List<OutfitPlan> saved = new ArrayList<>();

        for (SaveWearPlanRequest.Day day : days) {
            OutfitPlan existing = existingByDate.get(day.date());

            // Không tick ghi đè mà ngày đó đã có kế hoạch thì bỏ qua — kế hoạch người dùng tự đặt
            // không bao giờ bị xóa mà không có lệnh rõ ràng.
            if (existing != null && !day.replaceExisting()) {
                continue;
            }
            if (existing != null) {
                outfitPlanRepository.delete(existing);
            }

            Outfit outfit = outfitService.getOutfitById(username, day.outfitId());
            saved.add(outfitPlanRepository.save(OutfitPlan.builder()
                    .owner(owner)
                    .outfit(outfit)
                    .planDate(day.date())
                    .note(day.note())
                    .wearPlan(plan)
                    .build()));
        }

        if (saved.isEmpty()) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_REQUEST,
                    "Mọi ngày trong kế hoạch đều đã có lịch sẵn và bạn chọn giữ nguyên, nên không có gì để lưu.");
        }

        // Khoảng ngày thật có thể hẹp hơn yêu cầu ban đầu khi người dùng bỏ qua vài ngày trùng.
        plan.setStartDate(saved.get(0).getPlanDate());
        plan.setEndDate(saved.get(saved.size() - 1).getPlanDate());

        return WearPlanResponse.detail(plan, saved);
    }

    @Transactional(readOnly = true)
    public List<WearPlanResponse> listPlans(String username) {
        return wearPlanRepository.findAllByOwner_UsernameOrderByStartDateDesc(normalize(username)).stream()
                .map(plan -> WearPlanResponse.summary(
                        plan, outfitPlanRepository.findAllByWearPlan_IdOrderByPlanDateAsc(plan.getId())))
                .toList();
    }

    /** Đợt đang chạy hôm nay — thẻ kế hoạch ở trang chủ. Rỗng khi không có đợt nào phủ hôm nay. */
    @Transactional(readOnly = true)
    public List<WearPlanResponse> activePlans(String username, LocalDate today) {
        return wearPlanRepository
                .findAllByOwner_UsernameAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                        normalize(username), today, today)
                .stream()
                .map(plan -> WearPlanResponse.detail(
                        plan, outfitPlanRepository.findAllByWearPlan_IdOrderByPlanDateAsc(plan.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WearPlanResponse getPlan(String username, Long id) {
        WearPlan plan = findOwned(username, id);
        return WearPlanResponse.detail(
                plan, outfitPlanRepository.findAllByWearPlan_IdOrderByPlanDateAsc(plan.getId()));
    }

    /** Xóa cả đợt kèm mọi ngày của nó — người dùng bỏ chuyến đi thì không phải dọn từng ngày. */
    @Transactional
    public void deletePlan(String username, Long id) {
        WearPlan plan = findOwned(username, id);
        outfitPlanRepository.deleteAll(outfitPlanRepository.findAllByWearPlan_IdOrderByPlanDateAsc(plan.getId()));
        wearPlanRepository.delete(plan);
    }

    private WearPlan findOwned(String username, Long id) {
        return wearPlanRepository.findByIdAndOwner_Username(id, normalize(username))
                .orElseThrow(() -> new WearPlanNotFoundException(id));
    }

    private AppUser getOwner(String username) {
        return appUserRepository.findByUsername(normalize(username))
                .orElseThrow(() -> new AuthenticationFailedException("Tài khoản không tồn tại."));
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthenticationFailedException("Chưa đăng nhập.");
        }
        return username.trim();
    }

    private static LocalDate readDate(String raw) {
        try {
            return raw == null ? null : LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

}
