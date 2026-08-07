package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.expertise.workflow.domain.TaskInstance;
import ru.expertise.workflow.domain.TaskInstanceStatus;

import java.util.List;
import java.util.UUID;

public interface TaskInstanceRepository
        extends JpaRepository<TaskInstance, UUID>, JpaSpecificationExecutor<TaskInstance> {

    List<TaskInstance> findByProcessInstanceId(UUID processInstanceId);

    @Query("""
            select t from TaskInstance t
            join fetch t.processInstance pi
            join fetch pi.processDefinition pd
            left join fetch pd.slaPolicy sp
            where t.status not in :terminalStatuses and t.dueAt is not null
            """)
    List<TaskInstance> findActiveTasksWithSla(@Param("terminalStatuses") List<TaskInstanceStatus> terminalStatuses);
}
