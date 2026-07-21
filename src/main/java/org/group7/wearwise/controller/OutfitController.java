package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.FavoriteRequest;
import org.group7.wearwise.dto.request.OutfitRequest;
import org.group7.wearwise.dto.response.OutfitOptionsResponse;
import org.group7.wearwise.dto.response.OutfitResponse;
import org.group7.wearwise.dto.response.OutfitSuggestionResponse;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.service.OutfitService;
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

import java.util.List;

@RestController
@RequestMapping("/api/outfits")
public class OutfitController {

    private final OutfitService outfitService;

    public OutfitController(OutfitService outfitService) {
        this.outfitService = outfitService;
    }

    @GetMapping
    public List<OutfitResponse> findOutfits(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Season season,
            @RequestParam(required = false) Style style,
            @RequestParam(required = false) Boolean favorite
    ) {
        return outfitService.findOutfits(authentication.getName(), keyword, season, style, favorite)
                .stream()
                .map(OutfitResponse::from)
                .toList();
    }

    @GetMapping("/suggestions")
    public List<OutfitSuggestionResponse> suggestOutfits(
            Authentication authentication,
            @RequestParam double temperature,
            @RequestParam(defaultValue = "false") boolean raining,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return outfitService.suggestOutfits(authentication.getName(), temperature, raining, limit)
                .stream()
                .map(OutfitSuggestionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public OutfitResponse getOutfit(Authentication authentication, @PathVariable Long id) {
        return OutfitResponse.from(outfitService.getOutfitById(authentication.getName(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OutfitResponse createOutfit(Authentication authentication, @Valid @RequestBody OutfitRequest request) {
        Outfit outfit = outfitService.createOutfit(
                authentication.getName(),
                request.name(),
                request.description(),
                request.season(),
                request.style(),
                request.favorite(),
                request.clothingItemIds()
        );

        return OutfitResponse.from(outfit);
    }

    @PutMapping("/{id}")
    public OutfitResponse updateOutfit(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody OutfitRequest request
    ) {
        Outfit outfit = outfitService.updateOutfit(
                authentication.getName(),
                id,
                request.name(),
                request.description(),
                request.season(),
                request.style(),
                request.favorite(),
                request.clothingItemIds()
        );

        return OutfitResponse.from(outfit);
    }

    @PatchMapping("/{id}/favorite")
    public OutfitResponse updateFavorite(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody FavoriteRequest request
    ) {
        return OutfitResponse.from(outfitService.updateFavorite(authentication.getName(), id, request.favorite()));
    }

    @PatchMapping("/{id}/wear")
    public OutfitResponse markAsWorn(Authentication authentication, @PathVariable Long id) {
        return OutfitResponse.from(outfitService.markAsWorn(authentication.getName(), id));
    }

    @PatchMapping("/{outfitId}/items/{clothingItemId}")
    public OutfitResponse addClothingItem(
            Authentication authentication,
            @PathVariable Long outfitId,
            @PathVariable Long clothingItemId
    ) {
        return OutfitResponse.from(outfitService.addClothingItem(authentication.getName(), outfitId, clothingItemId));
    }

    @DeleteMapping("/{outfitId}/items/{clothingItemId}")
    public OutfitResponse removeClothingItem(
            Authentication authentication,
            @PathVariable Long outfitId,
            @PathVariable Long clothingItemId
    ) {
        return OutfitResponse.from(outfitService.removeClothingItem(authentication.getName(), outfitId, clothingItemId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOutfit(Authentication authentication, @PathVariable Long id) {
        outfitService.deleteOutfit(authentication.getName(), id);
    }

    @GetMapping("/options")
    public OutfitOptionsResponse getOptions() {
        return OutfitOptionsResponse.current();
    }
}
