package ru.expertise.workflow.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.expertise.workflow.analytics.AnalyticsService;
import ru.expertise.workflow.analytics.AnalyticsSummary;

/** Reporting endpoints for the analyst role (TZ §3, §9). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    public AnalyticsSummary summary() {
        return analyticsService.summary();
    }
}
