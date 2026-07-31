package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.expertise.workflow.domain.ProcessEventLog;

import java.util.List;
import java.util.UUID;

public interface ProcessEventLogRepository extends JpaRepository<ProcessEventLog, UUID> {

    List<ProcessEventLog> findByProcessInstanceIdOrderByOccurredAtAsc(UUID processInstanceId);
}
