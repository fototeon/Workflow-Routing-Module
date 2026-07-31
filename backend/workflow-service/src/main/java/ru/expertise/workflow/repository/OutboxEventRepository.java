package ru.expertise.workflow.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.expertise.workflow.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
