# ADR-005: Self-issued RS256 Access Tokens and Rotating Refresh Sessions

- **Status:** Accepted
- **Date:** 2026-11-12

## Context
JanSetu needs stateless API authentication for a browser SPA, revocable sessions, protection against stolen refresh tokens, and no long-lived secrets in browser storage. An external identity provider is out of scope for the MVP.

## Decision
- The backend issues **RS256 JWT access tokens** (15 min) with minimal claims: `iss`, `aud`, `sub` (user id), `iat`, `nbf`, `exp`, `jti`, `token_use=access`. No e-mail, roles or personal data.
- Tokens are validated by Spring Security's resource server (signature, issuer, audience, expiry, `token_use`).
- **Authorities are loaded from the database on every request** (status, roles, permissions, department memberships). Suspension and role changes take effect immediately. Tokens issued before the last password change are rejected.
- **Refresh tokens** are opaque random values. Only their SHA-256 hash is stored in `refresh_sessions`. They are sent only as an `HttpOnly; SameSite=Strict; Path=/api/v1/auth` cookie.
- Refresh **rotates** the token on every use. Presenting an already rotated token revokes the whole session family (reuse detection).
- The cookie endpoints (`/auth/refresh`, `/auth/logout`) also require an `X-JanSetu-Client` header as CSRF defence. A cross-site form cannot send a custom header, and the CORS allowlist blocks it from other origins.
- Signing keys come from `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`. Only the `local` and `test` profiles may fall back to an ephemeral key pair.

## Consequences
- **Per-request lookup cost:** about three small indexed queries per authenticated request. A short-lived cache (Redis) is possible later if load tests justify it.
- **Logout leaves access tokens briefly valid:** "logout everywhere" revokes refresh sessions immediately. Already issued access tokens stay valid until expiry (≤ 15 min) unless the account is suspended or the password changes.
- **Concurrent refreshes can log a user out:** two tabs refreshing at the same moment with the same token trigger reuse detection. The user must sign in again. A grace window can be added later.
- **Key rotation is a restart:** rotating keys means deploying new keys and restarting. A JWKS endpoint with multiple `kid`s is a future enhancement.
