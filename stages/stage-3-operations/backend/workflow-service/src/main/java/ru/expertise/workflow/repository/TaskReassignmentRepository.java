package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.expertise.workflow.domain.TaskReassignment;

import java.util.List;
import java.util.UUID;

public interface TaskReassignmentRepository extends JpaRepository<TaskReassignment, UUID> {

    List<TaskReassignment> findByTaskInstanceIdOrderByOccurredAtDesc(UUID taskInstanceId);
}
