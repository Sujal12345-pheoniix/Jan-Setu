# Deploying JanSetu: Vercel + Render + Neon

JanSetu is a portfolio simulation. Deploy it with synthetic data only. Never present it as an official government portal.

```
Browser ──HTTPS──▶ Vercel (React static site)
                      │  /api/*  (rewrite = same-origin proxy)
                      ▼
                 Render (Spring Boot Docker container) ──TLS──▶ Neon PostgreSQL
```

**Why Vercel proxies `/api`.** The refresh token is an `HttpOnly; Secure; SameSite=Strict` cookie, scoped to `/api/v1/auth`. The rewrite in `frontend/vercel.json` makes the browser see one site. That keeps the cookie working without relaxing it to `SameSite=None`. It is the production equivalent of the Vite dev-server proxy.

**Why the backend is not on Vercel.** Vercel runs static sites and short-lived serverless functions. A long-running Spring Boot server needs a container host. These steps use Render; Railway, Fly.io and Koyeb work the same way.

## 1. Prepare secrets (on your machine)

1. Rotate the Neon password (Neon console → Roles → Reset password). This is needed if the password was ever shared in chat or in a ticket.
2. Generate JWT signing keys:
   ```bash
   bash scripts/generate-jwt-keys.sh
   ```
   This prints `JWT_PRIVATE_KEY=...` and `JWT_PUBLIC_KEY=...`. Keep them secret.
3. Build the JDBC URL from Neon's **direct** host, the one without `-pooler`. Flyway's migration lock does not work through the pooler.
   ```
   jdbc:postgresql://ep-<id>.<region>.aws.neon.tech/neondb?sslmode=require&channelBinding=require
   ```

## 2. Backend on Render

1. Render → **New → Blueprint** → pick the GitHub repository. Render reads `render.yaml`.
2. Fill in the prompted secrets:

| Variable | Value |
|---|---|
| `DB_URL` | JDBC URL from step 1.3 |
| `DB_USERNAME` | `neondb_owner` (or a dedicated role) |
| `DB_PASSWORD` | rotated Neon password |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | from the script |
| `CORS_ALLOWED_ORIGINS` | `https://<your-app>.vercel.app,https://<your-app>-*.vercel.app` |
| `APP_BASE_URL` | `https://<your-app>.vercel.app` (used in password-reset links) |
| `BOOTSTRAP_ADMIN_EMAIL` | e.g. `admin@yourdomain.test` |
| `BOOTSTRAP_ADMIN_PASSWORD` | 12–64 characters, letters and digits |
| `SEED_DEMO_PASSWORD` | only if you set `SEED_ENABLED=true` for demo data |

3. **Deploy.** On first start the backend does three things:
   - **Migrates Neon.** Flyway applies any pending migrations (V1–V9) and Hibernate validates the schema.
   - **Creates the first admin.** `AdminBootstrap` creates the platform administrator, but only when no administrator exists yet.
   - **Seeds demo data, if enabled.** With `SEED_ENABLED=true` it creates synthetic departments, services and one account per role (`<role>@demo.jansetu.test`, password `SEED_DEMO_PASSWORD`).
4. Check `https://jansetu-backend.onrender.com/actuator/health`. It should return `{"status":"UP",...}`.
5. Sign in as the bootstrap admin (once the frontend is up), change the password, then **delete `BOOTSTRAP_ADMIN_*`** from Render.

If Render gives the service a different URL, update both `frontend/vercel.json` and `JWT_ISSUER`.

## 3. Frontend on Vercel

1. Vercel → **Add New → Project** → import the repository.
2. **Root Directory:** `frontend`. Vercel reads `frontend/vercel.json`, which sets the Vite build, the `dist` output, the API proxy, the SPA fallback and the security headers.
3. No environment variables are needed: `VITE_API_BASE_URL` defaults to `/api/v1`, which goes through the proxy.
4. **Deploy**, then open the site, sign in and test a full journey.

## 4. Known limitations of this free-tier setup

| Limitation | Effect | Fix |
|---|---|---|
| Uploaded documents live on the container's local disk | Lost on every redeploy/restart on Render free | Add a persistent disk (paid) or implement the planned S3/MinIO `DocumentStoragePort` adapter |
| Render free instances sleep when idle | First request after idle takes 30–60 s | Paid instance or an uptime ping |
| In-memory rate limiter | Per instance only | Keep one instance or move counters to Redis |
| E-mail is a logging adapter | Password-reset links are not e-mailed | Add an e-mail provider adapter |
| Actuator (incl. `/actuator/prometheus`) is on the public port | Metrics are publicly readable | Set `MANAGEMENT_SERVER_PORT` on a host with private networking, or restrict at the proxy |

## 5. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| 403 on every POST from the Vercel site | `CORS_ALLOWED_ORIGINS` doesn't include the exact Vercel URL |
| Logged out after each refresh | Site not served over HTTPS, or `/api` isn't going through the Vercel rewrite |
| Backend fails at start: `JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be configured` | Keys missing from Render |
| Flyway hangs or fails at start | `DB_URL` uses the `-pooler` host; use the direct host |
| `password authentication failed` | Neon password rotated but not updated on Render |
