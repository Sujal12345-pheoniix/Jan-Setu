# API Specification

## 1. API conventions

- Base path: `/api/v1`
- Transport: HTTPS in deployed environments.
- Content type: `application/json`; `multipart/form-data` for uploads.
- IDs: UUID.
- Time: ISO-8601 UTC.
- Collection endpoints: pagination, sorting allowlist, bounded page size.
- Errors: RFC 9457 Problem Details-style response.
- Authentication: bearer access token or configured secure session model.
- Idempotency: support for sensitive create/submit commands where retry risk exists.

## 2. Standard success response

```json
{
  "data": {
    "id": "UUID",
    "referenceNumber": "JAN-2026-000123",
    "status": "SUBMITTED"
  },
  "meta": {
    "requestId": "correlation-id"
  }
}
```

For collection endpoints use a page envelope with `content`, `page`, `size`, `totalElements`, and `totalPages`.

## 3. Standard error response

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/applications",
  "errorCode": "VALIDATION_ERROR",
  "requestId": "correlation-id",
  "fieldErrors": [
    {
      "field": "serviceId",
      "message": "A valid service is required."
    }
  ]
}
```

Do not return internal exception messages, SQL, stack traces, secrets, or infrastructure details.

## 4. Authentication

| Method | Endpoint | Permission | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public, rate-limited | Citizen registration |
| POST | `/auth/login` | Public, rate-limited | Authenticate |
| POST | `/auth/refresh` | Valid refresh session | Rotate session |
| POST | `/auth/logout` | Authenticated | Revoke session |
| POST | `/auth/forgot-password` | Public, rate-limited | Start reset |
| POST | `/auth/reset-password` | Valid reset token | Set new password |
| GET | `/auth/me` | Authenticated | Current principal |

## 5. Users and administration

| Method | Endpoint | Permission |
|---|---|---|
| GET | `/users` | USER:READ |
| POST | `/users` | USER:CREATE |
| GET | `/users/{id}` | USER:READ + scope |
| PATCH | `/users/{id}` | USER:UPDATE + scope |
| POST | `/users/{id}/deactivate` | USER:DEACTIVATE |
| GET | `/roles` | ROLE:READ |
| POST | `/roles` | ROLE:CREATE |
| PUT | `/roles/{id}/permissions` | ROLE:MANAGE |

## 6. Departments and services

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/departments` | List authorized departments |
| POST | `/departments` | Create department |
| PATCH | `/departments/{id}` | Update department |
| GET | `/services` | Browse published services |
| POST | `/services` | Create service draft |
| POST | `/services/{id}/publish` | Publish version |
| GET | `/services/{id}/versions/{version}` | Retrieve immutable version |

## 7. Applications

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/applications` | Create draft |
| GET | `/applications` | List accessible applications |
| GET | `/applications/{id}` | Details with scope check |
| PATCH | `/applications/{id}` | Update draft |
| POST | `/applications/{id}/submit` | Submit |
| POST | `/applications/{id}/withdraw` | Withdraw if policy permits |
| GET | `/applications/{id}/history` | Citizen-visible timeline |
| GET | `/applications/by-reference/{reference}` | Authorized reference lookup |

## 8. Documents

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/applications/{id}/documents` | Upload |
| GET | `/documents/{id}/metadata` | Authorized metadata |
| GET | `/documents/{id}/download` | Authorized download |
| POST | `/documents/{id}/verify` | Officer verification |
| POST | `/documents/{id}/reject` | Reject with reason |

## 9. Tasks and workflow

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/tasks/my` | My task queue |
| GET | `/tasks/{id}` | Task detail |
| POST | `/tasks/{id}/assign` | Assign |
| POST | `/tasks/{id}/claim` | Claim queue task |
| POST | `/tasks/{id}/clarification` | Request clarification |
| POST | `/tasks/{id}/recommend` | Recommend decision |
| POST | `/tasks/{id}/approve` | Approve |
| POST | `/tasks/{id}/reject` | Reject |
| POST | `/tasks/{id}/reassign` | Reassign |
| GET | `/workflow-instances/{id}/history` | Workflow history |

## 10. Grievances

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/grievances` | Register |
| GET | `/grievances` | List within scope |
| GET | `/grievances/{id}` | Details |
| POST | `/grievances/{id}/assign` | Assign |
| POST | `/grievances/{id}/notes` | Internal note |
| POST | `/grievances/{id}/responses` | Public response |
| POST | `/grievances/{id}/resolve` | Resolve |
| POST | `/grievances/{id}/appeal` | Appeal |
| POST | `/grievances/{id}/feedback` | Feedback |

## 11. SLA, notifications, audit, reporting

- `GET /sla/policies`, `POST /sla/policies`, `POST /sla/policies/{id}/publish`
- `GET /notifications`, `POST /notifications/{id}/read`
- `GET /audit-logs` (strictly permissioned and filtered)
- `GET /reports/department-summary`
- `GET /reports/application-aging`
- `GET /reports/sla-performance`
- `POST /reports/exports` for asynchronous bounded exports

## 12. API contract rules

- Use `POST` for commands that create a business action.
- Use `PATCH` for partial resource updates; only allow permitted fields.
- Do not allow clients to set status directly through generic update endpoints.
- Use `409 Conflict` for stale version or invalid state conflict.
- Use `401` for unauthenticated and `403` for authenticated but unauthorized.
- Use `404` where revealing record existence would create an enumeration risk.
- Use `429` for rate limits.
- Use `413` for oversized upload.
- Validate sort fields against an allowlist.
- Avoid returning unbounded nested collections.
- Document every endpoint's permission, ownership rules, request, response, and error cases in OpenAPI.
