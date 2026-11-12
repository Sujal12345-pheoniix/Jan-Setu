# Backup, Restore, and Disaster Recovery

## 1. Principles
A backup is not proven until restoration has been tested. Define RPO (maximum acceptable data loss) and RTO (maximum acceptable recovery time) with the actual service owner before production.

## 2. Components to protect
- PostgreSQL database.
- Object storage documents.
- Application configuration and deployment manifests.
- Encryption keys/secrets through approved backup/key management.
- Audit and operational records as required.

## 3. Backup approach
- Automated database backups and point-in-time recovery where supported.
- Object storage versioning or backup strategy.
- Encrypted backups.
- Separate backup access controls.
- Retention policy and periodic cleanup.
- Off-account/off-region copy where justified and legally permitted.
- Monitor backup completion and age.

## 4. Restore procedure
1. Declare incident and identify recovery point.
2. Provision clean infrastructure.
3. Restore database to approved point.
4. Restore object storage/versioned objects.
5. Apply compatible application release.
6. Verify referential and object integrity.
7. Run smoke and critical workflow tests.
8. Reconcile outbox and queued work safely.
9. Re-enable traffic.
10. Record incident and lessons learned.

## 5. Exercises
Perform restore drills before production and periodically thereafter. Record actual RPO/RTO achieved. Ensure backups do not contain unencrypted secrets or uncontrolled copies of sensitive documents.
