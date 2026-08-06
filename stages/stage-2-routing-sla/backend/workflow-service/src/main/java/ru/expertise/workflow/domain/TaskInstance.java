package ru.expertise.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_instance")
public class TaskInstance extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_instance_id", nullable = false)
    private ProcessInstance processInstance;

    @Column(name = "step_code", nullable = false)
    private String stepCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "assignee_id")
    private String assigneeId;

    @Column(name = "assignee_role")
    private String assigneeRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskInstanceStatus status = TaskInstanceStatus.CREATED;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_escalated_percent", nullable = false)
    private int lastEscalatedPercent = 0;

    @Version
    private int version;

    public UUID getId() {
        return id;
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public void setProcessInstance(ProcessInstance processInstance) {
        this.processInstance = processInstance;
    }

    public String getStepCode() {
        return stepCode;
    }

    public void setStepCode(String stepCode) {
        this.stepCode = stepCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(String assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getAssigneeRole() {
        return assigneeRole;
    }

    public void setAssigneeRole(String assigneeRole) {
        this.assigneeRole = assigneeRole;
    }

    public TaskInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(TaskInstanceStatus status) {
        this.status = status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getLastEscalatedPercent() {
        return lastEscalatedPercent;
    }

    public void setLastEscalatedPercent(int lastEscalatedPercent) {
        this.lastEscalatedPercent = lastEscalatedPercent;
    }

    public int getVersion() {
        return version;
    }
}
