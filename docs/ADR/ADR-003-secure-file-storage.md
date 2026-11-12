# ADR-003: Private Object Storage for Documents

- **Status:** Accepted
- **Date:** 2026-11-12

## Context
Application evidence must not be served from a public frontend static directory or exposed by guessable URLs.

## Decision
Store file bytes in private S3-compatible object storage, with metadata and opaque storage keys in PostgreSQL. Validate, quarantine, and scan files before they become downloadable. Authorize every access.

## Consequences
- Database remains focused on metadata and transactions.
- Object storage requires backup, retention, and access policy.
- Local development can use MinIO.
