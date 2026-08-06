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
import ru.expertise.workflow.security.AccessPolicy;
import ru.expertise.workflow.repository.TaskInstanceRepository;
import ru.expertise.workflow.repository.TaskReassignmentRepository;
import ru.expertise.workflow.sla.SlaDueDateCalculator;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskInstanceServiceTest {

    @Mock
    private TaskInstanceRepository taskInstanceRepository;
    @Mock
    private TaskReassignmentRepository taskReassignmentRepository;
    @Mock
    private SlaDueDateCalculator slaDueDateCalculator;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private AccessPolicy accessPolicy;

    private TaskInstanceService service;

    @BeforeEach
    void setUp() {
        service = new TaskInstanceService(taskInstanceRepository, taskReassignmentRepository,
                slaDueDateCalculator, outboxEventWriter, notificationPublisher, accessPolicy,
                new WorkflowProperties(), null);
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
    void reassignRejectsBlankReason() {
        UUID taskId = UUID.randomUUID();

        assertThatThrownBy(() -> service.reassignTask(taskId, "user-2", null, "  ", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reassignRequiresExactlyOneTarget() {
        UUID taskId = UUID.randomUUID();

        assertThatThrownBy(() -> service.reassignTask(taskId, null, null, "reason", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reassignTask(taskId, "user-2", "MANAGER", "reason", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reassignToRoleClearsThePersonalAssignee() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.IN_PROGRESS);
        task.setAssigneeId("coordinator1");
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        TaskInstance reassigned = service.reassignTask(task.getId(), null, "MANAGER", "Возврат в очередь", "manager1");

        org.assertj.core.api.Assertions.assertThat(reassigned.getAssigneeRole()).isEqualTo("MANAGER");
        org.assertj.core.api.Assertions.assertThat(reassigned.getAssigneeId()).isNull();
    }

    @Test
    void reassignRejectsCompletedTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.COMPLETED);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.reassignTask(task.getId(), "user-2", null, "workload rebalance", "user-1"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void completeRejectsAlreadyCompletedTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.COMPLETED);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.completeTask(task.getId(), "user-1"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void completeAllowsInProgressTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.IN_PROGRESS);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        TaskInstance completed = service.completeTask(task.getId(), "user-1");

        org.assertj.core.api.Assertions.assertThat(completed.getStatus()).isEqualTo(TaskInstanceStatus.COMPLETED);
    }
}
