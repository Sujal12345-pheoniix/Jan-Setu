# Integration Design

## 1. Integration principles

- External systems are accessed through adapters/interfaces.
- Business modules do not depend directly on provider SDKs.
- Network calls have timeouts and bounded retries.
- Retry only transient failures.
- External side effects happen after database commit.
- Secrets are stored in environment/secret management.
- Provider failures are observable and do not silently lose business intent.

## 2. Object storage

Interface: `DocumentStoragePort`
- `putObject(key, stream, metadata)`
- `getObject(key)`
- `deleteObject(key)` subject to retention policy
- `createShortLivedDownloadAccess(key)` only after authorization

Local implementation: MinIO. Production implementation: selected S3-compatible provider.

Persist metadata in PostgreSQL:
- object key;
- original display filename (sanitized);
- detected media type;
- size;
- checksum;
- scan state;
- uploader;
- upload time;
- retention metadata.

## 3. Email

Interface: `EmailDeliveryPort`.
Local: console/mock provider.
Production: selected SMTP/API provider.

Use templates with escaped user content. Avoid including sensitive application details in email. Email is a notification channel, not the authoritative record.

## 4. Redis

Use only for:
- bounded caching of public service catalogue data;
- rate limiting / abuse protection where architecture supports it;
- short-lived coordination or locks only where database constraints are insufficient;
- carefully designed ephemeral session support if selected.

Do not use Redis as the source of truth for applications, approvals, or audit records. Define TTLs, eviction policy, invalidation, and failure behavior.

## 5. Kafka and transactional outbox

MVP may use a database-backed outbox worker before Kafka is introduced.

Outbox table fields:
- id;
- aggregate type/id;
- event type;
- payload version;
- payload;
- occurred_at;
- status;
- attempts;
- next_attempt_at;
- published_at;
- last_error_code.

Publisher claims a bounded batch, publishes, and marks delivered. Handle crash between publish and marking as possible duplicate delivery. Consumers must be idempotent using event ID/inbox deduplication.

Suggested topics:
- `jansetu.application-events.v1`
- `jansetu.grievance-events.v1`
- `jansetu.notification-commands.v1`

Define retention, partitions, keys, schema compatibility, DLQ policy, and PII minimization before production.

## 6. MongoDB decision

Do not add MongoDB to MVP. Consider it only if form schema/configuration or another bounded document use case has requirements that justify document storage. Keep transactional application records and relational integrity in PostgreSQL. Avoid dual writes without an explicit consistency design.

## 7. External identity/payment integrations

Not included in MVP. Any future integration requires:
- approved provider and legal basis;
- sandbox credentials;
- data-sharing agreement where relevant;
- threat/privacy assessment;
- consent and user-facing disclosure where required;
- timeout, retry, audit, and failure handling;
- no storage of provider secrets in frontend code.
