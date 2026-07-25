package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.group7.wearwise.dto.response.AiOutfitSuggestionResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

    private static final String OWNER = "demo";

    @Mock
    private ClothingItemService clothingItemService;

    @Mock
    private OutfitService outfitService;

    @Mock
    private GeminiClient geminiClient;

    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new AiSuggestionService(clothingItemService, outfitService, geminiClient);
        lenient().when(geminiClient.isConfigured()).thenReturn(true);
    }

    /**
     * Ràng buộc cốt lõi: schema chỉ có một ô cho mỗi danh mục, nên không có chỗ để nhét
     * cái quần thứ hai — đây mới là thứ chặn lỗi, không phải câu chữ trong prompt.
     */
    @Test
    void schemaGivesExactlyOneSlotPerCategoryPresentInTheWardrobe() {
        wardrobe(shirt(1L), pants(2L), shoes(3L));
        answerWith("""
                {"suggestions":[{"name":"Bộ A","reason":"...","shirtId":1,"pantsId":2}]}
                """);

        service.suggest(OWNER, 28, false, "nắng", null);

        JsonNode item = capturedSchema().path("properties").path("suggestions").path("items");
        JsonNode slots = item.path("properties");

        assertThat(slots.has("shirtId")).isTrue();
        assertThat(slots.has("pantsId")).isTrue();
        assertThat(slots.has("shoesId")).isTrue();
        // Tủ không có áo khoác / phụ kiện thì không dựng ô, model khỏi bịa id.
        assertThat(slots.has("jacketId")).isFalse();
        assertThat(slots.has("accessoryId")).isFalse();
        assertThat(slots.has("itemIds")).as("không còn mảng id tự do").isFalse();
    }

    /**
     * Đo thực tế với Gemini: để ô áo/quần nullable thì model hay trả bộ chỉ có áo + giày.
     * Ép hai ô này bắt buộc là hết, nên đây là ràng buộc phải giữ.
     */
    @Test
    void forcesShirtAndPantsSlotsToBeFilled() {
        wardrobe(shirt(1L), pants(2L), shoes(3L));
        answerWith("""
                {"suggestions":[{"name":"Bộ A","reason":"...","shirtId":1,"pantsId":2}]}
                """);

        service.suggest(OWNER, 28, false, "nắng", null);

        JsonNode item = capturedSchema().path("properties").path("suggestions").path("items");
        List<String> required = new ArrayList<>();
        item.path("required").forEach(node -> required.add(node.asText()));

        assertThat(required).contains("shirtId", "pantsId");
        assertThat(item.path("properties").path("shirtId").has("nullable")).isFalse();
        // Giày là tùy chọn nên vẫn cho phép null.
        assertThat(item.path("properties").path("shoesId").path("nullable").asBoolean()).isTrue();
    }

    /** Tủ chưa có quần thì đừng ép ô quần, nếu không model buộc phải bịa ra một id. */
    @Test
    void doesNotForceACategoryTheWardrobeDoesNotHave() {
        wardrobe(shirt(1L), shoes(3L));
        answerWith("""
                {"suggestions":[{"name":"Bộ A","reason":"...","shirtId":1,"shoesId":3}]}
                """);

        service.suggest(OWNER, 28, false, "nắng", null);

        JsonNode item = capturedSchema().path("properties").path("suggestions").path("items");
        List<String> required = new ArrayList<>();
        item.path("required").forEach(node -> required.add(node.asText()));

        assertThat(item.path("properties").has("pantsId")).isFalse();
        assertThat(required).doesNotContain("pantsId");
    }

    private JsonNode capturedSchema() {
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(geminiClient).generateJson(anyString(), captor.capture(), anyInt());
        return captor.getValue();
    }

    /** Model nhét id của áo vào ô quần thì món đó bị loại, không được "sửa hộ". */
    @Test
    void dropsAnItemPlacedInTheWrongCategorySlot() {
        wardrobe(shirt(1L), pants(2L), shoes(3L));
        answerWith("""
                {"suggestions":[{"name":"Bộ lệch","reason":"...","shirtId":1,"pantsId":3,"shoesId":3}]}
                """);

        List<AiOutfitSuggestionResponse> result = service.suggest(OWNER, 28, false, "nắng", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).items()).extracting(item -> item.id()).containsExactly(1L, 3L);
        assertThat(result.get(0).items()).extracting(item -> item.category())
                .containsExactly(ClothingCategory.SHIRT, ClothingCategory.SHOES);
    }

    @Test
    void dropsIdsThatAreNotInTheWardrobe() {
        wardrobe(shirt(1L), pants(2L), shoes(3L));
        answerWith("""
                {"suggestions":[{"name":"Bộ bịa","reason":"...","shirtId":999,"pantsId":2,"shoesId":3}]}
                """);

        List<AiOutfitSuggestionResponse> result = service.suggest(OWNER, 28, false, "nắng", null);

        assertThat(result.get(0).items()).extracting(item -> item.id()).containsExactly(2L, 3L);
    }

    /** Còn dưới 2 món hợp lệ thì bỏ cả gợi ý — một cái quần trơ trọi không phải bộ đồ. */
    @Test
    void skipsSuggestionsLeftWithFewerThanTwoValidItems() {
        wardrobe(shirt(1L), pants(2L));
        answerWith("""
                {"suggestions":[{"name":"Chỉ một món","reason":"...","pantsId":2}]}
                """);

        assertThatThrownBy(() -> service.suggest(OWNER, 28, false, "nắng", null))
                .isInstanceOf(AiUnavailableException.class);
    }

    /** Đồ đang giặt / hư hỏng không được đưa vào prompt: gợi ý ra cũng không mặc được. */
    @Test
    void keepsUnwearableItemsOutOfThePrompt() {
        ClothingItem laundry = shirt(7L);
        laundry.setName("Áo đang giặt");
        laundry.setStatus(ClothingStatus.LAUNDRY);

        ClothingItem damaged = shoes(8L);
        damaged.setName("Giày rách");
        damaged.setCondition(ClothingCondition.DAMAGED);

        wardrobe(shirt(1L), pants(2L), laundry, damaged);
        answerWith("""
                {"suggestions":[{"name":"Bộ A","reason":"...","shirtId":1,"pantsId":2}]}
                """);

        service.suggest(OWNER, 28, false, "nắng", null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(promptCaptor.capture(), any(), anyInt());
        assertThat(promptCaptor.getValue())
                .doesNotContain("Áo đang giặt")
                .doesNotContain("Giày rách");
    }

    @Test
    void refusesWhenNotEnoughWearableItems() {
        ClothingItem laundry = pants(2L);
        laundry.setStatus(ClothingStatus.LAUNDRY);
        wardrobe(shirt(1L), laundry);

        assertThatThrownBy(() -> service.suggest(OWNER, 28, false, "nắng", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sẵn sàng");
    }

    private void wardrobe(ClothingItem... items) {
        when(clothingItemService.getAllItems(OWNER)).thenReturn(new ArrayList<>(List.of(items)));
    }

    private void answerWith(String json) {
        when(geminiClient.generateJson(anyString(), any(), anyInt())).thenReturn(json);
    }

    private static ClothingItem shirt(Long id) {
        return item(id, "Áo " + id, ClothingCategory.SHIRT);
    }

    private static ClothingItem pants(Long id) {
        return item(id, "Quần " + id, ClothingCategory.PANTS);
    }

    private static ClothingItem shoes(Long id) {
        return item(id, "Giày " + id, ClothingCategory.SHOES);
    }

    private static ClothingItem item(Long id, String name, ClothingCategory category) {
        return ClothingItem.builder()
                .id(id)
                .name(name)
                .category(category)
                .season(Season.ALL_SEASON)
                .style(Style.CASUAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(0)
                .favorite(false)
                .build();
    }
}
