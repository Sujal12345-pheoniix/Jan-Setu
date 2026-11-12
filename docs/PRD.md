# Product Requirements Document (PRD)
## JanSetu – Service Delivery & Grievance Management Platform

- **Version:** 1.0
- **Status:** Baseline for implementation
- **Owner:** Product / Engineering
- **Audience:** Product, design, engineering, QA, DevOps, reviewers

## 1. Executive summary

JanSetu is a multi-role portal that models the lifecycle of public-service applications and grievances. Citizens submit requests and supporting documents. Department staff process work through configurable stages. Supervisors monitor service levels. Administrators manage departments, services, roles, and workflows. Auditors can inspect permitted records of significant activity.

The initial release is a single-organization, multi-department simulation. Multi-tenancy, integrations with real government identity systems, real payments, and legally binding certificates are out of scope unless separately specified and authorized.

## 2. Problem statement

Manual or fragmented service processes create poor visibility, inconsistent handling, repeated document requests, weak accountability, and difficulty measuring processing time. JanSetu provides a common workflow, traceable status history, role-scoped work queues, and aggregated reporting.

## 3. Product vision

Deliver a secure, accessible, maintainable portal that makes service requests traceable from submission to decision and makes departmental work measurable.

## 4. Goals and measurable outcomes

| Goal | Measure / acceptance target |
|---|---|
| End-to-end service flow | A seeded service can be applied for, reviewed, decided, and tracked |
| Access control | Automated tests prove role, department, and record ownership boundaries |
| Traceability | Every defined critical state change creates an audit/history record |
| Reliability | Critical workflow operations are transactional and idempotent where retried |
| Usability | Main journeys work on mobile and desktop; accessible form labels and keyboard flows |
| Maintainability | Modules have documented boundaries, tests, and migrations |
| Performance | Establish a reproducible baseline; proposed initial target p95 < 500 ms for common read APIs under a documented test load, excluding external dependencies |

Targets are engineering goals, not claims of achieved performance.

## 5. Non-goals

- Official government service delivery or legal issuance of certificates.
- Real Aadhaar, DigiLocker, PAN, payment gateway, or government database integration.
- Real citizen personal data in development/demo environments.
- Native Android/iOS apps in the first release.
- AI-based eligibility decisions or automated rejection.
- Blockchain, facial recognition, biometrics, or predictive policing.
- Full BPMN-compliant workflow platform in the MVP.
- Multi-tenant SaaS billing.
- Microservices for every module.

## 6. Personas and permissions

### Citizen
Can manage their own profile, create drafts, submit eligible applications, upload documents, view their own application history, respond to clarification, raise grievances, and view notifications.

### Officer
Can view and process tasks assigned to them or their authorized queue, verify documents, request clarification, add internal notes, and make decisions only when granted the corresponding permission.

### Reviewer
Can review a completed verification stage and perform permitted approval decisions.

### Department Admin
Can manage staff and department-owned services/workflows, assign tasks, and view department-level reports.

### Platform Admin
Can manage platform configuration, users, roles, permissions, and departments. Administrative actions are audited.

### Auditor
Can read authorized audit and history data. Auditor access is read-only.

### Management Viewer
Can access aggregated dashboards and reports, not unrestricted personal records.

## 7. Scope and release priorities

### P0 – MVP
- Registration and authentication.
- User profile and account status.
- Role/permission administration.
- Department management.
- Service catalogue.
- Configurable required fields and document checklist.
- Application draft, submit, tracking, and history.
- Private document upload/download.
- Officer task assignment and processing.
- Workflow transitions: submit, review, clarification, resubmission, approve, reject, complete.
- Grievance registration, assignment, response, resolution, and appeal/reopen policy.
- Audit trail for critical events.
- OpenAPI documentation.
- Unit and integration tests.
- Docker-based local environment.

### P1 – Enhanced release
- SLA policy and business calendar.
- Escalations and notifications.
- Redis caching/rate limiting where justified.
- Department analytics and CSV export.
- Email provider integration.
- Advanced search and filters.
- Outbox-backed event publishing.
- CI/CD, metrics, dashboards, centralized logs.
- E2E and performance testing.

### P2 – Advanced learning extensions
- Kafka deployment and consumers.
- Extract notification service or another justified bounded context.
- MongoDB for a genuinely flexible document/form-schema use case.
- Kubernetes deployment.
- Advanced workflow designer.
- Localization and deeper accessibility testing.
- External identity or payment provider sandbox integrations, only with credentials and authorization.

