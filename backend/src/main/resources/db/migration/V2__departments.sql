-- Departments and staff membership (the basis of department-level data isolation).

CREATE TABLE departments (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code           varchar(32)  NOT NULL,
    name           varchar(150) NOT NULL,
    description    varchar(1000),
    contact_email  varchar(254),
    status         varchar(20)  NOT NULL,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_departments_code UNIQUE (code),
    CONSTRAINT uq_departments_name UNIQUE (name),
    CONSTRAINT ck_departments_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,31}$'),
    CONSTRAINT ck_departments_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_departments_status ON departments (status);

-- Named department_memberships (docs/DATABASE_DESIGN.md); the master prompt calls this department_members.
CREATE TABLE department_memberships (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id  uuid         NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    position       varchar(100),
    joined_at      timestamptz  NOT NULL,
    added_by       uuid         REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_department_memberships UNIQUE (department_id, user_id)
);
CREATE INDEX ix_department_memberships_user ON department_memberships (user_id);
