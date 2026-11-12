# Implementation Plan – From Empty Repository to Release

## 1. Execution philosophy

Build a working vertical slice early. Do not build every database table or every screen before proving one complete user journey. Implement in small pull requests, with tests and documentation.

Recommended first vertical slice:
1. Citizen login.
2. View active service.
3. Create draft.
4. Submit application.
5. Officer sees task.
6. Officer requests clarification.
7. Citizen responds.
8. Reviewer approves.
9. Citizen sees final history.

## 2. Prerequisites

- JDK 21 (confirm compatibility with selected Spring Boot release).
- Maven wrapper.
- Node.js LTS and package manager.
- Git.
- Docker Engine/Desktop.
- PostgreSQL client (optional).
- IDE: IntelliJ IDEA or VS Code.
- API client: Bruno/Postman/Insomnia.
- Browser with developer tools.

## 3. Repository bootstrap

```bash
mkdir jansetu && cd jansetu
git init
mkdir backend frontend infrastructure scripts docs
```

Create backend with Spring Initializr or an equivalent reproducible generator. Select compatible dependencies:
- Spring Web
- Spring Security
- Spring Data JPA
- Validation
- PostgreSQL Driver
- Flyway
- Spring Boot Actuator
- Spring Boot Test
- Springdoc OpenAPI (compatible version)
- Testcontainers (test scope)

Create frontend with Vite React TypeScript. Add Tailwind, router, query client, form validation, and test dependencies.

Commit generated lock files and Maven wrapper. Do not commit build output or secrets.

## 4. Phase-by-phase implementation

### Phase 0 – Product baseline
Deliver:
- Approved PRD and SRS.
- Persona/permission matrix.
- Workflow state diagrams.
- Initial ERD.
- ADR-001 modular monolith.
- API naming and error conventions.
Exit criteria: team can explain MVP scope and out-of-scope items.

### Phase 1 – Foundation
Backend:
- Boot application.
- Configuration profiles.
- PostgreSQL connection.
- Flyway baseline.
- Global error response.
- Request correlation ID.
- Actuator health.
Frontend:
- Router and layout.
- Design tokens.
- API client.
- Error/loading/empty primitives.
Infrastructure:
- Compose PostgreSQL and Redis optional profile.
- CI build/test workflow.
Exit criteria: clean clone can build and start using documented steps.

### Phase 2 – Identity and access
- User model and repository.
- Registration and login.
- Password hashing.
- Refresh session rotation/revocation.
- Spring Security filter chain/resource server.
- Roles and permissions.
- Department membership.
- Method/service-level authorization.
- Security test matrix.
Exit criteria: citizen, officer, admin access boundaries pass tests.

### Phase 3 – Catalogue and applications
- Department CRUD.
- Service draft/publish/version.
- Form schema validation.
- Application draft.
- Application submission transaction.
- Reference number allocator.
- Application list/detail/history.
Exit criteria: citizen can submit and track a valid service request.

### Phase 4 – Documents
- Storage port and MinIO adapter.
- Upload limits and signature validation.
- Quarantine and scan state.
- Metadata persistence.
- Authorized download.
- Retention policy hooks.
Exit criteria: unauthorized users cannot retrieve document bytes.

### Phase 5 – Workflow and tasks
- Workflow definition/version.
- State transition policy.
- Workflow instance/task creation.
- Assignment and reassignment.
- Clarification and resubmission.
- Approval/rejection.
- Optimistic locking.
- Business history and audit.
Exit criteria: full application lifecycle works under concurrent and unauthorized attempts.

### Phase 6 – Grievances
- Create and route grievance.
- Assignment and investigation.
- Public response/internal notes.
- Resolve and appeal.
- Feedback.
Exit criteria: citizen and officer journeys pass tests.

### Phase 7 – SLA and notifications
- SLA policy/version.
- Business calendar abstraction.
- Deadline calculation.
- Scheduler with safe batch claiming.
- Idempotent escalation.
- In-app notification.
- Outbox worker and email adapter.
Exit criteria: repeated scheduler execution creates no duplicate escalation.

