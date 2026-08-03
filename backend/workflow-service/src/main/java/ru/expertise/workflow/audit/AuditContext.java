package ru.expertise.workflow.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-thread scratch space that an {@link Audited} service method fills in while it executes;
 * {@code AuditAspect} reads and clears it right after the method returns successfully.
 */
public final class AuditContext {

    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void processInstanceId(UUID id) {
        entry().processInstanceId = id;
    }

    public static void taskInstanceId(UUID id) {
        entry().taskInstanceId = id;
    }

    /** Addresses a journal entry that has no process/task behind it — a template, rule or SLA policy. */
    public static void subject(String type, Object id) {
        entry().subjectType = type;
        entry().subjectId = String.valueOf(id);
    }

    public static void correlationId(String id) {
        entry().correlationId = id;
    }

    public static void detail(String key, Object value) {
        entry().details.put(key, value);
    }

    static Entry snapshotAndClear() {
        Entry snapshot = CURRENT.get();
        CURRENT.remove();
        return snapshot == null ? new Entry() : snapshot;
    }

    static void clear() {
        CURRENT.remove();
    }

    private static Entry entry() {
        Entry entry = CURRENT.get();
        if (entry == null) {
            entry = new Entry();
            CURRENT.set(entry);
        }
        return entry;
    }

    public static final class Entry {
        private UUID processInstanceId;
        private UUID taskInstanceId;
        private String subjectType;
        private String subjectId;
        private String correlationId;
        private final Map<String, Object> details = new HashMap<>();

        public UUID getProcessInstanceId() {
            return processInstanceId;
        }

        public UUID getTaskInstanceId() {
            return taskInstanceId;
        }

        public String getSubjectType() {
            return subjectType;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }
}
