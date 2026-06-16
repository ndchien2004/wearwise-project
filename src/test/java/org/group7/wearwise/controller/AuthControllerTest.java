package org.group7.wearwise.controller;

import org.group7.wearwise.dto.response.AuthResponse;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.GlobalExceptionHandler;
import org.group7.wearwise.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        Validator validator = validator();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void registerReturnsCreatedToken() throws Exception {
        when(authService.register("demo", "password123"))
                .thenReturn(new AuthResponse("Bearer", "token-value", 86400, "demo"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("token-value"))
                .andExpect(jsonPath("$.username").value("demo"));
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login("demo", "password123"))
                .thenReturn(new AuthResponse("Bearer", "token-value", 86400, "demo"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"));
    }

    @Test
    void invalidLoginReturnsUnauthorized() throws Exception {
        when(authService.login("demo", "wrong-password")).thenThrow(new AuthenticationFailedException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void invalidRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "ab",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("Username must be between 3 and 100 characters."))
                .andExpect(jsonPath("$.errors.password").value("Password must be between 6 and 100 characters."));
    }

    private static Validator validator() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.afterPropertiesSet();
        return validatorFactoryBean;
    }
}
