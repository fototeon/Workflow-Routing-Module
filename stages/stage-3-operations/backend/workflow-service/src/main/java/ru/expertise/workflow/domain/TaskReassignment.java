package ru.expertise.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** History record of a task reassignment, always carrying the mandatory reason (REQ-02-007). */
@Entity
@Table(name = "task_reassignment")
public class TaskReassignment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_instance_id", nullable = false)
    private TaskInstance taskInstance;

    @Column(name = "from_assignee")
    private String fromAssignee;

    /** Exactly one of the two is set: a person takes the task, or it goes back to a role queue. */
    @Column(name = "to_assignee")
    private String toAssignee;

    @Column(name = "to_role")
    private String toRole;

    @Column(nullable = false)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public TaskInstance getTaskInstance() {
        return taskInstance;
    }

    public void setTaskInstance(TaskInstance taskInstance) {
        this.taskInstance = taskInstance;
    }

    public String getFromAssignee() {
        return fromAssignee;
    }

    public void setFromAssignee(String fromAssignee) {
        this.fromAssignee = fromAssignee;
    }

    public String getToAssignee() {
        return toAssignee;
    }

    public void setToAssignee(String toAssignee) {
        this.toAssignee = toAssignee;
    }

    public String getToRole() {
        return toRole;
    }

    public void setToRole(String toRole) {
        this.toRole = toRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
