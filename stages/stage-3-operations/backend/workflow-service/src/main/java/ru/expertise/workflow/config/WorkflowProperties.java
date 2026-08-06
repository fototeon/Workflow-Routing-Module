package ru.expertise.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.List;

@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    private final Kafka kafka = new Kafka();
    private final Outbox outbox = new Outbox();
    private final Sla sla = new Sla();
    private final Access access = new Access();

    public Kafka getKafka() {
        return kafka;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Sla getSla() {
        return sla;
    }

    public Access getAccess() {
        return access;
    }

    public static class Kafka {
        private String topicProcessEvents;
        private String topicTaskEvents;
        private String topicSlaEvents;
        private String topicDlq;
        private String topicRequestAccepted;
        private String topicDefinitionEvents;
        private String topicNotifications;

        public String getTopicProcessEvents() {
            return topicProcessEvents;
        }

        public void setTopicProcessEvents(String topicProcessEvents) {
            this.topicProcessEvents = topicProcessEvents;
        }

        public String getTopicTaskEvents() {
            return topicTaskEvents;
        }

        public void setTopicTaskEvents(String topicTaskEvents) {
            this.topicTaskEvents = topicTaskEvents;
        }

        public String getTopicSlaEvents() {
            return topicSlaEvents;
        }

        public void setTopicSlaEvents(String topicSlaEvents) {
            this.topicSlaEvents = topicSlaEvents;
        }

        public String getTopicDlq() {
            return topicDlq;
        }

        public void setTopicDlq(String topicDlq) {
            this.topicDlq = topicDlq;
        }

        public String getTopicRequestAccepted() {
            return topicRequestAccepted;
        }

        public void setTopicRequestAccepted(String topicRequestAccepted) {
            this.topicRequestAccepted = topicRequestAccepted;
        }

        public String getTopicDefinitionEvents() {
            return topicDefinitionEvents;
        }

        public void setTopicDefinitionEvents(String topicDefinitionEvents) {
            this.topicDefinitionEvents = topicDefinitionEvents;
        }

        public String getTopicNotifications() {
            return topicNotifications;
        }

        public void setTopicNotifications(String topicNotifications) {
            this.topicNotifications = topicNotifications;
        }
    }

    public static class Outbox {
        private long pollIntervalMs = 2000;
        private int batchSize = 50;
        private int maxAttempts = 5;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    public static class Sla {
        private long scanIntervalMs = 30000;
        /** Non-working days excluded from business-hours SLA windows (TZ REQ-02-005, "календарями"). */
        private List<LocalDate> holidays = List.of();

        public long getScanIntervalMs() {
            return scanIntervalMs;
        }

        public void setScanIntervalMs(long scanIntervalMs) {
            this.scanIntervalMs = scanIntervalMs;
        }

        public List<LocalDate> getHolidays() {
            return holidays;
        }

        public void setHolidays(List<LocalDate> holidays) {
            this.holidays = holidays == null ? List.of() : List.copyOf(holidays);
        }
    }

    /** Attribute-based access model (TZ §10): who may see which process instances and tasks. */
    public static class Access {
        /** Roles exempt from ownership filtering — management and reporting need the whole picture. */
        private List<String> fullAccessRoles = List.of("ADMIN", "MANAGER", "ANALYST");
        /** JWT claim carrying the user's organization, matched against a process instance's owner org. */
        private String organizationClaim = "organization";

        public List<String> getFullAccessRoles() {
            return fullAccessRoles;
        }

        public void setFullAccessRoles(List<String> fullAccessRoles) {
            this.fullAccessRoles = fullAccessRoles == null ? List.of() : List.copyOf(fullAccessRoles);
        }

        public String getOrganizationClaim() {
            return organizationClaim;
        }

        public void setOrganizationClaim(String organizationClaim) {
            this.organizationClaim = organizationClaim;
        }
    }
}
