package ru.expertise.workflow.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.expertise.workflow.config.WorkflowProperties;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.events.OutboxEventWriter;
import ru.expertise.workflow.repository.ProcessDefinitionRepository;
import ru.expertise.workflow.repository.ProcessEventLogRepository;
import ru.expertise.workflow.repository.ProcessInstanceRepository;
import ru.expertise.workflow.routing.RoutingEngine;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceServiceTest {

    @Mock
    private ProcessDefinitionRepository processDefinitionRepository;
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    @Mock
    private ProcessEventLogRepository processEventLogRepository;
    @Mock
    private RoutingEngine routingEngine;
    @Mock
    private TaskInstanceService taskInstanceService;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private ru.expertise.workflow.repository.TaskInstanceRepository taskInstanceRepository;
    @Mock
    private ru.expertise.workflow.security.AccessPolicy accessPolicy;

    private ProcessInstanceService service;

    @BeforeEach
    void setUp() {
        service = new ProcessInstanceService(processDefinitionRepository, processInstanceRepository,
                processEventLogRepository, routingEngine, taskInstanceService, outboxEventWriter,
                new WorkflowProperties(), taskInstanceRepository, accessPolicy, null);
    }

    private ProcessDefinition definitionWithStatus(ProcessDefinitionStatus status) throws Exception {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setStatus(status);
        Field idField = ProcessDefinition.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(definition, UUID.randomUUID());
        return definition;
    }

    @Test
    void rejectsStartingFromDraftDefinition() throws Exception {
        ProcessDefinition draft = definitionWithStatus(ProcessDefinitionStatus.DRAFT);
        when(processDefinitionRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.startInstance(draft.getId(), "REQ-1", Map.of(), "corr-1"))
                .isInstanceOf(ProcessDefinitionNotPublishedException.class);
    }

    @Test
    void rejectsInvalidManualTransition() throws Exception {
        ProcessInstance instance = new ProcessInstance();
        instance.setStatus(ProcessInstanceStatus.COMPLETED);
        Field idField = ProcessInstance.class.getDeclaredField("id");
        idField.setAccessible(true);
        UUID id = UUID.randomUUID();
        idField.set(instance, id);
        when(processInstanceRepository.findById(id)).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> service.transitionStatus(id, ProcessInstanceStatus.RUNNING, "retry", "user-1"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
