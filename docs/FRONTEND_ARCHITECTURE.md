# Frontend Architecture

React 18 + TypeScript (strict) + Vite 6 + Tailwind CSS 4.

| Library | Use |
|---|---|
| React Router 6 | Routing |
| TanStack Query 5 | Server state |
| React Hook Form + Zod | Forms and validation |
| lucide-react | Icons |
| Recharts | Charts on the reports page only (lazy-loaded) |

Redux is **not** used: there is no shared client state that justifies it. Server state lives in TanStack Query; the session lives in a small React context.

## Layout

```text
frontend/src
├── app/          App.tsx (providers), queryClient.ts (retry policy)
├── api/          client.ts — the ONLY HTTP client (auth, refresh, errors, uploads, downloads)
├── auth/         AuthProvider (session restore, login/logout, can()), guards (RequireAuth, RequirePermission)
├── components/   ui/ design system (Button, Field/TextInput/Select/TextArea/Checkbox, Modal/ConfirmDialog/
│                 ReasonDialog, Card, PageHeader, Breadcrumbs, Badge/StatusBadge, Tabs, Pagination, DataTable,
│                 Alert/ErrorState/EmptyState/Skeleton, Toast, Dropdown, StatCard), Logo
├── layouts/      PublicLayout (header, demo banner, footer), AppShell (role-aware sidebar, mobile drawer)
├── pages/        Landing, NotFound, Unauthorized
├── routes/       AppRoutes (lazy, permission-guarded routes), navigation (role-aware menu)
├── features/     authentication, citizen, applications, documents, grievances, services, tasks, workflows,
│                 departments, administration, notifications, analytics, audit — each with api.ts + pages
├── hooks/        useUrlState (filters/pagination in the URL), useDebounced
├── types/        api.ts — TypeScript mirrors of backend DTOs
├── validation/   Zod schemas mirroring server rules (password policy, grievance form, …)
├── utils/        formatting (dates in local time from UTC), cn()
└── styles/       Tailwind theme tokens (brand palette, focus ring, reduced motion)
```

## Authentication and session handling

- **Access token in memory only.** The JWT lives in a module variable in `api/client.ts`. It is never written to localStorage, sessionStorage or cookies (a unit test asserts this).
- **Refresh token in an HttpOnly cookie** (`SameSite=Strict`, `Path=/api/v1/auth`). JavaScript can't read it. Requests to `/auth/refresh` and `/auth/logout` add `X-JanSetu-Client: web` (CSRF defence required by the backend).
- **Session restore.** On page load, `AuthProvider` calls `/auth/refresh`, then `/auth/me`.
- **Concurrency.** When several requests get a 401 at once, they share one in-flight refresh, and each request is retried once.
- **Session expiry.** If the refresh fails, a session-expired event clears the cache and the login page explains what happened.
- **Logout.** Calls `/auth/logout`, clears the in-memory token and the query cache.
- **Same origin.** In development Vite proxies `/api` to the backend; in production Vercel rewrites `/api` to the backend. Either way the browser sees one origin.

## Authorization in the UI

The UI uses three mechanisms, all for user experience only; the backend re-checks every rule.
- **Menus and routes.** Navigation items and routes are shown only when the user holds the permission (`routes/navigation.ts`, `RequirePermission`).
- **Case actions.** Buttons on applications and grievances come from the backend's `availableActions`, computed from status, assignment, scope and separation of duties.
- **Errors.** A 403 or 404 from the API is shown as is; the UI never second-guesses it.

## Data fetching conventions

- Every query passes TanStack's `AbortSignal` through to fetch, so cancellation works.
- **Retries:** only network errors and 5xx, at most twice; never 4xx, and never mutations.
- **Lists:** pagination, filters and sort live in the URL (`useUrlState`) and use `placeholderData` to avoid flicker.
- **After a mutation:** precise query invalidation, or `setQueryData` with the server's response.
- **Idempotency keys:** create commands (department, service, staff user, application submit) send an `Idempotency-Key` that stays stable across retries of the same form.
- **File uploads** use XMLHttpRequest for progress and cancellation. Files are pre-checked on the client (type and size); the server validates magic bytes.
- **Downloads** are authenticated blob fetches: PDFs and images open as a preview, other types download.

## Code splitting

Route-level `lazy()` chunks. Recharts (~400 kB) loads only on the Reports page; the main bundle is about 113 kB gzip.

## Accessibility

- **Structure:** skip link, landmarks, labelled form controls with `aria-invalid`/`aria-describedby`, and error summaries that take focus.
- **Dialogs:** focus moves into the dialog, Tab is trapped, Escape closes, and focus returns to the opener.
- **Feedback:** status badges always carry text, so colour is never the only signal. Toasts are `aria-live`.
- **Tables and charts:** tables have captions; charts have text summaries.
- **Motion and focus:** reduced motion is honoured, and the focus ring is visible.
- **Target:** WCAG 2.1 AA (not certified).

## Backend gaps found while building the UI (now implemented)

| Endpoint | Added for |
|---|---|
| `GET /departments/public` | Catalogue filter and grievance form (citizens can't use `GET /departments`) |
| `GET /auth/activity` | Account activity on the profile page |
| `GET /audit-logs/export` | Auditor CSV export |
| Applications, tasks, documents, grievances, notifications, reports APIs | All Phase 2 portals |
| `roles` on `GET /departments/{id}/members` | Showing only eligible assignees |

## Not yet implemented (documented, not faked)

- **Workflow designer and SLA policy editor.** The built-in workflow and the service processing days are shown read-only on the Workflow & SLA page.
- **Notification template editor.** Templates are defined in code.
- **E-mail delivery.** Preferences are stored, but nothing is sent.
- **Localisation.** The UI is English only.
