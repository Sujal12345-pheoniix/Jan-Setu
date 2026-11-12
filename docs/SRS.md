# Software Requirements Specification (SRS)

## 1. Purpose
This SRS converts the JanSetu PRD into testable system-level requirements. It is a baseline; detailed UI and API contracts are maintained in their dedicated documents.

## 2. System context
JanSetu consists of a browser-based React application, Spring Boot REST API, PostgreSQL database, private object storage, and optional Redis, Kafka, email, and observability services.

## 3. Interfaces
- Browser to API: HTTPS, JSON REST, multipart upload for files.
- Backend to PostgreSQL: JDBC over private network.
- Backend to Redis: authenticated private connection.
- Backend to object storage: provider SDK over private network/TLS.
- Backend to Kafka: authenticated broker connection when enabled.
- Backend to email: provider API or SMTP with TLS.
- Monitoring: Micrometer/Actuator metrics scraped by Prometheus.

## 4. Functional system requirements
- SYS-001: System shall authenticate users before protected operations.
- SYS-002: System shall authorize each protected operation using permissions and resource scope.
- SYS-003: System shall prevent citizens from accessing other citizens' records.
- SYS-004: System shall enforce application state transitions on the server.
- SYS-005: System shall preserve the service-form version used by an application.
- SYS-006: System shall validate document size, declared type, detected type, and file signature.
- SYS-007: System shall store document bytes outside the public web root.
- SYS-008: System shall record business history for applications and grievances.
- SYS-009: System shall create audit records for defined security-sensitive and business-critical events.
- SYS-010: System shall paginate and bound collection responses.
- SYS-011: System shall use database constraints to enforce uniqueness and referential integrity.
- SYS-012: System shall support health, readiness, and liveness checks appropriate to deployment.
- SYS-013: System shall expose API documentation in non-production environments or behind authorized access.
- SYS-014: System shall support database migration from a clean database using versioned scripts.

## 5. Data requirements
- Use UUID identifiers.
- Use UTC instants for persisted timestamps; render local timezone in UI.
- Use explicit enums or reference tables for controlled states.
- Use foreign keys and unique constraints.
- Avoid storing unnecessary sensitive personal data.
- Never store raw passwords or plaintext refresh tokens.
- Keep file metadata separate from file bytes.
- Retain historical form/workflow versions needed to interpret existing cases.

## 6. Error handling
Errors should use a consistent Problem Details-style format with a stable error code, safe user-facing detail, request/trace ID, and field-level validation details where appropriate. Internal stack traces and SQL details must not be returned.

## 7. Constraints
- Java 21 baseline.
- Spring Boot 3.x baseline.
- React + TypeScript baseline.
- PostgreSQL is the system of record.
- The MVP is a modular monolith.
- Optional infrastructure must not be required to run the core MVP locally unless explicitly enabled.

## 8. Verification
Every SYS requirement must map to one or more automated tests, manual test cases, operational checks, or documented design reviews. Maintain a requirements traceability matrix as implementation proceeds.
