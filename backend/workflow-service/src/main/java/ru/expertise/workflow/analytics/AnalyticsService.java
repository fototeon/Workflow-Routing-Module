package ru.expertise.workflow.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.repository.ProcessInstanceRepository;
import ru.expertise.workflow.repository.TaskInstanceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Indicators behind the analyst's dashboard (TZ §3: "формирует отчеты, дашборды, выгрузки и
 * анализирует показатели"). Everything is derived from the module's own tables — no projections in
 * other services are involved.
 */
@Service
public class AnalyticsService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final TaskInstanceRepository taskInstanceRepository;

    public AnalyticsService(ProcessInstanceRepository processInstanceRepository,
                             TaskInstanceRepository taskInstanceRepository) {
        this.processInstanceRepository = processInstanceRepository;
        this.taskInstanceRepository = taskInstanceRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary() {
        List<ProcessInstance> instances = processInstanceRepository.findAll();
        List<TaskInstance> tasks = taskInstanceRepository.findAll();
        Instant now = Instant.now();

        Map<String, Long> processesByStatus = countBy(instances, instance -> instance.getStatus().name());
        Map<String, Long> tasksByStatus = countBy(tasks, task -> task.getStatus().name());
        Map<String, Long> processesByTemplate = countBy(instances,
                instance -> instance.getProcessDefinition().getCode());

        long overdueTasks = tasks.stream()
                .filter(task -> task.getStatus() == TaskInstanceStatus.OVERDUE
                        || (task.getDueAt() != null && task.getDueAt().isBefore(now) && isOpen(task)))
                .count();

        List<ProcessInstance> completed = instances.stream()
                .filter(instance -> instance.getStatus() == ProcessInstanceStatus.COMPLETED)
                .filter(instance -> instance.getStartedAt() != null && instance.getCompletedAt() != null)
                .toList();
        Long averageCompletionMinutes = completed.isEmpty() ? null : Math.round(completed.stream()
                .mapToLong(instance -> Duration.between(instance.getStartedAt(), instance.getCompletedAt()).toMinutes())
                .average()
                .orElse(0));

        long slaBreached = tasks.stream().filter(task -> task.getStatus() == TaskInstanceStatus.OVERDUE).count();
        long slaOnTime = tasks.stream()
                .filter(task -> task.getStatus() == TaskInstanceStatus.COMPLETED && task.getDueAt() != null)
                .filter(task -> task.getCompletedAt() != null && !task.getCompletedAt().isAfter(task.getDueAt()))
                .count();

        return new AnalyticsSummary(
                instances.size(),
                tasks.size(),
                processesByStatus,
                tasksByStatus,
                processesByTemplate,
                overdueTasks,
                slaBreached,
                slaOnTime,
                averageCompletionMinutes);
    }

    private static boolean isOpen(TaskInstance task) {
        return task.getStatus() != TaskInstanceStatus.COMPLETED && task.getStatus() != TaskInstanceStatus.CANCELLED;
    }

    /** Counts by key, ordered from the most frequent value down so the dashboard reads top-first. */
    private static <T> Map<String, Long> countBy(List<T> items, Function<T, String> key) {
        return items.stream()
                .collect(Collectors.groupingBy(key, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }
}
