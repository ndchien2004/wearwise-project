package org.group7.wearwise.config;

import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.service.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Nhóm hạn mức phải bám vào chi phí thật của từng endpoint: thao tác đắt bị siết, còn request
 * chỉ đọc dữ liệu thì không được tính vào cùng một túi.
 */
class RateLimitFilterTest {

    private static final String CALLER_IP = "203.0.113.7";

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        // Không nạp gì từ database nên mọi tài khoản dùng hạn mức mặc định — đúng thứ cần đo ở đây.
        UserRateLimitOverrides overrides = new UserRateLimitOverrides(mock(AppUserRepository.class));

        // Một lượt mỗi nhóm đắt: request thứ hai cùng nhóm phải bị chặn ngay.
        filter = new RateLimitFilter(
                new RateLimiter(), new ClientIpResolver(false), overrides, 1, 1, 1, 20, 240);
    }

    @Test
    void aiStatusIsNotChargedToTheAiBudget() throws Exception {
        assertThat(statusOf("GET", "/api/ai/status")).isEqualTo(HttpStatus.OK.value());
        assertThat(statusOf("GET", "/api/ai/status")).isEqualTo(HttpStatus.OK.value());
        assertThat(statusOf("GET", "/api/ai/status")).isEqualTo(HttpStatus.OK.value());

        assertThat(statusOf("POST", "/api/ai/outfit-suggestions")).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void aiGenerationIsChargedToTheAiBudget() throws Exception {
        assertThat(statusOf("POST", "/api/ai/outfit-suggestions")).isEqualTo(HttpStatus.OK.value());

        assertThat(statusOf("POST", "/api/ai/clothing-items/analyze"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void tryOnGenerationHasItsOwnBudget() throws Exception {
        assertThat(statusOf("POST", "/api/try-on/items/5")).isEqualTo(HttpStatus.OK.value());

        assertThat(statusOf("POST", "/api/try-on/outfits/9"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void browsingTryOnHistoryIsNotChargedAsAGeneration() throws Exception {
        assertThat(statusOf("GET", "/api/try-on/items/5")).isEqualTo(HttpStatus.OK.value());
        assertThat(statusOf("GET", "/api/try-on/outfits/9")).isEqualTo(HttpStatus.OK.value());
        assertThat(statusOf("GET", "/api/try-on")).isEqualTo(HttpStatus.OK.value());

        assertThat(statusOf("POST", "/api/try-on/items/5")).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void uploadsShareOneBudgetAcrossEveryDestination() throws Exception {
        assertThat(statusOf("POST", "/api/images/clothing")).isEqualTo(HttpStatus.OK.value());

        assertThat(statusOf("POST", "/api/auth/avatar"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(statusOf("POST", "/api/try-on/body-photo"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void rejectionCarriesTheRetryAfterHeader() throws Exception {
        statusOf("POST", "/api/try-on/items/5");

        MockHttpServletResponse response = perform("POST", "/api/try-on/items/5");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void staticFilesAreNotRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(statusOf("GET", "/index.html")).isEqualTo(HttpStatus.OK.value());
        }
    }

    private int statusOf(String method, String path) throws Exception {
        return perform(method, path).getStatus();
    }

    private MockHttpServletResponse perform(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(CALLER_IP);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
