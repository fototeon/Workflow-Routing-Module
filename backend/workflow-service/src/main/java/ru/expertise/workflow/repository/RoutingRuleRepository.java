package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.expertise.workflow.domain.RoutingRule;

import java.util.List;
import java.util.UUID;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {

    List<RoutingRule> findByProcessDefinitionIdOrderByPriorityAsc(UUID processDefinitionId);
}
