package ru.expertise.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

import java.util.UUID;

/**
 * A single conditional-routing rule attached to a {@link ProcessDefinition}. {@code conditionTree}
 * holds the JSON condition tree (group/condition nodes) evaluated by the routing engine; rules are
 * evaluated in ascending {@code priority} order and the first match wins.
 */
@Entity
@Table(name = "routing_rule")
public class RoutingRule extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_definition_id", nullable = false)
    private ProcessDefinition processDefinition;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int priority = 0;

    @Type(JsonType.class)
    @Column(name = "condition_tree", columnDefinition = "jsonb", nullable = false)
    private JsonNode conditionTree;

    @Column(name = "target_step_code", nullable = false)
    private String targetStepCode;

    @Column(name = "target_role")
    private String targetRole;

    public UUID getId() {
        return id;
    }

    public ProcessDefinition getProcessDefinition() {
        return processDefinition;
    }

    public void setProcessDefinition(ProcessDefinition processDefinition) {
        this.processDefinition = processDefinition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public JsonNode getConditionTree() {
        return conditionTree;
    }

    public void setConditionTree(JsonNode conditionTree) {
        this.conditionTree = conditionTree;
    }

    public String getTargetStepCode() {
        return targetStepCode;
    }

    public void setTargetStepCode(String targetStepCode) {
        this.targetStepCode = targetStepCode;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }
}
