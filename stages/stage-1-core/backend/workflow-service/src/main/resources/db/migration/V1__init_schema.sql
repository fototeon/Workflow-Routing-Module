-- Core workflow/routing schema (TZ-02-WORKFLOW): templates with routing rules, process instances
-- and the tasks they open.

CREATE TABLE process_definition (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(64) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 1,
    status         VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
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
                               CHECK (status IN ('NOT_STARTED', 'RUNNING', 'COMPLETED', 'CANCELLED')),
    current_step_code      VARCHAR(64),
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

CREATE TABLE task_instance (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    process_instance_id  UUID NOT NULL REFERENCES process_instance (id) ON DELETE CASCADE,
    step_code            VARCHAR(64) NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    assignee_id          VARCHAR(128),
    assignee_role        VARCHAR(64),
    status               VARCHAR(32) NOT NULL DEFAULT 'CREATED'
                             CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    completed_at         TIMESTAMPTZ,
    version              INTEGER NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           VARCHAR(128),
    updated_by           VARCHAR(128)
);

CREATE INDEX idx_task_instance_process ON task_instance (process_instance_id);
CREATE INDEX idx_task_instance_assignee ON task_instance (assignee_id, status);
