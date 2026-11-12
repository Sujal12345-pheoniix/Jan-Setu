-- Phase 2: case workflow adjustments.

-- 1. Grievance lifecycle per Golden Prompt 2:
--    SUBMITTED -> ACKNOWLEDGED -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED, with policy-controlled REOPENED.
ALTER TABLE grievances DROP CONSTRAINT ck_grievances_status;
ALTER TABLE grievances ADD CONSTRAINT ck_grievances_status CHECK (status IN (
    'SUBMITTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REOPENED'));
ALTER TABLE grievances RENAME COLUMN appeal_deadline TO reopen_deadline;
ALTER TABLE grievances ADD COLUMN acknowledged_at timestamptz;
ALTER TABLE grievances ADD COLUMN resolution_summary varchar(4000);
ALTER TABLE grievances ADD COLUMN reopen_count integer NOT NULL DEFAULT 0;
ALTER TABLE grievances ADD CONSTRAINT ck_grievances_reopen_count CHECK (reopen_count >= 0);
ALTER TABLE grievances ADD CONSTRAINT ck_grievances_category CHECK (category IN (
    'SERVICE_DELAY', 'STAFF_CONDUCT', 'INCORRECT_DECISION', 'DOCUMENT_ISSUE', 'PORTAL_ISSUE', 'OTHER'));

CREATE TABLE grievance_status_history (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    grievance_id  uuid          NOT NULL REFERENCES grievances (id) ON DELETE CASCADE,
    from_status   varchar(30),
    to_status     varchar(30)   NOT NULL,
    actor_id      uuid          REFERENCES users (id) ON DELETE SET NULL,
    note          varchar(2000),
    occurred_at   timestamptz   NOT NULL
);
CREATE INDEX ix_grievance_status_history ON grievance_status_history (grievance_id, occurred_at);

-- 2. Built-in workflow: instances may run the platform's standard review/approval workflow
--    without a department-configured definition version.
ALTER TABLE workflow_instances ALTER COLUMN definition_version_id DROP NOT NULL;
ALTER TABLE workflow_instances ADD COLUMN workflow_code varchar(50) NOT NULL DEFAULT 'STANDARD_REVIEW_APPROVAL_V1';
ALTER TABLE workflow_instances ADD CONSTRAINT ck_workflow_instances_definition
    CHECK (definition_version_id IS NOT NULL OR workflow_code IS NOT NULL);
ALTER TABLE workflow_tasks ADD CONSTRAINT ck_workflow_tasks_step CHECK (step_code IN ('REVIEW', 'APPROVAL'));

-- 3. Documents may belong to an application or a grievance (evidence).
ALTER TABLE documents ADD COLUMN grievance_id uuid REFERENCES grievances (id) ON DELETE RESTRICT;
ALTER TABLE documents ADD CONSTRAINT ck_documents_parent CHECK (num_nonnulls(application_id, grievance_id) = 1);
CREATE INDEX ix_documents_grievance ON documents (grievance_id);
CREATE UNIQUE INDEX uq_documents_application_requirement ON documents (application_id, requirement_code)
    WHERE application_id IS NOT NULL AND requirement_code IS NOT NULL;

-- 4. Decisions record the actor's free-text remarks for recommendations/returns too.
ALTER TABLE application_decisions DROP CONSTRAINT ck_application_decisions_decision;
ALTER TABLE application_decisions ADD CONSTRAINT ck_application_decisions_decision
    CHECK (decision IN ('RECOMMENDED', 'APPROVED', 'REJECTED', 'RETURNED'));

-- 5. Officers and reviewers can see department-level aggregate reports.
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, now()
FROM roles r JOIN permissions p ON p.code = 'REPORT:READ_DEPARTMENT'
WHERE r.code IN ('OFFICER', 'REVIEWER');

-- 6. Officers may be offered document verification only within scope; reviewers verify too during approval.
INSERT INTO role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, now()
FROM roles r JOIN permissions p ON p.code = 'DOCUMENT:VERIFY'
WHERE r.code = 'REVIEWER';

-- 7. Citizens may raise grievances with supporting evidence and need to read published department names.
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_workflow_tasks_application ON workflow_tasks (application_id, status);
