package org.group7.wearwise.controller;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.GlobalExceptionHandler;
import org.group7.wearwise.exception.OutfitNotFoundException;
import org.group7.wearwise.service.OutfitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OutfitControllerTest {

    private static final String OWNER = "demo";

    private OutfitService outfitService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        outfitService = mock(OutfitService.class);
        Validator validator = validator();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new OutfitController(outfitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createOutfitReturnsCreatedOutfit() throws Exception {
        Outfit outfit = outfit(1L, "Office Set", Season.ALL_SEASON, Style.FORMAL, true);
        when(outfitService.createOutfit(
                OWNER,
                "Office Set",
                "Work clothes",
                Season.ALL_SEASON,
                Style.FORMAL,
                true,
                List.of(1L)
        )).thenReturn(outfit);

        mockMvc.perform(post("/api/outfits")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Office Set",
                                  "description": "Work clothes",
                                  "season": "ALL_SEASON",
                                  "style": "FORMAL",
                                  "favorite": true,
                                  "clothingItemIds": [1]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Office Set"))
                .andExpect(jsonPath("$.clothingItems[0].id").value(1));
    }

    @Test
    void findOutfitsPassesFilters() throws Exception {
        Outfit outfit = outfit(2L, "Sport Set", Season.SUMMER, Style.SPORT, false);
        when(outfitService.findOutfits(OWNER, "sport", Season.SUMMER, Style.SPORT, false))
                .thenReturn(List.of(outfit));

        mockMvc.perform(get("/api/outfits")
                        .principal(authentication())
                        .param("keyword", "sport")
                        .param("season", "SUMMER")
                        .param("style", "SPORT")
                        .param("favorite", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    void getMissingOutfitReturnsNotFound() throws Exception {
        when(outfitService.getOutfitById(OWNER, 99L)).thenThrow(new OutfitNotFoundException(99L));

        mockMvc.perform(get("/api/outfits/99")
                        .principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("99")));
    }

    @Test
    void emptyClothingItemListReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/outfits")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Office Set",
                                  "season": "ALL_SEASON",
                                  "style": "FORMAL",
                                  "clothingItemIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.clothingItemIds")
                        .value("At least one clothing item is required."));
    }

    @Test
    void duplicateClothingItemIdsReturnBadRequest() throws Exception {
        when(outfitService.createOutfit(
                OWNER,
                "Office Set",
                null,
                Season.ALL_SEASON,
                Style.FORMAL,
                false,
                List.of(1L, 1L)
        )).thenThrow(new IllegalArgumentException("Clothing item IDs must not contain duplicates."));

        mockMvc.perform(post("/api/outfits")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Office Set",
                                  "season": "ALL_SEASON",
                                  "style": "FORMAL",
                                  "favorite": false,
                                  "clothingItemIds": [1, 1]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Clothing item IDs must not contain duplicates."));
    }

    @Test
    void updateOutfitReturnsUpdatedOutfit() throws Exception {
        Outfit outfit = outfit(3L, "Weekend Set", Season.WINTER, Style.CASUAL, false);
        when(outfitService.updateOutfit(
                OWNER,
                3L,
                "Weekend Set",
                null,
                Season.WINTER,
                Style.CASUAL,
                false,
                List.of(1L)
        )).thenReturn(outfit);

        mockMvc.perform(put("/api/outfits/3")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Weekend Set",
                                  "season": "WINTER",
                                  "style": "CASUAL",
                                  "favorite": false,
                                  "clothingItemIds": [1]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Weekend Set"));
    }

    @Test
    void updateFavoriteReturnsUpdatedOutfit() throws Exception {
        Outfit outfit = outfit(4L, "Sport Set", Season.SUMMER, Style.SPORT, true);
        when(outfitService.updateFavorite(OWNER, 4L, true)).thenReturn(outfit);

        mockMvc.perform(patch("/api/outfits/4/favorite")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"favorite": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));
    }

    @Test
    void markAsWornReturnsUpdatedOutfitAndItems() throws Exception {
        LocalDateTime wornAt = LocalDateTime.of(2026, 6, 16, 14, 0);
        ClothingItem item = ClothingItem.builder()
                .id(1L)
                .name("White Shirt")
                .color("White")
                .category(ClothingCategory.SHIRT)
                .season(Season.ALL_SEASON)
                .style(Style.FORMAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(3)
                .lastWornAt(wornAt)
                .favorite(true)
                .build();
        Outfit outfit = Outfit.builder()
                .id(6L)
                .name("Office Set")
                .season(Season.ALL_SEASON)
                .style(Style.FORMAL)
                .favorite(false)
                .wearCount(2)
                .lastWornAt(wornAt)
                .clothingItems(new LinkedHashSet<>(List.of(item)))
                .build();
        when(outfitService.markAsWorn(OWNER, 6L)).thenReturn(outfit);

        mockMvc.perform(patch("/api/outfits/6/wear")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.wearCount").value(2))
                .andExpect(jsonPath("$.lastWornAt").value("2026-06-16T14:00:00"))
                .andExpect(jsonPath("$.clothingItems[0].wearCount").value(3))
                .andExpect(jsonPath("$.clothingItems[0].lastWornAt").value("2026-06-16T14:00:00"));
    }

    @Test
    void addClothingItemReturnsUpdatedOutfit() throws Exception {
        Outfit outfit = outfitWithItems(
                7L,
                "Office Set",
                item(5L, "White Shirt"),
                item(2L, "Black Pants")
        );
        when(outfitService.addClothingItem(OWNER, 7L, 2L)).thenReturn(outfit);

        mockMvc.perform(patch("/api/outfits/7/items/2")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.clothingItems[0].id").value(5))
                .andExpect(jsonPath("$.clothingItems[1].id").value(2));
    }

    @Test
    void removeClothingItemReturnsUpdatedOutfit() throws Exception {
        Outfit outfit = outfitWithItems(
                8L,
                "Office Set",
                item(5L, "White Shirt")
        );
        when(outfitService.removeClothingItem(OWNER, 8L, 2L)).thenReturn(outfit);

        mockMvc.perform(delete("/api/outfits/8/items/2")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.clothingItems.length()").value(1))
                .andExpect(jsonPath("$.clothingItems[0].id").value(5));
    }

    @Test
    void deleteOutfitReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/outfits/5")
                        .principal(authentication()))
                .andExpect(status().isNoContent());

        verify(outfitService).deleteOutfit(OWNER, 5L);
    }

    @Test
    void optionsReturnsEnums() throws Exception {
        mockMvc.perform(get("/api/outfits/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasons[0]").value("SUMMER"))
                .andExpect(jsonPath("$.styles[0]").value("CASUAL"));
    }

    private static Outfit outfit(Long id, String name, Season season, Style style, Boolean favorite) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        ClothingItem item = item(1L, "White Shirt");

        return Outfit.builder()
                .id(id)
                .name(name)
                .season(season)
                .style(style)
                .favorite(favorite)
                .clothingItems(new LinkedHashSet<>(List.of(item)))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static Outfit outfitWithItems(Long id, String name, ClothingItem... items) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);

        return Outfit.builder()
                .id(id)
                .name(name)
                .season(Season.ALL_SEASON)
                .style(Style.FORMAL)
                .favorite(false)
                .clothingItems(new LinkedHashSet<>(List.of(items)))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static ClothingItem item(Long id, String name) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);

        return ClothingItem.builder()
                .id(id)
                .name(name)
                .color("White")
                .category(ClothingCategory.SHIRT)
                .season(Season.ALL_SEASON)
                .style(Style.FORMAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(2)
                .favorite(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static Validator validator() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.afterPropertiesSet();
        return validatorFactoryBean;
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(OWNER, null);
    }
}
