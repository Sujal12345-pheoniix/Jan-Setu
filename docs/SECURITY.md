# Security Requirements and Controls

## 1. Security objectives

Confidentiality, integrity, availability, accountability, least privilege, and defense in depth.

Use OWASP ASVS as a verification reference and OWASP API Security Top 10 as a threat checklist. This project is not automatically ASVS-certified or compliant by adopting this document.

## 2. Authentication

- Use Spring Security; do not invent cryptographic token handling.
- Hash passwords with a framework-supported adaptive password encoder (for example Argon2id or BCrypt with an appropriately reviewed work factor).
- Use generic login/reset responses to reduce account enumeration.
- Rate-limit login, registration, password reset, and refresh endpoints.
- Revoke sessions on logout, password reset, account suspension, or suspected compromise.
- Rotate refresh tokens and detect reuse.
- Use secure, HttpOnly, SameSite cookies for browser refresh sessions if cookie architecture is chosen; implement CSRF protection.
- Keep access tokens short-lived.
- Require stronger controls/MFA for privileged accounts in a real deployment.

## 3. Authorization

- Deny by default.
- Enforce function-level and object-level access.
- Enforce department and task assignment scope.
- Verify citizen ownership on every record access.
- Do not trust role or user IDs supplied by the browser.
- Protect administrative endpoints separately.
- Test horizontal and vertical privilege escalation.

## 4. Input and API security

- Validate DTOs with server-side constraints.
- Use parameterized queries and JPA parameters.
- Avoid unsafe dynamic SQL and unrestricted sorting.
- Limit request body size, page size, and upload size.
- Rate-limit expensive endpoints.
- Configure CORS to explicit origins.
- Set secure headers at application/reverse-proxy layer.
- Do not expose stack traces or internal exception details.
- Use TLS for deployed service-to-service traffic.

## 5. File security

- Allowlist formats.
- Check extension, MIME, and magic bytes.
- Quarantine until scanning completes.
- Store outside public web root.
- Generate opaque object keys.
- Apply authorization on every download.
- Set safe content disposition and content type.
- Consider content disarm/reconstruction for high-risk formats.
- Do not render uploaded HTML/SVG inline without a safe policy.

## 6. Secrets and supply chain

- No secrets in Git, Docker image layers, frontend bundles, logs, or screenshots.
- Use environment variables for local development and a secret manager in deployed environments.
- Rotate credentials.
- Pin dependencies and review updates.
- Scan dependencies and container images.
- Generate an SBOM for release artifacts where practical.
- Protect GitHub Actions permissions and pin third-party actions to reviewed immutable references where feasible.

## 7. Data protection

- Minimize collected personal data.
- Encrypt traffic; evaluate encryption at rest through managed services/storage.
- Restrict database access by service role.
- Mask personal data in logs and analytics.
- Define retention and deletion/anonymization rules.
- Avoid production data in developer laptops and demos.
- Backups are sensitive data and require access controls and retention policies.

## 8. Audit

- Audit privileged actions, decisions, access changes, document verification, exports, and security events.
- Restrict audit read access.
- Protect records from ordinary update/delete operations.
- Monitor unusual access patterns.
- Avoid recording secrets or full documents.

## 9. Security testing

- Authentication and authorization test matrix.
- IDOR/BOLA tests.
- CSRF tests if cookies are used.
- XSS and injection tests.
- File upload bypass tests.
- Rate-limit tests.
- Dependency and image scanning.
- DAST against staging.
- Manual threat review before public deployment.

## 10. Incident response

Prepare a runbook for detection, triage, containment, credential rotation, evidence preservation, recovery, notification obligations, and post-incident review. Actual legal notification timelines must be determined with qualified counsel and applicable rules.
