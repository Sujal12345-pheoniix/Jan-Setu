# Phase 1 Report: Foundation, Architecture and Backend Core

_Date: 2026-11-12_

> JanSetu is an independent portfolio/simulation project. It is not an official government portal, has no real government integrations, and uses synthetic data only.

## 1. Repository review (before implementation)

All 28 documents, 4 ADRs, 2 diagrams and the root files were reviewed. Inconsistencies found and how they were resolved:

| Issue | Resolution |
|---|---|
| Docs lived in `JanSetu-Project-Documentation/`; there was no code and no git repository | Restructured into a monorepo at the repository root; `git init` |
| Table names differ between the master prompt and `DATABASE_DESIGN.md` | Docs names used where they carry meaning (`refresh_sessions`, `department_memberships`, `services` + `service_versions`); prompt-only tables were added too (see `DATABASE_MIGRATIONS.md`) |
| Package layout differs (prompt: flat features; docs: `modules/*`) | `modules/*` with controller/service/domain/repository inside each module; mapping in `ARCHITECTURE.md` |
| Permission naming was inconsistent (`APPLICATION:SUBMIT` vs `APPLICATION_READ_DEPARTMENT`) | Standardised on `RESOURCE:ACTION` |
| `.env.example` had only `JWT_ISSUER_URI`, but the backend issues its own tokens | Self-issued RS256 tokens with issuer, audience and PEM keys from the environment (ADR-005) |
| Refresh-token transport was undecided | HttpOnly SameSite=Strict cookie plus `X-JanSetu-Client` CSRF header |
| No `staging` profile in the docs | Added |
| The docs' "don't build every table up front" conflicts with the prompt's full table list | Full schema created (reviewed as a whole) but code implemented only for the Phase 1 modules |

## 2. What was implemented

- **Backend platform:**
  - Spring Boot 3.5.16 on Java 21 with a Maven wrapper;
  - four profiles with environment-only secrets;
  - problem-details error handling with stable codes, correlation ids, response envelopes and pagination with a sort allowlist;
  - idempotency keys, OpenAPI, Actuator and Prometheus, structured logs.
- **Security:**
  - deny-by-default filter chain and RS256 JWT issuance and validation;
  - authorities resolved per request, so suspension, role and department changes take effect immediately;
  - BCrypt via the delegating encoder, a password policy, account lockout, per-client rate limiting;
  - refresh rotation with reuse detection, logout per device or everywhere, device session management;
  - password reset (hashed, single-use, 30 minutes, revokes sessions) and password change (invalidates older access tokens);
  - CORS allowlist and security headers.
- **RBAC and isolation:**
  - 7 system roles and 42 permissions; `@PreAuthorize` on every protected endpoint;
  - department scope enforced in services, with 404 for out-of-scope resources;
  - a privilege-escalation guard for granting roles and creating custom roles;
  - system roles cannot be modified through the API.
- **Modules:**
  - users: administration and profile;
  - roles and permissions;
  - departments and department memberships;
  - service catalogue: draft, requirements, form schema, immutable versioned publish, deprecate, public catalogue;
  - audit: append-only log plus audited search.
- **Database:** 8 Flyway migrations.
  - 38 tables with keys, foreign keys, unique, check and partial-unique constraints and indexes;
  - optimistic-lock columns;
  - database triggers that make the audit log append-only and published service versions immutable.
- **Developer experience:**
  - Docker Compose (PostgreSQL, plus optional Redis, MinIO, Prometheus and Grafana) and a backend Dockerfile;
  - `.env.example` and a local-only idempotent seed (3 departments, 6 services, one demo account per role);
  - a JWT key generation script, a GitHub Actions CI workflow and ADR-005.

## 3. Files and modules created

```text
.github/workflows/backend-ci.yml
docker-compose.yml, .env.example (updated)
infrastructure/monitoring/prometheus.yml
scripts/generate-jwt-keys.sh
backend/pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/, Dockerfile, .dockerignore
backend/src/main/resources/application{,-local,-test,-staging,-production}.yml
backend/src/main/resources/db/migration/V1..V8
backend/src/main/java/com/jansetu/
  config/, common/{api,error,web,persistence,crypto,idempotency}/, security/, integration/email/,
  modules/{audit,access,department,identity,catalogue}/{api,service,domain,repository}/, devseed/
backend/src/test/java/com/jansetu/
  support/, unit/ (5 classes), integration/ (9 classes), architecture/
docs/ARCHITECTURE.md, IMPLEMENTATION_STATUS.md, DATABASE_MIGRATIONS.md, LOCAL_SETUP.md, PHASE_1_REPORT.md,
docs/ADR/ADR-005-jwt-and-refresh-sessions.md
```

