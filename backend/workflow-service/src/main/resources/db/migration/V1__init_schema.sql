-- Core workflow/routing schema (TZ-02-WORKFLOW)

CREATE TABLE sla_policy (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    duration_minutes    INTEGER NOT NULL CHECK (duration_minutes > 0),
    business_hours_only BOOLEAN NOT NULL DEFAULT TRUE,
    escalation_rules    JSONB NOT NULL DEFAULT '[]'::jsonb,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(128),
    updated_by          VARCHAR(128)
);

CREATE TABLE process_definition (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(64) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 1,
    status         VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    sla_policy_id  UUID REFERENCES sla_policy (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     VARCHAR(128),
    updated_by     VARCHAR(128),
    UNIQUE (code, version)
);

CREATE INDEX idx_process_definition_code ON process_definition (code);

CREATE TABLE routing_rule (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    process_definition_id  UUID NOT NULL REFERENCES process_definition (id) ON DELETE CASCADE,
    name                   VARCHAR(255) NOT NULL,
    priority               INTEGER NOT NULL DEFAULT 0,
    condition_tree         JSONB NOT NULL,
    target_step_code       VARCHAR(64) NOT NULL,
    target_role            VARCHAR(64),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             VARCHAR(128),
    updated_by             VARCHAR(128)
);

CREATE INDEX idx_routing_rule_definition ON routing_rule (process_definition_id, priority);

CREATE TABLE process_instance (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    process_definition_id  UUID NOT NULL REFERENCES process_definition (id),
    process_version        INTEGER NOT NULL,
    business_key           VARCHAR(128) NOT NULL,
    status                 VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED'
                               CHECK (status IN ('NOT_STARTED', 'RUNNING', 'WAITING_EXTERNAL',
                                                  'SUSPENDED', 'OVERDUE', 'COMPLETED', 'CANCELLED')),
    current_step_code      VARCHAR(64),
    parent_instance_id     UUID REFERENCES process_instance (id),
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    version                INTEGER NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             VARCHAR(128),
    updated_by             VARCHAR(128)
);

CREATE INDEX idx_process_instance_business_key ON process_instance (business_key);
CREATE INDEX idx_process_instance_status ON process_instance (status);
CREATE INDEX idx_process_instance_parent ON process_instance (parent_instance_id);

CREATE TABLE task_instance (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    process_instance_id  UUID NOT NULL REFERENCES process_instance (id) ON DELETE CASCADE,
    step_code            VARCHAR(64) NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    assignee_id          VARCHAR(128),
    assignee_role        VARCHAR(64),
    status               VARCHAR(32) NOT NULL DEFAULT 'CREATED'
                             CHECK (status IN ('CREATED', 'IN_PROGRESS', 'WAITING',
                                                'OVERDUE', 'COMPLETED', 'CANCELLED')),
    due_at               TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    version              INTEGER NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           VARCHAR(128),
    updated_by           VARCHAR(128)
);

CREATE INDEX idx_task_instance_process ON task_instance (process_instance_id);
CREATE INDEX idx_task_instance_assignee ON task_instance (assignee_id, status);
CREATE INDEX idx_task_instance_due_at ON task_instance (due_at) WHERE status NOT IN ('COMPLETED', 'CANCELLED');

CREATE TABLE task_reassignment (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_instance_id  UUID NOT NULL REFERENCES task_instance (id) ON DELETE CASCADE,
    from_assignee     VARCHAR(128),
    to_assignee       VARCHAR(128) NOT NULL,
    reason            VARCHAR(1000) NOT NULL,
    actor_id          VARCHAR(128) NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_reassignment_task ON task_reassignment (task_instance_id);

CREATE TABLE process_event_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    process_instance_id  UUID REFERENCES process_instance (id) ON DELETE CASCADE,
    task_instance_id     UUID REFERENCES task_instance (id),
    event_type           VARCHAR(64) NOT NULL,
    payload              JSONB NOT NULL DEFAULT '{}'::jsonb,
    correlation_id       VARCHAR(128),
    actor_id             VARCHAR(128),
    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_process_event_log_instance ON process_event_log (process_instance_id, occurred_at);
CREATE INDEX idx_process_event_log_correlation ON process_event_log (correlation_id);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(128) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    payload        JSONB NOT NULL,
    correlation_id VARCHAR(128),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER NOT NULL DEFAULT 0,
    last_error     VARCHAR(2000)
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
