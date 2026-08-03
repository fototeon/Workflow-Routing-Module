package ru.expertise.workflow.process;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.expertise.workflow.audit.AuditContext;
import ru.expertise.workflow.audit.Audited;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.domain.ProcessEventLog;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.events.DomainEventType;
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.repository.ProcessDefinitionRepository;
import ru.expertise.workflow.repository.ProcessEventLogRepository;
import ru.expertise.workflow.repository.ProcessInstanceRepository;
import ru.expertise.workflow.routing.NoRouteMatchedException;
import ru.expertise.workflow.routing.RoutingDecision;
import ru.expertise.workflow.routing.RoutingEngine;
import ru.expertise.workflow.repository.TaskInstanceRepository;
import ru.expertise.workflow.security.AccessPolicy;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Note on internal calls: {@code @Audited}/{@code @Transactional} are Spring proxy-based AOP, which
 * does not intercept self-invocation (a method calling another method of the same instance via
 * {@code this}). Wherever this class needs to invoke one of its own annotated methods, it goes
 * through the injected {@code self} proxy instead of a direct {@code this.method(...)} call, so the
 * advice still applies.
 */
@Service
public class ProcessInstanceService {

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessEventLogRepository processEventLogRepository;
    private final RoutingEngine routingEngine;
    private final TaskInstanceService taskInstanceService;
    private final OutboxEventWriter outboxEventWriter;
    private final WorkflowProperties properties;
    private final TaskInstanceRepository taskInstanceRepository;
    private final AccessPolicy accessPolicy;
    private final ProcessInstanceService self;

