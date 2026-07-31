package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.expertise.workflow.domain.ProcessInstance;

import java.util.Optional;
import java.util.UUID;

public interface ProcessInstanceRepository
        extends JpaRepository<ProcessInstance, UUID>, JpaSpecificationExecutor<ProcessInstance> {

    Optional<ProcessInstance> findByBusinessKeyAndProcessDefinitionId(String businessKey, UUID processDefinitionId);
}
