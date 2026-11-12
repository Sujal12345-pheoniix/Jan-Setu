-- Identity and access: users, roles, permissions, refresh sessions, password reset tokens.
-- Conventions: UUID primary keys, timestamptz in UTC, varchar + CHECK for controlled states,
-- bigint "version" for optimistic locking on concurrently modified aggregates.

CREATE TABLE users (
    id                         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email                      varchar(254) NOT NULL,
    email_normalized           varchar(254) NOT NULL,
    password_hash              varchar(255) NOT NULL,
    full_name                  varchar(150) NOT NULL,
    phone                      varchar(20),
    user_type                  varchar(20)  NOT NULL,
    status                     varchar(20)  NOT NULL,
    failed_login_attempts      integer      NOT NULL DEFAULT 0,
    locked_until               timestamptz,
    password_changed_at        timestamptz  NOT NULL,
    password_change_required   boolean      NOT NULL DEFAULT false,
    last_login_at              timestamptz,
    status_reason              varchar(500),
    created_at                 timestamptz  NOT NULL,
    updated_at                 timestamptz  NOT NULL,
    version                    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_users_user_type CHECK (user_type IN ('CITIZEN', 'STAFF')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_login_attempts >= 0),
    CONSTRAINT ck_users_email_lowercase CHECK (email_normalized = lower(email_normalized))
);
CREATE INDEX ix_users_status_type ON users (status, user_type);
CREATE INDEX ix_users_created_at ON users (created_at DESC);

CREATE TABLE roles (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code         varchar(64)  NOT NULL,
    name         varchar(100) NOT NULL,
    description  varchar(500),
    system_role  boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,63}$')
);

CREATE TABLE permissions (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code         varchar(100) NOT NULL,
    category     varchar(50)  NOT NULL,
    description  varchar(500) NOT NULL,
    created_at   timestamptz  NOT NULL,
    CONSTRAINT uq_permissions_code UNIQUE (code),
    CONSTRAINT ck_permissions_code_format CHECK (code ~ '^[A-Z_]+:[A-Z_]+$')
);

CREATE TABLE role_permissions (
    role_id        uuid        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id  uuid        NOT NULL REFERENCES permissions (id) ON DELETE RESTRICT,
    granted_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

CREATE TABLE user_roles (
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id      uuid        NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    assigned_at  timestamptz NOT NULL DEFAULT now(),
    assigned_by  uuid        REFERENCES users (id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- Refresh sessions. Only a SHA-256 hash of the opaque refresh token is stored.
-- Tokens rotate on every use; all sessions sharing a family_id are revoked if a rotated token is reused.
CREATE TABLE refresh_sessions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id       uuid         NOT NULL,
    token_hash      varchar(64)  NOT NULL,
    status          varchar(20)  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL,
    last_used_at    timestamptz,
    rotated_at      timestamptz,
    revoked_at      timestamptz,
    revoke_reason   varchar(50),
    replaced_by_id  uuid         REFERENCES refresh_sessions (id) ON DELETE SET NULL,
    user_agent      varchar(255),
    ip_address      varchar(45),
    CONSTRAINT uq_refresh_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_sessions_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED')),
    CONSTRAINT ck_refresh_sessions_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_refresh_sessions_user_status ON refresh_sessions (user_id, status);
CREATE INDEX ix_refresh_sessions_family ON refresh_sessions (family_id);
CREATE INDEX ix_refresh_sessions_expires ON refresh_sessions (expires_at);

CREATE TABLE password_reset_tokens (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    created_at   timestamptz NOT NULL,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id, used_at);