    public ProcessInstanceService(ProcessDefinitionRepository processDefinitionRepository,
                                   ProcessInstanceRepository processInstanceRepository,
                                   ProcessEventLogRepository processEventLogRepository,
                                   RoutingEngine routingEngine,
                                   TaskInstanceService taskInstanceService,
                                   OutboxEventWriter outboxEventWriter,
                                   WorkflowProperties properties,
                                   TaskInstanceRepository taskInstanceRepository,
                                   AccessPolicy accessPolicy,
                                   @Lazy ProcessInstanceService self) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.processEventLogRepository = processEventLogRepository;
        this.routingEngine = routingEngine;
        this.taskInstanceService = taskInstanceService;
        this.outboxEventWriter = outboxEventWriter;
        this.properties = properties;
        this.taskInstanceRepository = taskInstanceRepository;
        this.accessPolicy = accessPolicy;
        this.self = self;
    }

    /**
     * Transactional so the definition (and its lazy {@code slaPolicy}) stays attached to the same
     * persistence context that {@link #createInstance} later reads it from — loading it outside a
     * transaction yields a detached proxy that blows up with {@code LazyInitializationException}.
     */
    @Transactional
    public ProcessInstance startInstance(UUID processDefinitionId, String businessKey,
                                          Map<String, Object> attributes, String correlationId) {
        ProcessDefinition definition = processDefinitionRepository.findById(processDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("ProcessDefinition " + processDefinitionId + " not found"));
        if (definition.getStatus() != ProcessDefinitionStatus.PUBLISHED) {
            throw new ProcessDefinitionNotPublishedException(processDefinitionId);
        }
        return self.createInstance(definition, businessKey, attributes, correlationId, null);
    }

    @Transactional
    public ProcessInstance startSubProcess(UUID parentInstanceId, UUID subProcessDefinitionId,
                                            String businessKey, Map<String, Object> attributes, String correlationId) {
        ProcessInstance parent = get(parentInstanceId);
        ProcessDefinition definition = processDefinitionRepository.findById(subProcessDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("ProcessDefinition " + subProcessDefinitionId + " not found"));
        if (definition.getStatus() != ProcessDefinitionStatus.PUBLISHED) {
            throw new ProcessDefinitionNotPublishedException(subProcessDefinitionId);
        }
        return self.createInstance(definition, businessKey, attributes, correlationId, parent);
    }

    @Transactional
    @Audited(DomainEventType.PROCESS_STARTED)
    public ProcessInstance createInstance(ProcessDefinition definition, String businessKey,
                                           Map<String, Object> attributes, String correlationId, ProcessInstance parent) {
        ProcessInstance instance = new ProcessInstance();
        instance.setProcessDefinition(definition);
        instance.setProcessVersion(definition.getVersion());
        instance.setBusinessKey(businessKey);
        instance.setStatus(ProcessInstanceStatus.RUNNING);
        instance.setStartedAt(Instant.now());
        instance.setParentInstance(parent);
        // Ownership attributes feed the access model (TZ §10); they come from the starting payload
        // because the organization/user directory itself lives outside this module (TZ §2).
        instance.setOwnerOrgId(attributeAsString(attributes, "organizationId", "organization"));
        instance.setOwnerUserId(attributeAsString(attributes, "ownerUserId", "applicantId"));
        instance = processInstanceRepository.save(instance);

        RoutingDecision decision = routingEngine.resolve(definition.getId(), attributes);
        instance.setCurrentStepCode(decision.targetStepCode());
        instance = processInstanceRepository.save(instance);

        taskInstanceService.createTask(instance, decision, definition.getSlaPolicy(), correlationId);

        AuditContext.processInstanceId(instance.getId());
        AuditContext.correlationId(correlationId);
        AuditContext.detail("businessKey", businessKey);
        AuditContext.detail("processDefinitionCode", definition.getCode());
        AuditContext.detail("stepCode", decision.targetStepCode());

        outboxEventWriter.enqueue("ProcessInstance", instance.getId().toString(), DomainEventType.PROCESS_STARTED,
                properties.getKafka().getTopicProcessEvents(), correlationId, businessKey,
                Map.of("processInstanceId", instance.getId(), "status", instance.getStatus().name(),
                        "stepCode", decision.targetStepCode()));

        return instance;
    }

    /** Completes the given task and either advances the process to the next routed step or completes it. */
    @Transactional
    public ProcessInstance completeTaskAndAdvance(UUID taskId, String actorId, Map<String, Object> outcomeAttributes,
                                                   String correlationId) {
        TaskInstance completedTask = taskInstanceService.completeTask(taskId, actorId);
        ProcessInstance instance = get(completedTask.getProcessInstance().getId());

        try {
            RoutingDecision decision = routingEngine.resolve(instance.getProcessDefinition().getId(), outcomeAttributes);
            instance.setCurrentStepCode(decision.targetStepCode());
            processInstanceRepository.save(instance);
            taskInstanceService.createTask(instance, decision, instance.getProcessDefinition().getSlaPolicy(), correlationId);
            return instance;
        } catch (NoRouteMatchedException noNextStep) {
            return self.transitionStatus(instance.getId(), ProcessInstanceStatus.COMPLETED, "No further routing step", actorId);
        }
    }

    @Transactional
    @Audited(DomainEventType.PROCESS_STATE_CHANGED)
    public ProcessInstance transitionStatus(UUID instanceId, ProcessInstanceStatus target, String reason, String actorId) {
        ProcessInstance instance = get(instanceId);
        if (!instance.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(instance.getStatus().name(), target.name());
        }
        ProcessInstanceStatus previous = instance.getStatus();
        instance.setStatus(target);
        if (target == ProcessInstanceStatus.COMPLETED || target == ProcessInstanceStatus.CANCELLED) {
            instance.setCompletedAt(Instant.now());
        }
        instance = processInstanceRepository.save(instance);

        if (target == ProcessInstanceStatus.CANCELLED) {
            // A cancelled process must not leave actionable tasks behind (TZ §5).
            taskInstanceService.cancelOpenTasks(instance.getId(), reason, actorId);
        }

        AuditContext.processInstanceId(instance.getId());
        AuditContext.detail("from", previous.name());
        AuditContext.detail("to", target.name());
        AuditContext.detail("reason", reason);
        AuditContext.detail("actorId", actorId);

        outboxEventWriter.enqueue("ProcessInstance", instance.getId().toString(), DomainEventType.PROCESS_STATE_CHANGED,
                properties.getKafka().getTopicProcessEvents(), null, instance.getBusinessKey(),
                Map.of("processInstanceId", instance.getId(), "from", previous.name(), "to", target.name()));

        return instance;
    }

    public ProcessInstance cancel(UUID instanceId, String reason, String actorId) {
        return self.transitionStatus(instanceId, ProcessInstanceStatus.CANCELLED, reason, actorId);
    }

    /**
     * Suspends the process and stops the SLA clock of its open tasks (TZ §2 "паузы"): the deadlines
     * are moved forward by the length of the pause when it resumes, so waiting on an external party
     * does not consume the SLA window.
     */
    @Transactional
    @Audited(DomainEventType.PROCESS_SUSPENDED)
    public ProcessInstance suspend(UUID instanceId, String reason, String actorId) {
        ProcessInstance instance = get(instanceId);
        if (!instance.getStatus().canTransitionTo(ProcessInstanceStatus.SUSPENDED)) {
            throw new InvalidStatusTransitionException(instance.getStatus().name(), ProcessInstanceStatus.SUSPENDED.name());
        }
        Instant now = Instant.now();
        ProcessInstanceStatus previous = instance.getStatus();
        instance.setStatus(ProcessInstanceStatus.SUSPENDED);
        instance.setSuspendedAt(now);
        instance = processInstanceRepository.save(instance);

        taskInstanceService.pauseSla(instance.getId(), now);

        AuditContext.processInstanceId(instance.getId());
        AuditContext.detail("from", previous.name());
        AuditContext.detail("reason", reason);
        AuditContext.detail("actorId", actorId);

        outboxEventWriter.enqueue("ProcessInstance", instance.getId().toString(), DomainEventType.PROCESS_SUSPENDED,
                properties.getKafka().getTopicProcessEvents(), null, instance.getBusinessKey(),
                Map.of("processInstanceId", instance.getId(), "from", previous.name(), "reason", reason));

        return instance;
    }

    /** Resumes a suspended process and shifts its task deadlines by the length of the pause. */
    @Transactional
    @Audited(DomainEventType.PROCESS_RESUMED)
    public ProcessInstance resume(UUID instanceId, String actorId) {
        ProcessInstance instance = get(instanceId);
        if (instance.getStatus() != ProcessInstanceStatus.SUSPENDED) {
            throw new InvalidStatusTransitionException(instance.getStatus().name(), ProcessInstanceStatus.RUNNING.name());
        }
        Instant now = Instant.now();
        instance.setStatus(ProcessInstanceStatus.RUNNING);
        instance.setSuspendedAt(null);
        instance = processInstanceRepository.save(instance);

        taskInstanceService.resumeSla(instance.getId(), now);

        AuditContext.processInstanceId(instance.getId());
        AuditContext.detail("to", ProcessInstanceStatus.RUNNING.name());
        AuditContext.detail("actorId", actorId);

        outboxEventWriter.enqueue("ProcessInstance", instance.getId().toString(), DomainEventType.PROCESS_RESUMED,
                properties.getKafka().getTopicProcessEvents(), null, instance.getBusinessKey(),
                Map.of("processInstanceId", instance.getId(), "to", ProcessInstanceStatus.RUNNING.name()));

        return instance;
    }

    private static String attributeAsString(Map<String, Object> attributes, String... keys) {
        if (attributes == null) {
            return null;
        }
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public ProcessInstance get(UUID instanceId) {
        return processInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new EntityNotFoundException("ProcessInstance " + instanceId + " not found"));
    }

    /** Loads an instance only if the access model lets the current user see it (TZ §10). */
    @Transactional(readOnly = true)
    public ProcessInstance getForCurrentUser(UUID instanceId) {
        ProcessInstance instance = get(instanceId);
        if (!accessPolicy.canSee(instance, taskInstanceRepository.findByProcessInstanceId(instanceId))) {
            throw new AccessDeniedException("Process instance " + instanceId + " is not visible for the current user");
        }
        return instance;
    }

    @Transactional(readOnly = true)
    public Page<ProcessInstance> search(Specification<ProcessInstance> spec, Pageable pageable) {
        return processInstanceRepository.findAll(spec.and(accessPolicy.visibleInstances()), pageable);
    }

    /**
     * Rows for the CSV export (TZ §9), flattened inside the transaction — the controller would
     * otherwise dereference lazy associations on detached entities.
     */
    @Transactional(readOnly = true)
    public List<List<String>> exportRows(Specification<ProcessInstance> spec) {
        return processInstanceRepository.findAll(spec.and(accessPolicy.visibleInstances())).stream()
                .map(instance -> List.of(
                        instance.getBusinessKey(),
                        instance.getProcessDefinition().getCode(),
                        String.valueOf(instance.getProcessVersion()),
                        instance.getStatus().name(),
                        instance.getCurrentStepCode() == null ? "" : instance.getCurrentStepCode(),
                        instance.getStartedAt() == null ? "" : instance.getStartedAt().toString(),
                        instance.getCompletedAt() == null ? "" : instance.getCompletedAt().toString(),
                        instance.getCreatedBy() == null ? "" : instance.getCreatedBy()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProcessEventLog> getEventLog(UUID instanceId) {
        return processEventLogRepository.findByProcessInstanceIdOrderByOccurredAtAsc(instanceId);
    }
}
