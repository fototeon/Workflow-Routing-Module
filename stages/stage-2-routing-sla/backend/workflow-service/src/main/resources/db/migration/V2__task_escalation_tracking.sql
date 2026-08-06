ALTER TABLE task_instance
    ADD COLUMN last_escalated_percent INTEGER NOT NULL DEFAULT 0;
