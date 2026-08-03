package ru.expertise.workflow.process;

import java.util.List;

/** Flattened rows for the CSV exports, resolved inside the persistence context (TZ §9). */
public final class ExportRows {

    private ExportRows() {
    }

    public static final List<String> PROCESS_HEADER = List.of(
            "businessKey", "processDefinitionCode", "processVersion", "status", "currentStepCode",
            "startedAt", "completedAt", "createdBy");

    public static final List<String> TASK_HEADER = List.of(
            "businessKey", "stepCode", "status", "assigneeId", "assigneeRole", "dueAt", "completedAt");
}
