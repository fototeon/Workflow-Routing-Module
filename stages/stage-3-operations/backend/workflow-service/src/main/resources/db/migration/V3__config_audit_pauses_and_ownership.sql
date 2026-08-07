-- Configuration audit (TZ §10, REQ-02-002), process pauses (TZ §2) and ownership attributes
-- used by the attribute-based access model (TZ §10).

-- The journal previously only described process/task events; configuration changes (templates,
-- routing rules, SLA policies) have no instance to hang off, so they are addressed by subject.
ALTER TABLE process_event_log
    ADD COLUMN subject_type VARCHAR(64),
    ADD COLUMN subject_id   VARCHAR(128);

CREATE INDEX idx_process_event_log_subject ON process_event_log (subject_type, subject_id, occurred_at);

ALTER TABLE process_instance
    ADD COLUMN owner_org_id  VARCHAR(128),
    ADD COLUMN owner_user_id VARCHAR(128),
    ADD COLUMN suspended_at  TIMESTAMPTZ;

CREATE INDEX idx_process_instance_owner ON process_instance (owner_org_id, owner_user_id);

-- While a process is suspended its tasks' SLA clock stops; the deadline is shifted forward by the
-- length of the pause when the process resumes.
ALTER TABLE task_instance
    ADD COLUMN sla_paused_at TIMESTAMPTZ;
