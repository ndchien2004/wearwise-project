package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.ClothingItemRequest;
import org.group7.wearwise.dto.request.FavoriteRequest;
import org.group7.wearwise.dto.response.ClothingItemOptionsResponse;
import org.group7.wearwise.dto.response.ClothingItemResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.service.ClothingItemService;
import org.springframework.http.HttpStatus;
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

    private final ClothingItemService clothingItemService;

    public ClothingItemController(ClothingItemService clothingItemService) {
        this.clothingItemService = clothingItemService;
    }

    @GetMapping
    public List<ClothingItemResponse> findItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ClothingCategory category,
            @RequestParam(required = false) Season season,
            @RequestParam(required = false) Style style,
            @RequestParam(required = false) ClothingCondition condition,
            @RequestParam(required = false) ClothingStatus status,
            @RequestParam(required = false) Boolean favorite
    ) {
        return clothingItemService.findItems(keyword, category, season, style, condition, status, favorite)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @GetMapping("/recently-worn")
    public List<ClothingItemResponse> getRecentlyWornItems(
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return clothingItemService.getRecentlyWornItems(limit)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @GetMapping("/most-worn")
    public List<ClothingItemResponse> getMostWornItems(
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return clothingItemService.getMostWornItems(limit)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @GetMapping("/least-worn")
    public List<ClothingItemResponse> getLeastWornItems(
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return clothingItemService.getLeastWornItems(limit)
                .stream()
                .map(ClothingItemResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ClothingItemResponse getItem(@PathVariable Long id) {
        return ClothingItemResponse.from(clothingItemService.getItemById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClothingItemResponse createItem(@Valid @RequestBody ClothingItemRequest request) {
        ClothingItem item = clothingItemService.createItem(
                request.name(),
                request.color(),
                request.category(),
                request.season(),
                request.style(),
                request.condition(),
                request.status(),
                request.wearCount(),
                request.lastWornAt(),
                request.favorite()
        );

        return ClothingItemResponse.from(item);
    }

    @PutMapping("/{id}")
    public ClothingItemResponse updateItem(
            @PathVariable Long id,
            @Valid @RequestBody ClothingItemRequest request
    ) {
        ClothingItem item = clothingItemService.updateItem(
                id,
                request.name(),
                request.color(),
                request.category(),
                request.season(),
                request.style(),
                request.condition(),
                request.status(),
                request.wearCount(),
                request.lastWornAt(),
                request.favorite()
        );

        return ClothingItemResponse.from(item);
    }

    @PatchMapping("/{id}/favorite")
    public ClothingItemResponse updateFavorite(
            @PathVariable Long id,
            @Valid @RequestBody FavoriteRequest request
    ) {
        return ClothingItemResponse.from(clothingItemService.updateFavorite(id, request.favorite()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        clothingItemService.deleteItem(id);
    }

    @GetMapping("/options")
    public ClothingItemOptionsResponse getOptions() {
        return ClothingItemOptionsResponse.current();
    }
}
