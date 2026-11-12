-- Applications, documents, and workflow runtime tables. Business logic arrives in later phases.

CREATE SEQUENCE application_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE applications (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number    varchar(30),
    citizen_id          uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    service_id          uuid         NOT NULL REFERENCES services (id) ON DELETE RESTRICT,
    service_version_id  uuid         NOT NULL REFERENCES service_versions (id) ON DELETE RESTRICT,
    department_id       uuid         NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    status              varchar(30)  NOT NULL,
    form_data           jsonb        NOT NULL DEFAULT '{}'::jsonb,
    submitted_at        timestamptz,
    decided_at          timestamptz,
    completed_at        timestamptz,
    withdrawn_at        timestamptz,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_applications_reference UNIQUE (reference_number),
    CONSTRAINT ck_applications_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'CLARIFICATION_REQUIRED', 'RESUBMITTED',
        'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'WITHDRAWN')),
    CONSTRAINT ck_applications_reference_after_submit CHECK (
        status IN ('DRAFT') OR reference_number IS NOT NULL OR status = 'WITHDRAWN')
);
CREATE INDEX ix_applications_citizen_created ON applications (citizen_id, created_at DESC);
CREATE INDEX ix_applications_department_status ON applications (department_id, status, submitted_at);

CREATE TABLE application_status_history (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid         NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status     varchar(30),
    to_status       varchar(30)  NOT NULL,
    command         varchar(50)  NOT NULL,
    actor_id        uuid         REFERENCES users (id) ON DELETE SET NULL,
    reason          varchar(2000),
    citizen_visible boolean      NOT NULL DEFAULT true,
    occurred_at     timestamptz  NOT NULL
);
CREATE INDEX ix_application_status_history_app ON application_status_history (application_id, occurred_at);

CREATE TABLE application_assignments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    assignee_id     uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assigned_by     uuid        REFERENCES users (id) ON DELETE SET NULL,
    assigned_at     timestamptz NOT NULL,
    released_at     timestamptz,
    active          boolean     NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX uq_application_assignments_active ON application_assignments (application_id) WHERE active;
CREATE INDEX ix_application_assignments_assignee ON application_assignments (assignee_id, active);

CREATE TABLE application_comments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid          NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    author_id       uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    visibility      varchar(20)   NOT NULL,
    body            varchar(4000) NOT NULL,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ck_application_comments_visibility CHECK (visibility IN ('INTERNAL', 'PUBLIC'))
);
CREATE INDEX ix_application_comments_app ON application_comments (application_id, created_at);

CREATE TABLE application_decisions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid          NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    decision        varchar(30)   NOT NULL,
    reason_code     varchar(50),
    reason          varchar(2000),
    decided_by      uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    decided_at      timestamptz   NOT NULL,
    CONSTRAINT ck_application_decisions_decision CHECK (decision IN ('RECOMMENDED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_application_decisions_reject_reason CHECK (decision <> 'REJECTED' OR reason IS NOT NULL)
);

CREATE TABLE clarification_requests (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid          NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    requested_by    uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    question        varchar(2000) NOT NULL,
    due_at          timestamptz   NOT NULL,
    response        varchar(4000),
    responded_at    timestamptz,
    status          varchar(20)   NOT NULL,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ck_clarification_requests_status CHECK (status IN ('OPEN', 'RESPONDED', 'EXPIRED', 'CANCELLED'))
);
CREATE INDEX ix_clarification_requests_app ON clarification_requests (application_id, status);

-- Documents: metadata only. Bytes live in private object storage under an opaque storage_key.
CREATE TABLE documents (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id    uuid         REFERENCES applications (id) ON DELETE RESTRICT,
    owner_id          uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    requirement_code  varchar(50),
    status            varchar(20)  NOT NULL,
    current_version   integer      NOT NULL DEFAULT 1,
    verified_by       uuid         REFERENCES users (id) ON DELETE SET NULL,
    verified_at       timestamptz,
    rejection_reason  varchar(1000),
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_documents_status CHECK (status IN ('UPLOADED', 'QUARANTINED', 'SCANNING', 'AVAILABLE', 'VERIFIED', 'REJECTED'))
);
CREATE INDEX ix_documents_application ON documents (application_id);
CREATE INDEX ix_documents_owner ON documents (owner_id, created_at DESC);

CREATE TABLE document_versions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         uuid          NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    version_number      integer       NOT NULL,
    storage_key         varchar(200)  NOT NULL,
    original_filename   varchar(255)  NOT NULL,
    detected_media_type varchar(100)  NOT NULL,
    size_bytes          bigint        NOT NULL,
    checksum            varchar(100)  NOT NULL,
    scan_status         varchar(20)   NOT NULL,
    uploaded_by         uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    uploaded_at         timestamptz   NOT NULL,
    CONSTRAINT uq_document_versions UNIQUE (document_id, version_number),
    CONSTRAINT uq_document_versions_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_document_versions_size CHECK (size_bytes > 0),
    CONSTRAINT ck_document_versions_scan CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED', 'SKIPPED'))
);

CREATE TABLE workflow_instances (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id         uuid        NOT NULL REFERENCES applications (id) ON DELETE RESTRICT,
    definition_version_id  uuid        NOT NULL REFERENCES workflow_definition_versions (id) ON DELETE RESTRICT,
    status                 varchar(20) NOT NULL,
    current_step_code      varchar(50),
    started_at             timestamptz NOT NULL,
    completed_at           timestamptz,
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_workflow_instances_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);
-- At most one active workflow per application (guards against duplicate submission).
CREATE UNIQUE INDEX uq_workflow_instances_active_app ON workflow_instances (application_id) WHERE status = 'ACTIVE';

CREATE TABLE workflow_tasks (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_instance_id   uuid         NOT NULL REFERENCES workflow_instances (id) ON DELETE RESTRICT,
    application_id         uuid         NOT NULL REFERENCES applications (id) ON DELETE RESTRICT,
    department_id          uuid         NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    step_code              varchar(50)  NOT NULL,
    status                 varchar(20)  NOT NULL,
    assignee_id            uuid         REFERENCES users (id) ON DELETE SET NULL,
    due_at                 timestamptz,
    sla_policy_version_id  uuid         REFERENCES sla_policy_versions (id) ON DELETE RESTRICT,
    outcome                varchar(30),
    created_at             timestamptz  NOT NULL,
    claimed_at             timestamptz,
    completed_at           timestamptz,
    version                bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_workflow_tasks_status CHECK (status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX ix_workflow_tasks_assignee ON workflow_tasks (assignee_id, status, due_at);
CREATE INDEX ix_workflow_tasks_department ON workflow_tasks (department_id, status, due_at);
CREATE UNIQUE INDEX uq_workflow_tasks_active_step ON workflow_tasks (workflow_instance_id, step_code)
    WHERE status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED');

CREATE TABLE workflow_history (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_instance_id  uuid        NOT NULL REFERENCES workflow_instances (id) ON DELETE CASCADE,
    task_id               uuid        REFERENCES workflow_tasks (id) ON DELETE SET NULL,
    command               varchar(50) NOT NULL,
    from_state            varchar(30),
    to_state              varchar(30) NOT NULL,
    actor_id              uuid        REFERENCES users (id) ON DELETE SET NULL,
    note                  varchar(2000),
    occurred_at           timestamptz NOT NULL
);
CREATE INDEX ix_workflow_history_instance ON workflow_history (workflow_instance_id, occurred_at);
