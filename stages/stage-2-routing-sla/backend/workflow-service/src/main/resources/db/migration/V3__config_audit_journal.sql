-- Configuration audit (TZ §10, REQ-02-002): the journal previously only described process and task
-- events; changes to templates, routing rules and SLA policies have no instance to hang off, so
-- they are addressed by subject instead.
ALTER TABLE process_event_log
    ADD COLUMN subject_type VARCHAR(64),
    ADD COLUMN subject_id   VARCHAR(128);

CREATE INDEX idx_process_event_log_subject ON process_event_log (subject_type, subject_id, occurred_at);
