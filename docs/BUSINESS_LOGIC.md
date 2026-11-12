# Business Logic and Domain Rules

## 1. Domain boundaries

- Identity: accounts, credentials, account status.
- Access: roles, permissions, department scopes.
- Catalogue: services, form definitions, required document rules.
- Application: citizen request and its business lifecycle.
- Workflow: workflow definitions, instances, tasks, transitions.
- Document: upload metadata, storage references, verification state.
- Grievance: complaints, assignments, responses, appeals.
- SLA: policy, deadline, pause/resume, escalation.
- Notification: in-app/email delivery and preferences.
- Audit: security and administrative activity.
- Reporting: read models and aggregated metrics.

## 2. Application invariants

1. Every application belongs to one citizen and one service.
2. Every submitted application has a unique reference number.
3. A draft is editable only by its owner or explicitly authorized staff.
4. A submitted case retains the form schema version used at submission.
5. A status change must be an allowed transition.
6. A decision must reference the actor and decision timestamp.
7. Required documents must be present and in an accepted verification state.
8. A workflow instance must reference a valid workflow definition version.
9. A completed or rejected case cannot silently return to an earlier state.
10. Concurrent modifications must not overwrite a newer version.

## 3. Authorization logic

Authorization is a combination of:
- Authentication: who is the actor?
- Permission: may this actor perform this action?
- Scope: does the actor belong to the correct department/queue?
- Ownership: does the record belong to the citizen?
- State: is the action valid in the current state?
- Policy: does the service/workflow permit it?

A role name alone is not sufficient. Enforce object-level authorization in application services or dedicated authorization policies, not only at route level.

Example policy pseudocode:

```text
canViewApplication(actor, application):
  if actor has PLATFORM_APPLICATION_READ_ALL:
      return true
  if actor is citizen and application.citizenId == actor.id:
      return true
  if actor has APPLICATION_READ_DEPARTMENT
     and actor.departmentId == application.departmentId:
      return true
  return false
```

## 4. Reference number generation

Use a database-backed sequence or a collision-safe allocation strategy. Do not use timestamps or random numbers alone as a guarantee of uniqueness.

Human-readable example: `JAN-2026-000123`. Store the reference as a unique database column. The UUID remains the internal primary key.

## 5. Workflow transition algorithm

```text
executeTransition(caseId, command, actor):
  begin transaction
  load case and current version
  authorize actor for command and resource scope
  resolve active workflow definition version
  find transition matching current state + command
  validate transition preconditions
  validate required command payload
  apply domain state change
  update/complete/create workflow task
  append business history
  append audit event
  append outbox event if external side effect is required
  commit
```

The transition engine must reject unknown commands, illegal transitions, stale versions, and unauthorized actors.

## 6. Document rules

- Only allow configured file types.
- Validate both declared and detected content type.
- Enforce file size limits at proxy and application layers.
- Use malware scanning where available.
- Store outside static/public directories.
- Use random storage identifiers; never trust user filenames as storage keys.
- Apply access checks on every download.
- Do not include document bytes or sensitive extracted content in logs.
- Define retention and deletion rules before production use.

## 7. SLA calculation

SLA policy fields may include:
- target duration;
- calendar type (calendar time/business time);
- business hours;
- holidays;
- pause conditions;
- warning thresholds;
- escalation levels;
- policy version.

Persist the calculated deadline and policy version. If policy changes, existing cases should not silently change deadlines.

## 8. Audit logic

Audit events should be append-only at the application level and protected by database permissions. For stronger tamper evidence, consider restricted database roles, immutable external log storage, and cryptographic chaining only if the threat model justifies the added operational complexity.

Audit event fields:
- event ID;
- actor ID or system actor;
- action;
- resource type and ID;
- timestamp;
- request/trace ID;
- outcome;
- minimal before/after diff;
- source channel;
- reason/correlation metadata.

Never log passwords, tokens, secrets, full identity documents, or unnecessary personal data.

## 9. Notification logic

Business transactions create notification intent/outbox records. A worker delivers notifications. Delivery states:
`PENDING → PROCESSING → SENT` or `FAILED → RETRY_PENDING / DEAD_LETTER`.

Retries must use bounded exponential backoff and provider idempotency where supported. Do not let notification failure roll back an already committed business decision.

## 10. Analytics logic

- Use clearly defined metric definitions.
- Prefer aggregate queries/read models.
- Apply department scope and privacy filtering.
- Use date boundaries consistently in UTC.
- Do not expose small-group data if it could identify an individual.
- Export jobs must be bounded, authorized, audited, and protected from formula injection in CSV exports.
