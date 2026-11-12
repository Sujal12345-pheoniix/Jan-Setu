# ADR-001: Start with a Modular Monolith

- **Status:** Accepted
- **Date:** 2026-11-12

## Context
The project is built by a solo developer who is learning Java/Spring Boot. Immediate microservices would add distributed-system complexity before business boundaries are proven.

## Decision
Build a feature-oriented modular monolith with explicit module interfaces and a single PostgreSQL system of record. Use asynchronous outbox processing for side effects. Extract services only when a measurable operational or organizational reason exists.

## Consequences
Positive:
- Easier local development and debugging.
- Simpler transactions.
- Faster delivery of a coherent MVP.
- Clear path to learn modular design.

Negative:
- Requires discipline to prevent module coupling.
- Independent scaling/deployment is limited until extraction.
- A later extraction requires explicit data ownership and API boundaries.

## Revisit when
A module needs independent scaling, deployment, fault isolation, team ownership, or a distinct security boundary.
