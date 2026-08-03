package ru.expertise.workflow.analytics;

import java.util.Map;

/** Dashboard payload for the analyst role (TZ §3). */
public record AnalyticsSummary(
        long totalProcesses,
        long totalTasks,
        Map<String, Long> processesByStatus,
        Map<String, Long> tasksByStatus,
        Map<String, Long> processesByTemplate,
        long overdueTasks,
        long slaBreachedTasks,
        long slaMetTasks,
        Long averageCompletionMinutes
) {
}
