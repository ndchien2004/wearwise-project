package org.group7.wearwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.group7.wearwise.dto.response.ClothingItemSuggestionResponse;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.AiUnavailableException;
import org.group7.wearwise.exception.TryOnImageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClothingItemVisionServiceTest {

    @Mock
    private GeminiClient geminiClient;

    private ClothingItemVisionService service;

    @BeforeEach
    void setUp() {
        lenient().when(geminiClient.isConfigured()).thenReturn(true);
        service = new ClothingItemVisionService(geminiClient);
    }

    @Test
    void mapsGeminiAnswerOntoTheFormFields() throws IOException {
        answerWith("""
                {"name":"Áo thun trắng","color":"Trắng","colorTone":"NEUTRAL",
                 "category":"SHIRT","season":"SUMMER","style":"CASUAL"}
                """);

        ClothingItemSuggestionResponse suggestion = service.analyze(image(800, 600));

        assertThat(suggestion.name()).isEqualTo("Áo thun trắng");
        assertThat(suggestion.color()).isEqualTo("Trắng");
        assertThat(suggestion.colorTone()).isEqualTo(ColorTone.NEUTRAL);
        assertThat(suggestion.category()).isEqualTo(ClothingCategory.SHIRT);
        assertThat(suggestion.season()).isEqualTo(Season.SUMMER);
        assertThat(suggestion.style()).isEqualTo(Style.CASUAL);
    }

    /**
     * Ảnh gốc từ điện thoại có thể 3000-8000px. Không tin frontend đã thu nhỏ giúp — server
     * phải tự chặn để request gửi Gemini không phình lên vài MB.
     */
    @Test
    void shrinksOversizedImagesBeforeSendingThem() throws IOException {
        answerWith(validJson());

        service.analyze(image(3000, 2000));

        ArgumentCaptor<GeminiClient.InlineImage> captor =
                ArgumentCaptor.forClass(GeminiClient.InlineImage.class);
        verify(geminiClient).generateJson(anyString(), captor.capture(), any(), anyInt());

        BufferedImage sent = ImageIO.read(new ByteArrayInputStream(captor.getValue().data()));
        assertThat(Math.max(sent.getWidth(), sent.getHeight()))
                .isLessThanOrEqualTo(ClothingItemVisionService.MAX_IMAGE_EDGE);
        assertThat(captor.getValue().mimeType()).isEqualTo("image/jpeg");
        // Giữ đúng tỉ lệ 3:2 của ảnh gốc, không bóp méo.
        assertThat((double) sent.getWidth() / sent.getHeight()).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    void leavesSmallImagesAlone() throws IOException {
        answerWith(validJson());

        service.analyze(image(200, 150));

        ArgumentCaptor<GeminiClient.InlineImage> captor =
                ArgumentCaptor.forClass(GeminiClient.InlineImage.class);
        verify(geminiClient).generateJson(anyString(), captor.capture(), any(), anyInt());

        BufferedImage sent = ImageIO.read(new ByteArrayInputStream(captor.getValue().data()));
        assertThat(sent.getWidth()).isEqualTo(200);
        assertThat(sent.getHeight()).isEqualTo(150);
    }

    /** Giá trị lạ chỉ làm trống một ô, không được làm hỏng cả form. */
    @Test
    void unknownEnumValueBecomesNullInsteadOfFailing() throws IOException {
        answerWith("""
                {"name":"Váy hoa","color":"Hồng","colorTone":"NEON",
                 "category":"DRESS","season":"SUMMER","style":"CASUAL"}
                """);

        ClothingItemSuggestionResponse suggestion = service.analyze(image(400, 400));

        assertThat(suggestion.name()).isEqualTo("Váy hoa");
        assertThat(suggestion.colorTone()).isNull();
        assertThat(suggestion.category()).isNull();
        assertThat(suggestion.season()).isEqualTo(Season.SUMMER);
    }

    @Test
    void unreadableAnswerIsReportedAsAiFailure() throws IOException {
        answerWith("xin lỗi, tôi không nhìn thấy gì");

        assertThatThrownBy(() -> service.analyze(image(400, 400)))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void rejectsAFileThatIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.analyze(file))
                .isInstanceOf(TryOnImageException.class)
                .hasMessageContaining("JPG hoặc PNG");

        verify(geminiClient, never()).generateJson(anyString(), any(), any(), anyInt());
    }

    @Test
    void failsClearlyWhenTheApiKeyIsMissing() throws IOException {
        when(geminiClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.analyze(image(400, 400)))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("chưa được cấu hình");
    }

    /** Schema sinh từ enum nên thêm giá trị mới vào enum là AI biết ngay, không cần sửa prompt. */
    @Test
    void schemaListsEveryValueOfEveryEnum() throws IOException {
        answerWith(validJson());
        service.analyze(image(400, 400));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(geminiClient).generateJson(anyString(), any(), captor.capture(), anyInt());
        JsonNode properties = captor.getValue().path("properties");

        assertEnumProperty(properties, "category", ClothingCategory.values());
        assertEnumProperty(properties, "season", Season.values());
        assertEnumProperty(properties, "style", Style.values());
        assertEnumProperty(properties, "colorTone", ColorTone.values());
    }

    private void assertEnumProperty(JsonNode properties, String field, Enum<?>[] values) {
        List<String> allowed = new ArrayList<>();
        properties.path(field).path("enum").forEach(node -> allowed.add(node.asText()));

        assertThat(allowed)
                .as("giá trị hợp lệ của %s", field)
                .containsExactlyInAnyOrder(Arrays.stream(values).map(Enum::name).toArray(String[]::new));
    }

    private void answerWith(String json) {
        when(geminiClient.generateJson(anyString(), any(), any(), anyInt())).thenReturn(json);
    }

    private String validJson() {
        return """
                {"name":"Áo","color":"Trắng","colorTone":"NEUTRAL",
                 "category":"SHIRT","season":"SUMMER","style":"CASUAL"}
                """;
    }

    private MockMultipartFile image(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return new MockMultipartFile("file", "item.png", "image/png", buffer.toByteArray());
    }
}
