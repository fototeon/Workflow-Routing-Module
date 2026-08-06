package ru.expertise.workflow.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.events.NotificationPublisher;
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.repository.TaskInstanceRepository;
import ru.expertise.workflow.sla.SlaDueDateCalculator;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskInstanceServiceTest {

    @Mock
    private TaskInstanceRepository taskInstanceRepository;
    @Mock
    private SlaDueDateCalculator slaDueDateCalculator;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private NotificationPublisher notificationPublisher;

    private TaskInstanceService service;

    @BeforeEach
    void setUp() {
        service = new TaskInstanceService(taskInstanceRepository, slaDueDateCalculator,
                outboxEventWriter, notificationPublisher, new WorkflowProperties());
        lenient().when(taskInstanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TaskInstance taskWithStatus(TaskInstanceStatus status) throws Exception {
        TaskInstance task = new TaskInstance();
        task.setStatus(status);
        ProcessInstance processInstance = new ProcessInstance();
        assignId(processInstance);
        task.setProcessInstance(processInstance);
        assignId(task);
        return task;
    }

    /** Persisted entities always carry a generated id; unit tests fake it so event payloads look realistic. */
    private void assignId(Object entity) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, UUID.randomUUID());
    }

    @Test
    void completeAllowsInProgressTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.IN_PROGRESS);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThat(service.completeTask(task.getId(), "user-1").getStatus()).isEqualTo(TaskInstanceStatus.COMPLETED);
    }

    /** A task nobody claimed yet is taken into work implicitly, so completing it is not a dead end. */
    @Test
    void completeTakesAFreshTaskIntoWorkFirst() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.CREATED);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThat(service.completeTask(task.getId(), "user-1").getStatus()).isEqualTo(TaskInstanceStatus.COMPLETED);
    }

    /** An overdue task is still workable: the SLA breach records a fact, it does not close the task. */
    @Test
    void completeAllowsOverdueTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.OVERDUE);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThat(service.completeTask(task.getId(), "user-1").getStatus()).isEqualTo(TaskInstanceStatus.COMPLETED);
    }

    @Test
    void completeRejectsAlreadyCompletedTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.COMPLETED);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.completeTask(task.getId(), "user-1"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
