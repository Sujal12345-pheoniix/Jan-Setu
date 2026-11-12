# Release Acceptance Criteria

## 1. MVP acceptance
- [ ] Citizen can register, authenticate, and view own profile.
- [ ] Admin can create departments, users, roles, and permissions.
- [ ] Service can be drafted and published as a version.
- [ ] Citizen can create, edit, and submit a valid application.
- [ ] Application gets a unique reference number.
- [ ] Citizen can upload and view authorized documents.
- [ ] Officer receives and processes an assigned task.
- [ ] Clarification and resubmission work.
- [ ] Reviewer can approve or reject with a recorded reason.
- [ ] Citizen can view permitted timeline and decision.
- [ ] Citizen can create and track a grievance.
- [ ] Officer can resolve grievance and handle appeal according to policy.
- [ ] Critical actions generate audit records.
- [ ] Authorization tests prevent cross-citizen and cross-department access.
- [ ] Database can be built from zero using Flyway.
- [ ] API documentation is generated.
- [ ] Local environment starts from documented instructions.

## 2. Enhanced release acceptance
- [ ] SLA deadlines are calculated using versioned policy.
- [ ] Escalation scheduler is idempotent.
- [ ] Notifications are delivered asynchronously.
- [ ] Outbox backlog and failures are observable.
- [ ] Analytics use defined formulas and access scope.
- [ ] CSV exports are bounded and safe.
- [ ] Redis usage has documented invalidation and failure behavior.
- [ ] CI/CD deploys to staging and runs smoke tests.

## 3. Production-style release gate
- [ ] Threat model reviewed.
- [ ] Security test matrix passes.
- [ ] Dependency/container scans reviewed.
- [ ] No unresolved critical vulnerability without documented risk acceptance.
- [ ] Load test results documented.
- [ ] Backup and restore tested.
- [ ] Monitoring and alerts configured.
- [ ] Runbooks tested.
- [ ] Secrets and access policies reviewed.
- [ ] Privacy, legal, accessibility, and operational reviews completed as applicable.
- [ ] Rollback/forward-fix plan documented.
- [ ] Release owner signs off.

## 4. Evidence
For every checked item, link a test report, PR, deployment, runbook, screenshot, or review record. A checkbox without evidence is not proof.
