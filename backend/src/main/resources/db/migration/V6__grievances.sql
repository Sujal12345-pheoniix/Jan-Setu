-- Grievance management tables. Business logic arrives in a later phase.

CREATE SEQUENCE grievance_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE grievances (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number  varchar(30)   NOT NULL,
    citizen_id        uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    department_id     uuid          NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    application_id    uuid          REFERENCES applications (id) ON DELETE SET NULL,
    category          varchar(50)   NOT NULL,
    subject           varchar(200)  NOT NULL,
    description       varchar(4000) NOT NULL,
    status            varchar(30)   NOT NULL,
    priority          varchar(10)   NOT NULL DEFAULT 'NORMAL',
    assignee_id       uuid          REFERENCES users (id) ON DELETE SET NULL,
    resolved_at       timestamptz,
    appeal_deadline   timestamptz,
    closed_at         timestamptz,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_grievances_reference UNIQUE (reference_number),
    CONSTRAINT ck_grievances_status CHECK (status IN (
        'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESPONSE_PROVIDED', 'RESOLVED', 'APPEALED', 'APPEAL_REVIEW', 'CLOSED')),
    CONSTRAINT ck_grievances_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);
CREATE INDEX ix_grievances_department_status ON grievances (department_id, status, created_at);
CREATE INDEX ix_grievances_citizen ON grievances (citizen_id, created_at DESC);

CREATE TABLE grievance_assignments (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    grievance_id  uuid        NOT NULL REFERENCES grievances (id) ON DELETE CASCADE,
    assignee_id   uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    assigned_by   uuid        REFERENCES users (id) ON DELETE SET NULL,
    assigned_at   timestamptz NOT NULL,
    released_at   timestamptz
);
CREATE INDEX ix_grievance_assignments_grievance ON grievance_assignments (grievance_id, assigned_at);

-- Internal notes and public responses share a table but are separated by visibility.
CREATE TABLE grievance_comments (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    grievance_id  uuid          NOT NULL REFERENCES grievances (id) ON DELETE CASCADE,
    author_id     uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    visibility    varchar(20)   NOT NULL,
    body          varchar(4000) NOT NULL,
    created_at    timestamptz   NOT NULL,
    CONSTRAINT ck_grievance_comments_visibility CHECK (visibility IN ('INTERNAL', 'PUBLIC'))
);
CREATE INDEX ix_grievance_comments_grievance ON grievance_comments (grievance_id, created_at);

CREATE TABLE grievance_feedback (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    grievance_id  uuid          NOT NULL REFERENCES grievances (id) ON DELETE CASCADE,
    rating        smallint      NOT NULL,
    comment       varchar(2000),
    submitted_at  timestamptz   NOT NULL,
    CONSTRAINT uq_grievance_feedback UNIQUE (grievance_id),
    CONSTRAINT ck_grievance_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
