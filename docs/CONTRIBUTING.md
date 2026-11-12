# Contributing and Engineering Workflow

## 1. Branches
- `main`: releasable.
- `develop`: integration branch if needed.
- `feature/<short-name>`: feature work.
- `bugfix/<short-name>`: bug fixes.
- `docs/<short-name>`: documentation-only work.

For a solo developer, feature branches and pull requests are still useful for practicing review discipline.

## 2. Commit messages
Use a consistent format:
- `feat(application): add draft submission`
- `fix(workflow): prevent duplicate approval`
- `test(auth): cover refresh token reuse`
- `docs(api): document grievance endpoints`
- `refactor(document): isolate storage adapter`

## 3. Pull requests
Include problem, approach, scope, test evidence, migration notes, security impact, screenshots for UI, and rollback considerations.

## 4. Code review questions
- Is the requirement clear?
- Are authorization and ownership enforced?
- Are invalid states rejected?
- Are concurrent operations safe?
- Are errors safe and useful?
- Are tests meaningful?
- Are queries bounded?
- Are secrets/PII absent from logs?
- Are API contracts and docs updated?

## 5. Definition of done
Use the checklist in `IMPLEMENTATION.md`. Do not merge failing tests or bypass required security checks without a documented exception.
