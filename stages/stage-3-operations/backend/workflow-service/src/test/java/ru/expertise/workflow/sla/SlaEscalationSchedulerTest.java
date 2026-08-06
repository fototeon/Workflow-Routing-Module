package ru.expertise.workflow.sla;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.SlaPolicy;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaEscalationSchedulerTest {

    @Mock
    private ru.expertise.workflow.repository.TaskInstanceRepository taskInstanceRepository;
    @Mock
    private EscalationResolver escalationResolver;
    @Mock
    private TaskSlaProcessor taskSlaProcessor;

    private SlaEscalationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SlaEscalationScheduler(taskInstanceRepository, escalationResolver, taskSlaProcessor);
    }

    private TaskInstance taskWithCreatedAt(Instant createdAt, Instant dueAt, TaskInstanceStatus status,
                                            ProcessInstance processInstance) throws Exception {
        TaskInstance task = new TaskInstance();
        task.setProcessInstance(processInstance);
        task.setStatus(status);
        task.setDueAt(dueAt);
        setId(task);

        Field createdAtField = findField(task.getClass(), "createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(task, createdAt);
        return task;
    }

    private void setId(TaskInstance task) throws Exception {
        Field idField = TaskInstance.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(task, java.util.UUID.randomUUID());
    }

    private Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private ProcessInstance processInstanceWithSla(SlaPolicy policy) {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setSlaPolicy(policy);
        ProcessInstance instance = new ProcessInstance();
        instance.setProcessDefinition(definition);
        return instance;
    }

    @Test
    void marksTaskOverdueWhenPastDueDate() throws Exception {
        Instant now = Instant.now();
        ProcessInstance pi = processInstanceWithSla(null);
        TaskInstance task = taskWithCreatedAt(now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.MINUTES),
                TaskInstanceStatus.IN_PROGRESS, pi);

        when(taskInstanceRepository.findActiveTasksWithSla(any())).thenReturn(List.of(task));

        scheduler.scan();

        verify(taskSlaProcessor, times(1)).markOverdue(task.getId());
        verify(taskSlaProcessor, never()).escalate(any(), any());
    }

    @Test
    void skipsAlreadyOverdueTasks() throws Exception {
        Instant now = Instant.now();
        ProcessInstance pi = processInstanceWithSla(null);
        TaskInstance task = taskWithCreatedAt(now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.MINUTES),
                TaskInstanceStatus.OVERDUE, pi);

        when(taskInstanceRepository.findActiveTasksWithSla(any())).thenReturn(List.of(task));

        scheduler.scan();

        verify(taskSlaProcessor, never()).markOverdue(any());
        verify(taskSlaProcessor, never()).escalate(any(), any());
    }

    @Test
    void appliesEscalationWhenThresholdCrossed() throws Exception {
        Instant now = Instant.now();
        SlaPolicy policy = new SlaPolicy();
        ProcessInstance pi = processInstanceWithSla(policy);
        Instant createdAt = now.minus(80, ChronoUnit.MINUTES);
        Instant dueAt = now.plus(20, ChronoUnit.MINUTES);
        TaskInstance task = taskWithCreatedAt(createdAt, dueAt, TaskInstanceStatus.IN_PROGRESS, pi);

        EscalationRule rule = new EscalationRule(80, "MANAGER");
        when(taskInstanceRepository.findActiveTasksWithSla(any())).thenReturn(List.of(task));
        when(escalationResolver.parseRules(any())).thenReturn(List.of(rule));
        when(escalationResolver.elapsedPercent(eq(createdAt), eq(dueAt), any())).thenReturn(80);
        when(escalationResolver.nextDueRule(List.of(rule), 80, 0)).thenReturn(Optional.of(rule));

        scheduler.scan();

        verify(taskSlaProcessor, times(1)).escalate(task.getId(), rule);
        verify(taskSlaProcessor, never()).markOverdue(any());
    }

    @Test
    void doesNothingWhenNoSlaPolicyConfigured() throws Exception {
        Instant now = Instant.now();
        ProcessInstance pi = processInstanceWithSla(null);
        TaskInstance task = taskWithCreatedAt(now.minus(10, ChronoUnit.MINUTES), now.plus(50, ChronoUnit.MINUTES),
                TaskInstanceStatus.IN_PROGRESS, pi);

        when(taskInstanceRepository.findActiveTasksWithSla(any())).thenReturn(List.of(task));

        scheduler.scan();

        verify(taskSlaProcessor, never()).markOverdue(any());
        verify(taskSlaProcessor, never()).escalate(any(), any());
    }
}
