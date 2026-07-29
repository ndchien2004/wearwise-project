package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.GenerateWearPlanRequest;
import org.group7.wearwise.dto.response.WearPlanPreviewResponse;
import org.group7.wearwise.service.WearPlanService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sinh kế hoạch bằng Gemini. Nằm dưới {@code /api/ai} là <b>có chủ đích</b>: mỗi lượt gọi tốn tiền
 * thật, và {@code RateLimitFilter} xếp nhóm theo tiền tố đường dẫn. Chuyển endpoint này sang
 * {@code /api/wear-plans} là đẩy nó vào hạn mức GENERAL — 240 lượt/phút, đủ để đốt sạch quota.
 *
 * <p>Kết quả <b>không</b> được lưu: người dùng xem trước rồi mới bấm lưu qua
 * {@link WearPlanController}. Sinh xong đổ thẳng vào lịch thì mỗi lần kế hoạch không ưng ý họ lại
 * phải đi dọn từng ngày.</p>
 */
@RestController
@RequestMapping("/api/ai")
public class WearPlanGenerationController {

    private final WearPlanService wearPlanService;

    public WearPlanGenerationController(WearPlanService wearPlanService) {
        this.wearPlanService = wearPlanService;
    }

    @PostMapping("/wear-plans")
    public WearPlanPreviewResponse generate(
            Authentication authentication,
            @Valid @RequestBody GenerateWearPlanRequest request
    ) {
        return wearPlanService.generate(authentication.getName(), request);
    }
}
