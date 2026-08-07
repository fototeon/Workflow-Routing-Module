package ru.expertise.workflow.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;
import ru.expertise.workflow.domain.ProcessInstance;
import ru.expertise.workflow.domain.ProcessInstanceStatus;
import ru.expertise.workflow.repository.ProcessDefinitionRepository;
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
    private RoutingEngine routingEngine;
    @Mock
    private TaskInstanceService taskInstanceService;

    private ProcessInstanceService service;

    @BeforeEach
    void setUp() {
        service = new ProcessInstanceService(processDefinitionRepository, processInstanceRepository,
                routingEngine, taskInstanceService);
    }

    /** Persisted entities always carry a generated id; unit tests fake it. */
    private static void assignId(Object entity) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, UUID.randomUUID());
    }

    @Test
    void rejectsStartingFromDraftDefinition() throws Exception {
        ProcessDefinition draft = new ProcessDefinition();
        draft.setStatus(ProcessDefinitionStatus.DRAFT);
        assignId(draft);
        when(processDefinitionRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.startInstance(draft.getId(), "REQ-1", Map.of()))
                .isInstanceOf(ProcessDefinitionNotPublishedException.class);
    }

    @Test
    void rejectsInvalidManualTransition() throws Exception {
        ProcessInstance instance = new ProcessInstance();
        instance.setStatus(ProcessInstanceStatus.COMPLETED);
        assignId(instance);
        when(processInstanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> service.transitionStatus(instance.getId(), ProcessInstanceStatus.RUNNING))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
