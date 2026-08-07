package ru.expertise.workflow.process;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.repository.ProcessDefinitionRepository;
import ru.expertise.workflow.repository.ProcessInstanceRepository;
import ru.expertise.workflow.routing.NoRouteMatchedException;
import ru.expertise.workflow.routing.RoutingDecision;
import ru.expertise.workflow.routing.RoutingEngine;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Runs process instances: start, advance from step to step as tasks are completed, cancel. */
@Service
public class ProcessInstanceService {

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final RoutingEngine routingEngine;
    private final TaskInstanceService taskInstanceService;

    public ProcessInstanceService(ProcessDefinitionRepository processDefinitionRepository,
                                   ProcessInstanceRepository processInstanceRepository,
                                   RoutingEngine routingEngine,
                                   TaskInstanceService taskInstanceService) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.routingEngine = routingEngine;
        this.taskInstanceService = taskInstanceService;
    }

    /** Starts a process by its published template and opens the first task the routing rules choose. */
    @Transactional
    public ProcessInstance startInstance(UUID processDefinitionId, String businessKey, Map<String, Object> attributes) {
        ProcessDefinition definition = processDefinitionRepository.findById(processDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("ProcessDefinition " + processDefinitionId + " not found"));
        if (definition.getStatus() != ProcessDefinitionStatus.PUBLISHED) {
            throw new ProcessDefinitionNotPublishedException(processDefinitionId);
        }

        ProcessInstance instance = new ProcessInstance();
        instance.setProcessDefinition(definition);
        instance.setProcessVersion(definition.getVersion());
        instance.setBusinessKey(businessKey);
        instance.setStatus(ProcessInstanceStatus.RUNNING);
        instance.setStartedAt(Instant.now());
        instance = processInstanceRepository.save(instance);

        RoutingDecision decision = routingEngine.resolve(definition.getId(), attributes);
        instance.setCurrentStepCode(decision.targetStepCode());
        instance = processInstanceRepository.save(instance);

        taskInstanceService.createTask(instance, decision);
        return instance;
    }

    /**
     * Completes the given task and either advances the process to the next routed step or, when no
     * rule matches the outcome any more, treats the process as finished.
     */
    @Transactional
    public ProcessInstance completeTaskAndAdvance(UUID taskId, String actorId, Map<String, Object> outcomeAttributes) {
        TaskInstance completedTask = taskInstanceService.completeTask(taskId, actorId);
        ProcessInstance instance = get(completedTask.getProcessInstance().getId());

        try {
            RoutingDecision decision = routingEngine.resolve(instance.getProcessDefinition().getId(), outcomeAttributes);
            instance.setCurrentStepCode(decision.targetStepCode());
            instance = processInstanceRepository.save(instance);
            taskInstanceService.createTask(instance, decision);
            return instance;
        } catch (NoRouteMatchedException noNextStep) {
            return transitionStatus(instance.getId(), ProcessInstanceStatus.COMPLETED);
        }
    }

    @Transactional
    public ProcessInstance transitionStatus(UUID instanceId, ProcessInstanceStatus target) {
        ProcessInstance instance = get(instanceId);
        if (!instance.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(instance.getStatus().name(), target.name());
        }
        instance.setStatus(target);
        if (target == ProcessInstanceStatus.COMPLETED || target == ProcessInstanceStatus.CANCELLED) {
            instance.setCompletedAt(Instant.now());
        }
        return processInstanceRepository.save(instance);
    }

    public ProcessInstance cancel(UUID instanceId) {
        return transitionStatus(instanceId, ProcessInstanceStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public ProcessInstance get(UUID instanceId) {
        return processInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new EntityNotFoundException("ProcessInstance " + instanceId + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<ProcessInstance> search(Specification<ProcessInstance> spec, Pageable pageable) {
        return processInstanceRepository.findAll(spec, pageable);
    }
}
