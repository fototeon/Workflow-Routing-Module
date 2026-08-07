package ru.expertise.workflow.process;

import org.springframework.data.jpa.domain.Specification;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;

public final class ProcessInstanceSpecifications {

    private ProcessInstanceSpecifications() {
    }

    public static Specification<ProcessInstance> filter(ProcessInstanceStatus status, String businessKey, String processDefinitionCode) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (businessKey != null && !businessKey.isBlank()) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("businessKey")), "%" + businessKey.toLowerCase() + "%"));
            }
            if (processDefinitionCode != null && !processDefinitionCode.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("processDefinition").get("code"), processDefinitionCode));
            }
            return predicate;
        };
    }
}