### Phase 8 – Frontend completion
- Citizen portal.
- Officer workbench.
- Department admin.
- Platform admin.
- Audit and reporting views.
- Responsive and accessible interactions.
Exit criteria: E2E tests cover primary journeys.

### Phase 9 – Advanced architecture
- Redis cache only for measured use cases.
- Outbox publisher.
- Kafka integration.
- Idempotent consumers and DLQ.
- Extract a module only with ADR and clear ownership boundary.
- Optional MongoDB only with a justified use case.
Exit criteria: retry, duplicate, timeout, and broker outage scenarios are tested.

### Phase 10 – Production-style release
- Security review.
- Dependency/container scan.
- Load tests.
- Backup/restore rehearsal.
- Deployment automation.
- Metrics, logs, traces.
- Alerting and runbooks.
- Staging acceptance.
Exit criteria: release gate checklist passes and evidence is recorded.

## 5. Backend coding standards

- Constructor injection; avoid field injection.
- Small controllers; business logic in services/domain.
- Request/response DTOs.
- Explicit transaction boundaries.
- Avoid broad `@Transactional` on entire controller paths.
- Use `@Version` or safe conditional update for concurrent decisions.
- Avoid N+1; inspect SQL and query plans.
- Do not return `Page<Entity>` directly.
- Validate pagination and sorting.
- Use structured logs and safe error messages.
- Prefer explicit mapping; MapStruct is optional.
- Avoid premature generic frameworks.

## 6. Frontend coding standards

- TypeScript strict mode.
- Feature-based folders.
- Shared API client and typed API functions.
- Server state via TanStack Query.
- Keep client state local unless shared state is necessary.
- Forms use schema validation and accessible errors.
- Route guards are UX only; backend remains authoritative.
- Avoid storing long-lived secrets in localStorage.
- Use semantic HTML and keyboard-accessible controls.
- Handle loading, empty, error, unauthorized, and success states.

## 7. Pull request checklist

- [ ] Scope is focused and linked to a requirement.
- [ ] Business rules and authorization are server-side.
- [ ] Tests cover success, failure, boundary, and permission cases.
- [ ] Database migration included and reviewed.
- [ ] No secrets or personal data committed.
- [ ] API docs/types updated.
- [ ] Logs contain no sensitive data.
- [ ] Performance impact considered.
- [ ] Accessibility considered for UI changes.
- [ ] README/runbook/ADR updated if needed.

## 8. Suggested sprint plan

| Sprint | Outcome |
|---|---|
| 0 | Product, architecture, UX, schema baseline |
| 1 | Repo, CI, database, application foundation |
| 2 | Identity, RBAC, department scope |
| 3 | Service catalogue and citizen application |
| 4 | Documents and workflow tasks |
| 5 | Clarification, approval, history |
| 6 | Grievance lifecycle |
| 7 | SLA, notifications, audit hardening |
| 8 | Dashboards, reports, frontend polish |
| 9 | Integration tests, E2E, security testing |
| 10 | Docker deployment, monitoring, backup/restore |
| 11+ | Kafka, service extraction, Kubernetes, advanced hardening |

## 9. Definition of done

A story is complete only when:
- Acceptance criteria pass.
- Authorization and ownership are verified.
- Validation and error paths work.
- Unit and relevant integration tests pass.
- Database migrations are included.
- API and UI are integrated.
- Logs/metrics are appropriate.
- Documentation is updated.
- PR review is complete.
- No known critical vulnerability is introduced.

## 10. First implementation task list

1. Create monorepo and branch protections.
2. Generate Spring Boot backend and React TypeScript frontend.
3. Add local PostgreSQL Compose service.
4. Configure Flyway and initial schema.
5. Add health endpoint and global error handling.
6. Add CI workflow for backend and frontend.
7. Add initial architecture test.
8. Build first vertical slice: citizen registration/login and `/auth/me`.
9. Add integration tests using PostgreSQL Testcontainers.
10. Update README with verified commands and screenshots.
