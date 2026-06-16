package org.group7.wearwise.controller;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.GlobalExceptionHandler;
import org.group7.wearwise.service.ClothingItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

class ClothingItemControllerTest {

    private static final String OWNER = "demo";

    private ClothingItemService clothingItemService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clothingItemService = mock(ClothingItemService.class);
        Validator validator = validator();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClothingItemController(clothingItemService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createItemReturnsCreatedItem() throws Exception {
        ClothingItem createdItem = item(1L, "White Shirt", "White", ClothingCategory.SHIRT, Season.ALL_SEASON, Style.FORMAL, true);
        when(clothingItemService.createItem(
                OWNER,
                "White Shirt",
                "White",
                ClothingCategory.SHIRT,
                Season.ALL_SEASON,
                Style.FORMAL,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                2,
                null,
                true
        ))
                .thenReturn(createdItem);

        mockMvc.perform(post("/api/clothing-items")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "White Shirt",
                                  "color": "White",
                                  "category": "SHIRT",
                                  "season": "ALL_SEASON",
                                  "style": "FORMAL",
                                  "condition": "GOOD",
                                  "status": "AVAILABLE",
                                  "wearCount": 2,
                                  "favorite": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("White Shirt"))
                .andExpect(jsonPath("$.condition").value("GOOD"))
                .andExpect(jsonPath("$.wearCount").value(0))
                .andExpect(jsonPath("$.favorite").value(true));
    }

    @Test
    void findItemsPassesCombinedFilters() throws Exception {
        ClothingItem sneaker = item(2L, "Running Sneakers", "Gray", ClothingCategory.SHOES, Season.SUMMER, Style.SPORT, true);
        when(clothingItemService.findItems(
                OWNER,
                "run",
                ClothingCategory.SHOES,
                Season.SUMMER,
                Style.SPORT,
                ClothingCondition.GOOD,
                ClothingStatus.AVAILABLE,
                true
        ))
                .thenReturn(List.of(sneaker));

        mockMvc.perform(get("/api/clothing-items")
                        .principal(authentication())
                        .param("keyword", "run")
                        .param("category", "SHOES")
                        .param("season", "SUMMER")
                        .param("style", "SPORT")
                        .param("condition", "GOOD")
                        .param("status", "AVAILABLE")
                        .param("favorite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].category").value("SHOES"));
    }

    @Test
    void recentlyWornItemsReturnsLimitedItems() throws Exception {
        ClothingItem shirt = itemWithWear(
                6L,
                "White Shirt",
                ClothingCategory.SHIRT,
                4,
                LocalDateTime.of(2026, 6, 15, 8, 30)
        );
        when(clothingItemService.getRecentlyWornItems(OWNER, 3)).thenReturn(List.of(shirt));

        mockMvc.perform(get("/api/clothing-items/recently-worn")
                        .principal(authentication())
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(6))
                .andExpect(jsonPath("$[0].wearCount").value(4));
    }

    @Test
    void mostWornItemsReturnsLimitedItems() throws Exception {
        ClothingItem shoes = itemWithWear(
                7L,
                "Running Shoes",
                ClothingCategory.SHOES,
                12,
                LocalDateTime.of(2026, 6, 14, 18, 0)
        );
        when(clothingItemService.getMostWornItems(OWNER, 2)).thenReturn(List.of(shoes));

        mockMvc.perform(get("/api/clothing-items/most-worn")
                        .principal(authentication())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].wearCount").value(12));
    }

    @Test
    void leastWornItemsReturnsLimitedItems() throws Exception {
        ClothingItem jacket = itemWithWear(
                8L,
                "Rain Jacket",
                ClothingCategory.JACKET,
                0,
                null
        );
        when(clothingItemService.getLeastWornItems(OWNER, 5)).thenReturn(List.of(jacket));

        mockMvc.perform(get("/api/clothing-items/least-worn")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].wearCount").value(0));
    }

    @Test
    void invalidListLimitReturnsBadRequest() throws Exception {
        when(clothingItemService.getLeastWornItems(OWNER, 0))
                .thenThrow(new IllegalArgumentException("Limit must be at least 1."));

        mockMvc.perform(get("/api/clothing-items/least-worn")
                        .principal(authentication())
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Limit must be at least 1."));
    }

    @Test
    void getMissingItemReturnsNotFound() throws Exception {
        when(clothingItemService.getItemById(OWNER, 99L)).thenThrow(new ClothingItemNotFoundException(99L));

        mockMvc.perform(get("/api/clothing-items/99")
                        .principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("99")));
    }

    @Test
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/clothing-items")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "color": "Black"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").value("Name is required."))
                .andExpect(jsonPath("$.errors.category").value("Category is required."))
                .andExpect(jsonPath("$.errors.season").value("Season is required."))
                .andExpect(jsonPath("$.errors.style").value("Style is required."))
                .andExpect(jsonPath("$.errors.condition").value("Condition is required."))
                .andExpect(jsonPath("$.errors.status").value("Status is required."));
    }

    @Test
    void updateItemReturnsUpdatedItem() throws Exception {
        ClothingItem updatedItem = item(3L, "Black Jeans", "Black", ClothingCategory.PANTS, Season.ALL_SEASON, Style.CASUAL, false);
        when(clothingItemService.updateItem(
                OWNER,
                3L,
                "Black Jeans",
                "Black",
                ClothingCategory.PANTS,
                Season.ALL_SEASON,
                Style.CASUAL,
                ClothingCondition.GOOD,
                ClothingStatus.LAUNDRY,
                6,
                null,
                false
        ))
                .thenReturn(updatedItem);

        mockMvc.perform(put("/api/clothing-items/3")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Black Jeans",
                                  "color": "Black",
                                  "category": "PANTS",
                                  "season": "ALL_SEASON",
                                  "style": "CASUAL",
                                  "condition": "GOOD",
                                  "status": "LAUNDRY",
                                  "wearCount": 6,
                                  "favorite": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Black Jeans"));
    }

    @Test
    void updateFavoriteReturnsUpdatedItem() throws Exception {
        ClothingItem updatedItem = item(4L, "Silver Watch", "Silver", ClothingCategory.ACCESSORY, Season.ALL_SEASON, Style.FORMAL, true);
        when(clothingItemService.updateFavorite(OWNER, 4L, true)).thenReturn(updatedItem);

        mockMvc.perform(patch("/api/clothing-items/4/favorite")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "favorite": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));
    }

    @Test
    void markAsWornReturnsUpdatedItem() throws Exception {
        ClothingItem updatedItem = itemWithWear(
                9L,
                "White Shirt",
                ClothingCategory.SHIRT,
                3,
                LocalDateTime.of(2026, 6, 16, 9, 0)
        );
        when(clothingItemService.markAsWorn(OWNER, 9L)).thenReturn(updatedItem);

        mockMvc.perform(patch("/api/clothing-items/9/wear")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.wearCount").value(3))
                .andExpect(jsonPath("$.lastWornAt").value("2026-06-16T09:00:00"));
    }

    @Test
    void deleteItemReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/clothing-items/5")
                        .principal(authentication()))
                .andExpect(status().isNoContent());

        verify(clothingItemService).deleteItem(OWNER, 5L);
    }

    @Test
    void optionsReturnsEnums() throws Exception {
        mockMvc.perform(get("/api/clothing-items/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0]").value("SHIRT"))
                .andExpect(jsonPath("$.seasons[0]").value("SUMMER"))
                .andExpect(jsonPath("$.styles[0]").value("CASUAL"))
                .andExpect(jsonPath("$.conditions[0]").value("GOOD"))
                .andExpect(jsonPath("$.statuses[0]").value("AVAILABLE"));
    }

    @Test
    void invalidWearDataReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/clothing-items")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "White Shirt",
                                  "category": "SHIRT",
                                  "season": "ALL_SEASON",
                                  "style": "FORMAL",
                                  "condition": "GOOD",
                                  "status": "AVAILABLE",
                                  "wearCount": -1,
                                  "lastWornAt": "2999-01-01T10:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.wearCount").value("Wear count must be zero or greater."))
                .andExpect(jsonPath("$.errors.lastWornAt").value("Last worn at cannot be in the future."));
    }

    private static ClothingItem item(
            Long id,
            String name,
            String color,
            ClothingCategory category,
            Season season,
            Style style,
            Boolean favorite
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 9, 10, 0);

        return ClothingItem.builder()
                .id(id)
                .name(name)
                .color(color)
                .category(category)
                .season(season)
                .style(style)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(0)
                .favorite(favorite)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static ClothingItem itemWithWear(
            Long id,
            String name,
            ClothingCategory category,
            Integer wearCount,
            LocalDateTime lastWornAt
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 9, 10, 0);

        return ClothingItem.builder()
                .id(id)
                .name(name)
                .color("Black")
                .category(category)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(wearCount)
                .lastWornAt(lastWornAt)
                .favorite(false)
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
