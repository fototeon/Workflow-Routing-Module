package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.expertise.workflow.domain.TaskInstance;

import java.util.List;
import java.util.UUID;

public interface TaskInstanceRepository
        extends JpaRepository<TaskInstance, UUID>, JpaSpecificationExecutor<TaskInstance> {

    List<TaskInstance> findByProcessInstanceId(UUID processInstanceId);
}
