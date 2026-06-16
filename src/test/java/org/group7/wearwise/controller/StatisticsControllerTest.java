package org.group7.wearwise.controller;

import org.group7.wearwise.dto.response.StatisticsResponse;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatisticsControllerTest {

    private static final String OWNER = "demo";

    private StatisticsService statisticsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        statisticsService = mock(StatisticsService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatisticsController(statisticsService))
                .build();
    }

    @Test
    void getStatisticsReturnsDashboardData() throws Exception {
        StatisticsResponse response = new StatisticsResponse(
                5,
                2,
                1,
                1,
                12,
                Map.of(ClothingCategory.SHIRT, 2L),
                Map.of(Style.CASUAL, 2L),
                Map.of(Season.ALL_SEASON, 5L),
                Map.of(ClothingCondition.GOOD, 4L),
                Map.of(ClothingStatus.AVAILABLE, 3L),
                List.of()
        );
        when(statisticsService.getStatistics(OWNER)).thenReturn(response);

        mockMvc.perform(get("/api/statistics")
                        .principal(new UsernamePasswordAuthenticationToken(OWNER, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClothingItems").value(5))
                .andExpect(jsonPath("$.favoriteOutfits").value(1))
                .andExpect(jsonPath("$.totalWearCount").value(12))
                .andExpect(jsonPath("$.clothingItemsByCategory.SHIRT").value(2));
    }
}
