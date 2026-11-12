# Threat Model

## 1. Assets
- User credentials and refresh sessions.
- Citizen profile and application data.
- Uploaded documents.
- Workflow decisions and approvals.
- Role/permission configuration.
- Audit records.
- Database and storage credentials.
- Availability of the portal.

## 2. Trust boundaries
- Browser ↔ API.
- API ↔ database.
- API ↔ object storage.
- API ↔ Redis/Kafka.
- Application ↔ email provider.
- CI/CD ↔ artifact registry/deployment environment.
- Admin users ↔ privileged management functions.

## 3. Threat scenarios and mitigations

| Threat | Example | Controls |
|---|---|---|
| BOLA/IDOR | Citizen changes application UUID | Object ownership and scope checks |
| Privilege escalation | User assigns self admin role | Server-side permission checks; audit |
| Credential stuffing | Automated login attempts | Rate limits, lockout/risk controls |
| Token theft/replay | Stolen refresh token | Rotation, revocation, reuse detection |
| CSRF | Forged cookie-authenticated action | SameSite and CSRF token controls |
| XSS | Untrusted content rendered | Output encoding, sanitization, CSP |
| SQL injection | Unsafe dynamic query | Parameterized queries, validation |
| Malicious upload | Polyglot or malware file | Signature checks, quarantine, scanning |
| Data leakage | Public storage bucket | Private bucket, scoped download |
| Event replay | Duplicate Kafka event | Idempotent consumer/inbox |
| Race condition | Two approvals at once | Optimistic locking and constraints |
| Log leakage | Token written to logs | Redaction and safe structured logging |
| Supply chain | Compromised dependency/action | Pin, scan, review, SBOM |
| DoS | Large query/upload | Limits, rate limiting, timeouts |
| Insider misuse | Unauthorized record access | Least privilege, audit, monitoring |

## 4. Abuse cases to test
- Guess another user's application ID.
- Change department ID in a request.
- Reuse a refresh token after rotation.
- Upload executable content disguised as PDF.
- Request an unbounded report.
- Submit an approval twice concurrently.
- Attempt to read internal grievance notes as a citizen.
- Manipulate client-side role claims.
- Use expired or revoked credentials.
- Trigger repeated notification retries.

## 5. Residual risk
No document eliminates risk. Reassess the threat model when adding external identity, payment, mobile, multi-tenant, or real personal-data processing capabilities.
