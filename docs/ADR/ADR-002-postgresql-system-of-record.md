# ADR-002: PostgreSQL as the Transactional System of Record

- **Status:** Accepted
- **Date:** 2026-11-12

## Context
Applications, workflow transitions, assignments, and decisions require relational integrity and transactional consistency.

## Decision
Use PostgreSQL as the primary system of record. Use JSONB only for versioned flexible form answers/configuration where appropriate. Keep query-critical fields normalized.

## Consequences
- Strong relational constraints and transactions.
- Clear migration strategy through Flyway.
- Avoids unnecessary polyglot persistence in MVP.
- Redis/MongoDB are optional and must have explicit use cases.
