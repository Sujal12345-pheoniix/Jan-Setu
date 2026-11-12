# Functional Specification and Module Contracts

## 1. Identity and access module

### Responsibilities
Registration, login, credential lifecycle, user status, role/permission assignment, department membership, and authorization support.

### Operations
- Register citizen.
- Authenticate user.
- Rotate refresh session.
- Revoke session.
- Reset password.
- Create/update/deactivate staff user.
- Assign/revoke role.
- Assign department membership.
- Resolve current principal and authorities.

### Rules
- Passwords are stored only as adaptive hashes.
- Refresh credentials are protected, rotated, and revocable.
- Account status is checked during authentication and sensitive actions.
- Privilege changes are audited.
- Prevent privilege escalation through self-service endpoints.
- Use MFA for privileged accounts in a real deployment where feasible.

## 2. Service catalogue module

### Responsibilities
Service metadata, publication state, eligibility text, form schema, required documents, owning department, workflow mapping, SLA policy reference.

### Operations
- Create service.
- Update draft service.
- Publish a new version.
- Deprecate service.
- Browse active services.
- Retrieve service version and form definition.

### Rules
- Published versions are immutable; changes create a new version.
- Existing applications retain the version used at submission.
- Only authorized admins can publish.
- Do not implement legal eligibility decisions without approved domain rules.

## 3. Application module

### Responsibilities
Drafts, submissions, reference numbers, status, case history, citizen tracking, and application queries.

### Operations
- Create draft.
- Update draft.
- Submit.
- Withdraw if policy allows.
- Get details.
- List by citizen or authorized department.
- Search by reference number under access policy.
- Retrieve timeline.

### Rules
- Server validates all submitted data.
- Enforce ownership and department scope.
- Use optimistic locking or equivalent concurrency protection.
- Use pagination and safe filters.
- State transitions are delegated to workflow/application policy.

## 4. Workflow module

### Responsibilities
Versioned workflow definitions, instances, tasks, assignment, claims, transitions, and history.

### Operations
- Create/update draft workflow definition.
- Publish workflow version.
- Start workflow.
- Create task.
- Assign/reassign task.
- Claim/release task if queue mode is used.
- Execute transition.
- Retrieve workflow history.

### Rules
- Validate graph/state transitions.
- Prevent cycles unless explicitly supported.
- Prevent two active tasks for the same stage unless policy allows.
- Preserve definition version.
- Check assignment and scope on every action.

## 5. Document module

### Responsibilities
Upload authorization, file validation, object storage, metadata, scanning state, download authorization, retention.

### Operations
- Request upload or upload multipart.
- Validate and quarantine.
- Mark scan result.
- Attach to application.
- Verify/reject document.
- Download authorized document.
- Delete/expire according to retention policy.

### Rules
- Metadata and file bytes are separate.
- Storage key is random and opaque.
- A document is not downloadable before it is safe/available.
- A user cannot attach another user's file.
- File verification decision is audited.

## 6. Grievance module

### Responsibilities
Grievance creation, categorization, assignment, investigation, public response, appeal, feedback, and closure.

### Operations
- Create grievance.
- Assign/reassign.
- Start investigation.
- Add internal note.
- Add public response.
- Resolve.
- Appeal/reopen under policy.
- Submit feedback.
- List authorized grievances.

### Rules
- Internal notes are never returned by citizen endpoints.
- Public and internal comments have separate types and authorization.
- Resolution requires a response.
- Appeal window and eligibility are configurable.
- Every assignment and decision is audited.

## 7. SLA module

### Responsibilities
Policy versions, deadline calculation, warnings, escalation, pause/resume, and due-work scheduling.

### Operations
- Create/publish SLA policy.
- Calculate deadline.
- Pause/resume where policy allows.
- Find due work in batches.
- Create escalation.
- Record escalation delivery.

### Rules
- Scheduled work is idempotent.
- Use database locking/claiming to avoid duplicate processing.
- Scheduler time zone and business calendar are explicit.
- A policy version is stored with each calculated deadline.

## 8. Notification module

### Responsibilities
In-app notifications, email delivery, preferences, retry, delivery state, templates.

### Operations
- Create notification intent.
- List unread notifications.
- Mark read.
- Dispatch email.
- Retry transient failures.
- Inspect dead-letter records.

### Rules
- Notification is not the source of truth for business status.
- Do not expose sensitive content in email.
- Provider credentials are secrets.
- Respect user notification preferences and legal requirements.

## 9. Audit module

### Responsibilities
Append audit records, query authorized audit data, export bounded records, and apply retention controls.

### Operations
- Record event.
- Search by actor/resource/action/date within permission scope.
- Export authorized audit report.
- Apply retention policy.

### Rules
- Audit events are append-only for application users.
- Audit read access is tightly controlled.
- Do not record secrets or excessive personal data.
- Audit log access itself may also be audited.

## 10. Reporting module

### Responsibilities
Department summaries, backlog, aging, SLA compliance, throughput, grievance metrics, and safe exports.

### Operations
- Get dashboard summary.
- Get status distribution.
- Get processing-time aggregates.
- Get aging buckets.
- Export filtered report.

### Rules
- Enforce scope.
- Define metric formulas.
- Avoid unbounded synchronous exports.
- Protect CSV exports against spreadsheet formula injection.
