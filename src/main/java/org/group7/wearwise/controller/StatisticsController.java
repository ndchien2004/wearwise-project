package org.group7.wearwise.controller;

import org.group7.wearwise.dto.response.StatisticsResponse;
import org.group7.wearwise.dto.response.WearHistoryResponse;
import org.group7.wearwise.service.StatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public StatisticsResponse getStatistics(Authentication authentication) {
        return statisticsService.getStatistics(authentication.getName());
    }

    /** Bỏ trống khoảng ngày thì lấy tháng hiện tại. */
    @GetMapping("/history")
    public WearHistoryResponse getHistory(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit
    ) {
        return statisticsService.getHistory(authentication.getName(), from, to, limit);
    }
}
