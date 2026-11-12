-- Notifications, audit log, SLA breaches, transactional outbox, idempotency records.

CREATE TABLE notifications (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type          varchar(50)   NOT NULL,
    title         varchar(200)  NOT NULL,
    body          varchar(2000) NOT NULL,
    resource_type varchar(50),
    resource_id   uuid,
    read_at       timestamptz,
    created_at    timestamptz   NOT NULL
);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, read_at, created_at DESC);

CREATE TABLE notification_preferences (
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel        varchar(20) NOT NULL,
    event_category varchar(50) NOT NULL,
    enabled        boolean     NOT NULL DEFAULT true,
    updated_at     timestamptz NOT NULL,
    PRIMARY KEY (user_id, channel, event_category),
    CONSTRAINT ck_notification_preferences_channel CHECK (channel IN ('IN_APP', 'EMAIL'))
);

-- Audit log: append-only for the application (UPDATE/DELETE rejected by trigger).
-- Retention/archival must run under a separate privileged database role.
CREATE TABLE audit_logs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at    timestamptz  NOT NULL,
    actor_id       uuid,
    actor_type     varchar(20)  NOT NULL,
    action         varchar(80)  NOT NULL,
    resource_type  varchar(50)  NOT NULL,
    resource_id    varchar(100),
    outcome        varchar(20)  NOT NULL,
    request_id     varchar(100),
    ip_address     varchar(45),
    user_agent     varchar(255),
    details        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_audit_logs_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'ANONYMOUS')),
    CONSTRAINT ck_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_action ON audit_logs (action, occurred_at DESC);

CREATE FUNCTION prevent_audit_log_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();

CREATE TABLE sla_breaches (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id                uuid        NOT NULL REFERENCES workflow_tasks (id) ON DELETE RESTRICT,
    sla_policy_version_id  uuid        NOT NULL REFERENCES sla_policy_versions (id) ON DELETE RESTRICT,
    escalation_level       integer     NOT NULL,
    breach_type            varchar(20) NOT NULL,
    idempotency_key        varchar(150) NOT NULL,
    detected_at            timestamptz NOT NULL,
    acknowledged_at        timestamptz,
    acknowledged_by        uuid        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_sla_breaches_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_sla_breaches_type CHECK (breach_type IN ('WARNING', 'BREACH'))
);

CREATE TABLE outbox_events (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type   varchar(50)  NOT NULL,
    aggregate_id     uuid         NOT NULL,
    event_type       varchar(100) NOT NULL,
    payload_version  integer      NOT NULL DEFAULT 1,
    payload          jsonb        NOT NULL,
    occurred_at      timestamptz  NOT NULL,
    status           varchar(20)  NOT NULL,
    attempts         integer      NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz  NOT NULL,
    published_at     timestamptz,
    last_error_code  varchar(100),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER'))
);
CREATE INDEX ix_outbox_events_status_next ON outbox_events (status, next_attempt_at);

-- Idempotency records protect create/submit commands from duplicate client retries.
CREATE TABLE idempotency_records (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id         uuid         NOT NULL,
    operation        varchar(100) NOT NULL,
    idempotency_key  varchar(100) NOT NULL,
    request_hash     varchar(64)  NOT NULL,
    response_status  integer,
    response_body    jsonb,
    resource_id      varchar(100),
    created_at       timestamptz  NOT NULL,
    expires_at       timestamptz  NOT NULL,
    CONSTRAINT uq_idempotency_records UNIQUE (actor_id, operation, idempotency_key)
);
CREATE INDEX ix_idempotency_records_expires ON idempotency_records (expires_at);
