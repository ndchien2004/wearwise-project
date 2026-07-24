package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.AiSuggestionRequest;
import org.group7.wearwise.dto.request.AiWeeklyPlanRequest;
import org.group7.wearwise.dto.response.AiOutfitRankingResponse;
import org.group7.wearwise.dto.response.AiOutfitSuggestionResponse;
import org.group7.wearwise.dto.response.AiWeeklyDayPlanResponse;
import org.group7.wearwise.service.AiSuggestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Gợi ý phối đồ bằng Gemini dựa trên thời tiết + thuộc tính quần áo + tone màu. */
@RestController
@RequestMapping("/api/ai")
public class AiSuggestionController {

    private final AiSuggestionService aiSuggestionService;

    public AiSuggestionController(AiSuggestionService aiSuggestionService) {
        this.aiSuggestionService = aiSuggestionService;
    }

    @PostMapping("/outfit-suggestions")
    public List<AiOutfitSuggestionResponse> suggestOutfits(
            Authentication authentication,
            @Valid @RequestBody AiSuggestionRequest request
    ) {
        return aiSuggestionService.suggest(
                authentication.getName(),
                request.temperature(),
                Boolean.TRUE.equals(request.raining()),
                request.weatherDescription(),
                request.tone()
        );
    }

    /** Nhờ Gemini xếp hạng các outfit CÓ SẴN theo mức phù hợp thời tiết (bấm nút mới chạy). */
    @PostMapping("/outfit-ranking")
    public List<AiOutfitRankingResponse> rankOutfits(
            Authentication authentication,
            @Valid @RequestBody AiSuggestionRequest request
    ) {
        return aiSuggestionService.rankOutfits(
                authentication.getName(),
                request.temperature(),
                Boolean.TRUE.equals(request.raining()),
                request.weatherDescription(),
                request.tone()
        );
    }

    /** Nhờ Gemini lên kế hoạch mặc cho nhiều ngày dựa vào dự báo thời tiết. */
    @PostMapping("/weekly-plan")
    public List<AiWeeklyDayPlanResponse> planWeek(
            Authentication authentication,
            @Valid @RequestBody AiWeeklyPlanRequest request
    ) {
        return aiSuggestionService.planWeek(
                authentication.getName(),
                request.days(),
                request.tone()
        );
    }

    /** Trạng thái cấu hình — frontend dùng để ẩn/hiện nút AI. */
    @GetMapping("/status")
    public Map<String, Boolean> getStatus() {
        return Map.of("geminiConfigured", aiSuggestionService.isConfigured());
    }
}
