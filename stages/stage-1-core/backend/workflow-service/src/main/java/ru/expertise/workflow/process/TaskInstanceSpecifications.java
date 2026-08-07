package ru.expertise.workflow.process;

import org.springframework.data.jpa.domain.Specification;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;

import java.util.UUID;

public final class TaskInstanceSpecifications {

    private TaskInstanceSpecifications() {
    }

    public static Specification<TaskInstance> filter(TaskInstanceStatus status, String assigneeId,
                                                       String assigneeRole, UUID processInstanceId) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (assigneeRole != null && !assigneeRole.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("assigneeRole"), assigneeRole));
            }
            if (processInstanceId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("processInstance").get("id"), processInstanceId));
            }
            return predicate;
        };
    }
}