## 4. APIs available (`/api/v1`)

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/logout-all`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/change-password`; `GET`/`PATCH /auth/me`; `GET /auth/sessions`; `DELETE /auth/sessions/{id}` |
| Users | `GET /users`, `GET /users/{id}`, `POST /users`, `PATCH /users/{id}`, `POST /users/{id}/suspend`, `/deactivate`, `/reactivate`, `PUT /users/{id}/roles` |
| Roles | `GET /roles`, `GET /roles/{id}`, `POST /roles`, `PUT /roles/{id}/permissions`, `GET /permissions` |
| Departments | `GET /departments`, `GET /departments/{id}`, `POST /departments`, `PATCH /departments/{id}`, `POST /departments/{id}/activate`, `/deactivate`; `GET`/`POST /departments/{id}/members`; `DELETE /departments/{id}/members/{userId}` |
| Services | Public: `GET /services`, `GET /services/{id}`, `GET /services/{id}/versions/{n}`. Management: `GET /services/manage`, `GET /services/{id}/manage`, `POST /services`, `PATCH /services/{id}`, `POST /services/{id}/publish`, `/deprecate`; `POST`/`PATCH`/`DELETE /services/{id}/requirements[/{reqId}]` |
| Audit | `GET /audit-logs` |
| Operations | `/actuator/health{,/liveness,/readiness}`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs`, `/swagger-ui.html` |

## 5. Quality gates: what was actually run

| Gate | Result |
|---|---|
| 1. Compilation (`./mvnw test-compile`, `-Xlint:all`) | ✅ no errors |
| 2. All backend tests (`./mvnw test`) | ✅ **114 tests, 0 failures, 0 errors, 0 skipped** (second run; the first run had 5 failures, fixed as described below) |
| 3. Flyway on a clean database | ✅ in Testcontainers (`DatabaseSchemaIT`), and against Docker Compose PostgreSQL 16: "Successfully applied 8 migrations … now at version v8" |
| 4. Authentication and authorization verified | ✅ `AuthIT` (22 tests), `AuthorizationIT` (18 tests), `AuthServiceTest` |
| 5. Department-level isolation verified | ✅ `DepartmentIsolationIT` (7 tests); manual: the REVENUE admin sees only REVENUE and its services |
| 6. APIs verified through integration tests | ✅ `DepartmentIT`, `UserManagementIT`, `ServiceCatalogueIT`, `ErrorHandlingIT`, `RateLimitIT` |
| 7. Compilation errors resolved | ✅ |
| 8. Failing tests resolved | ✅ |
| 9. Hardcoded credentials check | ✅ grep for secret literals and private keys in sources, config, compose and SQL found none; `.env` is git-ignored |
| 10. Documentation updated | ✅ |

**Manual smoke test of the running application** (local profile, Compose PostgreSQL):
- health was UP;
- 5 published demo services were listed anonymously, and the draft was hidden;
- the demo department admin logged in; `/auth/me` returned `DEPARTMENT_ADMIN`;
- the departments list and service management were scoped to REVENUE;
- refresh rotation returned 200;
- Swagger UI returned 200, and Prometheus metrics were exposed.

**Defects found by the first test run and fixed:**
1. **Privilege-escalation guard too strict.** Platform admins could not grant operational roles. The guard was refined to cover *privileged* permissions only.
2. **Missing requirement id.** Adding a service requirement returned a merged copy without its id. Fixed by flushing instead of merging.
3. **Test assertion bug.** A check that the response never contains a password also matched the `passwordChangeRequired` field name. The assertion was fixed.

**Not run in Phase 1:** `docker build` of the backend image, and the GitHub Actions workflow on GitHub.

Test environment: Windows 11, Temurin JDK 21.0.13, Maven 3.9.11 (wrapper), Docker 29.9.3, PostgreSQL 16 (Testcontainers / Compose).

## 6. Known limitations and remaining risks

See `IMPLEMENTATION_STATUS.md` → "Known limitations and risks". The most significant:
- in-memory rate limiting;
- no e-mail verification or MFA;
- the logging e-mail adapter;
- up to 15 minutes of residual access-token validity after "logout everywhere";
- no cleanup jobs for expired tokens and idempotency records;
- the Docker image and CI not yet verified.

## 7. Exact commands to run the backend

Copy the environment file, then set `DB_PASSWORD` (and optionally `SEED_DEMO_PASSWORD`) in `.env`:

```bash
cp .env.example .env
```

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run the backend (migrates and seeds on start):

```bash
set -a && . ./.env && set +a && cd backend && ./mvnw spring-boot:run
```

Run all tests from `backend/` (Docker must be running):

```bash
./mvnw verify
```

Then open http://localhost:8080/swagger-ui.html. Windows PowerShell equivalents are in `LOCAL_SETUP.md`.

## 8. Next phase (per `IMPLEMENTATION.md`)

Phase 2/3 continues with:
- application drafts and submission: reference numbers from `application_reference_seq`, idempotent submit, workflow instance and first task;
- the React/TypeScript frontend shell for auth and the catalogue.
