package org.group7.wearwise.controller;

import org.group7.wearwise.dto.response.TryOnProfileResponse;
import org.group7.wearwise.dto.response.TryOnResultResponse;
import org.group7.wearwise.service.TryOnService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/try-on")
public class TryOnController {

    private final TryOnService tryOnService;

    public TryOnController(TryOnService tryOnService) {
        this.tryOnService = tryOnService;
    }

    /** Trạng thái tính năng thử đồ của người dùng hiện tại. */
    @GetMapping("/profile")
    public TryOnProfileResponse getProfile(Authentication authentication) {
        return tryOnService.getProfile(authentication.getName());
    }

    /** Tải ảnh bản thân lên (thao tác thực hiện 1 lần). */
    @PostMapping("/body-photo")
    public TryOnProfileResponse uploadBodyPhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        return tryOnService.uploadBodyPhoto(authentication.getName(), file);
    }

    @DeleteMapping("/body-photo")
    public TryOnProfileResponse deleteBodyPhoto(Authentication authentication) {
        return tryOnService.deleteBodyPhoto(authentication.getName());
    }

    /**
     * Ghép một món đồ (có ảnh) lên ảnh của người dùng và lưu kết quả.
     *
     * @param baseResultId tuỳ chọn — ghép chồng lên ảnh kết quả này thay vì ảnh cơ thể gốc,
     *                     để mặc lần lượt quần rồi áo lên cùng một người
     */
    @PostMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TryOnResultResponse generateForItem(
            Authentication authentication,
            @PathVariable Long itemId,
            @RequestParam(required = false) Long baseResultId
    ) {
        return TryOnResultResponse.from(
                tryOnService.generateForItem(authentication.getName(), itemId, baseResultId));
    }

    /** Lịch sử ảnh thử đồ của riêng một món đồ (để hiển thị ở trang chi tiết trong tủ đồ). */
    @GetMapping("/items/{itemId}")
    public List<TryOnResultResponse> listResultsForItem(Authentication authentication, @PathVariable Long itemId) {
        return tryOnService.listResultsForItem(authentication.getName(), itemId)
                .stream()
                .map(TryOnResultResponse::from)
                .toList();
    }

    /** Thử nguyên một outfit (ghép tất cả món có ảnh) lên ảnh của người dùng. */
    @PostMapping("/outfits/{outfitId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TryOnResultResponse generateForOutfit(
            Authentication authentication,
            @PathVariable Long outfitId,
            @RequestParam(required = false) Long baseResultId
    ) {
        return TryOnResultResponse.from(
                tryOnService.generateForOutfit(authentication.getName(), outfitId, baseResultId));
    }

    /** Lịch sử ảnh thử đồ của riêng một outfit (hiển thị ở trang chi tiết outfit). */
    @GetMapping("/outfits/{outfitId}")
    public List<TryOnResultResponse> listResultsForOutfit(Authentication authentication, @PathVariable Long outfitId) {
        return tryOnService.listResultsForOutfit(authentication.getName(), outfitId)
                .stream()
                .map(TryOnResultResponse::from)
                .toList();
    }

    /** Bộ sưu tập ảnh thử đồ đã tạo. */
    @GetMapping
    public List<TryOnResultResponse> listResults(Authentication authentication) {
        return tryOnService.listResults(authentication.getName())
                .stream()
                .map(TryOnResultResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResult(Authentication authentication, @PathVariable Long id) {
        tryOnService.deleteResult(authentication.getName(), id);
    }
}
