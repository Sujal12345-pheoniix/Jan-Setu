# Phase 2 Report: Frontend and End-to-End Business Workflows

_Date: 2026-11-12_ · JanSetu is a portfolio simulation, not an official government portal.

## 1. Backend added to support the UI

The Phase 2 prompt requires the backend to implement any missing endpoint before a frontend feature counts as complete. These were added:

- **V9 migration:**
  - grievance lifecycle `SUBMITTED → ACKNOWLEDGED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`, plus `REOPENED` (once, within 14 days);
  - grievance status history and evidence documents;
  - built-in workflow `STANDARD_REVIEW_APPROVAL_V1`;
  - report permissions for officers and reviewers.
- **Applications:**
  - drafts with versioned form-schema validation;
  - document upload (allowlist, magic bytes, size, private storage, versions);
  - submission with a reference number (`application_reference_seq`) and an Idempotency-Key;
  - start review, request clarification (the SLA pauses), recommend (mandatory documents must be verified), approve, reject (reason required), return for correction, complete;
  - separation of duties: the recommender can never approve;
  - task queue with claiming and assignment to eligible department members only;
  - backend-computed `availableActions`.
- **Grievances:** register, acknowledge, assign, start, internal notes vs public replies, resolve, reopen, close, feedback, evidence.
- **Notifications:** in-app notifications on key events, unread count, read/read-all, per-category preferences.
- **Reports** (aggregate-only, scoped): summary, daily volume, department performance, workload, grievance trends.
- **Other:** audit CSV export (bounded, formula-injection safe, audited), account activity, public department list, roles on member lists.
- **Deployment:**
  - one-time admin bootstrap through `BOOTSTRAP_ADMIN_*`;
  - opt-in demo seed;
  - PaaS-ready production profile (`PORT`, forwarded headers, small connection pool);
  - fixed Docker image storage permissions;
  - CORS origin patterns.

## 2. Frontend

React, TypeScript, Vite, Tailwind, TanStack Query, React Hook Form + Zod, lucide-react and Recharts. Details are in FRONTEND_ARCHITECTURE.md, UI_COMPONENT_GUIDE.md and USER_GUIDE.md.

| Portal | Screens |
|---|---|
| Public | Landing, service catalogue (search, filters, pagination), service detail, login, register, forgot/reset password, 403, 404 |
| Citizen | Dashboard, my applications, 3-step application wizard with uploads, tracking (timeline, clarification response, decision), grievances (new, list, detail with reopen/close/feedback/evidence), notifications, profile & security (profile, password, preferences, devices, activity) |
| Officer / Reviewer | Task queue (mine, unassigned, overdue, stage), department applications with filters, case detail (verification checklist, preview/download, all decisions with mandatory reasons, assignment, internal comments, SLA warnings, history), grievance handling |
| Department admin | Service catalogue management (draft, form builder, requirements, publish/deprecate), departments & staff, Workflow & SLA (read-only), reports |
| Platform admin | Users (create staff, roles, suspend/deactivate/reactivate), departments, roles & permissions (custom roles), system health |
| Auditor | Audit log search, event detail, CSV export |
| Management | Reports with date range: KPIs, volume chart, status distribution, department performance, workload, grievance trends |

## 3. Quality gates (actually run)

| Gate | Result |
|---|---|
| TypeScript `tsc -b` (strict) | ✅ 0 errors |
| ESLint (`--max-warnings=0`) | ✅ |
| Production build (`vite build`) | ✅ route-level code splitting; main bundle ≈113 kB gzip |
| Vitest unit/component tests | ✅ 32/32. Covers the API client (refresh coordination, session expiry, no token storage), guards and role-based navigation, login/registration validation, the dynamic form, draft save with server field errors, submission, clarification response, officer reject-requires-reason and backend-authorization errors, grievance submission and reopen visibility, department administration with idempotency, error states, upload pre-checks, the new-service department fix |
| Playwright e2e against the real backend | ✅ 11/11 (desktop + mobile), see E2E_TEST_REPORT.md |
| Backend `mvnw test` | ✅ 131/131 (unit, ArchUnit, Testcontainers integration incl. full application and grievance lifecycles) |
| Fresh clone of GitHub `main` → `mvnw verify` | ✅ (this caught `integration/storage` being excluded by `.gitignore`, now fixed) |
| Docker image built and run in the production profile on an empty DB | ✅ migrations, admin bootstrap, login, refresh cookie, CORS allow/deny, Swagger disabled, container healthy |
| Neon migrated to V9 | ✅ 9/9 migrations, 39 tables |

**Checks:**
- **Mobile:** layouts verified at Pixel 7 width (no horizontal overflow, navigation drawer).
- **Keyboard:** skip link and form order.
- **Routes:** 403 and 404 pages.
- **Unauthorized access:** handled in both UI and API.
- **Console errors:** none from the app (React Router v7 future-flag warnings only).
- **Empty and error states:** present on every list.

## 4. Known gaps and risks

- **Workflow and SLA editing:** no workflow designer or SLA policy editor (the built-in workflow is shown read-only), and no notification template editor.
- **No e-mail delivery.** Preferences are stored, but nothing is sent.
- **Storage:** documents are on local disk, so they are lost on free PaaS redeploys. The S3/MinIO adapter is still to do.
- **No automated axe scan; English only.**
- **Rate limiting is in memory** (one instance).
- **Grievance lifecycle differs from WORKFLOW.md:** it follows the Phase 2 prompt rather than the original docs, as noted in V9.

## 5. Deployment

See DEPLOYMENT_VERCEL_RENDER.md: frontend on Vercel (`frontend/vercel.json`), backend on Render (`render.yaml`), database on Neon.
