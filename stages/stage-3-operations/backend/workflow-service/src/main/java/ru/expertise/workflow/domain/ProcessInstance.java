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
@Table(name = "process_instance")
public class ProcessInstance extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_definition_id", nullable = false)
    private ProcessDefinition processDefinition;

    @Column(name = "process_version", nullable = false)
    private int processVersion;

    @Column(name = "business_key", nullable = false)
    private String businessKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessInstanceStatus status = ProcessInstanceStatus.NOT_STARTED;

    @Column(name = "current_step_code")
    private String currentStepCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_instance_id")
    private ProcessInstance parentInstance;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Organization owning the case, taken from the start attributes — input to the access model (TZ §10). */
    @Column(name = "owner_org_id")
    private String ownerOrgId;

    /** User the case belongs to, taken from the start attributes — input to the access model (TZ §10). */
    @Column(name = "owner_user_id")
    private String ownerUserId;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Version
    private int version;

    public UUID getId() {
        return id;
    }

    public ProcessDefinition getProcessDefinition() {
        return processDefinition;
    }

    public void setProcessDefinition(ProcessDefinition processDefinition) {
        this.processDefinition = processDefinition;
    }

    public int getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(int processVersion) {
        this.processVersion = processVersion;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public ProcessInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessInstanceStatus status) {
        this.status = status;
    }

    public String getCurrentStepCode() {
        return currentStepCode;
    }

    public void setCurrentStepCode(String currentStepCode) {
        this.currentStepCode = currentStepCode;
    }

    public ProcessInstance getParentInstance() {
        return parentInstance;
    }

    public void setParentInstance(ProcessInstance parentInstance) {
        this.parentInstance = parentInstance;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getOwnerOrgId() {
        return ownerOrgId;
    }

    public void setOwnerOrgId(String ownerOrgId) {
        this.ownerOrgId = ownerOrgId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(Instant suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public int getVersion() {
        return version;
    }
}
