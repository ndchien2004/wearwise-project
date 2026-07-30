package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.WardrobeImportResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trọng tâm: một dòng sai không được làm đổ cả file, và nhập lại đúng file đó không được nhân đôi
 * tủ đồ. Đó là hai thứ quyết định người dùng có dám bấm nút nhập lần thứ hai hay không.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WardrobeCsvServiceTest {

    private static final String USER = "an";
    private static final String HEADER =
            "name,category,color,colorTone,season,style,condition,status,wearCount,lastWornAt,favorite,imageUrl\n";

    @Mock private ClothingItemService clothingItemService;

    @InjectMocks private WardrobeCsvService wardrobeCsvService;

    // ------------------------------------------------------------------ xuất

    @Test
    void exportWritesOneRowPerItemBehindABom() {
        when(clothingItemService.getAllItems(USER)).thenReturn(List.of(item("Áo sơ mi trắng"), item("Quần jean")));

        String csv = wardrobeCsvService.export(USER);

        assertThat(csv).startsWith("﻿");
        assertThat(csv.lines()).hasSize(3);
        assertThat(csv).contains("Áo sơ mi trắng,SHIRT,Trắng,NEUTRAL,ALL_SEASON,FORMAL,GOOD,AVAILABLE,4,");
    }

    /** Tên có dấu phẩy phải được bọc ngoặc kép, nếu không file xuất ra tự lệch cột khi nhập lại. */
    @Test
    void exportQuotesValuesContainingCommas() {
        ClothingItem tricky = item("Áo khoác, dày");
        when(clothingItemService.getAllItems(USER)).thenReturn(List.of(tricky));

        assertThat(wardrobeCsvService.export(USER)).contains("\"Áo khoác, dày\",SHIRT");
    }

    @Test
    void templateListsEveryAllowedEnumValue() {
        String template = wardrobeCsvService.template();

        assertThat(template).contains("SHIRT / PANTS / SHOES / JACKET / ACCESSORY");
        assertThat(template).contains("SUMMER / WINTER / ALL_SEASON");
        assertThat(template).contains("# Bắt buộc: name, category, season, style");
    }

    /** File mẫu phải nhập lại được nguyên trạng, nếu không thì nó không phải là file mẫu. */
    @Test
    void theTemplateItselfImportsCleanly() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(wardrobeCsvService.template()), true);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isZero();
    }

    // ------------------------------------------------------------------ nhập

    @Test
    void importCreatesOneItemPerRow() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(HEADER
                + "Áo thun đen,SHIRT,Đen,DARK,SUMMER,CASUAL,GOOD,AVAILABLE,3,2026-07-01,true,\n"
                + "Giày trắng,SHOES,Trắng,,ALL_SEASON,CASUAL,,,,,,\n"), true);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.problems()).isEmpty();

        verify(clothingItemService).createItem(
                eq(USER), eq("Áo thun đen"), eq("Đen"), eq(ColorTone.DARK), eq(ClothingCategory.SHIRT),
                eq(Season.SUMMER), eq(Style.CASUAL), eq(ClothingCondition.GOOD), eq(ClothingStatus.AVAILABLE),
                eq(3), eq(LocalDate.of(2026, 7, 1).atStartOfDay()), eq(true), eq(""));

        // Cột để trống phải rơi về mặc định, không phải null xuống tới database.
        verify(clothingItemService).createItem(
                eq(USER), eq("Giày trắng"), eq("Trắng"), isNull(), eq(ClothingCategory.SHOES),
                eq(Season.ALL_SEASON), eq(Style.CASUAL), eq(ClothingCondition.GOOD),
                eq(ClothingStatus.AVAILABLE), eq(0), isNull(), eq(false), eq(""));
    }

    /**
     * Luật quan trọng nhất: dòng 3 sai thì dòng 2 và 4 vẫn phải vào tủ. Hủy cả file nghĩa là người
     * dùng phải dò lại từ đầu một file 300 dòng vì một ô gõ sai.
     */
    @Test
    void oneBadRowDoesNotStopTheRest() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(HEADER
                + "Áo hợp lệ,SHIRT,,,SUMMER,CASUAL,,,,,,\n"
                + "Áo sai mùa,SHIRT,,,MUA_HE,CASUAL,,,,,,\n"
                + "Giày hợp lệ,SHOES,,,SUMMER,CASUAL,,,,,,\n"), true);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            // Dòng 3 trong file: 1 là header.
            assertThat(problem.line()).isEqualTo(3);
            assertThat(problem.name()).isEqualTo("Áo sai mùa");
            assertThat(problem.kind()).isEqualTo(WardrobeImportResponse.Kind.INVALID);
            assertThat(problem.message()).contains("season").contains("SUMMER / WINTER / ALL_SEASON");
        });
    }

    @Test
    void rowsMissingARequiredValueAreReportedNotThrown() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(HEADER
                + ",SHIRT,,,SUMMER,CASUAL,,,,,,\n"
                + "Không có danh mục,,,,SUMMER,CASUAL,,,,,,\n"), true);

        assertThat(result.created()).isZero();
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.problems().get(0).message()).contains("name");
        assertThat(result.problems().get(1).message()).contains("category");
    }

    /** Nhập lại đúng file vừa nhập không được nhân đôi tủ đồ. */
    @Test
    void namesAlreadyInTheWardrobeAreSkipped() {
        when(clothingItemService.getAllItems(USER)).thenReturn(List.of(item("Áo thun đen")));

        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(HEADER
                + "áo thun đen,SHIRT,,,SUMMER,CASUAL,,,,,,\n"
                + "Giày trắng,SHOES,,,SUMMER,CASUAL,,,,,,\n"), true);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.problems()).singleElement()
                .satisfies(problem -> assertThat(problem.kind())
                        .isEqualTo(WardrobeImportResponse.Kind.SKIPPED_DUPLICATE));
    }

    /** Hai dòng cùng tên trong cùng một file cũng phải tính là trùng, không chỉ trùng với tủ cũ. */
    @Test
    void aNameRepeatedInsideTheFileIsSkippedToo() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(HEADER
                + "Áo thun đen,SHIRT,,,SUMMER,CASUAL,,,,,,\n"
                + "Áo thun đen,SHIRT,,,WINTER,FORMAL,,,,,,\n"), true);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void duplicateNamesAreAllowedWhenTheUserTurnsTheCheckOff() {
        when(clothingItemService.getAllItems(USER)).thenReturn(List.of(item("Áo thun đen")));

        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(HEADER
                + "Áo thun đen,SHIRT,,,SUMMER,CASUAL,,,,,,\n"), false);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
    }

    /** Thứ tự cột do người dùng quyết định — họ kéo cột trong Excel là chuyện bình thường. */
    @Test
    void columnsAreMatchedByHeaderNameNotByPosition() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(
                "style,name,season,category\nCASUAL,Áo thun,SUMMER,SHIRT\n"), true);

        assertThat(result.created()).isEqualTo(1);
        verify(clothingItemService).createItem(
                eq(USER), eq("Áo thun"), eq(""), isNull(), eq(ClothingCategory.SHIRT), eq(Season.SUMMER),
                eq(Style.CASUAL), eq(ClothingCondition.GOOD), eq(ClothingStatus.AVAILABLE),
                eq(0), isNull(), eq(false), eq(""));
    }

    @Test
    void aFileMissingRequiredColumnsIsRejectedWholesale() {
        assertThatThrownBy(() -> wardrobeCsvService.importCsv(USER, csv("name,color\nÁo thun,Đen\n"), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("category")
                .hasMessageContaining("season")
                .hasMessageContaining("style");

        verify(clothingItemService, never()).createItem(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void filesLongerThanTheCapAreRejectedBeforeAnythingIsWritten() {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int index = 0; index <= WardrobeCsvService.MAX_IMPORT_ROWS; index++) {
            csv.append("Món ").append(index).append(",SHIRT,,,SUMMER,CASUAL,,,,,,\n");
        }

        assertThatThrownBy(() -> wardrobeCsvService.importCsv(USER, csv(csv.toString()), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tối đa " + WardrobeCsvService.MAX_IMPORT_ROWS);

        verify(clothingItemService, never()).createItem(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anEmptyFileIsRejected() {
        assertThatThrownBy(() -> wardrobeCsvService.importCsv(USER, csv(""), true))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** File từ Excel mở lại: có BOM, dòng CRLF, ô bọc ngoặc kép chứa dấu phẩy. */
    @Test
    void excelQuirksAreHandled() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(
                "﻿name,category,season,style\r\n\"Áo khoác, dày\",JACKET,WINTER,CASUAL\r\n"), true);

        assertThat(result.created()).isEqualTo(1);
        verify(clothingItemService).createItem(
                eq(USER), eq("Áo khoác, dày"), any(), any(), eq(ClothingCategory.JACKET), eq(Season.WINTER),
                eq(Style.CASUAL), any(), any(), any(), any(), any(), any());
    }

    /** Người dùng gõ tay trong Excel: chữ thường, khoảng trắng thừa, gạch ngang thay gạch dưới. */
    @Test
    void enumValuesAreForgivingAboutCaseAndSeparators() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(
                "name,category,season,style,favorite\nÁo thun, shirt ,all season,casual,x\n"), true);

        assertThat(result.created()).isEqualTo(1);
        verify(clothingItemService).createItem(
                eq(USER), eq("Áo thun"), any(), any(), eq(ClothingCategory.SHIRT), eq(Season.ALL_SEASON),
                eq(Style.CASUAL), any(), any(), any(), any(), eq(true), any());
    }

    @Test
    void commentLinesInTheTemplateAreIgnored() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(
                "name,category,season,style\n# đây là ghi chú\nÁo thun,SHIRT,SUMMER,CASUAL\n"), true);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
    }

    @Test
    void aNegativeWearCountIsReportedOnItsOwnRow() {
        WardrobeImportResponse result = wardrobeCsvService.importCsv(USER, csv(
                "name,category,season,style,wearCount\nÁo thun,SHIRT,SUMMER,CASUAL,-3\n"), true);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.problems().get(0).message()).contains("wearCount");
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file", "tu-do.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static ClothingItem item(String name) {
        return ClothingItem.builder()
                .id(1L)
                .name(name)
                .color("Trắng")
                .colorTone(ColorTone.NEUTRAL)
                .category(ClothingCategory.SHIRT)
                .season(Season.ALL_SEASON)
                .style(Style.FORMAL)
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(4)
                .lastWornAt(LocalDateTime.of(2026, 7, 20, 8, 0))
                .favorite(false)
                .build();
    }
}
