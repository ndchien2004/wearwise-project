package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.SaveWearPlanRequest;
import org.group7.wearwise.dto.response.WearPlanResponse;
import org.group7.wearwise.service.WearPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Đọc và lưu các đợt kế hoạch mặc. Chỉ đụng database nên nằm ở nhánh thường — bước sinh kế hoạch
 * (tốn một lượt gọi Gemini) nằm riêng ở {@link WearPlanGenerationController} dưới {@code /api/ai}.
 */
@RestController
@RequestMapping("/api/wear-plans")
public class WearPlanController {

    private final WearPlanService wearPlanService;

    public WearPlanController(WearPlanService wearPlanService) {
        this.wearPlanService = wearPlanService;
    }

    @GetMapping
    public List<WearPlanResponse> list(
            Authentication authentication,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        return activeOnly
                ? wearPlanService.activePlans(authentication.getName(), LocalDate.now())
                : wearPlanService.listPlans(authentication.getName());
    }

    @GetMapping("/{id}")
    public WearPlanResponse get(Authentication authentication, @PathVariable Long id) {
        return wearPlanService.getPlan(authentication.getName(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WearPlanResponse save(
            Authentication authentication,
            @Valid @RequestBody SaveWearPlanRequest request
    ) {
        return wearPlanService.save(authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        wearPlanService.deletePlan(authentication.getName(), id);
    }
}
