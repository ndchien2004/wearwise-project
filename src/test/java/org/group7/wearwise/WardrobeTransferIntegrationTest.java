package org.group7.wearwise;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nhập / xuất tủ đồ đi qua cả tầng HTTP.
 *
 * <p>Unit test đã phủ luật từng dòng; phần chỉ hỏng khi chạy thật nằm ở đây: multipart có tới được
 * controller không, món nhập vào có thực sự nằm trong database không, và tên tiếng Việt trong file
 * tải về có còn nguyên dấu không — Spring mặc định áp ISO-8859-1 cho {@code text/csv}, nên đây là
 * loại lỗi mà mọi unit test đều bỏ lọt.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class WardrobeTransferIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String OWNER = "csvuser";
    private static final String OTHER = "nguoikhac";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ClothingItemRepository clothingItemRepository;

    @Autowired
    private OutfitRepository outfitRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void resetState() {
        // Test dùng chung một database H2, và bài kiểm tra ở đây đếm số món trong tủ — nên phải
        // dọn sạch trước, giống AdminSecurityIntegrationTest.
        outfitRepository.deleteAll();
        clothingItemRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        appUserRepository.deleteAll();

        saveUser(OWNER);
        saveUser(OTHER);

        token = "Bearer " + login(OWNER);
    }

    @Test
    void templateIsServedAsAnAttachment() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wardrobe/template").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("wearwise-mau-tu-do.csv")))
                .andReturn();

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("name,category,color");
        assertThat(body).contains("SHIRT / PANTS / SHOES / JACKET / ACCESSORY");
    }

    /** Nhập rồi xuất: món phải vào database thật, và tên tiếng Việt phải về nguyên dấu. */
    @Test
    void importThenExportKeepsVietnameseNamesIntact() throws Exception {
        String csv = "name,category,season,style,color\n"
                + "Áo dài họa tiết,SHIRT,SUMMER,FORMAL,Đỏ\n"
                + "Quần âu xám,PANTS,ALL_SEASON,FORMAL,Xám\n";

        mockMvc.perform(multipart("/api/wardrobe/import")
                        .file(csvFile(csv))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.problems").isEmpty());

        assertThat(clothingItemRepository.countByOwner_UsernameAndArchivedAtIsNull(OWNER)).isEqualTo(2);

        // Lấy byte thô rồi tự giải mã: getContentAsString() dùng charset của response, đúng chỗ
        // từng làm mất dấu tiếng Việt nên không dùng được để kiểm chứng chính nó.
        String body = exportBody();
        assertThat(body).contains("Áo dài họa tiết").contains("Quần âu xám").contains("Đỏ");
    }

    /** Xuất ra rồi nhập lại chính file đó không được nhân đôi tủ đồ. */
    @Test
    void reimportingAnExportedFileAddsNothing() throws Exception {
        mockMvc.perform(multipart("/api/wardrobe/import")
                        .file(csvFile("name,category,season,style\nÁo thun đen,SHIRT,SUMMER,CASUAL\n"))
                        .header("Authorization", token))
                .andExpect(status().isOk());

        String exported = exportBody();

        mockMvc.perform(multipart("/api/wardrobe/import")
                        .file(csvFile(exported))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(1));

        assertThat(clothingItemRepository.countByOwner_UsernameAndArchivedAtIsNull(OWNER)).isEqualTo(1);
    }

    /** Dòng sai được báo lại kèm số dòng, các dòng còn lại vẫn vào tủ. */
    @Test
    void badRowsComeBackWithTheirLineNumber() throws Exception {
        String csv = "name,category,season,style\n"
                + "Áo tốt,SHIRT,SUMMER,CASUAL\n"
                + "Áo xấu,KHONG_CO_LOAI_NAY,SUMMER,CASUAL\n";

        mockMvc.perform(multipart("/api/wardrobe/import")
                        .file(csvFile(csv))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.problems[0].line").value(3))
                .andExpect(jsonPath("$.problems[0].kind").value("INVALID"));

        assertThat(clothingItemRepository.countByOwner_UsernameAndArchivedAtIsNull(OWNER)).isEqualTo(1);
    }

    @Test
    void aFileWithoutTheRequiredColumnsIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/wardrobe/import")
                        .file(csvFile("ten,mau\nÁo thun,Đen\n"))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(clothingItemRepository.countByOwner_UsernameAndArchivedAtIsNull(OWNER)).isZero();
    }

    /**
     * Tủ đồ là dữ liệu riêng: khách chưa đăng nhập không chạm được, kể cả vào file mẫu — một
     * endpoint xuất bỏ quên phân quyền là rò cả tủ đồ của người khác.
     */
    @Test
    void anonymousCallersCannotTouchTheseEndpoints() throws Exception {
        mockMvc.perform(get("/api/wardrobe/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wardrobe/template")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/wardrobe/import").file(csvFile("name\n")))
                .andExpect(status().isUnauthorized());
    }

    /** Chỉ xuất tủ của chính mình. */
    @Test
    void exportOnlyContainsTheCallersOwnItems() throws Exception {
        clothingItemRepository.save(ClothingItem.builder()
                .name("Áo của người khác")
                .category(ClothingCategory.SHIRT)
                .season(Season.SUMMER)
                .style(Style.CASUAL)
                .owner(appUserRepository.findByUsername(OTHER).orElseThrow())
                .build());

        assertThat(exportBody()).doesNotContain("Áo của người khác");
    }

    @Test
    void exportOfAnEmptyWardrobeStillCarriesTheHeaderRow() throws Exception {
        String body = exportBody();

        assertThat(body).contains("name,category,color");
        assertThat(body.lines()).hasSize(1);
    }

    // ---------------------------------------------------------------- phụ trợ

    private String exportBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wardrobe/export").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();

        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "tu-do.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private void saveUser(String username) {
        appUserRepository.save(AppUser.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("USER")
                .build());
    }

    private String login(String username) {
        try {
            String body = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "%s", "password": "password123"}
                                    """.formatted(username)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            return OBJECT_MAPPER.readTree(body).get("accessToken").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Không đăng nhập được trong test", exception);
        }
    }
}
