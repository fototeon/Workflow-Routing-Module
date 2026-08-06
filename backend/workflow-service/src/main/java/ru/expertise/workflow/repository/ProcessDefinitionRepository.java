package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.expertise.workflow.domain.ProcessDefinition;
import ru.expertise.workflow.domain.ProcessDefinitionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, UUID> {

    List<ProcessDefinition> findByCodeOrderByVersionDesc(String code);

    List<ProcessDefinition> findByStatusOrderByCodeAscVersionDesc(ProcessDefinitionStatus status);

    List<ProcessDefinition> findAllByOrderByCodeAscVersionDesc();

    Optional<ProcessDefinition> findFirstByCodeAndStatusOrderByVersionDesc(String code, ProcessDefinitionStatus status);

    boolean existsByCodeAndVersion(String code, int version);
}
