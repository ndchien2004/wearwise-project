package org.group7.wearwise.controller;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.GlobalExceptionHandler;
import org.group7.wearwise.service.ClothingItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
        when(clothingItemService.createItem("White Shirt", "White", ClothingCategory.SHIRT, Season.ALL_SEASON, Style.FORMAL, true))
                .thenReturn(createdItem);

        mockMvc.perform(post("/api/clothing-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "White Shirt",
                                  "color": "White",
                                  "category": "SHIRT",
                                  "season": "ALL_SEASON",
                                  "style": "FORMAL",
                                  "favorite": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("White Shirt"))
                .andExpect(jsonPath("$.favorite").value(true));
    }

    @Test
    void findItemsPassesCombinedFilters() throws Exception {
        ClothingItem sneaker = item(2L, "Running Sneakers", "Gray", ClothingCategory.SHOES, Season.SUMMER, Style.SPORT, true);
        when(clothingItemService.findItems("run", ClothingCategory.SHOES, Season.SUMMER, Style.SPORT, true))
                .thenReturn(List.of(sneaker));

        mockMvc.perform(get("/api/clothing-items")
                        .param("keyword", "run")
                        .param("category", "SHOES")
                        .param("season", "SUMMER")
                        .param("style", "SPORT")
                        .param("favorite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].category").value("SHOES"));
    }

    @Test
    void getMissingItemReturnsNotFound() throws Exception {
        when(clothingItemService.getItemById(99L)).thenThrow(new ClothingItemNotFoundException(99L));

        mockMvc.perform(get("/api/clothing-items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("99")));
    }

    @Test
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/clothing-items")
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
                .andExpect(jsonPath("$.errors.style").value("Style is required."));
    }

    @Test
    void updateItemReturnsUpdatedItem() throws Exception {
        ClothingItem updatedItem = item(3L, "Black Jeans", "Black", ClothingCategory.PANTS, Season.ALL_SEASON, Style.CASUAL, false);
        when(clothingItemService.updateItem(3L, "Black Jeans", "Black", ClothingCategory.PANTS, Season.ALL_SEASON, Style.CASUAL, false))
                .thenReturn(updatedItem);

        mockMvc.perform(put("/api/clothing-items/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Black Jeans",
                                  "color": "Black",
                                  "category": "PANTS",
                                  "season": "ALL_SEASON",
                                  "style": "CASUAL",
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
        when(clothingItemService.updateFavorite(4L, true)).thenReturn(updatedItem);

        mockMvc.perform(patch("/api/clothing-items/4/favorite")
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
    void deleteItemReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/clothing-items/5"))
                .andExpect(status().isNoContent());

        verify(clothingItemService).deleteItem(5L);
    }

    @Test
    void optionsReturnsEnums() throws Exception {
        mockMvc.perform(get("/api/clothing-items/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0]").value("SHIRT"))
                .andExpect(jsonPath("$.seasons[0]").value("SUMMER"))
                .andExpect(jsonPath("$.styles[0]").value("CASUAL"));
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
                .favorite(favorite)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static Validator validator() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.afterPropertiesSet();
        return validatorFactoryBean;
    }
}
