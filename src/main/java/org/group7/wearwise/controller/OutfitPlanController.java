package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.OutfitPlanRequest;
import org.group7.wearwise.dto.response.OutfitPlanResponse;
import org.group7.wearwise.entity.OutfitPlan;
import org.group7.wearwise.service.OutfitPlanService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/outfit-plans")
public class OutfitPlanController {

    private final OutfitPlanService outfitPlanService;

    public OutfitPlanController(OutfitPlanService outfitPlanService) {
        this.outfitPlanService = outfitPlanService;
    }

    @GetMapping
    public List<OutfitPlanResponse> findPlans(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return outfitPlanService.findPlans(authentication.getName(), start, end)
                .stream()
                .map(OutfitPlanResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public OutfitPlanResponse getPlan(Authentication authentication, @PathVariable Long id) {
        return OutfitPlanResponse.from(outfitPlanService.getPlanById(authentication.getName(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OutfitPlanResponse createPlan(Authentication authentication, @Valid @RequestBody OutfitPlanRequest request) {
        OutfitPlan plan = outfitPlanService.createPlan(
                authentication.getName(),
                request.date(),
                request.outfitId(),
                request.note()
        );

        return OutfitPlanResponse.from(plan);
    }

    @PutMapping("/{id}")
    public OutfitPlanResponse updatePlan(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody OutfitPlanRequest request
    ) {
        OutfitPlan plan = outfitPlanService.updatePlan(
                authentication.getName(),
                id,
                request.date(),
                request.outfitId(),
                request.note()
        );

        return OutfitPlanResponse.from(plan);
    }

    @PatchMapping("/{id}/complete")
    public OutfitPlanResponse completePlan(Authentication authentication, @PathVariable Long id) {
        return OutfitPlanResponse.from(outfitPlanService.completePlan(authentication.getName(), id));
    }

    @PatchMapping("/{id}/uncomplete")
    public OutfitPlanResponse uncompletePlan(Authentication authentication, @PathVariable Long id) {
        return OutfitPlanResponse.from(outfitPlanService.uncompletePlan(authentication.getName(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(Authentication authentication, @PathVariable Long id) {
        outfitPlanService.deletePlan(authentication.getName(), id);
    }
}
