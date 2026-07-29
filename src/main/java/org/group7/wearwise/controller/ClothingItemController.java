package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.ClothingItemBatchRequest;
import org.group7.wearwise.dto.request.ClothingItemRequest;
import org.group7.wearwise.dto.request.FavoriteRequest;
import org.group7.wearwise.dto.response.ClothingItemOptionsResponse;
import org.group7.wearwise.dto.response.ClothingItemResponse;
import org.group7.wearwise.dto.response.OutfitResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.service.ClothingItemService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.List;

@RestController
@RequestMapping("/api/clothing-items")
public class ClothingItemController {

    /**
     * Thứ tự cố định theo id tăng dần. Bắt buộc phải có khi phân trang: thiếu {@code order by},
     * database không cam kết trả về cùng một thứ tự giữa hai lần gọi, và người dùng sẽ thấy có
     * món lặp lại ở trang sau còn món khác thì biến mất. Chọn id vì nó giữ nguyên thứ tự mà
     * giao diện vẫn hiển thị từ trước tới nay (món thêm trước đứng trước).
     */
    private static final Sort ITEM_SORT = Sort.by(Sort.Direction.ASC, "id");

    /** Chặn trên kích thước trang để một request không kéo cả tủ đồ về. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ClothingItemService clothingItemService;

    public ClothingItemController(ClothingItemService clothingItemService) {
        this.clothingItemService = clothingItemService;
    }

    /**
     * Danh sách món đồ, cắt trang ở database.
     *
     * <p>Trả về {@code Page<>} nên phần tử nằm trong {@code content}, kèm {@code totalPages} và
     * {@code totalElements} để giao diện dựng thanh phân trang mà không phải tự đếm.
     *
     * <p>{@code unpaged=true} lấy toàn bộ kết quả trong một lần gọi. Chỉ dành cho ô chọn món khi
     * phối outfit — ở đó người dùng phải chọn được bất kỳ món nào trong tủ, cắt trang sẽ khiến
     * món ở trang sau không bao giờ chọn tới. Đây là dữ liệu của riêng một người dùng nên khối
     * lượng có giới hạn tự nhiên; đừng dùng cờ này cho danh sách hiển thị thông thường.
     */
    @GetMapping
    public Page<ClothingItemResponse> findItems(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ClothingCategory category,
            @RequestParam(required = false) Season season,
            @RequestParam(required = false) Style style,
            @RequestParam(required = false) ClothingCondition condition,
            @RequestParam(required = false) ClothingStatus status,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) ColorTone colorTone,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "false") boolean unpaged
    ) {
        Pageable pageable = unpaged
                ? Pageable.unpaged(ITEM_SORT)
                : PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE), ITEM_SORT);

        return clothingItemService
                .findItems(authentication.getName(), keyword, category, season, style,
                        condition, status, favorite, colorTone, hasImage, pageable)
                .map(ClothingItemResponse::from);
    }

    @GetMapping("/recently-worn")
    public List<ClothingItemResponse> getRecentlyWornItems(
            Authentication authentication,
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return clothingItemService.getRecentlyWornItems(authentication.getName(), limit)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @GetMapping("/most-worn")
    public List<ClothingItemResponse> getMostWornItems(
            Authentication authentication,
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return clothingItemService.getMostWornItems(authentication.getName(), limit)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @GetMapping("/least-worn")
    public List<ClothingItemResponse> getLeastWornItems(
            Authentication authentication,
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return clothingItemService.getLeastWornItems(authentication.getName(), limit)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ClothingItemResponse getItem(Authentication authentication, @PathVariable Long id) {
        return ClothingItemResponse.from(clothingItemService.getItemById(authentication.getName(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClothingItemResponse createItem(Authentication authentication, @Valid @RequestBody ClothingItemRequest request) {
        ClothingItem item = clothingItemService.createItem(
                authentication.getName(),
                request.name(),
                request.color(),
                request.colorTone(),
                request.category(),
                request.season(),
                request.style(),
                request.condition(),
                request.status(),
                request.wearCount(),
                request.lastWornAt(),
                request.favorite(),
                request.imageUrl()
        );

        return ClothingItemResponse.from(item);
    }

    /** Thêm nhiều món cùng lúc — dùng sau khi quét ảnh nhiều món bằng AI. */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ClothingItemResponse> createItems(
            Authentication authentication,
            @Valid @RequestBody ClothingItemBatchRequest request
    ) {
        List<ClothingItemService.ClothingItemDraft> drafts = request.items().stream()
                .map(item -> new ClothingItemService.ClothingItemDraft(
                        item.name(),
                        item.color(),
                        item.colorTone(),
                        item.category(),
                        item.season(),
                        item.style(),
                        item.condition(),
                        item.status(),
                        item.favorite(),
                        item.imageUrl()
                ))
                .toList();

        return clothingItemService.createItems(authentication.getName(), drafts)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public ClothingItemResponse updateItem(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ClothingItemRequest request
    ) {
        ClothingItem item = clothingItemService.updateItem(
                authentication.getName(),
                id,
                request.name(),
                request.color(),
                request.colorTone(),
                request.category(),
                request.season(),
                request.style(),
                request.condition(),
                request.status(),
                request.wearCount(),
                request.lastWornAt(),
                request.favorite(),
                request.imageUrl()
        );

        return ClothingItemResponse.from(item);
    }

    @PatchMapping("/{id}/favorite")
    public ClothingItemResponse updateFavorite(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody FavoriteRequest request
    ) {
        return ClothingItemResponse.from(clothingItemService.updateFavorite(authentication.getName(), id, request.favorite()));
    }

    @PatchMapping("/{id}/wear")
    public ClothingItemResponse markAsWorn(Authentication authentication, @PathVariable Long id) {
        return ClothingItemResponse.from(clothingItemService.markAsWorn(authentication.getName(), id));
    }

    /** Danh sách món đã ẩn — mục "Không khả dụng" của tủ đồ. */
    @GetMapping("/archived")
    public List<ClothingItemResponse> getArchivedItems(Authentication authentication) {
        return clothingItemService.getArchivedItems(authentication.getName())
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    /** Ẩn món đồ (xóa mềm) khi không xóa cứng được vì đang nằm trong outfit / đã có lịch sử mặc. */
    @PatchMapping("/{id}/archive")
    public ClothingItemResponse archiveItem(Authentication authentication, @PathVariable Long id) {
        return ClothingItemResponse.from(clothingItemService.archiveItem(authentication.getName(), id));
    }

    @PatchMapping("/{id}/restore")
    public ClothingItemResponse restoreItem(Authentication authentication, @PathVariable Long id) {
        return ClothingItemResponse.from(clothingItemService.restoreItem(authentication.getName(), id));
    }

    /** Outfit đang dùng món này — giao diện hiện danh sách trước khi người dùng xác nhận ẩn. */
    @GetMapping("/{id}/outfits")
    public List<OutfitResponse> getOutfitsUsingItem(Authentication authentication, @PathVariable Long id) {
        return clothingItemService.findOutfitsUsing(authentication.getName(), id)
                .stream()
                .map(OutfitResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(Authentication authentication, @PathVariable Long id) {
        clothingItemService.deleteItem(authentication.getName(), id);
    }

    @GetMapping("/options")
    public ClothingItemOptionsResponse getOptions() {
        return ClothingItemOptionsResponse.current();
    }
}
