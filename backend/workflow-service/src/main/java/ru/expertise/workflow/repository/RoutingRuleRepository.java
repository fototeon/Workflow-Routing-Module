package ru.expertise.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.expertise.workflow.domain.RoutingRule;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {

    List<RoutingRule> findByProcessDefinitionIdOrderByPriorityAsc(UUID processDefinitionId);

    /**
     * Rule counts for a whole page of templates in one query — the template list shows them so an
     * administrator can see at a glance which drafts are still missing routing rules.
     * Returns {definitionId, count} pairs; templates without rules are simply absent.
     */
    @Query("select r.processDefinition.id, count(r) from RoutingRule r "
            + "where r.processDefinition.id in :definitionIds group by r.processDefinition.id")
    List<Object[]> countByProcessDefinitionIds(@Param("definitionIds") Collection<UUID> definitionIds);
}
