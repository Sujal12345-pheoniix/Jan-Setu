# Repository Structure and Module Boundaries

## 1. Recommended monorepo

```text
jansetu/
├── README.md
├── LICENSE
├── .gitignore
├── .editorconfig
├── .env.example
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/jansetu/
│       │   ├── JanSetuApplication.java
│       │   ├── config/
│       │   ├── common/
│       │   │   ├── api/
│       │   │   ├── error/
│       │   │   ├── security/
│       │   │   ├── pagination/
│       │   │   └── observability/
│       │   ├── modules/
│       │   │   ├── identity/
│       │   │   ├── access/
│       │   │   ├── department/
│       │   │   ├── catalogue/
│       │   │   ├── application/
│       │   │   ├── workflow/
│       │   │   ├── document/
│       │   │   ├── grievance/
│       │   │   ├── notification/
│       │   │   ├── audit/
│       │   │   ├── sla/
│       │   │   └── reporting/
│       │   └── integration/
│       │       ├── storage/
│       │       ├── email/
│       │       ├── messaging/
│       │       └── external/
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-local.yml
│       │   ├── application-test.yml
│       │   ├── application-prod.yml
│       │   └── db/migration/
│       └── test/java/com/jansetu/
│           ├── unit/
│           ├── integration/
│           ├── architecture/
│           └── fixtures/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile
│   └── src/
│       ├── app/
│       │   ├── router/
│       │   ├── providers/
│       │   └── store/
│       ├── components/
│       │   ├── ui/
│       │   ├── layout/
│       │   ├── forms/
│       │   └── tables/
│       ├── features/
│       │   ├── auth/
│       │   ├── citizen/
│       │   ├── catalogue/
│       │   ├── applications/
│       │   ├── tasks/
│       │   ├── grievances/
│       │   ├── departments/
│       │   ├── administration/
│       │   ├── audit/
│       │   └── reporting/
│       ├── lib/
│       ├── services/
│       ├── hooks/
│       ├── types/
│       ├── utils/
│       └── styles/
├── infrastructure/
│   ├── compose/
│   ├── monitoring/
│   ├── dashboards/
│   ├── reverse-proxy/
│   └── k8s/
├── scripts/
│   ├── dev/
│   ├── database/
│   └── quality/
└── docs/
    ├── ADR/
    └── diagrams/
```

## 2. Backend module template

Each business module owns its API, application logic, persistence adapter, and module-specific types.

```text
modules/application/
├── api/
│   ├── ApplicationController.java
│   ├── request/
│   └── response/
├── application/
│   ├── ApplicationCommandService.java
│   ├── ApplicationQueryService.java
│   └── port/
├── domain/
│   ├── Application.java
│   ├── ApplicationStatus.java
│   ├── ApplicationPolicy.java
│   └── event/
├── infrastructure/
│   ├── persistence/
│   └── mapper/
└── package-info.java
```

For a simpler first implementation, controller/service/repository/entity/dto packages inside each feature are acceptable. Keep module boundaries even if the internal layering is initially conventional.

## 3. Dependency rules

1. API controllers depend on application services, not repositories.
2. Application services coordinate use cases and transactions.
3. Domain logic must not depend on web, database, or cloud SDK classes.
4. A module must not query another module's repository directly.
5. Cross-module interactions use public application interfaces, domain events, or explicit query interfaces.
6. Infrastructure adapters implement ports/interfaces where external technology may change.
7. Shared `common` code must remain small; do not turn it into a dumping ground.
8. Avoid cyclic module dependencies. Add architecture tests to detect them.

## 4. Frontend feature template

```text
features/applications/
├── api/
├── components/
├── hooks/
├── pages/
├── schemas/
├── types/
└── index.ts
```

Feature pages compose shared UI components. API calls live in feature API modules and use one configured HTTP client. Do not duplicate token, error, or base-URL handling across features.

## 5. Naming conventions

- Java packages: lowercase.
- Java classes: PascalCase.
- Java methods/variables: camelCase.
- Constants: UPPER_SNAKE_CASE.
- Database tables/columns: snake_case.
- REST paths: plural nouns, lowercase, kebab-case only where needed.
- TypeScript components: PascalCase.
- TypeScript functions/variables: camelCase.
- Environment variables: UPPER_SNAKE_CASE.

## 6. Configuration boundaries

Keep secrets in environment/secret managers. Commit only safe placeholders in `.env.example`. Separate local, test, staging, and production configuration. Do not place real credentials in YAML, Dockerfiles, frontend variables, screenshots, logs, or documentation.

## 7. Module extraction criteria

Extract a module into a service only when there is a documented reason such as independent scaling, separate deployment cadence, a clear data/ownership boundary, fault isolation, or a team ownership need. Extraction requires API contracts, observability, authentication between services, retries/timeouts, data ownership, and operational runbooks.
