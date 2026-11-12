# Performance and Scalability Plan

## 1. Performance approach
Measure first. Define representative workloads and capture baseline results before optimization.

## 2. API targets (initial hypotheses)
- Common read APIs: p95 < 500 ms under documented test load.
- Critical write APIs: p95 < 800 ms excluding external provider latency.
- Error rate: establish an SLO after baseline measurement.
- Pagination: default 20, maximum 100 unless a specific endpoint justifies otherwise.

These are proposed targets, not guaranteed outcomes.

## 3. Database
- Index query predicates and ordering based on EXPLAIN ANALYZE.
- Avoid N+1 by fetch joins/entity graphs/projections where appropriate.
- Use DTO projections for dashboards.
- Bound transaction duration.
- Avoid holding DB transactions open during external network calls.
- Use connection pool sizing based on load tests and DB capacity.
- Use keyset pagination for very large sequential datasets if offset pagination becomes expensive.

## 4. Caching
Cache only stable, frequently read data such as published service catalogue entries. Define cache key, TTL, invalidation, stale behavior, and failure mode. Do not cache authorization decisions or sensitive user-specific data without careful design.

## 5. Asynchronous work
Use outbox/worker for notifications, exports, scanning, and other slow tasks. Jobs need retry limits, dead-letter handling, idempotency, visibility, and operator controls.

## 6. Load testing
Use k6 or equivalent to model:
- anonymous service browsing;
- citizen login and application submission;
- officer task list and decision;
- grievance creation;
- dashboard queries;
- concurrent approvals;
- document upload/download.

Record hardware, dataset size, concurrency, duration, latency percentiles, throughput, errors, and resource use.
