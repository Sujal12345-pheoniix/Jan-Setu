# Testing Strategy and Quality Plan

## 1. Test pyramid

- Unit tests: domain policies, validators, services, state transitions.
- Slice tests: MVC/controller and repository behavior.
- Integration tests: real PostgreSQL and Redis through Testcontainers where appropriate.
- Contract tests: API schema and consumer expectations.
- E2E tests: primary citizen/officer/admin journeys.
- Non-functional tests: performance, security, accessibility, resilience.

## 2. Backend tools

- JUnit 5.
- Mockito.
- Spring Boot Test.
- MockMvc.
- Testcontainers.
- ArchUnit (optional architecture rules).
- JaCoCo (coverage reporting).

## 3. Frontend tools

- Vitest or Jest (choose one consistently).
- React Testing Library.
- Playwright for E2E.
- axe accessibility checks where practical.

## 4. Required test matrix

### Identity
- Correct and incorrect credentials.
- Suspended account.
- Refresh rotation and reuse detection.
- Logout revokes session.
- Password reset token expiry and single use.

### Authorization
- Citizen accesses own application.
- Citizen cannot access another citizen's application.
- Officer can access assigned department task.
- Officer cannot access another department without permission.
- Reviewer cannot perform platform administration.
- Auditor cannot mutate business records.

### Application
- Valid draft submission.
- Missing field/document.
- Inactive service.
- Invalid state transition.
- Duplicate idempotency key.
- Concurrent submit/decision.
- Form version compatibility.

### Documents
- Oversized file.
- Mismatched extension/MIME/signature.
- Unauthorized download.
- Quarantined file download denied.
- Malware scan failure.
- Storage provider unavailable.

### Workflow and SLA
- Valid/invalid transition.
- Task reassignment.
- Duplicate task completion.
- Scheduler duplicate run.
- Business holiday and timezone boundary.
- Retry after transient error.

### Grievance
- Citizen ownership.
- Internal note privacy.
- Public response visibility.
- Appeal window.
- Unauthorized resolution.

## 5. Coverage guidance

Set initial coverage thresholds for core domain/service packages after a baseline is established. Prefer meaningful branch and behavior coverage over an arbitrary global percentage. Exclude generated code only with documented configuration.

## 6. Test data

- Use synthetic fixtures only.
- Never copy production personal data.
- Use deterministic IDs/time where possible.
- Clean up test containers and temporary files.
- Ensure tests do not depend on execution order.

## 7. Release test gates

- Build and static checks pass.
- Unit and integration suites pass.
- E2E critical paths pass.
- Dependency and image scans reviewed.
- No unresolved critical/high issue without explicit risk acceptance.
- Migration tested from previous release.
- Backup restore test completed for production-like release.
- Performance results attached to release evidence.
