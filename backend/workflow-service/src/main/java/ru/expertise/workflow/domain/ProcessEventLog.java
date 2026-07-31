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

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only process journal (TZ-02-WORKFLOW section 4/5/10): every domain event and audit-relevant
 * action is recorded here, keyed by process instance and optionally by task instance.
 */
@Entity
@Table(name = "process_event_log")
public class ProcessEventLog {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_instance_id")
    private ProcessInstance processInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_instance_id")
    private TaskInstance taskInstance;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public void setProcessInstance(ProcessInstance processInstance) {
        this.processInstance = processInstance;
    }

    public TaskInstance getTaskInstance() {
        return taskInstance;
    }

    public void setTaskInstance(TaskInstance taskInstance) {
        this.taskInstance = taskInstance;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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
