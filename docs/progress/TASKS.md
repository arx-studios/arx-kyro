# Task Board

Statuses: TODO | IN_PROGRESS | DONE | BLOCKED

Parallel groups: **Phase 1** FND-1 ∥ FND-2 (FND-3 after FND-1 root files) · **Phase 2** API-1 ∥ WEB-1 ∥ ADM-1 ∥ INF-1 · **Phase 3** API-2 ∥ API-3 ∥ API-4 ∥ WEB-2 · **Phase 4** E2E-1 · **Phase 5 (later)** LATER-1 ∥ LATER-2. Full specs are in PLAN.md §12.

| ID | Title | Owner | Status | Owned paths | Depends on |
|----|-------|-------|--------|-------------|------------|
| PLAN-0 | Produce PLAN.md + populate this board | planner-opus | DONE | docs/progress/, docs/architecture/ | - |
| FND-1 | [P1 ∥ FND-2] Monorepo root, docker-compose local infra (postgres, minio, mailpit, flyway tool), Flyway V1–V5 SQL, shared TS config | - | TODO | package.json, pnpm-workspace.yaml, .gitignore, .gitattributes, .editorconfig, .nvmrc, .npmrc, README.md, docker-compose.yml, .env.example, packages/config/, infrastructure/docker/local/, apps/api/src/main/resources/db/migration/, scripts/db/ | - |
| FND-2 | [P1 ∥ FND-1] OpenAPI 3.0.3 contract (30 operations) + redocly lint + generated TS client (@kyro/api-client) | - | TODO | packages/api-spec/, packages/api-client/ | - |
| FND-3 | [P1, after FND-1 root files] Design system (@kyro/design-system): Tailwind v4 tokens, accessible components, money utils | - | TODO | packages/design-system/ | FND-1 |
| API-1 | [P2 ∥ WEB-1, ADM-1, INF-1] Gradle wrapper + Spring Boot 4 core: common (errors, security, CSRF, rate limit, correlation, S3 storage), identity + auth endpoints, audit, dev seed users, frozen cross-module ports, test support, ArchUnit | - | TODO | settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat, gradle/, apps/api/ (excluding db/migration/, and excluding com/kyro/platform/education/ except education/application/port/, verification/, discovery/ in main and test) | FND-1, FND-2 |
| WEB-1 | [P2 ∥ API-1, ADM-1, INF-1] Web PWA foundation: Next.js shell, providers, AppShell, landing/trust, register/verify/login, recipient dashboard/create/detail with document upload | - | TODO | apps/web/ (excluding apps/web/src/app/(donor)/ and apps/web/src/features/discovery/) | FND-2, FND-3 |
| ADM-1 | [P2 ∥ API-1, WEB-1, INF-1] Admin app: login, role guard, verification queue, case detail with document view, claim and decision, audit page | - | TODO | apps/admin/ | FND-2, FND-3 |
| INF-1 | [P2 ∥ API-1, WEB-1, ADM-1] GitHub Actions CI (contracts, api, migrations, frontend, security), Dockerfiles, docker-compose.apps.yml, dependabot | - | TODO | .github/ (excluding .github/workflows/e2e.yml), infrastructure/docker/ (excluding infrastructure/docker/local/), docker-compose.apps.yml | FND-1, FND-2 (final check after API-1, WEB-1, ADM-1) |
| API-2 | [P3 ∥ API-3, API-4, WEB-2] Education requests + documents: CRUD, lifecycle state machine, presigned uploads with validation and hashing, EducationReviewFacade impl, duplicate indicators, events, demo approved-request seed | - | TODO | apps/api/src/main/java/com/kyro/platform/education/ (excluding application/port/), apps/api/src/test/java/com/kyro/platform/education/ | API-1 |
| API-3 | [P3 ∥ API-2, API-4, WEB-2] Verification: case opening via events, queue, claim, decisions with self-review guard, document view-url, audit | - | TODO | apps/api/src/main/java/com/kyro/platform/verification/, apps/api/src/test/java/com/kyro/platform/verification/ | API-1 (API-2 merged before final integration tests) |
| API-4 | [P3 ∥ API-2, API-3, WEB-2] Discovery: public feed from view with keyset paging, public detail, idempotent swipes, saved list | - | TODO | apps/api/src/main/java/com/kyro/platform/discovery/, apps/api/src/test/java/com/kyro/platform/discovery/ | API-1 |
| WEB-2 | [P3 ∥ API-2, API-3, API-4] Donor UI: accessible swipe feed (touch, mouse, keyboard, reduced motion), request detail, saved list | - | TODO | apps/web/src/app/(donor)/, apps/web/src/features/discovery/ | WEB-1 |
| E2E-1 | [P4] Slice integration: smoke script, Playwright E2E (web + admin) with axe checks, e2e CI workflow, local-dev runbook, small cross-cutting fixes | - | TODO | e2e/, scripts/smoke/, docs/runbooks/, .github/workflows/e2e.yml | API-2, API-3, API-4, WEB-2, ADM-1, INF-1 |
| LATER-1 | [P5 ∥ LATER-2, re-plan first] Non-binding pledges (V6__create_funding.sql, funding module, pledge UI; no payments) | - | TODO | apps/api/src/main/java/com/kyro/platform/funding/, apps/api/src/test/java/com/kyro/platform/funding/, apps/api/src/main/resources/db/migration/V6__create_funding.sql, apps/web/src/features/funding/ | E2E-1 |
| LATER-2 | [P5 ∥ LATER-1, re-plan first] Terraform AWS staging foundation (validate and plan only; no apply) | - | TODO | infrastructure/terraform/ | INF-1 |

### Critical Files for Implementation
- D:\kyro\packages\api-spec\openapi.yaml
- D:\kyro\apps\api\src\main\resources\db\migration\ (V1–V5)
- D:\kyro\apps\api\src\main\java\com\kyro\platform\education\application\port\EducationReviewFacade.java
- D:\kyro\apps\api\src\main\java\com\kyro\platform\common\security\SecurityConfig.java
- D:\kyro\docker-compose.yml
