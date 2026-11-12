# JanSetu
## Enterprise Government-Style Service Delivery & Grievance Management Platform

**Status:** Phase 1 (backend core) and Phase 2 (React frontend and case workflows) implemented. See docs/PHASE_2_REPORT.md and docs/DEPLOYMENT_VERCEL_RENDER.md.  
**Documentation version:** 1.0.0  
**Project type:** Portfolio / learning / reference implementation  
**Important:** JanSetu is an independently developed simulation. It is not an official government portal, is not endorsed by a government body, and must not be represented as a real government or PSU deployment.

JanSetu is a secure, workflow-driven web platform for managing public-service-style applications, departmental reviews, document verification, grievances, service-level agreements (SLAs), notifications, audit trails, and operational analytics.

The project is designed to demonstrate enterprise full-stack engineering using Java, Spring Boot, React, relational data modelling, API security, automated testing, containerization, CI/CD, observability, and carefully selected asynchronous integrations.

## Product goals

1. Give citizens a clear way to discover services, submit applications, upload documents, and track progress.
2. Give officers controlled work queues for verification, review, approval, rejection, and clarification.
3. Give department administrators tools to manage staff, services, workflows, and SLA policies.
4. Give platform administrators secure controls for users, roles, permissions, and configuration.
5. Give auditors traceable records of significant actions.
6. Give management reliable operational metrics without exposing unnecessary personal data.

## Core personas

| Persona | Main capabilities |
|---|---|
| Citizen | Manage own profile, applications, documents, and grievances |
| Officer | Process assigned tasks within authorized departments |
| Reviewer | Review recommendations and make permitted decisions |
| Department Admin | Manage department staff, services, queues, and policies |
| Platform Admin | Manage platform-wide configuration and access |
| Auditor | Read permitted audit and compliance information |
| Management Viewer | View aggregated dashboards and reports |

## Technology baseline

| Area | Baseline |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Maven |
| API | REST/JSON, OpenAPI |
| Security | Spring Security, OAuth2 Resource Server/JWT, secure refresh-session design |
| Persistence | PostgreSQL, Spring Data JPA, Hibernate, Flyway |
| Frontend | React, TypeScript, Vite, Tailwind CSS |
| Server state | TanStack Query |
| Client state | Local state first; Redux Toolkit only where shared client state justifies it |
| Cache / coordination | Redis where justified |
| Object storage | S3-compatible storage / MinIO for local development |
| Messaging | Kafka only after core transactional workflows are stable |
| Tests | JUnit 5, Mockito, Spring Boot Test, Testcontainers, Vitest/RTL, Playwright |
| Delivery | Docker, Docker Compose, GitHub Actions |
| Observability | Actuator, Micrometer, Prometheus, Grafana, structured logs |

Pin exact compatible dependency versions in the Maven and npm lock files. Do not copy version numbers from this document without checking current compatibility.

## Repository map

```text
jansetu/
├── README.md
├── LICENSE
├── .gitignore
├── .editorconfig
├── .env.example
├── backend/
├── frontend/
├── infrastructure/
├── scripts/
└── docs/
    ├── PRD.md
    ├── SRS.md
    ├── STRUCTURE.md
    ├── WORKFLOW.md
    ├── BUSINESS_LOGIC.md
    ├── FUNCTIONAL_SPECIFICATION.md
    ├── DATABASE_DESIGN.md
    ├── API_SPECIFICATION.md
    ├── INTEGRATION_DESIGN.md
    ├── SECURITY.md
    ├── IMPLEMENTATION.md
    ├── TESTING_STRATEGY.md
    ├── DEPLOYMENT_OPERATIONS.md
    ├── OBSERVABILITY.md
    ├── PRIVACY_DATA_GOVERNANCE.md
    ├── ACCESSIBILITY_UX.md
    ├── PERFORMANCE_SCALABILITY.md
    ├── BACKUP_DISASTER_RECOVERY.md
    ├── THREAT_MODEL.md
    ├── ACCEPTANCE_CRITERIA.md
    ├── RELEASE_PLAN.md
    ├── CONTRIBUTING.md
    ├── GLOSSARY.md
    ├── diagrams/
    └── ADR/
```

## Quick start (backend, verified in Phase 1)

Requires JDK 21 and Docker. Full instructions, including PowerShell equivalents and the demo accounts, are in [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md).

1. Copy the environment file, then set `DB_PASSWORD` (and optionally `SEED_DEMO_PASSWORD`) in `.env`:

   ```bash
   cp .env.example .env
   ```

2. Start PostgreSQL 16:

   ```bash
   docker compose up -d postgres
   ```

3. Start the backend. Flyway migrates the database and local demo data is seeded on start:

   ```bash
   set -a && . ./.env && set +a && cd backend && ./mvnw spring-boot:run
   ```

4. Open http://localhost:8080/swagger-ui.html.

5. Run the tests from `backend/`. These are unit, architecture and Testcontainers integration tests:

   ```bash
   ./mvnw verify
   ```

Current status: [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md). Phase 1 report: [docs/PHASE_1_REPORT.md](docs/PHASE_1_REPORT.md).

Never commit `.env`, credentials, production data, private keys, access tokens, or uploaded user documents.

## Engineering principles

- Secure by default; deny access unless explicitly granted.
- Enforce authorization and ownership on the server, not only in the UI.
- Keep business rules in application/domain services, not controllers.
- Use DTOs at API boundaries; do not serialize persistence entities.
- Use database constraints as a second line of integrity protection.
- Use transactions for multi-record business operations.
- Make retryable background work idempotent.
- Use pagination and bounded queries for collections.
- Store uploaded files outside the web root and serve them only after authorization.
- Make migrations explicit and reviewable.
- Add tests with each feature.
- Document architectural decisions in ADRs.
- Avoid microservices until independent deployment, scaling, ownership, or fault isolation provides a real benefit.

## Documentation index

Start with `docs/PRD.md`, then `docs/STRUCTURE.md`, `docs/WORKFLOW.md`, `docs/BUSINESS_LOGIC.md`, `docs/DATABASE_DESIGN.md`, `docs/API_SPECIFICATION.md`, and `docs/IMPLEMENTATION.md`.

## Current delivery status

This documentation package defines the intended product and engineering approach. It does **not** mean the software has been implemented, security-tested, load-tested, deployed, certified, or audited. All production-readiness claims must be supported by actual test and operational evidence.
