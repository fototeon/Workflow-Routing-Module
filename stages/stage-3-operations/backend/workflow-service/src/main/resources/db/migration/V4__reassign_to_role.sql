-- Reassignment may target a specific person or hand the task back to a role queue (TZ REQ-02-007).
ALTER TABLE task_reassignment
    ALTER COLUMN to_assignee DROP NOT NULL,
    ADD COLUMN to_role VARCHAR(64),
    ADD CONSTRAINT chk_task_reassignment_target
        CHECK ((to_assignee IS NOT NULL) <> (to_role IS NOT NULL));
