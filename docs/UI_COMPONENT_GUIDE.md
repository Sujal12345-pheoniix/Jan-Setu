# UI Component Guide

All components are in `frontend/src/components/ui` and exported from `@/components/ui`.

The brand is JanSetu's own: a deep teal and amber palette, defined as Tailwind theme tokens in `src/styles/index.css`. It is deliberately not modelled on any government identity.

| Component | Use | Accessibility notes |
|---|---|---|
| `Button` | Variants `primary`, `secondary`, `danger`, `ghost`, `link`; sizes `sm`/`md`/`lg`; `loading` shows a spinner | `aria-busy` while loading; always `type="button"` unless it submits |
| `LinkButton` | A router link styled as a button | Never nest a `<button>` inside an `<a>` |
| `TextInput`, `TextArea`, `Select`, `Checkbox` | Labelled form controls with `hint` and `error` | `htmlFor`/`id`, `aria-invalid`, `aria-describedby`, required marker plus screen-reader text |
| `FormErrorSummary` | List of form or server errors | `role="alert"`, receives focus |
| `Modal` | Dialog with title, body and footer | `aria-modal`, focus moves in and is trapped, Escape closes, focus returns to the opener |
| `ConfirmDialog` | Confirms destructive or irreversible actions | `danger` variant for destructive actions |
| `ReasonDialog` | Collects a reason; `required` enforces non-empty | Used for reject, return, resolve, reopen, status changes |
| `Card`, `PageHeader`, `Breadcrumbs`, `DescriptionList` | Page structure | Breadcrumbs use `nav[aria-label]` and `aria-current` |
| `Badge`, `StatusBadge` | Status labels | Text is always shown; colour is never the only signal |
| `Tabs` | In-page filters | `role=tablist/tab`, `aria-selected`, arrow-key navigation |
| `DataTable` | Semantic table that scrolls sideways on narrow screens; `hideOnMobile` columns | `<caption>`, `scope="col"` |
| `Pagination` | Previous/next with totals | `nav[aria-label="Pagination"]` |
| `Alert`, `ErrorState`, `EmptyState`, `Skeleton`, `TableSkeleton`, `Spinner`, `PageLoader` | Loading, empty, error and success states | `ErrorState` shows the server request id; Retry is hidden for 403/404 |
| `Toast` (`useToast().notify`) | Success and error feedback | `aria-live="polite"`, auto-dismisses after 5 s, can be dismissed manually |
| `Dropdown` | Small action menu | `aria-haspopup`, `aria-expanded`, closes on Escape and on blur |
| `StatCard` | Dashboard metric tile | Text value, with tone shown as a border accent |

## Feature components

- **`DocumentUploader`:** client pre-check, progress bar, cancel and retry.
- **`DocumentCard`:** preview, download, version history and rejection reason.
- **`DynamicForm`:** renders a service's versioned form schema (text, textarea, number, date, email, phone, checkbox, select) and builds the matching Zod schema.
- **`Timeline`:** status history with timestamps.

## Rules

1. Every data-driven screen handles loading, empty, error, success and forbidden states.
2. Destructive actions always go through `ConfirmDialog` or `ReasonDialog`.
3. Never hide a server error; show its message (and request id).
4. Forms keep valid input after a failed submit, and put server field errors next to the right field.
5. Use `useUrlState` for list filters so views can be shared and survive reloads.
