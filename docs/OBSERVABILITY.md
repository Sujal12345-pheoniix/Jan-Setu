# Observability and Monitoring

## 1. Signals

### Metrics
- Request count, error count, latency histogram.
- JVM heap, GC pause, thread count.
- Database pool active/idle/pending.
- Database query latency.
- Cache hit/miss and eviction.
- Queue/outbox depth and oldest age.
- Kafka consumer lag (if used).
- Scheduler run duration/failure.
- Workflow transition failure count.
- SLA breach and escalation count.

### Logs
Use structured JSON logs with timestamp, severity, service, environment, trace ID, request ID, route template, duration, outcome, and safe actor identifier where appropriate.

### Traces
Instrument HTTP, database, messaging, and external calls with trace context. Do not put personal data or secrets in trace attributes.

## 2. Dashboards

- Application health and latency.
- JVM and runtime.
- Database and connection pool.
- Outbox/queue processing.
- Workflow business operations.
- SLA and grievance operations.
- Security events and authentication failures.

## 3. Alert principles

Alert on actionable symptoms, not every metric fluctuation. Define owner, severity, threshold, duration, runbook, and escalation path.

Examples:
- Sustained elevated 5xx rate.
- p95 latency above agreed SLO.
- Database pool near exhaustion.
- Outbox oldest event age above threshold.
- Scheduler has not completed expected run.
- Disk/storage nearing capacity.
- Backup job failure.
- Repeated authentication abuse.

## 4. Privacy-safe telemetry

Do not log tokens, passwords, full request bodies, document contents, identity numbers, or unnecessary citizen details. Restrict dashboard access and define metric retention.
