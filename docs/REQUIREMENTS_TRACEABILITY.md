# Requirements Traceability Matrix

| Requirement | PRD | Module | API / UI | Verification |
|---|---|---|---|---|
| FR-001 Authentication | PRD §8 | Identity | `/auth/*`, auth pages | Auth integration tests |
| FR-002 User management | PRD §8 | Identity | `/users`, admin UI | Admin authorization tests |
| FR-003 RBAC | PRD §8 | Access | roles/permissions APIs | Permission matrix tests |
| FR-004 Department scope | PRD §8 | Access | tasks/apps queries | Cross-department denial tests |
| FR-005 Service catalogue | PRD §8 | Catalogue | `/services` | Version/publish tests |
| FR-006 Drafts | PRD §8 | Application | `POST/PATCH /applications` | Draft ownership tests |
| FR-007 Submission | PRD §8 | Application/Workflow | `/submit` | Transaction + idempotency tests |
| FR-008 Reference number | PRD §8 | Application | submit response | Unique constraint/concurrency test |
| FR-009 Documents | PRD §8 | Document | `/documents` | Upload/download security tests |
| FR-010 Officer tasks | PRD §8 | Workflow | `/tasks` | Assignment/scope tests |
| FR-011 State transitions | PRD §8 | Workflow | task commands | State machine tests |
| FR-012 Clarification | PRD §8 | Workflow | `/clarification` | Full lifecycle E2E |
| FR-013 Decisions/history | PRD §8 | Application/Workflow | history endpoints | Audit/history tests |
| FR-014 Grievance creation | PRD §8 | Grievance | `/grievances` | Grievance integration tests |
| FR-015 Resolution/appeal | PRD §8 | Grievance | resolve/appeal | Policy boundary tests |
| FR-016 Audit | PRD §8 | Audit | `/audit-logs` | Append-only/access tests |
| FR-017 Notifications | PRD §8 | Notification | `/notifications` | Delivery retry tests |
| FR-018 SLA | PRD §8 | SLA | `/sla` | Calendar/idempotency tests |
| FR-019 Analytics | PRD §8 | Reporting | `/reports` | Metric correctness/scope tests |
| FR-020 Exports | PRD §8 | Reporting | export job | Bounds/CSV injection tests |
