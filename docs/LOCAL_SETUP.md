# Local Setup

JanSetu is a portfolio simulation and uses synthetic data only. It is not an official government portal.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (e.g. Eclipse Temurin 21) | `JAVA_HOME` must point to it |
| Docker | Docker Desktop / Engine with Compose v2 | Needed for PostgreSQL and the integration tests (Testcontainers) |
| Git | any recent | |
| Maven | not required | The Maven wrapper (`backend/mvnw`) downloads Maven 3.9.11 |

## 1. Configure the environment

Copy the example environment file, then edit `.env`:

```bash
cp .env.example .env
```

In `.env`:
- set `DB_PASSWORD` to any local value;
- set `SEED_DEMO_PASSWORD` to a 12+ character password with letters and digits if you want demo accounts.

`.env` is git-ignored. Never commit it.

## 2. Start PostgreSQL

```bash
docker compose up -d postgres
```

PostgreSQL 16 listens on `127.0.0.1:5432` and keeps its data in the `postgres-data` volume.

## 3. Run the backend

Bash (Linux, macOS, Git Bash):

```bash
set -a && . ./.env && set +a && cd backend && ./mvnw spring-boot:run
```

PowerShell:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }; cd backend; .\mvnw.cmd spring-boot:run
```

On startup, the backend does three things:
1. **Flyway migrates the schema.** Migrations V1–V8 are applied to an empty database.
2. **Signing keys are generated.** With no `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` set, the local profile creates an ephemeral RSA key pair. Tokens become invalid after a restart.
3. **Demo data is seeded.** `DevDataSeeder` runs in the local profile only and is idempotent. It creates three demo departments, five published services, one draft service and, if `SEED_DEMO_PASSWORD` is set, the demo accounts below.

Alternatively, run the backend in a container: `docker compose --profile app up -d --build`.

## 4. Use the API

| URL | Purpose |
|---|---|
| http://localhost:8080/swagger-ui.html | Interactive API documentation |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/actuator/health | Health (`/liveness`, `/readiness` probes) |
| http://localhost:8080/actuator/prometheus | Metrics |

### Demo accounts

All demo accounts use the password in `SEED_DEMO_PASSWORD`, and all use the domain `@demo.jansetu.test`.

| Email | Role | Department |
|---|---|---|
| platform.admin@demo.jansetu.test | PLATFORM_ADMIN | all (global scope) |
| revenue.admin@demo.jansetu.test | DEPARTMENT_ADMIN | REVENUE |
| revenue.officer@demo.jansetu.test | OFFICER | REVENUE |
| revenue.reviewer@demo.jansetu.test | REVIEWER | REVENUE |
| municipal.admin@demo.jansetu.test | DEPARTMENT_ADMIN | MUNICIPAL |
| municipal.officer@demo.jansetu.test | OFFICER | MUNICIPAL |
| auditor@demo.jansetu.test | AUDITOR | all (read-only audit) |
| management@demo.jansetu.test | MANAGEMENT_VIEWER | all (aggregates) |
| citizen.one@demo.jansetu.test, citizen.two@demo.jansetu.test | CITIZEN | – |

### Example session (curl)

```bash
curl -s -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"email":"revenue.admin@demo.jansetu.test","password":"<SEED_DEMO_PASSWORD>"}' \
  http://localhost:8080/api/v1/auth/login
# -> {"data":{"accessToken":"...","tokenType":"Bearer","expiresIn":900,...}}  (refresh token is in cookies.txt)

curl -s -H "Authorization: Bearer <accessToken>" http://localhost:8080/api/v1/auth/me
curl -s -H "Authorization: Bearer <accessToken>" http://localhost:8080/api/v1/departments
curl -s -b cookies.txt -c cookies.txt -X POST -H 'X-JanSetu-Client: web' http://localhost:8080/api/v1/auth/refresh
```

Password reset e-mails are not sent locally. The reset link is printed to the backend console (`[DEV ONLY - email not sent]`).

## 5. Run the tests

```bash
cd backend && ./mvnw verify
```

Docker must be running: integration tests start a throw-away PostgreSQL 16 container. Test reports are written to `backend/target/surefire-reports`.

## 6. Reset the local database

```bash
docker compose down -v
```

This deletes the `postgres-data` volume. The next backend start migrates and seeds the database again.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `JAVA_HOME` errors from `mvnw` | Point `JAVA_HOME` at a JDK 21 installation |
| `Could not find a valid Docker environment` in tests | Start Docker Desktop |
| `password authentication failed` | `.env` `DB_PASSWORD` differs from the password used when the volume was first created. Run `docker compose down -v` |
| 401 after restarting the backend | Ephemeral JWT keys changed. Log in again, or set persistent keys with `scripts/generate-jwt-keys.sh` |
| 429 on login | Auth endpoints allow 20 requests per minute per client (`AUTH_RATE_LIMIT_PER_MINUTE`) |
