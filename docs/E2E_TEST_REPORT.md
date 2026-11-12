# End-to-End Test Report

_Run date: 2026-11-12 · Playwright 1.49.1 · Chromium · real backend (Spring Boot, local profile) + PostgreSQL 16 (Docker Compose) · synthetic data. No mocks._

## Result: 11 / 11 passed (final run, about 1.8 minutes)

| Spec | Test | Project |
|---|---|---|
| application-lifecycle | Citizen registers, searches the catalogue, starts an application, fills the dynamic form, uploads two PDFs, accepts the declaration, submits and sees a `JAN-YYYY-NNNNNN` reference | desktop |
| application-lifecycle | Officer sees the unassigned task, starts the review, requests clarification | desktop |
| application-lifecycle | Citizen gets the notification, reads the question, responds → RESUBMITTED | desktop |
| application-lifecycle | Officer verifies both documents and recommends; the recommending officer is offered no Approve action | desktop |
| application-lifecycle | Reviewer approves and completes; the citizen sees the decision, and internal recommendation remarks are hidden | desktop |
| grievance-and-access | Citizen raises a grievance (client validation first); officer acknowledges, adds an internal note and a public reply; the citizen sees only the public reply | desktop |
| grievance-and-access | Anonymous user is redirected to login; a citizen opening `/admin/users` sees the 403 page; unknown routes show the 404 page | desktop |
| grievance-and-access | Department admin sees only own-department services and creates a draft service | desktop |
| grievance-and-access | Keyboard: skip link is the first Tab stop; Tab moves from email to password | desktop |
| responsive | Landing page and catalogue have no horizontal overflow at Pixel 7 width | mobile |
| responsive | Mobile navigation drawer opens, navigates and fits the screen | mobile |

## Defects found by e2e and fixed

| Defect | Fix |
|---|---|
| Modal stole focus back to its first field on every re-render (typing in dialogs was broken) | Focus effect no longer depends on the `onClose` identity |
| "New service" could submit an empty department while the dropdown appeared to show one | Department derived at click time; submit disabled until departments load |
| The submission confirmation (reference number) could vanish immediately | Only the lists are refreshed after submit |
| Any grievance action cleared the officer's unsent comment | The comment box is cleared only after posting a comment |

Earlier failures in intermediate runs were test-side issues (selectors matching text shown twice, races). They were fixed in the tests, not by weakening assertions.

## How to run

The backend must be running in the local profile, with the seed enabled and `SEED_DEMO_PASSWORD` set (see LOCAL_SETUP.md). Install the browser once, then run the suite from `frontend/`:

```bash
npx playwright install chromium
```

```bash
E2E_PASSWORD=<SEED_DEMO_PASSWORD> npx playwright test
```

Playwright starts the Vite dev server itself. The HTML report is written to `frontend/playwright-report/`.

## Not covered by automation

- Automated accessibility scanning (axe) is not wired in; keyboard flows and semantics are covered manually and by RTL tests.
- Download preview in a new tab (blob URL) is verified in component code, not in e2e.
- E2E does not run in GitHub Actions: it needs the backend and a database. Frontend CI runs the type check, lint, unit/component tests and build.