## 8. Functional requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-001 | Citizens can register and authenticate | P0 |
| FR-002 | Admins can create, deactivate, and manage users | P0 |
| FR-003 | Roles map to explicit permissions | P0 |
| FR-004 | Department-scoped access is enforced server-side | P0 |
| FR-005 | Admins can create and publish services | P0 |
| FR-006 | Citizens can save application drafts | P0 |
| FR-007 | Citizens can submit applications after validation | P0 |
| FR-008 | System generates unique human-readable reference numbers | P0 |
| FR-009 | Documents are stored privately and access-controlled | P0 |
| FR-010 | Authorized officers can receive and process tasks | P0 |
| FR-011 | Workflow transitions are validated against current state | P0 |
| FR-012 | Clarification and resubmission are supported | P0 |
| FR-013 | Decisions and reasons are retained in history | P0 |
| FR-014 | Citizens can create and track grievances | P0 |
| FR-015 | Grievances can be assigned, resolved, and appealed under policy | P0 |
| FR-016 | Critical actions create immutable-by-application audit records | P0 |
| FR-017 | Users receive in-app notifications for important events | P1 |
| FR-018 | SLA deadlines and escalations are calculated | P1 |
| FR-019 | Management can view aggregated operational metrics | P1 |
| FR-020 | Authorized users can export bounded reports | P1 |

## 9. Business rules

1. A citizen may only access applications and grievances they own unless an explicit administrative permission and lawful operational reason allow otherwise.
2. An officer may only act within assigned department and task scope.
3. Only a DRAFT application may be edited by the citizen.
4. Only valid transitions may change application status.
5. Approval/rejection requires the relevant permission and a recorded decision.
6. Rejection requires a reason; clarification requires a question and response deadline/policy.
7. A submitted application must have all mandatory fields and required documents.
8. A document must pass file validation before it can be attached.
9. A task cannot be completed twice; duplicate requests must be rejected or safely treated as idempotent.
10. Critical workflow actions, assignment changes, role changes, and document verification decisions must be audited.
11. SLA calculation uses the policy version active at the time the case enters the applicable stage, unless policy explicitly defines a migration rule.
12. Analytics must aggregate or mask personal data unless a user has explicit access to identifiable records.

## 10. Non-functional requirements

### Security
- TLS in deployed environments.
- Password hashing using a modern adaptive password hash supported by the framework.
- Short-lived access tokens and protected refresh-session handling.
- RBAC plus record-level and department-level authorization.
- CSRF protection when cookie-based authentication is used.
- Rate limiting on sensitive endpoints.
- Secure file handling.
- Secret management outside source control.
- Dependency and container image scanning.

### Performance
- Pagination on list endpoints.
- Avoid N+1 queries.
- Index actual query patterns.
- Use bounded exports and asynchronous jobs for large reports.
- Establish load-test scenarios and report results.

### Reliability
- Database transactions for critical state changes.
- Idempotent scheduled jobs and event consumers.
- Health and readiness checks.
- Retry only transient failures, with backoff and dead-letter handling where messaging is used.

### Accessibility
- Aim for WCAG 2.1 AA.
- Keyboard operability, visible focus, labels, error summaries, contrast, semantic landmarks, and screen-reader-friendly status updates.

### Maintainability
- Feature-oriented modules.
- DTO-based API boundaries.
- Flyway migrations.
- Automated tests.
- ADRs for material decisions.

## 11. User stories and acceptance criteria

### US-APP-01: Submit an application
As a citizen, I want to submit a service application so that the responsible department can process it.

Acceptance:
- Given an authenticated citizen with a valid draft, when all required fields and documents are present and they submit, then the server validates ownership and eligibility, changes status exactly once, records submission time, creates the initial workflow task, records history/audit, and returns a reference number.
- If validation fails, no partial submission is committed and actionable field errors are returned.
- If a concurrent or duplicate submission occurs, no duplicate workflow instance is created.

### US-WF-01: Officer requests clarification
As an assigned officer, I want to request missing information so that the citizen can correct the application.

Acceptance:
- Only an assigned/authorized officer can request clarification.
- The reason and requested information are mandatory.
- The application and task move to the configured clarification state transactionally.
- Citizen receives an in-app notification.
- The action appears in history and audit.

### US-GRV-01: Citizen raises grievance
As a citizen, I want to register a grievance about a service so that it can be investigated.

Acceptance:
- Required category, description, and related reference (if applicable) are validated.
- A unique grievance reference is generated.
- The grievance is routed according to category/department configuration.
- Citizen can view status and permitted responses.

## 12. Success metrics

- Application completion rate.
- Median and p95 processing duration.
- Percentage of cases processed within SLA.
- Clarification rate.
- Rejection rate by service and reason category.
- Grievance resolution time.
- Backlog by department and age bucket.
- API error rate and latency.
- Workflow failure and retry count.
- User-reported usability issues.

Metrics must be defined precisely before reporting. Do not compare departments without accounting for case complexity and service differences.

## 13. Assumptions and open questions

- One simulated organization with multiple departments for v1.
- English-first UI; Hindi localization is a planned enhancement.
- Email is optional for local MVP; in-app notification is the baseline.
- No real personal data or real government integrations.
- Service form schemas are versioned; submitted applications retain the schema version used at submission.
- Legal retention periods, privacy notices, and deployment jurisdiction must be reviewed before any real-world use.

Open decisions:
1. Whether citizens can register with email only or phone verification too.
2. Whether grievances can be linked to an application.
3. Whether approval requires one or multiple levels per service.
4. Business-day calendar and holiday source.
5. Which cloud/storage provider will be used for deployment.
