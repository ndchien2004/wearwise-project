package org.group7.wearwise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.enums.AuditAction;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.AuditEventRepository;
import org.group7.wearwise.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng ranh giới của vai trò quản trị viên: ai vào được, ai không, và những gì quản trị
 * viên cố tình <b>không</b> làm được.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetState() {
        auditEventRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        appUserRepository.deleteAll();

        saveUser("boss", "ADMIN");
        saveUser("member", "USER");
    }

    // ---------- Phân quyền ----------

    @Test
    void adminEndpointsRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Người dùng thường phải nhận 403 chứ không phải 401: 401 ngụ ý "đăng nhập lại đi", mà đăng
     * nhập lại thì cũng chẳng giải quyết được gì.
     */
    @Test
    void adminEndpointsRejectOrdinaryUsersWithForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/overview").header("Authorization", bearer("member")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer("member")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-events").header("Authorization", bearer("member")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadOverviewAndUserList() throws Exception {
        mockMvc.perform(get("/api/admin/overview").header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersTotal").value(2))
                .andExpect(jsonPath("$.usersAdmin").value(1));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    /**
     * Quản trị viên quản lý tài khoản, không xem nội dung. Danh sách người dùng không được để lọt
     * ảnh cơ thể hay ảnh đại diện — đó là dữ liệu riêng tư, và một tài khoản quản trị bị chiếm
     * không được biến thành quyền xem ảnh của mọi người.
     */
    @Test
    void userListNeverExposesPersonalContent() throws Exception {
        appUserRepository.findByUsername("member").ifPresent(user -> {
            user.setBodyPhotoUrl("https://res.cloudinary.com/demo/body-photos/secret.jpg");
            user.setAvatarUrl("https://res.cloudinary.com/demo/avatars/secret.jpg");
            appUserRepository.save(user);
        });

        String body = mockMvc.perform(get("/api/admin/users").header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("bodyPhotoUrl", "avatarUrl", "secret.jpg", "passwordHash");
    }

    // ---------- Khóa / mở khóa ----------

    @Test
    void lockingAnAccountTakesEffectImmediatelyAndIsAudited() throws Exception {
        // Lấy token TRƯỚC khi khóa: sau khi khóa thì chính /login cũng trả 423, nên không còn
        // cách nào lấy được token để kiểm chứng "token cũ có bị vô hiệu không".
        String memberToken = bearer("member");

        mockMvc.perform(post("/api/admin/users/member/lock")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Spam ảnh thử đồ", "durationMinutes": 60}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        // Access token cũ mất hiệu lực ngay, không phải chờ hết hạn 15 phút.
        mockMvc.perform(get("/api/clothing-items").header("Authorization", memberToken))
                .andExpect(status().isUnauthorized());

        assertThat(auditEventRepository.findAll())
                .anyMatch(event -> event.getAction() == AuditAction.ADMIN_LOCKED_ACCOUNT
                        && "boss".equals(event.getActorUsername())
                        && "member".equals(event.getTargetUsername())
                        && event.getDetail().contains("Spam ảnh thử đồ"));
    }

    @Test
    void unlockingRestoresAccess() throws Exception {
        lockMember();

        mockMvc.perform(post("/api/admin/users/member/unlock")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Đã xác minh nhầm"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

        mockMvc.perform(get("/api/clothing-items").header("Authorization", bearer("member")))
                .andExpect(status().isOk());
    }

    /** Tự khóa mình là cách nhanh nhất để mất quyền quản trị mà không ai mở lại được. */
    @Test
    void adminCannotLockThemselves() throws Exception {
        mockMvc.perform(post("/api/admin/users/boss/lock")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "thử xem", "durationMinutes": 10}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_ACTION_REJECTED"));
    }

    @Test
    void adminCannotLockAnotherAdmin() throws Exception {
        saveUser("boss2", "ADMIN");

        mockMvc.perform(post("/api/admin/users/boss2/lock")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "tranh chấp nội bộ", "durationMinutes": 10}
                                """))
                .andExpect(status().isConflict());
    }

    /** Bắt nhập lý do buộc người bấm nút dừng lại một nhịp, và làm nhật ký có giá trị tra cứu. */
    @Test
    void lockingRequiresAReason() throws Exception {
        mockMvc.perform(post("/api/admin/users/member/lock")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "  ", "durationMinutes": 60}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").isNotEmpty());
    }

    @Test
    void lockingAnUnknownAccountReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/admin/users/khong-ton-tai/lock")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "kiểm tra", "durationMinutes": 10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ---------- Hạn mức riêng ----------

    @Test
    void adminCanSetAndResetPerAccountQuota() throws Exception {
        mockMvc.perform(put("/api/admin/users/member/rate-limit")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"aiPerHour": 5, "externalPerHour": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiQuotaPerHour").value(5))
                .andExpect(jsonPath("$.externalQuotaPerHour").value(2));

        assertThat(appUserRepository.findByUsername("member").orElseThrow().getAiQuotaPerHour())
                .isEqualTo(5);

        // Bỏ trống nghĩa là quay về mức mặc định của hệ thống.
        mockMvc.perform(put("/api/admin/users/member/rate-limit")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiQuotaPerHour").doesNotExist());
    }

    // ---------- Nhật ký kiểm toán ----------

    @Test
    void auditLogRecordsFailedLoginsIncludingUnknownAccounts() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "member", "password": "sai-mat-khau"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "khong-ton-tai", "password": "bat-ky"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(auditEventRepository.findAll())
                .filteredOn(event -> event.getAction() == AuditAction.LOGIN_FAILED)
                .hasSize(2)
                .anyMatch(event -> "khong-ton-tai".equals(event.getActorUsername()));
    }

    /** Nhật ký mà người bị giám sát sửa được thì không còn là bằng chứng. */
    @Test
    void thereIsNoApiToModifyOrDeleteAuditEvents() throws Exception {
        // Lấy token trước khi đo: bản thân việc đăng nhập cũng sinh ra sự kiện kiểm toán,
        // gọi bearer() giữa hai lần đếm sẽ làm phép so sánh vô nghĩa.
        String adminToken = bearer("boss");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "member", "password": "sai-mat-khau"}
                                """))
                .andExpect(status().isUnauthorized());

        long before = auditEventRepository.count();
        assertThat(before).isPositive();

        Long eventId = auditEventRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/admin/audit-events/" + eventId)
                        .header("Authorization", adminToken))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(put("/api/admin/audit-events/" + eventId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detail\": \"đã xóa dấu vết\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(auditEventRepository.count()).isEqualTo(before);
    }

    @Test
    void auditEventsCanBeFilteredByAction() throws Exception {
        login("member");

        JsonNode page = OBJECT_MAPPER.readTree(mockMvc.perform(get("/api/admin/audit-events")
                        .param("action", "LOGIN_SUCCEEDED")
                        .header("Authorization", bearer("boss")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(page.get("content")).isNotEmpty();
        page.get("content").forEach(event ->
                assertThat(event.get("action").asText()).isEqualTo("LOGIN_SUCCEEDED"));
    }

    // ---------- helpers ----------

    private void lockMember() throws Exception {
        mockMvc.perform(post("/api/admin/users/member/lock")
                        .header("Authorization", bearer("boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "kiểm tra", "durationMinutes": 60}
                                """))
                .andExpect(status().isOk());
    }

    /**
     * Lấy access token thật qua /login thay vì tự ký: như vậy bài kiểm tra đi qua đúng đường mà
     * người dùng thật đi, kể cả phần kiểm tra tài khoản bị khóa trong bộ lọc xác thực.
     */
    private String bearer(String username) throws Exception {
        return "Bearer " + login(username);
    }

    private String login(String username) throws Exception {
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
    }

    private void saveUser(String username, String role) {
        appUserRepository.save(AppUser.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(role)
                .passwordChangedAt(LocalDateTime.now().minusDays(1))
                .build());
    }
}
