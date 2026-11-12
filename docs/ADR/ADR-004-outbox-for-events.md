# ADR-004: Transactional Outbox for Reliable Side Effects

- **Status:** Accepted
- **Date:** 2026-11-12

## Context
A business transaction may commit while notification/event publication fails, or an event may be delivered twice.

## Decision
Write event intent to an outbox table in the same transaction as the business change. A worker publishes after commit. Consumers deduplicate by event ID and are idempotent.

## Consequences
- Reliable eventual delivery with at-least-once semantics.
- Requires outbox monitoring, retry, retention, and dead-letter handling.
- Exactly-once end-to-end delivery is not claimed.
