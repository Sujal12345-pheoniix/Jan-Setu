# Privacy and Data Governance

## 1. Scope
This document defines engineering controls for a portfolio simulation. It is not legal advice or a legal compliance certification.

## 2. Data inventory

| Data category | Example | Handling |
|---|---|---|
| Account | Email, display name | Minimize, protect, restrict |
| Application | Service answers and status | Access by ownership and department scope |
| Documents | Uploaded evidence | Private object storage, scanning, retention |
| Workflow | Assignments and decisions | Restricted staff access, history |
| Grievance | Description and responses | Separate internal/public content |
| Audit | Actor/action/time | Restricted, append-only application access |
| Telemetry | Request metrics | Avoid personal identifiers |

## 3. Data minimization
Collect only information required for the simulated service. Use synthetic data in demos. Do not collect government identity numbers or sensitive documents merely to make the project look realistic.

## 4. Retention and deletion
Define a retention schedule per entity and document type before production. Account deletion, anonymization, legal holds, audit retention, backups, and foreign-key relationships must be considered together. Do not promise immediate deletion if retained backups or legal obligations apply.

## 5. Access and disclosure
- Citizen sees own data.
- Officers see only authorized cases.
- Internal notes are not citizen-visible.
- Management receives aggregates where possible.
- Exports are permissioned, time-bounded, audited, and protected.

## 6. Legal review
For real deployment in India, obtain qualified review of applicable data protection law/rules, sectoral obligations, government procurement/security requirements, hosting and localization requirements, accessibility obligations, and contractual terms. Do not infer compliance from this engineering blueprint.
