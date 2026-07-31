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
import ru.expertise.workflow.events.OutboxEventWriter;
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

    private TaskInstanceService service;

    @BeforeEach
    void setUp() {
        service = new TaskInstanceService(taskInstanceRepository, taskReassignmentRepository,
                slaDueDateCalculator, outboxEventWriter, new WorkflowProperties());
        lenient().when(taskInstanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TaskInstance taskWithStatus(TaskInstanceStatus status) throws Exception {
        TaskInstance task = new TaskInstance();
        task.setStatus(status);
        task.setProcessInstance(new ProcessInstance());
        Field idField = TaskInstance.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(task, UUID.randomUUID());
        return task;
    }

    @Test
    void reassignRejectsBlankReason() {
        UUID taskId = UUID.randomUUID();

        assertThatThrownBy(() -> service.reassignTask(taskId, "user-2", "  ", "user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reassignRejectsCompletedTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.COMPLETED);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.reassignTask(task.getId(), "user-2", "workload rebalance", "user-1"))
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
