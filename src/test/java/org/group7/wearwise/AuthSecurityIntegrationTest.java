package org.group7.wearwise;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.RevokedAuthTokenRepository;
import org.group7.wearwise.service.AuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RevokedAuthTokenRepository revokedAuthTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthTokenService authTokenService;

    @BeforeEach
    void cleanUsers() {
        revokedAuthTokenRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void protectedApiRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/clothing-items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiAllowsValidBearerToken() throws Exception {
        appUserRepository.save(AppUser.builder()
                .username("demo")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("USER")
                .build());
        String token = authTokenService.createToken("demo");

        mockMvc.perform(get("/api/clothing-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void registerEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "public-user",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void meEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointReturnsCurrentUserForValidBearerToken() throws Exception {
        appUserRepository.save(AppUser.builder()
                .username("demo")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("USER")
                .build());
        String token = authTokenService.createToken("demo");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void logoutRevokesCurrentBearerToken() throws Exception {
        appUserRepository.save(AppUser.builder()
                .username("demo")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("USER")
                .build());
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
}
