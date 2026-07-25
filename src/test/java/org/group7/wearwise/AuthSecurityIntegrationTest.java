package org.group7.wearwise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.PasswordResetTokenRepository;
import org.group7.wearwise.repository.RefreshTokenRepository;
import org.group7.wearwise.repository.RevokedAuthTokenRepository;
import org.group7.wearwise.service.AuthMailService;
import org.group7.wearwise.service.AuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RevokedAuthTokenRepository revokedAuthTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthTokenService authTokenService;

    /** Chặn việc thực sự gửi mail và cho phép lấy lại mã đặt lại mật khẩu trong test. */
    @MockitoBean
    private AuthMailService authMailService;

    @BeforeEach
    void cleanUsers() {
        revokedAuthTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void protectedApiRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/clothing-items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiAllowsValidBearerToken() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String token = authTokenService.createToken("demo");

        mockMvc.perform(get("/api/clothing-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void registerEndpointIsPublicAndReturnsBothTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "public-user",
                                  "email": "public-user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        saveUser("demo", "taken@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "another",
                                  "email": "taken@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email này đã được dùng cho một tài khoản khác."));
    }

    /** Ô đăng nhập nhận cả hai: người dùng thường nhớ email hơn tên đăng nhập. */
    @Test
    void loginAcceptsEitherTheUsernameOrTheEmail() throws Exception {
        saveUser("demo", "demo@example.com", "password123");

        assertThat(login("demo", "password123").get("accessToken").asText()).isNotBlank();

        JsonNode byEmail = postJson("/api/auth/login", """
                {"username": "DEMO@Example.com", "password": "password123"}
                """, status().isOk());
        assertThat(byEmail.get("accessToken").asText()).isNotBlank();
        assertThat(byEmail.get("username").asText()).isEqualTo("demo");
    }

    @Test
    void loginWithAnUnknownIdentifierIsRejectedWithoutRevealingWhichPartIsWrong() throws Exception {
        saveUser("demo", "demo@example.com", "password123");

        postJson("/api/auth/login", """
                {"username": "khong-ton-tai", "password": "password123"}
                """, status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "demo", "password": "sai-mat-khau"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập/email hoặc mật khẩu không đúng."));
    }

    @Test
    void meEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointReturnsCurrentUserForValidBearerToken() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String token = authTokenService.createToken("demo");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void logoutRevokesCurrentBearerToken() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String token = authTokenService.createToken("demo");

        mockMvc.perform(get("/api/clothing-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/clothing-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenRotatesAndOldTokenStopsWorking() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String firstRefreshToken = login("demo", "password123").get("refreshToken").asText();

        JsonNode refreshed = postJson("/api/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(firstRefreshToken), status().isOk());

        assertThat(refreshed.get("accessToken").asText()).isNotBlank();
        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(firstRefreshToken);

        // Dùng lại refresh token cũ phải bị từ chối.
        postJson("/api/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(firstRefreshToken), status().isUnauthorized());
    }

    @Test
    void reusingRevokedRefreshTokenKillsTheWholeSession() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String firstRefreshToken = login("demo", "password123").get("refreshToken").asText();

        String secondRefreshToken = postJson("/api/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(firstRefreshToken), status().isOk())
                .get("refreshToken").asText();

        // Token cũ bị dùng lại: coi như bị đánh cắp, thu hồi toàn bộ phiên.
        postJson("/api/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(firstRefreshToken), status().isUnauthorized());

        postJson("/api/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(secondRefreshToken), status().isUnauthorized());
    }

    @Test
    void repeatedWrongPasswordLocksTheAccount() throws Exception {
        saveUser("demo", "demo@example.com", "password123");

        for (int attempt = 1; attempt < 5; attempt++) {
            postJson("/api/auth/login", """
                    {"username": "demo", "password": "wrong-password"}
                    """, status().isUnauthorized());
        }

        // Lần sai thứ 5 chạm ngưỡng và khóa tài khoản.
        postJson("/api/auth/login", """
                {"username": "demo", "password": "wrong-password"}
                """, status().isLocked());

        // Mật khẩu đúng cũng bị chặn khi đang bị khóa.
        postJson("/api/auth/login", """
                {"username": "demo", "password": "password123"}
                """, status().isLocked());
    }

    @Test
    void forgotPasswordReturnsNeutralMessageForUnknownEmail() throws Exception {
        postJson("/api/auth/forgot-password", """
                {"email": "nobody@example.com"}
                """, status().isOk());

        verify(authMailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void forgotPasswordThenResetAllowsLoginWithNewPassword() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String oldRefreshToken = login("demo", "password123").get("refreshToken").asText();

        postJson("/api/auth/forgot-password", """
                {"email": "DEMO@example.com"}
                """, status().isOk());

        String resetToken = captureResetToken();

        mockMvc.perform(get("/api/auth/reset-password/validate").param("token", resetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        postJson("/api/auth/reset-password", """
                {"token": "%s", "newPassword": "brandNew456"}
                """.formatted(resetToken), status().isOk());

        // Mật khẩu mới dùng được, mật khẩu cũ thì không.
        postJson("/api/auth/login", """
                {"username": "demo", "password": "brandNew456"}
                """, status().isOk());
        postJson("/api/auth/login", """
                {"username": "demo", "password": "password123"}
                """, status().isUnauthorized());

        // Phiên cũ bị đăng xuất khỏi mọi thiết bị.
        postJson("/api/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(oldRefreshToken), status().isUnauthorized());
    }

    /**
     * Đăng ký mới bắt buộc có email, và email đó dùng được ngay cho luồng quên mật khẩu.
     * Đây là đường duy nhất để một tài khoản có email — không thể bổ sung sau.
     */
    @Test
    void emailFromRegistrationDrivesThePasswordResetFlow() throws Exception {
        postJson("/api/auth/register", """
                {"username": "newbie", "email": "Newbie@Example.COM", "password": "password123"}
                """, status().isCreated());

        String accessToken = login("newbie", "password123").get("accessToken").asText();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                // Email được chuẩn hóa về chữ thường trước khi lưu.
                .andExpect(jsonPath("$.email").value("newbie@example.com"));

        postJson("/api/auth/forgot-password", """
                {"email": "newbie@example.com"}
                """, status().isOk());
        String resetToken = captureResetToken();

        postJson("/api/auth/reset-password", """
                {"token": "%s", "newPassword": "brandNew456"}
                """.formatted(resetToken), status().isOk());

        postJson("/api/auth/login", """
                {"username": "newbie", "password": "brandNew456"}
                """, status().isOk());
    }

    /** Email chốt một lần khi đăng ký: API đổi email đã bị gỡ hẳn, không chỉ ẩn trên giao diện. */
    @Test
    void emailCannotBeChangedAfterRegistration() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String accessToken = login("demo", "password123").get("accessToken").asText();

        mockMvc.perform(put("/api/auth/me/email")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "password123", "email": "new@example.com"}
                                """))
                .andExpect(status().is4xxClientError());

        assertThat(appUserRepository.findByUsername("demo").orElseThrow().getEmail())
                .isEqualTo("demo@example.com");
    }

    @Test
    void resetTokenCannotBeUsedTwice() throws Exception {
        saveUser("demo", "demo@example.com", "password123");

        postJson("/api/auth/forgot-password", """
                {"email": "demo@example.com"}
                """, status().isOk());
        String resetToken = captureResetToken();

        postJson("/api/auth/reset-password", """
                {"token": "%s", "newPassword": "brandNew456"}
                """.formatted(resetToken), status().isOk());

        postJson("/api/auth/reset-password", """
                {"token": "%s", "newPassword": "yetAnother789"}
                """.formatted(resetToken), status().isBadRequest());
    }

    @Test
    void expiredResetTokenIsRejected() throws Exception {
        saveUser("demo", "demo@example.com", "password123");

        postJson("/api/auth/forgot-password", """
                {"email": "demo@example.com"}
                """, status().isOk());
        String resetToken = captureResetToken();

        passwordResetTokenRepository.findAll().forEach(token -> {
            token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            passwordResetTokenRepository.save(token);
        });

        mockMvc.perform(get("/api/auth/reset-password/validate").param("token", resetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        postJson("/api/auth/reset-password", """
                {"token": "%s", "newPassword": "brandNew456"}
                """.formatted(resetToken), status().isBadRequest());
    }

    @Test
    void changePasswordInvalidatesTokensIssuedBeforeTheChange() throws Exception {
        saveUser("demo", "demo@example.com", "password123");
        String oldAccessToken = login("demo", "password123").get("accessToken").asText();

        // `iat` của JWT chỉ có độ phân giải giây — chờ qua mốc giây để mốc đổi mật khẩu
        // thực sự nằm sau thời điểm phát hành token cũ.
        Thread.sleep(1100);

        JsonNode changed = OBJECT_MAPPER.readTree(mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + oldAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "password123", "newPassword": "brandNew456"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        // Token mới vẫn dùng được...
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + changed.get("accessToken").asText()))
                .andExpect(status().isOk());

        // ...còn token phát hành trước khi đổi mật khẩu thì không.
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());
    }

    private String captureResetToken() {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(authMailService).sendPasswordResetEmail(
                anyString(), anyString(), tokenCaptor.capture(), anyLong());
        return tokenCaptor.getValue();
    }

    private JsonNode login(String username, String password) throws Exception {
        return postJson("/api/auth/login", """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password), status().isOk());
    }

    private JsonNode postJson(
            String path,
            String body,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus
    ) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expectedStatus)
                .andReturn()
                .getResponse()
                .getContentAsString();

        return response.isBlank() ? OBJECT_MAPPER.createObjectNode() : OBJECT_MAPPER.readTree(response);
    }

    private void saveUser(String username, String email, String rawPassword) {
        appUserRepository.save(AppUser.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("USER")
                .build());
    }
}
