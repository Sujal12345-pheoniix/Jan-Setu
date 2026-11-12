-- Service catalogue. "services" is the editable working copy; every publish writes an immutable
-- snapshot to service_versions. Citizens and applications always reference a published version.

CREATE TABLE services (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id            uuid          NOT NULL REFERENCES departments (id) ON DELETE RESTRICT,
    code                     varchar(50)   NOT NULL,
    name                     varchar(200)  NOT NULL,
    description              varchar(4000) NOT NULL,
    category                 varchar(50)   NOT NULL,
    eligibility_text         varchar(4000),
    processing_days          integer       NOT NULL,
    fee_amount               numeric(12, 2) NOT NULL DEFAULT 0,
    form_schema              jsonb         NOT NULL DEFAULT '{}'::jsonb,
    status                   varchar(20)   NOT NULL,
    current_version          integer       NOT NULL DEFAULT 0,
    has_unpublished_changes  boolean       NOT NULL DEFAULT true,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    version                  bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_services_code UNIQUE (code),
    CONSTRAINT ck_services_code_format CHECK (code ~ '^[A-Z][A-Z0-9_-]{1,49}$'),
    CONSTRAINT ck_services_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED')),
    CONSTRAINT ck_services_processing_days CHECK (processing_days BETWEEN 1 AND 365),
    CONSTRAINT ck_services_fee CHECK (fee_amount >= 0),
    CONSTRAINT ck_services_current_version CHECK (current_version >= 0),
    CONSTRAINT ck_services_published_has_version CHECK (status = 'DRAFT' OR current_version > 0)
);
CREATE INDEX ix_services_department_status ON services (department_id, status);
CREATE INDEX ix_services_status_category ON services (status, category);

-- Named service_requirements per the master prompt (docs call it service_required_documents).
CREATE TABLE service_requirements (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id          uuid          NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    code                varchar(50)   NOT NULL,
    name                varchar(200)  NOT NULL,
    description         varchar(1000),
    requirement_type    varchar(20)   NOT NULL,
    mandatory           boolean       NOT NULL DEFAULT true,
    allowed_media_types varchar(500),
    max_size_bytes      bigint,
    display_order       integer       NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    version             bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_requirements_code UNIQUE (service_id, code),
    CONSTRAINT ck_service_requirements_type CHECK (requirement_type IN ('DOCUMENT', 'DECLARATION', 'INFORMATION')),
    CONSTRAINT ck_service_requirements_size CHECK (max_size_bytes IS NULL OR max_size_bytes > 0)
);

CREATE TABLE service_versions (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id       uuid          NOT NULL REFERENCES services (id) ON DELETE RESTRICT,
    version_number   integer       NOT NULL,
    name             varchar(200)  NOT NULL,
    description      varchar(4000) NOT NULL,
    category         varchar(50)   NOT NULL,
    eligibility_text varchar(4000),
    processing_days  integer       NOT NULL,
    fee_amount       numeric(12, 2) NOT NULL,
    form_schema      jsonb         NOT NULL,
    requirements     jsonb         NOT NULL,
    published_at     timestamptz   NOT NULL,
    published_by     uuid          REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_service_versions UNIQUE (service_id, version_number),
    CONSTRAINT ck_service_versions_number CHECK (version_number > 0)
);

-- Published versions are immutable.
CREATE FUNCTION prevent_service_version_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'service_versions rows are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_service_versions_immutable
    BEFORE UPDATE ON service_versions
    FOR EACH ROW EXECUTE FUNCTION prevent_service_version_update();
