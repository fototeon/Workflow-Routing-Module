package ru.expertise.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Type;

import java.util.UUID;

@Entity
@Table(name = "sla_policy")
public class SlaPolicy extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "business_hours_only", nullable = false)
    private boolean businessHoursOnly = true;

    /**
     * List of {@code {"afterPercent": 80, "escalateToRole": "MANAGER"}} escalation steps,
     * evaluated in order against elapsed-time percentage of the SLA duration.
     */
    @Type(JsonType.class)
    @Column(name = "escalation_rules", columnDefinition = "jsonb", nullable = false)
    private JsonNode escalationRules;

    @Version
    private int version;

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isBusinessHoursOnly() {
        return businessHoursOnly;
    }

    public void setBusinessHoursOnly(boolean businessHoursOnly) {
        this.businessHoursOnly = businessHoursOnly;
    }

    public JsonNode getEscalationRules() {
        return escalationRules;
    }

    public void setEscalationRules(JsonNode escalationRules) {
        this.escalationRules = escalationRules;
    }

    public int getVersion() {
        return version;
    }
}
