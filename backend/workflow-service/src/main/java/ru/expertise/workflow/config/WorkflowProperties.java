package ru.expertise.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    private final Kafka kafka = new Kafka();
    private final Outbox outbox = new Outbox();
    private final Sla sla = new Sla();

    public Kafka getKafka() {
        return kafka;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Sla getSla() {
        return sla;
    }

    public static class Kafka {
        private String topicProcessEvents;
        private String topicTaskEvents;
        private String topicSlaEvents;
        private String topicDlq;
        private String topicRequestAccepted;

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

        public long getScanIntervalMs() {
            return scanIntervalMs;
        }

        public void setScanIntervalMs(long scanIntervalMs) {
            this.scanIntervalMs = scanIntervalMs;
        }
    }
}
