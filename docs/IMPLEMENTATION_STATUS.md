# Implementation Status

_Last updated: 2026-11-12 (end of Phase 1: foundation, architecture and backend core)_

Legend: ✅ implemented and tested · 🟡 schema only / partial · ⬜ not started

## Platform foundation

| Item | Status | Evidence |
|---|---|---|
| Monorepo layout (`backend/`, `frontend/`, `infrastructure/`, `scripts/`, `docs/`, `.github/`) | ✅ | repository root |
| Java 21, Spring Boot 3.5.16, Maven wrapper (Maven 3.9.11) | ✅ | `backend/pom.xml`, `backend/mvnw` |
| Profiles: local / test / staging / production, secrets from environment | ✅ | `application*.yml` |
| Flyway migrations V1–V8 (full data model) | ✅ | `DatabaseSchemaIT` |
| Global exception handling, RFC 9457 problem details, stable error codes | ✅ | `ErrorHandlingIT` |
| Request correlation ids (`X-Request-Id`, MDC, error bodies) | ✅ | `ErrorHandlingIT` |
| Standard response envelope, pagination, sort allowlist | ✅ | `PageQueryTest`, `UserManagementIT` |
| Idempotency-Key for create commands | ✅ | `DepartmentIT`, `UserManagementIT`, `ServiceCatalogueIT` |
| OpenAPI / Swagger UI (disabled in production) | ✅ | `ErrorHandlingIT` |
| Actuator health (liveness/readiness), Prometheus metrics | ✅ | `ErrorHandlingIT`, manual smoke test |
| Structured JSON logs (staging/production) | ✅ | `application-staging.yml`, `application-production.yml` |
| Backend Dockerfile (multi-stage, non-root, health check) | ✅ (written, built in CI) | `backend/Dockerfile` |
| Docker Compose (PostgreSQL; optional Redis/MinIO; Prometheus/Grafana) | ✅ PostgreSQL verified locally | `docker-compose.yml` |
| GitHub Actions backend CI | ✅ (written, not yet run on GitHub) | `.github/workflows/backend-ci.yml` |
| Architecture rules (module boundaries, no cycles) | ✅ | `ArchitectureTest` |

## Business modules

| Module | Status | Notes |
|---|---|---|
| Identity / authentication | ✅ | Register, login, refresh rotation and reuse detection, logout (device / everywhere), sessions list/revoke, password reset, change password, `/auth/me`, profile update, lockout, rate limit |
| Users (administration) | ✅ | Staff creation, profile update (optimistic locking), suspend/deactivate/reactivate, role replacement, department-scoped listing |
| Roles and permissions | ✅ | 7 system roles, 42 permissions, custom roles, privilege-escalation guard |
| Departments | ✅ | CRUD, activate/deactivate, membership management, data isolation |
| Service catalogue | ✅ | Draft → publish (immutable versions) → deprecate; requirements; form schema validation; public catalogue |
| Audit | ✅ | Append-only log (DB trigger), audit search for AUDITOR (search itself audited) |
| Applications, documents, workflow, tasks | 🟡 | Tables, sequences and invariants created (V5); services in Phases 3–5 |
| Grievances | 🟡 | Tables created (V6) |
| Notifications, outbox, SLA, analytics | 🟡 | Tables created (V4, V7) |
| Frontend (React/TypeScript/Vite) | ⬜ | `frontend/` placeholder |

## Known limitations and risks (Phase 1)

1. **Rate limiting is per instance.** Counters are in memory. Move them to Redis before running more than one replica.
2. **No e-mail verification on registration, no MFA for privileged accounts, no CAPTCHA.** These are planned hardening items; this is acceptable for a synthetic-data simulation.
3. **E-mail delivery is a logging adapter.** It prints reset links to the console in the local profile only. A real provider adapter is planned with notifications.
4. **Access tokens survive "logout everywhere" for up to 15 minutes.** Refresh sessions are revoked immediately. Suspension and password change also cut access tokens immediately (per-request status check plus `iat` vs. `password_changed_at`).
5. **Concurrent refreshes can log a user out.** Two browser tabs refreshing at the same instant trigger reuse detection, and the user must sign in again.
6. **JWT key rotation requires a restart.** There is no JWKS endpoint and only one active `kid`.
7. **Each authenticated request loads authorities from the database** (about three indexed queries). This needs a cache if load tests show it matters.
8. **No expired-data cleanup jobs yet.** Expired idempotency records, refresh sessions and reset tokens are not purged by a scheduler.
9. **The registration duplicate-email response (409) reveals that an account exists.** It is rate limited. Login and reset responses are generic.
10. **The Docker image and the CI workflow were not verified.** No `docker build` was run locally in Phase 1, and the workflow has not run on GitHub yet.
11. **The forwarded client IP is not configured.** Behind a reverse proxy, set `server.forward-headers-strategy` so rate limits and audit IPs use the real client.
