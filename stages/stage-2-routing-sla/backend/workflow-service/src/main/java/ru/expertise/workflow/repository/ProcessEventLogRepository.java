package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.expertise.workflow.domain.ProcessEventLog;

import java.util.List;
import java.util.UUID;

public interface ProcessEventLogRepository extends JpaRepository<ProcessEventLog, UUID> {

    List<ProcessEventLog> findByProcessInstanceIdOrderByOccurredAtAsc(UUID processInstanceId);

    /** Journal of a configuration subject (template, SLA policy) — entries have no process instance. */
    List<ProcessEventLog> findBySubjectTypeAndSubjectIdOrderByOccurredAtAsc(String subjectType, String subjectId);
}
