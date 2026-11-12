# Deployment and Operations

## 1. Environments

| Environment | Data | Purpose |
|---|---|---|
| Local | Synthetic | Developer workflow |
| CI | Synthetic/ephemeral | Automated tests |
| Development | Synthetic | Integration |
| Staging | Synthetic or masked | Release validation |
| Production | Approved real data only | Actual operation |

Never copy production data into local or development environments without a formally approved masking process.

## 2. Containerization

Build separate backend and frontend images. Use multi-stage builds, non-root runtime users, minimal base images, pinned image versions, health checks, and no secrets in image layers.

Local Compose should include:
- backend;
- frontend;
- PostgreSQL;
- optional Redis;
- optional MinIO;
- optional Kafka profile;
- optional Prometheus/Grafana profile.

Use named volumes for local persistent state. Production persistence must use managed/approved storage and backup policy.

## 3. Configuration

Required environment variables should be documented in `.env.example` with placeholders:
- `SPRING_PROFILES_ACTIVE`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_ISSUER_URI` or equivalent issuer/JWK configuration
- `APP_BASE_URL`
- `CORS_ALLOWED_ORIGINS`
- `OBJECT_STORAGE_ENDPOINT`
- `OBJECT_STORAGE_BUCKET`
- `OBJECT_STORAGE_ACCESS_KEY`
- `OBJECT_STORAGE_SECRET_KEY`
- `REDIS_URL` (optional)
- `KAFKA_BOOTSTRAP_SERVERS` (optional)
- `MAIL_PROVIDER_*` (optional)

Do not expose backend secrets through `VITE_` frontend variables.

## 4. CI/CD

Pull request:
1. Checkout.
2. Set up JDK and Node.
3. Build backend.
4. Run unit tests.
5. Run frontend lint/typecheck/tests.
6. Run integration tests.
7. Scan dependencies.
8. Build images (optional PR stage).

Release:
1. Build immutable artifacts.
2. Generate provenance/SBOM where feasible.
3. Scan images.
4. Push to registry.
5. Deploy to staging.
6. Run smoke tests.
7. Require approval for production.
8. Deploy using rolling/blue-green strategy as supported.
9. Verify health and metrics.
10. Roll back if release gates fail.

## 5. Database migrations

- Back up before production migration.
- Prefer backward-compatible expand-and-contract changes.
- Run migrations with controlled privileges.
- Do not let multiple application replicas race to perform uncontrolled schema changes.
- Verify migration status before app rollout.
- Test rollback strategy; many schema/data migrations require forward-fix rather than automatic rollback.

## 6. Health checks

- Liveness: process is alive.
- Readiness: application can serve requests and required dependencies are available according to policy.
- Startup: initialization has completed.
- Do not make liveness depend on every external provider; transient dependency outages should not cause restart loops.

## 7. Operational runbooks

Create runbooks for:
- API unavailable.
- Database unavailable or connection pool exhaustion.
- High latency.
- Kafka backlog / consumer failure.
- Outbox backlog.
- Object storage outage.
- Email provider outage.
- Failed migration.
- Credential compromise.
- Suspected data exposure.
- Backup restoration.
- Disk and storage capacity alerts.

## 8. Deployment checklist

- TLS and domain configured.
- Secrets loaded from secure store.
- Database network is private.
- Storage bucket is private.
- CORS allowlist is exact.
- Admin bootstrap is secure and one-time.
- Default/demo credentials removed or rotated.
- Debug endpoints disabled/restricted.
- Backups enabled and restore tested.
- Alerts configured.
- Privacy notice and terms reviewed for real deployment.
