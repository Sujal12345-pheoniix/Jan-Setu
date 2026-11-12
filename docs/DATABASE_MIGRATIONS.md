# Database Migrations

Flyway owns the schema. Hibernate runs with `ddl-auto: validate`: it only checks that entities match the schema and never changes it. Migrations live in `backend/src/main/resources/db/migration` and run automatically at startup, in every profile.

## Migration inventory

| Version | File | Contents |
|---|---|---|
| V1 | `V1__identity_and_access.sql` | `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `refresh_sessions` (hashed tokens, rotation families), `password_reset_tokens` |
| V2 | `V2__departments.sql` | `departments`, `department_memberships` |
| V3 | `V3__service_catalogue.sql` | `services` (working copy), `service_requirements`, `service_versions` (immutable snapshots, enforced by trigger) |
| V4 | `V4__workflow_and_sla_definitions.sql` | `sla_policies`, `sla_policy_versions`, `workflow_definitions`, `workflow_definition_versions`, `workflow_steps`. Adds workflow/SLA links to `services` |
| V5 | `V5__applications_documents_workflow_runtime.sql` | `application_reference_seq`, `applications`, `application_status_history`, `application_assignments`, `application_comments`, `application_decisions`, `clarification_requests`, `documents`, `document_versions`, `workflow_instances`, `workflow_tasks`, `workflow_history` |
| V6 | `V6__grievances.sql` | `grievance_reference_seq`, `grievances`, `grievance_assignments`, `grievance_comments`, `grievance_feedback` |
| V7 | `V7__notifications_audit_platform_ops.sql` | `notifications`, `notification_preferences`, `audit_logs` (append-only, enforced by trigger), `sla_breaches`, `outbox_events`, `idempotency_records` |
| V8 | `V8__reference_data_roles_permissions.sql` | 43 permissions, 7 system roles, role→permission matrix |

V4–V7 create the full data model now, so it can be reviewed as a whole. The Java code that uses those tables arrives in later phases.

## Conventions

- **Keys and timestamps:** UUID primary keys (`gen_random_uuid()`) and `timestamptz` stored in UTC.
- **Controlled states:** `varchar` plus a `CHECK` constraint, not PostgreSQL enums, so states can change through migrations.
- **Concurrency:** a `version bigint` column for optimistic locking on concurrently edited aggregates.
- **Constraint names:** constraints are named `uq_*`, `ck_*` and `ix_*`. The API maps `uq_*` violations to `409 DUPLICATE_RESOURCE`.
- **Money:** `numeric(12,2)`.
- **Partial unique indexes guard business invariants:**
  - one active workflow per application;
  - one active task per workflow step;
  - one active assignment per application.
- **Human-readable references:** `application_reference_seq` and `grievance_reference_seq` back numbers such as `JAN-2026-000123`.
- **Soft deletion:** none. Lifecycle is expressed with explicit status columns (`users.status`, `departments.status`, `services.status`).

## Mapping to the master prompt's table names

The docs (`DATABASE_DESIGN.md`) and the Golden Prompt used different names. Phase 1 uses:

| Master prompt | Implemented as | Why |
|---|---|---|
| `refresh_tokens` | `refresh_sessions` | Stores hashed session tokens with rotation families, per `DATABASE_DESIGN.md` |
| `department_members` | `department_memberships` | Docs name |
| `service_catalogue` | `services` + `service_versions` | Editable working copy plus immutable published snapshots |
| `service_requirements` | `service_requirements` | Prompt name kept |
| everything else in the prompt | same name | – |
| (docs only) | `application_decisions`, `clarification_requests`, `workflow_definition_versions`, `workflow_history`, `grievance_assignments`, `grievance_feedback`, `sla_policy_versions`, `outbox_events`, `idempotency_records`, `password_reset_tokens` | Needed by documented workflows |

## Seed data

- **Reference data** (roles and permissions) is in V8 and applies to every environment.
- **Synthetic demo data** is not a migration. `DevDataSeeder` creates it only in the `local` profile, through the normal services, so validation and audit apply.

## Rules for new migrations

1. **One logical change per file**, named `V<n>__<description>.sql`. Never edit a migration that has run in a shared environment; add a new one.
2. **Use expand-and-contract for incompatible changes:** add new columns/tables, deploy code that writes both, backfill, switch reads, then drop the old ones in a later release.
3. **Test from an empty database.** `DatabaseSchemaIT` runs every migration on a clean PostgreSQL 16 container in CI.
4. **Back up before production migrations.** Run migrations with a privileged migration role. The runtime role should not be able to `ALTER` tables or disable the audit trigger.
5. **Audit-log retention and archival** must run under a separate privileged role, because the trigger blocks `UPDATE`/`DELETE` for the application.

## Commands

Migrations run when the application starts. To check which ones are applied, query the Flyway history table:

```bash
docker compose exec postgres psql -U jansetu -d jansetu -c "select version, description, success from flyway_schema_history order by installed_rank"
```

To verify migrations on a clean database:

```bash
docker compose down -v && docker compose up -d postgres
```

Then start the backend and look for `Successfully applied 8 migrations` in the log.
