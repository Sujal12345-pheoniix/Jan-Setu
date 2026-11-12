-- Workflow and SLA configuration (versioned). Runtime behaviour is implemented in later phases;
-- the schema is created now so the data model is reviewed as a whole.

CREATE TABLE sla_policies (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id  uuid          NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    code           varchar(50)   NOT NULL,
    name           varchar(150)  NOT NULL,
    status         varchar(20)   NOT NULL,
    current_version integer      NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_sla_policies_code UNIQUE (code),
    CONSTRAINT ck_sla_policies_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
);

CREATE TABLE sla_policy_versions (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sla_policy_id            uuid        NOT NULL REFERENCES sla_policies (id) ON DELETE RESTRICT,
    version_number           integer     NOT NULL,
    target_duration_minutes  integer     NOT NULL,
    calendar_type            varchar(20) NOT NULL,
    warning_threshold_pct    integer     NOT NULL DEFAULT 80,
    pause_on_clarification   boolean     NOT NULL DEFAULT true,
    escalation_levels        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    published_at             timestamptz NOT NULL,
    published_by             uuid        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_sla_policy_versions UNIQUE (sla_policy_id, version_number),
    CONSTRAINT ck_sla_policy_versions_duration CHECK (target_duration_minutes > 0),
    CONSTRAINT ck_sla_policy_versions_calendar CHECK (calendar_type IN ('CALENDAR', 'BUSINESS')),
    CONSTRAINT ck_sla_policy_versions_warning CHECK (warning_threshold_pct BETWEEN 1 AND 100)
);

CREATE TABLE workflow_definitions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id  uuid         NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    code           varchar(50)  NOT NULL,
    name           varchar(150) NOT NULL,
    status         varchar(20)  NOT NULL,
    current_version integer     NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_definitions_code UNIQUE (code),
    CONSTRAINT ck_workflow_definitions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
);

CREATE TABLE workflow_definition_versions (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_definition_id  uuid        NOT NULL REFERENCES workflow_definitions (id) ON DELETE RESTRICT,
    version_number          integer     NOT NULL,
    transitions             jsonb       NOT NULL,
    published_at            timestamptz NOT NULL,
    published_by            uuid        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_workflow_definition_versions UNIQUE (workflow_definition_id, version_number)
);

CREATE TABLE workflow_steps (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_version_id    uuid         NOT NULL REFERENCES workflow_definition_versions (id) ON DELETE CASCADE,
    step_code                varchar(50)  NOT NULL,
    name                     varchar(150) NOT NULL,
    sequence_number          integer      NOT NULL,
    required_permission      varchar(100) NOT NULL,
    sla_policy_version_id    uuid         REFERENCES sla_policy_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_workflow_steps_code UNIQUE (definition_version_id, step_code),
    CONSTRAINT uq_workflow_steps_sequence UNIQUE (definition_version_id, sequence_number)
);

-- Link each service to the workflow it runs (configured in the workflow phase).
ALTER TABLE services ADD COLUMN workflow_definition_id uuid REFERENCES workflow_definitions (id) ON DELETE RESTRICT;
ALTER TABLE services ADD COLUMN sla_policy_id uuid REFERENCES sla_policies (id) ON DELETE RESTRICT;
