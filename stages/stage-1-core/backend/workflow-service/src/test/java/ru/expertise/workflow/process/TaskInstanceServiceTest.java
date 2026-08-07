package ru.expertise.workflow.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.repository.TaskInstanceRepository;

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

    private TaskInstanceService service;

    @BeforeEach
    void setUp() {
        service = new TaskInstanceService(taskInstanceRepository);
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

    private static void assignId(Object entity) throws Exception {
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

        TaskInstance completed = service.completeTask(task.getId(), "user-1");

        assertThat(completed.getStatus()).isEqualTo(TaskInstanceStatus.COMPLETED);
        assertThat(completed.getAssigneeId()).isEqualTo("user-1");
    }

    @Test
    void completeRejectsAlreadyCompletedTask() throws Exception {
        TaskInstance task = taskWithStatus(TaskInstanceStatus.COMPLETED);
        when(taskInstanceRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.completeTask(task.getId(), "user-1"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
