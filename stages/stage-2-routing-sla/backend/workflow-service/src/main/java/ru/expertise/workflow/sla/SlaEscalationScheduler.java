package ru.expertise.workflow.sla;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.repository.TaskInstanceRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Demo-scale single-node poller (REQ-02-006 / NFR §11): each pass marks newly-overdue tasks and
 * applies the next-due escalation step for tasks still within their SLA window. Not a clustered
 * scheduler — acceptable for a practice-project deployment, called out in the README.
 */
@Component
public class SlaEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationScheduler.class);
    private static final List<TaskInstanceStatus> TERMINAL_STATUSES =
            List.of(TaskInstanceStatus.COMPLETED, TaskInstanceStatus.CANCELLED);

    private final TaskInstanceRepository taskInstanceRepository;
    private final EscalationResolver escalationResolver;
    private final TaskSlaProcessor taskSlaProcessor;

    public SlaEscalationScheduler(TaskInstanceRepository taskInstanceRepository,
                                   EscalationResolver escalationResolver,
                                   TaskSlaProcessor taskSlaProcessor) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.escalationResolver = escalationResolver;
        this.taskSlaProcessor = taskSlaProcessor;
    }

    @Scheduled(fixedDelayString = "${workflow.sla.scan-interval-ms:30000}")
    public void scan() {
        Instant now = Instant.now();
        List<TaskInstance> active = taskInstanceRepository.findActiveTasksWithSla(TERMINAL_STATUSES);

        for (TaskInstance task : active) {
            try {
                processTask(task, now);
            } catch (Exception e) {
                log.warn("SLA scan failed for task {}: {}", task.getId(), e.getMessage(), e);
            }
        }
    }

    private void processTask(TaskInstance task, Instant now) {
        if (task.getStatus() == TaskInstanceStatus.OVERDUE) {
            return;
        }
        if (task.getDueAt().isBefore(now)) {
            taskSlaProcessor.markOverdue(task.getId());
            return;
        }

        SlaPolicy policy = task.getProcessInstance().getProcessDefinition().getSlaPolicy();
        if (policy == null) {
            return;
        }
        List<EscalationRule> rules = escalationResolver.parseRules(policy.getEscalationRules());
        int elapsedPercent = escalationResolver.elapsedPercent(task.getCreatedAt(), task.getDueAt(), now);
        Optional<EscalationRule> dueRule = escalationResolver.nextDueRule(rules, elapsedPercent, task.getLastEscalatedPercent());
        dueRule.ifPresent(rule -> taskSlaProcessor.escalate(task.getId(), rule));
    }
}
