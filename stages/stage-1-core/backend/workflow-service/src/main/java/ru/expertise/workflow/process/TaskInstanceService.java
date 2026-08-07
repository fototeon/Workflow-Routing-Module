package ru.expertise.workflow.process;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;
import ru.expertise.workflow.repository.TaskInstanceRepository;
import ru.expertise.workflow.routing.RoutingDecision;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskInstanceService {

    private final TaskInstanceRepository taskInstanceRepository;

    public TaskInstanceService(TaskInstanceRepository taskInstanceRepository) {
        this.taskInstanceRepository = taskInstanceRepository;
    }

    /** Creates the task the routing decision points at, addressed to a role rather than a person. */
    @Transactional
    public TaskInstance createTask(ProcessInstance processInstance, RoutingDecision decision) {
        TaskInstance task = new TaskInstance();
        task.setProcessInstance(processInstance);
        task.setStepCode(decision.targetStepCode());
        task.setName(decision.targetStepCode());
        task.setAssigneeRole(decision.targetRole());
        task.setStatus(TaskInstanceStatus.CREATED);
        return taskInstanceRepository.save(task);
    }

    @Transactional
    public TaskInstance completeTask(UUID taskId, String actorId) {
        TaskInstance task = get(taskId);
        if (task.getStatus() == TaskInstanceStatus.CREATED) {
            // Completing an unclaimed task implicitly takes it into work first: CREATED -> COMPLETED
            // is not a legal transition on its own (see TaskInstanceStatus), and there is no separate
            // "take into work" action in the API.
            assertTransitionAllowed(task.getStatus(), TaskInstanceStatus.IN_PROGRESS);
            task.setStatus(TaskInstanceStatus.IN_PROGRESS);
        }
        assertTransitionAllowed(task.getStatus(), TaskInstanceStatus.COMPLETED);

        task.setStatus(TaskInstanceStatus.COMPLETED);
        task.setAssigneeId(actorId);
        task.setCompletedAt(Instant.now());
        return taskInstanceRepository.save(task);
    }

    @Transactional(readOnly = true)
    public TaskInstance get(UUID taskId) {
        return taskInstanceRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("TaskInstance " + taskId + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<TaskInstance> search(Specification<TaskInstance> spec, Pageable pageable) {
        return taskInstanceRepository.findAll(spec, pageable);
    }

    private void assertTransitionAllowed(TaskInstanceStatus from, TaskInstanceStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new InvalidStatusTransitionException(from.name(), to.name());
        }
    }
}
