# PLAN.md — KYRO First Vertical Slice Build Plan

Owner: planner-opus. Source of truth for product: `KYRO_startup_plan_v1.2.md`. This plan turns §23 "First vertical slice" into work packages that builders can run in parallel. The slice is: **recipient registers → submits tuition bill → admin verifies it → approved bill appears in donor swipe feed.**

Hard constraints (apply to every task): no live payments, no payment provider SDKs, no wallet or pooled funds. Use sandbox or fictional data only (seed emails use `@kyro.local`). Store money as integer minor units plus an ISO 4217 code. Every UI shows a persistent "Sandbox — fictional data, no real money" banner.

---

## 0. How builders work (read first)

1. Read the blueprint, this PLAN, TASKS.md and the latest LOG.md entries. Claim your task in TASKS.md.
2. **Git:** work on branch `task/<ID>`, ideally in its own worktree (`git worktree add ../kyro-<ID> -b task/<ID>`). Commit there. Before merging, rebase on `main` and re-run your verification commands. Merge to `main` only when they pass. Owned paths are disjoint, so merges should be trivial.
3. **Shared files with special rules:**
   - `pnpm-lock.yaml`: if it conflicts, take `main`'s version and run `pnpm install` again. Never hand-edit it.
   - `packages/api-spec/openapi.yaml`: after FND-2 it is a frozen contract. To change it, add an entry to DECISIONS.md, keep the change additive where possible, and in the same commit regenerate `packages/api-client` (`pnpm gen:client`). Note the change in LOG.md.
   - Flyway migrations V1–V5: frozen after FND-1. Until E2E-1 is DONE you may edit them in place, because no database has been deployed. Each edit needs a DECISIONS.md entry, and developers then run `docker compose down -v`. After E2E-1, schema changes go in new migrations only.
   - Files marked `// FROZEN CONTRACT` (Java ports created by API-1): change their signatures only with a DECISIONS.md entry.
4. Do not touch paths outside your task. If you need something from another path, write a LOG.md note and coordinate. Do not edit it yourself.
5. When finished, append to LOG.md (what you built, how to run it, known gaps) and set the task to DONE.

---

## 1. Phases

| Phase | Tasks | Parallelism | Exit criterion |
|---|---|---|---|
| 1 Contracts & foundation | FND-1, FND-2, FND-3 | FND-1 and FND-2 in parallel. FND-3 can start as soon as FND-1's root `package.json` and `packages/config` exist. | `docker compose up` works; migrations apply; spec lints; TS client generates; design system builds |
| 2 Cores | API-1, WEB-1, ADM-1, INF-1 | All 4 in parallel | API boots with auth and audit; web shell plus recipient screens build; admin builds; CI is green |
| 3 Domain features | API-2, API-3, API-4, WEB-2 | All 4 in parallel. API-3's final integration tests need API-2 merged first. | All slice endpoints implemented and tested; donor UI builds |
| 4 Integration | E2E-1 | Single task | Playwright E2E for the full slice passes locally and in CI |
| 5 Later (not in slice) | LATER-1 (non-binding pledges), LATER-2 (Terraform AWS staging) | In parallel | Re-plan before starting |

Backlog after the slice (no tasks yet; listed so nobody builds them now):
- `apps/worker` with a transactional outbox and SQS, using ElasticMQ locally
- Malware scanning and quarantine; EXIF/metadata stripping
- Notifications module
- Admin MFA (TOTP) and an OIDC identity provider
- Spring Session JDBC; Redis rate limiting
- User reports (OPS-002), disputes, appeals (TRUST-007)
- Donor preferences endpoints, filters (MATCH-004), ranking
- Payment sandbox, double-entry ledger, reconciliation, refunds (migrations V7–V9)
- OpenTelemetry tracing; pen test; DR exercises

---

## 2. Repository layout (target after Phase 4)

```text
kyro/
├── KYRO_startup_plan_v1.2.md
├── README.md                         FND-1
├── package.json                      FND-1  (pnpm workspace root, scripts only)
├── pnpm-workspace.yaml               FND-1
├── .gitignore .gitattributes .editorconfig .nvmrc .npmrc   FND-1
├── docker-compose.yml                FND-1  (infra services)
├── docker-compose.apps.yml           INF-1  (containerised api/web/admin, profile "apps")
├── .env.example                      FND-1
├── settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat gradle/   API-1
├── apps/
│   ├── api/                          API-1 (except paths below)
│   │   └── src/main/resources/db/migration/   FND-1
│   │   └── src/main/java/com/kyro/platform/
│   │       ├── PlatformApplication.java       API-1
│   │       ├── common/   identity/   audit/   devseed/      API-1
│   │       ├── education/application/port/   API-1 (FROZEN CONTRACT)
│   │       ├── education/**  (rest)            API-2
│   │       ├── verification/**                 API-3
│   │       └── discovery/**                    API-4
│   ├── web/                          WEB-1 (except src/app/(donor)/, src/features/discovery/ → WEB-2)
│   └── admin/                        ADM-1
├── packages/
│   ├── config/                       FND-1  (@kyro/config: tsconfig base, eslint, prettier)
│   ├── api-spec/                     FND-2  (@kyro/api-spec: openapi.yaml + redocly)
│   ├── api-client/                   FND-2  (@kyro/api-client: generated types + fetch client)
│   └── design-system/                FND-3  (@kyro/design-system)
├── infrastructure/
│   ├── docker/local/                 FND-1  (minio-init.sh etc.)
│   ├── docker/*.Dockerfile           INF-1
│   └── terraform/                    LATER-2
├── scripts/db/                       FND-1
├── scripts/smoke/                    E2E-1
├── e2e/                              E2E-1  (@kyro/e2e Playwright)
├── docs/progress/  docs/architecture/   planner
├── docs/runbooks/                    E2E-1
└── .github/workflows/                INF-1 (except e2e.yml → E2E-1)
```

The blueprint lists `packages/schemas/`. It is not created in this slice; the OpenAPI spec is the schema source.

---

## 3. Fixed technology and versions

| Area | Decision |
|---|---|
| Java | Installed JDK 27 compiles with `options.release = 21` (`tasks.withType<JavaCompile> { options.release.set(21) }`). No `java.toolchain` block. CI uses Temurin 25. The runtime image is `eclipse-temurin:25-jre`. |
| Build | Gradle wrapper, Kotlin DSL, version catalog `gradle/libs.versions.toml`. Use the newest Gradle 9.x/10.x that officially supports running on JDK 27 (check gradle.org/compatibility). **If none does:** add the `org.gradle.toolchains.foojay-resolver-convention` plugin to settings and run `./gradlew updateDaemonJvm --jvm-version=25`, which commits `gradle/gradle-daemon-jvm.properties` so the daemon is auto-provisioned on JDK 25. Record the choice in DECISIONS.md. |
| Wrapper bootstrap | No Gradle is installed. Download `gradle-<ver>-bin.zip` from services.gradle.org into `%USERPROFILE%\.kyro-bootstrap\` (outside the repo), unzip it, then run `<unzipped>\bin\gradle wrapper --gradle-version <ver> --distribution-type bin` at the repo root. Commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`. |
| Spring | Latest Spring Boot **4.x GA**: Spring Framework 7, Spring Security 7, Jackson 3, Spring MVC, Spring Data JPA plus JdbcTemplate. Boot 4 gotchas: use `spring-boot-starter-webmvc`, `spring-boot-starter-flyway` (Flyway auto-config is its own module now) plus `org.flywaydb:flyway-database-postgresql`, and test auto-config packages have moved. |
| Contract codegen (Java) | `org.openapi.generator` Gradle plugin, generator `spring`. Options: `interfaceOnly=true`, `useTags=true`, `useSpringBoot3=true`, `useJakartaEe=true`, `useBeanValidation=true`, `openApiNullable=false`, `skipDefaultInterface=true`, `documentationProvider=none`, `annotationLibrary=none`, `apiPackage=com.kyro.platform.contract.api`, `modelPackage=com.kyro.platform.contract.model`. Output goes to `apps/api/build/generated/openapi` and is wired before `compileJava`. Input: `rootProject.file("packages/api-spec/openapi.yaml")`. Fallback (only if the generator cannot compile against Boot 4 after a reasonable attempt): hand-written DTOs whose names match the spec schemas, plus a DECISIONS.md entry. |
| DB | PostgreSQL 17 (`postgres:17-alpine`), Flyway, `spring.jpa.hibernate.ddl-auto=validate`, `open-in-view=false` |
| Storage | AWS SDK for Java v2 (`s3`, BOM-managed) with `forcePathStyle(true)`. Locally this talks to MinIO. |
| Tests (API) | JUnit 5, Spring Boot test, spring-security-test, Testcontainers (postgresql, minio) with `@ServiceConnection`, ArchUnit |
| Frontend | Node 24, pnpm 12 (`"packageManager": "pnpm@12.x"`), latest stable Next.js with App Router, React 19, TypeScript strict, Tailwind CSS v4, TanStack Query v5, React Hook Form plus Zod, `motion` (swipe gestures), Vitest plus Testing Library, ESLint 9 flat config, Prettier, Playwright (E2E) |
| TS client | `openapi-typescript` (generates `schema.d.ts`) plus `openapi-fetch` (runtime) |
| Spec lint | `@redocly/cli lint` with the recommended ruleset |
| pnpm build scripts | pnpm ≥10 blocks dependency build scripts. Allow the needed ones (`esbuild`, `sharp`, `@tailwindcss/oxide`, …) in `pnpm-workspace.yaml` (`onlyBuiltDependencies` / `allowBuilds`, whichever pnpm 12 uses). |

Standard package scripts. Every TS package defines the ones that apply: `dev`, `build`, `start`, `lint`, `typecheck`, `test`.

Root `package.json` scripts:
- `lint`, `typecheck`, `test`, `build` (each `pnpm -r --if-present <x>`)
- `gen:client` (`pnpm --filter @kyro/api-client generate`)
- `spec:lint`
- `infra:up` (`docker compose up -d --wait`)
- `infra:down`

`.gitattributes`: `* text=auto eol=lf`, `*.bat eol=crlf`, `*.jar binary`, `*.png binary`, `*.pdf binary`.

---

## 4. Local environment contract

### Ports

| Service | Host port | Notes |
|---|---|---|
| web (Next.js) | 3000 | open at `http://localhost:3000` |
| admin (Next.js) | 3001 | **open at `http://127.0.0.1:3001`** so its cookie jar is separate from web's (cookies ignore ports) |
| api (Spring Boot) | 8080 | `/api/v1/**`, `/actuator/health` |
| postgres | 5433 → 5432 | db `kyro`, user `kyro`, password `kyro_local` |
| minio S3 API | 9000 | |
| minio console | 9001 | |
| mailpit SMTP / UI | 1025 / 8025 | dev email inbox; the HTTP API is used by E2E |

### docker-compose.yml services (FND-1)

- **postgres**: `postgres:17-alpine`, volume `kyro-pg`, healthcheck `pg_isready -U kyro -d kyro`.
- **minio**: `minio/minio:RELEASE.2025-09-07T16-13-09Z`. If that tag cannot be pulled, use the newest `RELEASE.*` tag on Docker Hub and record it in DECISIONS.md; Testcontainers must use the same tag.
  - Command: `server /data --console-address :9001`.
  - Environment: `MINIO_ROOT_USER=kyro-minio`, `MINIO_ROOT_PASSWORD=kyro-minio-secret`, `MINIO_API_CORS_ALLOW_ORIGIN=http://localhost:3000,http://127.0.0.1:3001`.
  - Volume `kyro-minio`; healthcheck on `/minio/health/live`.
- **minio-init**: `minio/mc`. Waits for minio to be healthy, then runs `infrastructure/docker/local/minio-init.sh`. The script creates bucket `kyro-documents-local` (`mc mb --ignore-existing`) and runs `mc anonymous set none` so the bucket stays private.
- **mailpit**: `axllent/mailpit`.
- **flyway** (profile `tools`): `flyway/flyway:11-alpine`.
  - Mounts `./apps/api/src/main/resources/db/migration:/flyway/sql:ro`.
  - Environment: `FLYWAY_URL=jdbc:postgresql://postgres:5432/kyro`, `FLYWAY_USER=kyro`, `FLYWAY_PASSWORD=kyro_local`, `FLYWAY_LOCATIONS=filesystem:/flyway/sql`.
  - Usage: `docker compose --profile tools run --rm flyway migrate`.

`docker-compose.apps.yml` (INF-1) adds `api`, `web` and `admin` under profile `apps`, built from `infrastructure/docker/*.Dockerfile`.

### Environment variables

API (`.env.example` documents them; `application.yml` gives each one a local default):

| Var | Local default |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` |
| `KYRO_DB_URL` | `jdbc:postgresql://localhost:5433/kyro` |
| `KYRO_DB_USER` / `KYRO_DB_PASSWORD` | `kyro` / `kyro_local` |
| `KYRO_S3_ENDPOINT` | `http://localhost:9000` (used for server-side calls) |
| `KYRO_S3_PUBLIC_ENDPOINT` | `http://localhost:9000` (host baked into presigned URLs the browser uses) |
| `KYRO_S3_REGION` | `us-east-1` |
| `KYRO_S3_ACCESS_KEY` / `KYRO_S3_SECRET_KEY` | `kyro-minio` / `kyro-minio-secret` |
| `KYRO_S3_DOCUMENTS_BUCKET` | `kyro-documents-local` |
| `KYRO_MAIL_HOST` / `KYRO_MAIL_PORT` / `KYRO_MAIL_FROM` | `localhost` / `1025` / `no-reply@kyro.local` |
| `KYRO_WEB_BASE_URL` | `http://localhost:3000` (used in email verification links) |
| `KYRO_POLICY_TERMS_VERSION` / `KYRO_POLICY_PRIVACY_VERSION` | `2026-11-01` / `2026-11-01` |
| `KYRO_SEED_ENABLED` | `true` in profile `local`, `false` otherwise |
| `KYRO_SEED_PASSWORD` | `KyroLocal!2026` |

Web and admin:

| Var | Default |
|---|---|
| `KYRO_API_ORIGIN` | `http://localhost:8080` (server-side only; used by Next rewrites) |

`application.yml` config tree (API-1 writes all of it, so API-2/3/4 never need to edit it):

```yaml
kyro:
  web-base-url: ${KYRO_WEB_BASE_URL:http://localhost:3000}
  policy: { terms-version: ..., privacy-version: ... }
  auth: { verification-token-ttl: 24h, login-rate-per-minute: 10, register-rate-per-minute: 5, resend-rate-per-minute: 3 }
  storage: { endpoint, public-endpoint, region, access-key, secret-key, documents-bucket }
  documents: { max-size-bytes: 10485760, max-per-request: 10, allowed-content-types: [application/pdf, image/jpeg, image/png], upload-url-ttl: 10m, view-url-ttl: 5m }
  discovery: { default-page-size: 20, max-page-size: 50, story-excerpt-length: 240 }
  seed: { enabled: ..., password: ... }
server.servlet.session: { timeout: 8h, cookie: { name: KYRO_SESSION, http-only: true, same-site: lax, secure: false (true outside local) } }
```

### Browser → API topology

Both Next apps proxy requests through `next.config.ts` rewrites: `{ source: '/api/:path*', destination: `${KYRO_API_ORIGIN}/api/:path*` }`. The browser therefore only ever talks same-origin, so no CORS is needed for the API. The only cross-origin call is the browser `PUT` of a file to the MinIO presigned URL, which MinIO's CORS setting allows.

---

## 5. Auth approach (local dev; OIDC deferred)

- Spring Security uses a server-side `HttpSession` (in memory for now). The cookie is `KYRO_SESSION`: HttpOnly, SameSite=Lax.
- Passwords use `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt). Password length is 12–128.
- **Login:** `POST /api/v1/auth/session` is a JSON controller. It calls `AuthenticationManager`, changes the session id (fixation protection), and saves the `SecurityContext` explicitly through `HttpSessionSecurityContextRepository`.
- **Principal:** `KyroPrincipal(UUID userId, String email, Set<Role> roles)`. Roles are loaded at login, so a role change takes effect at the next login.
- **Registration:** `POST /auth/register` always returns `202`, whether or not the email exists, so it cannot be used to discover accounts.
  - New email: creates the user with status `PENDING_VERIFICATION`.
  - Existing email: sends a "someone tried to register with your email" mail instead.
  - Allowed roles are only `RECIPIENT` and/or `DONOR`. Asking for `VERIFIER` or `ADMIN` returns 422 `ROLE_NOT_ALLOWED`.
  - Records two `consent_records` rows (TERMS, PRIVACY). The versions must equal the current policy versions, otherwise 422 `CONSENT_VERSION_MISMATCH`.
- **Email verification:**
  - The token is 32 random bytes, base64url-encoded. Only its SHA-256 is stored. It expires after 24h and is single use.
  - The mail goes to Mailpit with the link `${KYRO_WEB_BASE_URL}/verify-email?token=...`.
  - In profile `local` the link is also logged at INFO with the prefix `DEV-ONLY`.
  - Verifying sets `email_verified_at` and status `ACTIVE`.
  - Logging in before verification returns 403 `EMAIL_NOT_VERIFIED`. Phone verification is deferred.
- **CSRF:** enabled for every mutating request, including login. Use `.csrf(c -> c.spa())` if the Security version provides it. Otherwise use `CookieCsrfTokenRepository.withHttpOnlyFalse()` with a non-XOR `CsrfTokenRequestAttributeHandler`. The cookie is `XSRF-TOKEN` and the header is `X-XSRF-TOKEN`. `GET /api/v1/auth/csrf` returns the token and sets the cookie. The TS client sends the header automatically.
- **Rate limits:** an in-memory fixed-window filter keyed by client IP on `/auth/session`, `/auth/register` and `/auth/verify-email/resend`. Exceeding it returns 429 `RATE_LIMITED`.
- **Entry points:** unauthenticated requests get a 401 problem body and denied requests get a 403 problem body. There are no redirects.
- **Route rules** (in addition to object-level checks in services):

| Rule | Paths |
|---|---|
| permitAll | `GET /api/v1/auth/csrf`, `POST /api/v1/auth/register`, `POST /api/v1/auth/verify-email`, `POST /api/v1/auth/verify-email/resend`, `POST /api/v1/auth/session`, `GET /api/v1/policies/current`, `GET /api/v1/discovery/feed`, `GET /api/v1/discovery/feed/*`, `GET /actuator/health` |
| `hasAnyRole(VERIFIER, ADMIN)` | `/api/v1/admin/**` |
| `hasRole(RECIPIENT)` | `/api/v1/education/**`, `/api/v1/me/education/**` |
| `hasRole(DONOR)` | `/api/v1/discovery/requests/*/swipes`, `/api/v1/discovery/saved/**` |
| authenticated | everything else |

- **Seed users** (profile `local` only, password `KYRO_SEED_PASSWORD`, all fictional):

| Email | Roles | Created by |
|---|---|---|
| `admin@kyro.local` | ADMIN, VERIFIER | API-1 |
| `verifier@kyro.local` | VERIFIER | API-1 |
| `donor.demo@kyro.local` | DONOR | API-1 |
| `recipient.demo@kyro.local` | RECIPIENT | API-1; API-2 adds 3 fictional APPROVED requests owned by this user, so the feed has content |

- Deferred: admin MFA, OIDC, persistent sessions.

---

## 6. Error envelope and API conventions

- Errors are RFC 9457 `application/problem+json`, built with Spring's `ProblemDetail`.
- Correlation:
  - Header `X-Correlation-Id`: accepted if valid (≤64 chars, `[A-Za-z0-9-]`), otherwise a new UUID is generated.
  - It is echoed on every response, stored in MDC key `correlationId`, and written to audit events.

```json
{
  "type": "urn:kyro:problem:VALIDATION_FAILED",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/education/requests",
  "code": "VALIDATION_FAILED",
  "correlationId": "4f1c2b0e-...",
  "errors": [ { "field": "amount.amountMinor", "message": "must be greater than 0" } ]
}
```

Error codes (the `code` field) and their HTTP statuses:

| Status | Codes |
|---|---|
| 400 | `MALFORMED_REQUEST` (unparseable JSON or a bad parameter type) |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` |
| 403 | `FORBIDDEN`, `EMAIL_NOT_VERIFIED`, `SELF_REVIEW_FORBIDDEN`, `CSRF_INVALID` |
| 404 | `NOT_FOUND` (also used for objects the caller may not see, so their existence is not revealed) |
| 409 | `INVALID_STATE_TRANSITION`, `STALE_VERSION`, `CASE_ALREADY_ASSIGNED`, `CASE_NOT_ASSIGNED_TO_YOU` |
| 422 | `VALIDATION_FAILED`, `REQUEST_INCOMPLETE`, `DOCUMENT_INVALID`, `DOCUMENT_LIMIT_REACHED`, `UNSUPPORTED_CURRENCY`, `ROLE_NOT_ALLOWED`, `CONSENT_VERSION_MISMATCH`, `TOKEN_INVALID` |
| 429 | `RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` (never leaks stack traces) |

Other conventions:
- Creation returns 201 plus a `Location` header.
- Pagination is cursor-based. Query params `cursor` (opaque base64url) and `limit` (1–50, default 20). Response shape `{ items: [...], nextCursor: string | null }`.
- Timestamps are UTC ISO-8601 (`date-time`); `dueDate` is `date`.
- Money is `{ amountMinor: int64 ≥ 1, currency: "^[A-Z]{3}$" }`. In this slice the server accepts only `INR` (otherwise 422 `UNSUPPORTED_CURRENCY`).
- IDs are UUIDs.

---

## 7. OpenAPI contract (FND-2): `packages/api-spec/openapi.yaml`

- OpenAPI **3.0.3** (for generator compatibility).
- `servers: [{ url: http://localhost:8080 }]`. Paths include the full `/api/v1` prefix.
- `securitySchemes`: `sessionCookie` (apiKey, in cookie, `KYRO_SESSION`) and `csrfHeader` (apiKey, in header, `X-XSRF-TOKEN`).
- Every 4xx/5xx response references `components/responses/Problem*` with schema `Problem`.
- **operationIds and tags below are fixed.** They become Java interface method names and TS identifiers.

| # | Method | Path | operationId | Tag | Auth | Success |
|---|---|---|---|---|---|---|
| 1 | GET | /api/v1/auth/csrf | getCsrfToken | Auth | public | 200 CsrfToken |
| 2 | POST | /api/v1/auth/register | register | Auth | public | 202 AcceptedMessage |
| 3 | POST | /api/v1/auth/verify-email | verifyEmail | Auth | public | 204 |
| 4 | POST | /api/v1/auth/verify-email/resend | resendVerificationEmail | Auth | public | 202 AcceptedMessage |
| 5 | POST | /api/v1/auth/session | login | Auth | public | 200 CurrentUser |
| 6 | DELETE | /api/v1/auth/session | logout | Auth | auth | 204 |
| 7 | GET | /api/v1/me | getCurrentUser | Me | auth | 200 CurrentUser |
| 8 | PATCH | /api/v1/me/profile | updateMyProfile | Me | auth | 200 CurrentUser |
| 9 | GET | /api/v1/policies/current | getCurrentPolicies | Policies | public | 200 PolicyVersions |
| 10 | POST | /api/v1/education/requests | createEducationRequest | EducationRequests | RECIPIENT | 201 EducationRequest |
| 11 | GET | /api/v1/me/education/requests | listMyEducationRequests | EducationRequests | RECIPIENT | 200 EducationRequestSummaryList |
| 12 | GET | /api/v1/education/requests/{requestId} | getEducationRequest | EducationRequests | RECIPIENT, owner | 200 EducationRequest |
| 13 | PATCH | /api/v1/education/requests/{requestId} | updateEducationRequest | EducationRequests | owner | 200 EducationRequest |
| 14 | POST | /api/v1/education/requests/{requestId}/submit | submitEducationRequest | EducationRequests | owner | 200 EducationRequest |
| 15 | POST | /api/v1/education/requests/{requestId}/withdraw | withdrawEducationRequest | EducationRequests | owner | 200 EducationRequest |
| 16 | POST | /api/v1/education/requests/{requestId}/documents/upload-url | createDocumentUploadUrl | Documents | owner | 201 DocumentUploadAuthorization |
| 17 | POST | /api/v1/education/requests/{requestId}/documents/{documentId}/complete | completeDocumentUpload | Documents | owner | 200 RequestDocument |
| 18 | GET | /api/v1/education/requests/{requestId}/documents | listRequestDocuments | Documents | owner | 200 RequestDocumentList |
| 19 | DELETE | /api/v1/education/requests/{requestId}/documents/{documentId} | deleteRequestDocument | Documents | owner | 204 |
| 20 | GET | /api/v1/admin/verification/cases | listVerificationCases | Verification | VERIFIER/ADMIN | 200 VerificationCasePage |
| 21 | GET | /api/v1/admin/verification/cases/{caseId} | getVerificationCase | Verification | VERIFIER/ADMIN | 200 VerificationCaseDetail |
| 22 | POST | /api/v1/admin/verification/cases/{caseId}/assign | assignVerificationCase | Verification | VERIFIER/ADMIN | 200 VerificationCaseDetail |
| 23 | POST | /api/v1/admin/verification/cases/{caseId}/decision | decideVerificationCase | Verification | assignee | 200 VerificationCaseDetail |
| 24 | POST | /api/v1/admin/verification/cases/{caseId}/documents/{documentId}/view-url | createDocumentViewUrl | Verification | VERIFIER/ADMIN | 200 DocumentViewUrl |
| 25 | GET | /api/v1/admin/audit-events | listAuditEvents | Audit | VERIFIER/ADMIN | 200 AuditEventPage |
| 26 | GET | /api/v1/discovery/feed | getDiscoveryFeed | Discovery | public (personalised if DONOR) | 200 FeedPage |
| 27 | GET | /api/v1/discovery/feed/{requestId} | getPublicRequest | Discovery | public | 200 PublicRequestDetail |
| 28 | POST | /api/v1/discovery/requests/{requestId}/swipes | recordSwipe | Discovery | DONOR | 200 SwipeResult |
| 29 | GET | /api/v1/discovery/saved | listSaved | Discovery | DONOR | 200 SavedPage |
| 30 | DELETE | /api/v1/discovery/saved/{requestId} | removeSaved | Discovery | DONOR | 204 |

Query parameters:
- #20: `queue` enum `UNASSIGNED` (default), `MINE`, `CLOSED`, `ALL`; plus `cursor`, `limit`.
- #25: `entityType`, `entityId` (uuid), `actorUserId` (uuid), `cursor`, `limit`.
- #26, #29: `cursor`, `limit`.

The blueprint's `/education/requests/{id}/status` endpoint is folded into `EducationRequest` (`status`, `statusHistory`, `reviewerFeedback`).

### Enums

- `Role`: RECIPIENT, DONOR, VERIFIER, ADMIN
- `RequestStatus`: DRAFT, SUBMITTED, IN_REVIEW, CHANGES_REQUESTED, APPROVED, REJECTED, WITHDRAWN, FUNDED, CLOSED. FUNDED and CLOSED are reserved and not used by the slice.
- `ExpenseType`: TUITION, EXAMINATION_FEE, COURSE_MATERIALS, OTHER_ACADEMIC
- `DocumentType`: TUITION_INVOICE, FEE_STATEMENT, ENROLLMENT_PROOF, OTHER
- `DocumentStatus`: PENDING_UPLOAD, UPLOADED, REJECTED, DELETED
- `DocumentContentType`: application/pdf, image/jpeg, image/png
- `CaseStatus`: OPEN, ASSIGNED, CLOSED
- `CaseOutcome`: APPROVED, REJECTED, CHANGES_REQUESTED, WITHDRAWN
- `Decision`: APPROVE, REJECT, REQUEST_CHANGES
- `SwipeAction`: PASS, INTERESTED, SAVED
- `DuplicateKind`: DOCUMENT_HASH_MATCH, FEE_REFERENCE_MATCH

### Key schemas

Field constraints are written into the spec so that Bean Validation and Zod both derive from them.

- **Problem**: `type`, `title`, `status`, `detail`, `instance`, `code`, `correlationId`, `errors[]` of `{field, message}`.
- **CsrfToken**: `{headerName, token}`.
- **AcceptedMessage**: `{message}`.
- **PolicyVersions**: `{termsVersion, privacyVersion}`.
- **Money**: `{amountMinor: int64 min 1, currency: string pattern ^[A-Z]{3}$}`.
- **UserSummary**: `{id, displayName}`.

Identity:
- **RegisterRequest**:
  - `email` (format email, ≤254), `password` (12–128), `displayName` (2–80)
  - `publicDisplayName` (2–40; used for the recipient and/or donor profile)
  - `countryCode` (^[A-Z]{2}$)
  - `roles` (array of RECIPIENT/DONOR, 1–2 items, unique)
  - `consents` `{termsVersion, privacyVersion}`
  - All fields required.
- **VerifyEmailRequest**: `{token}`. **ResendVerificationRequest**: `{email}`. **LoginRequest**: `{email, password}`.
- **CurrentUser**: `{id, email, displayName, countryCode, roles[], emailVerified, recipientProfile?: {publicDisplayName}, donorProfile?: {publicDisplayName}, createdAt}`.
- **UpdateProfileRequest**: `{displayName?, recipientPublicDisplayName?, donorPublicDisplayName?}`.

Education requests:
- **EducationRequestCreate**:
  - Required: `title` (5–120), `expenseType`, `amount` (Money).
  - Optional: `story` (≤2000), `institutionName` (≤200), `institutionCity` (≤100), `institutionCountry` (^[A-Z]{2}$, default IN), `courseName` (≤200), `feeReference` (≤100), `dueDate` (date).
- **EducationRequestUpdate**: the same fields, all optional, plus `version` (int64, **required**; used for optimistic locking).
- **EducationRequest**:
  - All create fields.
  - Plus `id`, `status`, `publicDisplayName` (read-only, from the recipient profile), `editable` (bool), `submissionCount`, `documents[]` (RequestDocument), `statusHistory[]` (StatusHistoryEntry), `reviewerFeedback` (string|null; the latest message to the recipient), `submittedAt`, `approvedAt`, `createdAt`, `updatedAt`, `version`.
- **StatusHistoryEntry**: `{fromStatus|null, toStatus, changedAt, note|null}`. `note` is recipient-visible text.
- **EducationRequestSummary**: `{id, title, status, amount, dueDate, documentCount, updatedAt}`. **EducationRequestSummaryList**: `{items[]}`.

Documents:
- **DocumentUploadRequest**: `{documentType, fileName (1–200), contentType (DocumentContentType), sizeBytes (1–10485760)}`.
- **RequestDocument**: `{id, documentType, originalFileName, contentType, sizeBytes, status, createdAt, uploadedAt|null}`. It never contains a storage key or URL. **RequestDocumentList**: `{items[]}`.
- **DocumentUploadAuthorization**: `{document: RequestDocument, uploadUrl, method: "PUT", requiredHeaders: {string: string}, expiresAt}`.

Verification:
- **VerificationCaseSummary**: `{id, requestId, requestTitle, recipientPublicName, amount, status, submissionNumber, assignedTo: UserSummary|null, openedAt, closedAt|null, outcome|null}`. **VerificationCasePage**: `{items[], nextCursor}`.
- **ReviewRecipient**: `{id, displayName, email, emailVerified, memberSince}`.
- **ReviewRequestView**: all EducationRequest fields plus `recipient` (ReviewRecipient).
- **DuplicateIndicator**: `{kind, otherRequestId, otherRequestStatus, detail}`.
- **VerificationDecisionView**: `{id, decision, rationale, messageToRecipient|null, decidedBy: UserSummary, createdAt}`.
- **VerificationCaseDetail**: VerificationCaseSummary fields plus `request` (ReviewRequestView), `duplicateIndicators[]`, `decisions[]` (all cases for this request, newest first), `canDecide` (bool for the caller).
- **DecisionInput**: `{decision, rationale (10–2000, internal only), messageToRecipient (≤2000)}`. `messageToRecipient` is required when `decision` is REJECT or REQUEST_CHANGES; the server enforces this with 422.
- **DocumentViewUrl**: `{url, expiresAt}`.

Audit:
- **AuditEvent**: `{id, occurredAt, actor: UserSummary|null, action, entityType, entityId, correlationId, details: object}`. **AuditEventPage**: `{items[], nextCursor}`.

Discovery:
- **FundingProgress**: `{target: Money, pledgedMinor: int64, confirmedMinor: int64}`. Both are always 0 in this slice; pledges and confirmed payments are kept separate.
- **RequestCard**: `{id, title, storyExcerpt, recipientPublicName, expenseType, institutionName, institutionCity, institutionCountry, courseName, amount, dueDate, verifiedAt, funding: FundingProgress}`. It never contains feeReference, documents, email or recipient id.
- **PublicRequestDetail**: RequestCard plus `story`, plus `viewerAction` (SwipeAction|null; null when anonymous).
- **FeedPage**: `{items: RequestCard[], nextCursor}`.
- **SwipeInput**: `{action}`. **SwipeResult**: `{requestId, action, updatedAt}`.
- **SavedItem**: `{request: RequestCard, action (SAVED or INTERESTED), savedAt}`. **SavedPage**: `{items[], nextCursor}`.

### Generated interfaces and the module that implements each

| Interface | Module | Task |
|---|---|---|
| AuthApi, MeApi, PoliciesApi | identity | API-1 |
| AuditApi | audit | API-1 |
| EducationRequestsApi, DocumentsApi | education | API-2 |
| VerificationApi | verification | API-3 |
| DiscoveryApi | discovery | API-4 |

---

## 8. Request lifecycle state machine

```text
            create
              │
              ▼
  ┌──────► DRAFT ──submit──► SUBMITTED ──assign──► IN_REVIEW ──APPROVE──► APPROVED ──(later)──► FUNDED ──► CLOSED
  │  (edit)                      ▲                     │  │
  │                              │ resubmit            │  └──REJECT──► REJECTED (terminal)
  │                       CHANGES_REQUESTED ◄──REQUEST_CHANGES
  │                              (edit)
  └─ withdraw allowed from DRAFT, SUBMITTED, IN_REVIEW, CHANGES_REQUESTED, APPROVED ──► WITHDRAWN (terminal)
```

| From | To | Actor | Guard / side effects |
|---|---|---|---|
| (none) | DRAFT | recipient | `submission_count = 0` |
| DRAFT, CHANGES_REQUESTED | SUBMITTED | owner | **Completeness:** title, story ≥50 chars, expenseType, institutionName, institutionCity, courseName, feeReference, amount (INR), dueDate ≥ today, and ≥1 document with status UPLOADED. Otherwise 422 `REQUEST_INCOMPLETE` with `errors[]`. On success: `submission_count += 1`, `submitted_at` set, and event `EducationRequestSubmitted` published, from which verification opens a case. |
| SUBMITTED | IN_REVIEW | verifier (assign) | Called via `EducationReviewFacade.markInReview` |
| IN_REVIEW | APPROVED | assignee | `approved_at` set. The request becomes visible in `discovery_public_requests`. |
| IN_REVIEW | REJECTED | assignee | `messageToRecipient` goes into the status history note |
| IN_REVIEW | CHANGES_REQUESTED | assignee | `messageToRecipient` goes into the status history note; the request is editable again |
| DRAFT, SUBMITTED, IN_REVIEW, CHANGES_REQUESTED, APPROVED | WITHDRAWN | owner | Publishes `EducationRequestWithdrawn`. Verification closes the open case with outcome WITHDRAWN. |

- **Editable** (PATCH, document upload and delete): only DRAFT and CHANGES_REQUESTED. Any other status returns 409 `INVALID_STATE_TRANSITION`. An APPROVED request can never be edited; covers blueprint scenario 3: altered bills must go back to review.
- Every transition runs in one transaction with three writes:
  - a conditional update (`WHERE id=? AND status=? AND version=?`)
  - a `funding_request_status_history` row
  - an `audit_events` row
- Verification case states: OPEN → ASSIGNED → CLOSED, with an outcome. There is at most one non-CLOSED case per request (DB-enforced), and one case per submission (`submission_number`).

---

## 9. Database (FND-1): `apps/api/src/main/resources/db/migration/`

General rules:
- UUID primary keys with `DEFAULT gen_random_uuid()`.
- `timestamptz` columns default `now()`.
- **Use `varchar(n)` instead of `char`/`text`** so Hibernate `validate` passes. Restrict enum-like columns with `CHECK`.
- `version bigint NOT NULL DEFAULT 0` on entities that are edited.
- Index every foreign key.

**V1__create_identity.sql**
- `users`:
  - `id`, `email varchar(254)`, `email_normalized varchar(254) UNIQUE` (lower-cased and trimmed), `password_hash varchar(255)`, `display_name varchar(80)`
  - `country_code varchar(2)`, CHECK `^[A-Z]{2}$`
  - `status varchar(32)`, CHECK IN (PENDING_VERIFICATION, ACTIVE, SUSPENDED)
  - `email_verified_at`, `created_at`, `updated_at`, `version`
- `roles`: `code varchar(32)` PK, `description varchar(200)`. Seed rows: RECIPIENT, DONOR, VERIFIER, ADMIN.
- `user_roles`: `user_id` FK→users ON DELETE CASCADE, `role_code` FK→roles, `granted_at`, `granted_by` FK→users (nullable). PK(user_id, role_code).
- `recipient_profiles`: `user_id` PK FK, `public_display_name varchar(40)`, `created_at`, `updated_at`, `version`.
- `donor_profiles`: same shape as `recipient_profiles`.
- `email_verification_tokens`: `id`, `user_id` FK, `token_hash varchar(64) UNIQUE`, `expires_at`, `consumed_at`, `created_at`.
- `consent_records`: `id`, `user_id` FK, `policy_type varchar(16)` CHECK IN (TERMS, PRIVACY), `policy_version varchar(32)`, `accepted_at`, `ip_address varchar(45)`, `user_agent varchar(512)`.

**V2__create_education_requests.sql**
- `institutions`: `id`, `name varchar(200)`, `city varchar(100)`, `country_code varchar(2)`, `website varchar(300)`, `verified boolean DEFAULT false`, `created_at`. UNIQUE(lower(name), country_code). Not used by the slice UI; requests carry free-text institution fields and an optional FK.
- `funding_requests`:
  - `id`, `recipient_user_id` FK→users, `institution_id` FK nullable
  - `status varchar(32)` CHECK IN (all 9 RequestStatus values), default DRAFT
  - `title varchar(120) NOT NULL`, `story varchar(2000)`
  - `expense_type varchar(32)` CHECK
  - `institution_name varchar(200)`, `institution_city varchar(100)`, `institution_country varchar(2) DEFAULT 'IN'`, `course_name varchar(200)`
  - `fee_reference varchar(100)`, `fee_reference_normalized varchar(100)` (upper-case alphanumerics only)
  - `amount_minor bigint NOT NULL` CHECK > 0, `currency varchar(3) NOT NULL` CHECK `^[A-Z]{3}$`
  - `due_date date`, `submission_count int NOT NULL DEFAULT 0`
  - `submitted_at`, `approved_at`, `created_at`, `updated_at`, `version`
  - Indexes: (recipient_user_id, created_at DESC); (status, approved_at DESC, id DESC); (fee_reference_normalized).
- `request_documents`:
  - `id`, `request_id` FK, `uploaded_by` FK→users
  - `document_type varchar(32)` CHECK
  - `storage_key varchar(300) UNIQUE` (format `requests/{requestId}/documents/{documentId}`; never contains the file name)
  - `original_file_name varchar(200)`, `content_type varchar(64)` CHECK IN (3 types), `size_bytes bigint` CHECK 1..10485760
  - `sha256 varchar(64)`
  - `status varchar(32)` CHECK, default PENDING_UPLOAD
  - `scan_status varchar(32) DEFAULT 'NOT_SCANNED'` (placeholder for future malware scanning)
  - `created_at`, `uploaded_at`, `deleted_at`
  - Indexes: (request_id), (sha256).
- `funding_request_status_history`: `id`, `request_id` FK, `from_status varchar(32)` nullable, `to_status varchar(32)`, `changed_by` FK→users nullable, `note varchar(2000)`, `changed_at`. Index (request_id, changed_at).

**V3__create_verification.sql**
- `verification_cases`:
  - `id`, `request_id` FK, `submission_number int`
  - `status varchar(16)` CHECK (OPEN, ASSIGNED, CLOSED)
  - `outcome varchar(32)` CHECK nullable
  - `assigned_to` FK→users nullable, `assigned_at`, `opened_at`, `closed_at`, `version`
  - UNIQUE(request_id, submission_number)
  - UNIQUE INDEX on (request_id) WHERE status <> 'CLOSED'
  - Index (status, opened_at)
- `verification_decisions`:
  - `id`, `case_id` FK UNIQUE (one decision per case), `decided_by` FK→users
  - `decision varchar(32)` CHECK
  - `rationale varchar(2000) NOT NULL` CHECK length ≥ 10
  - `message_to_recipient varchar(2000)`, `created_at`

**V4__create_discovery.sql**
- `swipes`:
  - `id`, `donor_user_id` FK→users, `request_id` FK→funding_requests
  - `action varchar(16)` CHECK (PASS, INTERESTED, SAVED)
  - `created_at`, `updated_at`
  - UNIQUE(donor_user_id, request_id); index (donor_user_id, action, updated_at DESC)
- `donor_preferences`: `user_id` PK FK, `expense_types varchar(32)[]`, `max_amount_minor bigint`, `currency varchar(3)`, `updated_at`. The table only; no endpoints in this slice.
- **VIEW `discovery_public_requests`**: the privacy-safe interface from the education module to discovery.
  - Selects from funding_requests joined to recipient_profiles: `r.id, r.recipient_user_id, r.title, r.story, r.expense_type, r.institution_name, r.institution_city, r.institution_country, r.course_name, r.amount_minor, r.currency, r.due_date, r.approved_at, p.public_display_name AS recipient_public_name`
  - `WHERE r.status = 'APPROVED'`
  - It must never include fee_reference or document data.

**V5__create_audit.sql**
- `audit_events`:
  - `id`, `occurred_at`, `actor_user_id` FK→users nullable, `actor_roles varchar(128)`
  - `action varchar(64)`, `entity_type varchar(64)`, `entity_id uuid`
  - `correlation_id varchar(64)`, `ip_address varchar(45)`, `details jsonb NOT NULL DEFAULT '{}'`
  - Indexes: (entity_type, entity_id, occurred_at DESC); (actor_user_id, occurred_at DESC)
- A plpgsql trigger function `audit_events_immutable()` raises an exception on UPDATE or DELETE. It is attached BEFORE UPDATE OR DELETE.

Renumbering: later migrations shift by one relative to the blueprint. V6 funding (campaigns, pledges, contributions), V7 payments, V8 ledger, V9 operations (reports, disputes). See D-003.

`scripts/db/verify.sql` (FND-1): one transaction ending in `ROLLBACK`, using `DO` blocks to assert expected failures. It checks:
- the CHECK constraints reject bad status, currency and amount values
- the partial unique index on open cases holds
- the swipes unique constraint holds
- the view excludes non-APPROVED requests and has no fee column
- UPDATE and DELETE on `audit_events` raise

---

## 10. Backend architecture (API-1 sets this up; everyone follows it)

Gradle: root project `kyro`. `settings.gradle.kts` does `include(":apps:api")`; `:apps:worker` comes later. The root `build.gradle.kts` applies no plugins. There is a single deployable module, and modularity comes from packages.

```text
com.kyro.platform
├── PlatformApplication
├── common/        config, errors (ApiException family + @RestControllerAdvice), security (SecurityConfig, KyroPrincipal, Role, CurrentActor, RateLimitFilter),
│                  observability (CorrelationIdFilter), storage (ObjectStorage + S3ObjectStorage), validation, time (Clock bean)
├── identity/      api (controllers implementing AuthApi/MeApi/PoliciesApi), application (services, UserDirectory), domain, infrastructure (JPA, mail)
├── audit/         api (AuditApi), application (AuditService), infrastructure (JdbcTemplate writer/reader)
├── devseed/       @Profile("local") IdentityDevSeeder (users)
├── education/     api, application (+ application/port = FROZEN, created by API-1), domain, infrastructure (+ dev seeder for approved requests)
├── verification/  api, application, domain, infrastructure
└── discovery/     api, application, infrastructure (JdbcTemplate over discovery_public_requests + swipes)
```

**Boundary rule.** API-1 writes an ArchUnit test, `ModuleBoundaryTest`, that enforces it. A class in module X may depend only on:
- its own module
- `common..`
- `com.kyro.platform.contract..` (generated code)
- `<other>.application..` of identity, audit and education, which includes `education.application.port..`

Discovery reads the database view and never imports education code. No module may import another module's `domain` or `infrastructure` package.

**Frozen cross-module Java contracts.** API-1 creates these files with `// FROZEN CONTRACT` headers:

```java
// common.security
public enum Role { RECIPIENT, DONOR, VERIFIER, ADMIN }
public record KyroPrincipal(UUID userId, String email, Set<Role> roles) {}
public interface CurrentActor { Optional<KyroPrincipal> current(); KyroPrincipal require(); }

// common.errors
public class ApiException extends RuntimeException { ApiException(HttpStatus status, String code, String detail, List<FieldError> errors) }
// + NotFoundException, ConflictException(code), ForbiddenException(code), UnprocessableException(code, errors)

// common.storage
public interface ObjectStorage {
  PresignedUrl presignPut(String key, String contentType, long contentLength, Duration ttl);
  PresignedUrl presignGet(String key, Duration ttl);
  Optional<StoredObjectMetadata> head(String key);   // size, contentType
  InputStream open(String key);
  void delete(String key);
}
public record PresignedUrl(String url, Map<String,String> requiredHeaders, Instant expiresAt) {}
public record StoredObjectMetadata(long sizeBytes, String contentType) {}

// audit.application
public interface AuditService { void record(AuditEntry entry); } // participates in caller's transaction
public record AuditEntry(String action, String entityType, UUID entityId, Map<String,Object> details) {} // actor, roles, correlationId, IP filled from context
public final class AuditActions { /* String constants, see list below */ }

// identity.application
public interface UserDirectory { Map<UUID, UserSummaryView> findSummaries(Collection<UUID> ids); Optional<ReviewRecipientView> findRecipient(UUID userId); }

// education.application.port
public interface EducationReviewFacade {
  Map<UUID, ReviewSummary> getReviewSummaries(Collection<UUID> requestIds);   // title, recipientPublicName, amountMinor, currency, recipientUserId
  ReviewSnapshot getReviewSnapshot(UUID requestId);                           // full private request view + documents + status history + duplicate indicators
  void markInReview(UUID requestId, UUID verifierId);                          // SUBMITTED -> IN_REVIEW
  void applyDecision(UUID requestId, VerificationOutcome outcome, UUID verifierId, String messageToRecipient);
  PresignedUrl issueDocumentViewUrl(UUID requestId, UUID documentId, UUID actorId); // audits DOCUMENT_VIEW_URL_ISSUED
}
public enum VerificationOutcome { APPROVED, REJECTED, CHANGES_REQUESTED }
public record EducationRequestSubmitted(UUID requestId, UUID recipientUserId, int submissionNumber, Instant submittedAt) {}
public record EducationRequestWithdrawn(UUID requestId, UUID recipientUserId, Instant withdrawnAt) {}
// + records ReviewSummary, ReviewSnapshot, ReviewDocument, ReviewStatusHistoryEntry, DuplicateIndicatorView
```

- Events are published with `ApplicationEventPublisher` inside the education transaction. Verification consumes them with a synchronous `@EventListener`, so a case is created atomically with the submit.
- Dependency direction is verification → education only.
- API-1 also adds `education/application/port/package-info.java` and an empty `NoOpEducationReviewFacade` placed in `apps/api/src/test/...` only, so that API-3's unit tests have a placeholder.

Audit action constants:
- Identity: USER_REGISTERED, REGISTRATION_ATTEMPT_EXISTING_EMAIL, EMAIL_VERIFIED, LOGIN_SUCCEEDED, LOGIN_FAILED, LOGOUT, PROFILE_UPDATED
- Requests: REQUEST_CREATED, REQUEST_UPDATED, REQUEST_STATUS_CHANGED
- Documents: DOCUMENT_UPLOAD_AUTHORIZED, DOCUMENT_UPLOADED, DOCUMENT_REJECTED, DOCUMENT_DELETED, DOCUMENT_VIEW_URL_ISSUED
- Access: ACCESS_DENIED (details: resource, reason)
- Verification: CASE_OPENED, CASE_ASSIGNED, CASE_CLOSED, VERIFICATION_DECIDED

Swipes are not audited.

**Shared test support** (API-1, frozen), in `apps/api/src/test/java/com/kyro/platform/support/`:
- `IntegrationTestBase`: `@SpringBootTest` plus MockMvc, singleton Testcontainers for Postgres 17 and MinIO (same tag as compose), and the bucket created on start.
- `TestUsers`: `createActiveUser(Role...)` returns `TestUser(id, email, password, MockHttpSession)` with a logged-in session.
- `Csrf` helpers: use spring-security-test `csrf()`.
- `DbFixtures`: a JdbcTemplate helper that can insert `funding_requests` rows in any status. API-3 and API-4 use it so they are not blocked by API-2.

---

## 11. Frontend shared contracts

**@kyro/api-client** (FND-2):
- `src/schema.d.ts`: generated and committed. CI checks it has not drifted from the spec.
- `src/index.ts` exports:
  - `createKyroClient({ baseUrl = '' })`: an openapi-fetch client with `credentials: 'include'` and middleware. On non-GET requests the middleware reads the `XSRF-TOKEN` cookie; if it is missing, it first calls `GET /api/v1/auth/csrf`; it then sets `X-XSRF-TOKEN`. It also adds `X-Correlation-Id`.
  - `KyroProblem` type and `isProblem(x)`.
  - `ApiError` class (carries the Problem).
  - `unwrap(res)`: returns `data` or throws `ApiError`.
  - Schema aliases: `export type EducationRequest = components['schemas']['EducationRequest']`, and the same for every schema in §7.

**@kyro/design-system** (FND-3):
- Shipped as TS source; consumers add it to `transpilePackages`.
- `theme.css` holds Tailwind v4 `@theme` tokens:
  - Colors: `--color-navy-900` (primary), `--color-champagne-400` (accent), `--color-warm-50` (background), `--color-charcoal-900` (text), `--color-success-600`, `--color-danger-600`
  - Font: Inter
  - Radii and shadows
- Components: Button (primary/secondary/ghost/danger; loading state), Card, Badge, RequestStatusBadge, CaseStatusBadge, Input, Textarea, Select, Field (label, hint, error, aria wiring), Alert, EmptyState, Spinner, Dialog (accessible), SandboxBanner, MoneyText, DateText.
- `money.ts`:
  - `formatMoney(amountMinor, currency, locale='en-IN')`
  - `parseMoneyInput(str, currency)`: parses a string into integer minor units using `Intl` currency digits. **No floats.**
  - `minorUnitDigits(currency)`
- Unit tests for the money functions and Field accessibility.

**apps/web shared internals.** WEB-1 creates these and WEB-2 consumes them:
- `src/lib/api.ts`: exports `api = createKyroClient()`.
- `src/lib/query.tsx`: `QueryProvider`.
- Query key factory `src/lib/queryKeys.ts`: `me`, `education.list`, `education.detail(id)`, `discovery.feed`, `discovery.detail(id)`, `discovery.saved`. WEB-1 defines all keys, including the discovery ones.
- `src/features/authentication/useMe.ts`: `useMe()` returns `{ user: CurrentUser | null, isLoading }`; a 401 maps to null.
- `src/components/RequireAuth.tsx`: `<RequireAuth roles?: Role[]>`. Redirects to `/login?next=<path>`. Shows a "not permitted" state when the role is missing.
- `src/components/AppShell.tsx`:
  - Header nav: Discover (`/discover`), Saved (`/saved`, DONOR), My requests (`/recipient/dashboard`, RECIPIENT), Login/Logout.
  - Includes `SandboxBanner`.
- The root `app/layout.tsx` wraps everything in providers plus AppShell. WEB-2 owns `app/(donor)/layout.tsx`.

---

## 12. Work packages

### FND-1 — Monorepo root, local infra, DB migrations (Phase 1)
**Owns:** root workspace files (`package.json`, `pnpm-workspace.yaml`, `.gitignore`, `.gitattributes`, `.editorconfig`, `.nvmrc`=24, `.npmrc`, `README.md`), `docker-compose.yml`, `.env.example`, `packages/config/`, `infrastructure/docker/local/`, `apps/api/src/main/resources/db/migration/`, `scripts/db/`.

**Build:**
- Everything in §4 (compose, env), §9 (V1–V5 plus verify.sql) and the root scripts in §3.
- `packages/config` (`@kyro/config`): `tsconfig.base.json` (strict, ES2023, bundler resolution), `eslint.config.mjs` (flat; TS plus react-hooks plus jsx-a11y presets exported for Next apps), `prettier.config.mjs`.
- README: prerequisites, quickstart (`pnpm install`, `pnpm infra:up`, run the API, run web and admin), ports table, seed accounts.

**Acceptance and verification:**
- `pnpm install` succeeds on Windows.
- `docker compose up -d --wait` shows postgres, minio and mailpit healthy, and minio-init exits 0. The bucket `kyro-documents-local` exists and anonymous access is `none` (`docker compose run --rm minio-init mc anonymous get local/kyro-documents-local` or equivalent).
- `docker compose --profile tools run --rm flyway migrate`, then `... flyway info`: V1–V5 are Success, and `validate` passes.
- `docker compose exec -T postgres psql -U kyro -d kyro -v ON_ERROR_STOP=1 -f - < scripts/db/verify.sql` exits 0.
- `docker compose down -v && docker compose up -d --wait` is repeatable.

### FND-2 — OpenAPI contract and generated TS client (Phase 1, parallel with FND-1)
**Owns:** `packages/api-spec/`, `packages/api-client/`.

**Build:**
- `openapi.yaml` exactly as in §7: all 30 operations, all schemas with constraints, Problem responses, security schemes, `Location` header on 201, example values using fictional INR data.
- `@kyro/api-spec`: script `lint` (`redocly lint openapi.yaml`) and `redocly.yaml`.
- `@kyro/api-client`: script `generate` (`openapi-typescript ../api-spec/openapi.yaml -o src/schema.d.ts`), plus `src/index.ts` per §11, `typecheck`, and `test` (Vitest covering CSRF priming, header injection and `unwrap` error mapping with a mocked fetch).

**Acceptance and verification:**
- `pnpm --filter @kyro/api-spec lint` reports 0 errors.
- `pnpm gen:client && git diff --exit-code packages/api-client/src/schema.d.ts` shows no drift.
- `pnpm --filter @kyro/api-client typecheck test` passes.
- Every operationId and tag matches §7.

### FND-3 — Design system (Phase 1, after FND-1 root files)
**Owns:** `packages/design-system/`.

**Build:** per §11. Peer dependencies: react, react-dom. No Next-specific code.

**Acceptance:**
- `pnpm --filter @kyro/design-system lint typecheck test` passes.
- `parseMoneyInput('12,500.50','INR') === 1250050`.
- Invalid input returns an error result; it does not return NaN.
- Components forward refs, show a visible focus ring, and meet WCAG 2.2 AA contrast for the token pairs used (record the contrast ratios in the package README).

### API-1 — Gradle, Spring Boot core, identity, audit, storage (Phase 2)
**Owns:**
- Root Gradle files (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/`)
- `apps/api/**`, except the migration directory, `education/**` (apart from `education/application/port/**`, which API-1 does own), `verification/**` and `discovery/**`, in both main and test.

**Build:**
- Wrapper bootstrap and JDK handling per §3.
- `apps/api/build.gradle.kts` with the codegen config from §3 and dependencies: webmvc, security, validation, data-jpa, jdbc, mail, actuator, flyway plus flyway-database-postgresql, postgresql, AWS SDK v2 s3, Testcontainers (postgresql, minio, junit-jupiter), spring-boot-testcontainers, spring-security-test, ArchUnit. Add every dependency API-2/3/4 will need now; they must not edit Gradle files.
- `application.yml` with the full tree from §4, plus `application-local.yml`.
- Common code: errors and advice (§6, including bean-validation → 422 and unparseable JSON → 400), CorrelationIdFilter, SecurityConfig (§5), RateLimitFilter, S3ObjectStorage. The storage class uses two clients: an internal endpoint client and a presigner on the public endpoint.
- Identity: endpoints #1–#9, Mailpit email via `JavaMailSender`, and consent records.
- Audit: AuditService (JdbcTemplate, `?::jsonb`) and endpoint #25.
- Dev seeder: the 4 users from §5.
- All frozen contracts and test support from §10, plus ModuleBoundaryTest.

**Acceptance (tests required):**
- Register: always 202, including for an existing email.
- VERIFIER role in registration returns 422.
- Login before verification returns 403 `EMAIL_NOT_VERIFIED`.
- Verify-email: a token works once; an expired or reused token returns 422 `TOKEN_INVALID`.
- Login sets `KYRO_SESSION` and rotates the session id.
- POST without CSRF returns 403.
- `/me` returns 401 when anonymous.
- A wrong password returns 401 and writes a LOGIN_FAILED audit event.
- The 11th login attempt within a minute returns 429.
- Problem bodies include `code` and `correlationId`, and the `X-Correlation-Id` header is echoed.
- An `audit_events` UPDATE fails (DB trigger).
- ModuleBoundaryTest passes.
- `ObjectStorage` round-trip against the MinIO container: presignPut, then an HTTP PUT, then head and open.

**Verification:**
- `./gradlew :apps:api:build` is green. On Windows use `gradlew.bat` or `./gradlew` from Git Bash.
- `pnpm infra:up`, then `./gradlew :apps:api:bootRun`. `curl -s localhost:8080/actuator/health` shows UP, and the seed users exist.
- Manual cookie-jar run: get the csrf token, log in as `admin@kyro.local`, call `GET /api/v1/me`.

```bash
curl -s -c j -b j localhost:8080/api/v1/auth/csrf
T=$(awk '/XSRF-TOKEN/{print $7}' j)
curl -s -c j -b j -H "X-XSRF-TOKEN: $T" -H 'Content-Type: application/json' \
  -d '{"email":"admin@kyro.local","password":"KyroLocal!2026"}' localhost:8080/api/v1/auth/session
curl -s -b j localhost:8080/api/v1/me
```

### API-2 — Education requests and documents (Phase 3)
**Owns:** `apps/api/src/{main,test}/java/com/kyro/platform/education/**` except `education/application/port/**`.

**Build:**
- Endpoints #10–#19.
- The state machine from §8 as a domain enum with `canTransitionTo`, plus conditional updates.
- `EducationReviewFacade` implementation, including duplicate indicators:
  - same `sha256` on an UPLOADED document of a different request
  - same `fee_reference_normalized` combined with case-insensitive `institution_name` on a different non-WITHDRAWN request
- Event publishing.
- Document flow:
  - upload-url creates a PENDING_UPLOAD row with key `requests/{rid}/documents/{did}` and presigns a PUT that signs content-type and length (TTL 10m). The limit is 10 non-deleted documents per request.
  - complete: HEAD the object and check size equals the declared size; check magic bytes (`%PDF`, `FF D8 FF`, `89 50 4E 47`) against the declared type; stream it to compute SHA-256; then set UPLOADED.
  - On failure: delete the object, set REJECTED, audit, and return 422 `DOCUMENT_INVALID`.
  - Delete is a soft delete plus removing the object, and only while the request is editable.
- Object-level authorization: anyone other than the owner gets 404 plus an `ACCESS_DENIED` audit (blueprint scenario 4).
- Local dev seeder: 3 fictional APPROVED requests for `recipient.demo@kyro.local`, each with full status history.

**Acceptance (integration tests):**
- Create, then patch with a stale `version`: 409 `STALE_VERSION`.
- Submit without a document: 422 `REQUEST_INCOMPLETE` with field errors.
- Full upload round-trip against MinIO; a fake PDF (PNG bytes) is rejected.
- A second recipient GETting the first recipient's request or document gets 404, and an ACCESS_DENIED audit row exists.
- A donor calling education endpoints gets 403.
- PATCH on an APPROVED (fixture) request: 409.
- Withdraw from APPROVED removes it from `discovery_public_requests`.
- Submit publishes `EducationRequestSubmitted`; assert with `@RecordApplicationEvents`.
- The facade transitions and status history rows are correct.
- Duplicate indicators are detected.

**Verify:** `./gradlew :apps:api:test --tests 'com.kyro.platform.education.*'`, then the full `./gradlew :apps:api:build`.

### API-3 — Verification (Phase 3)
**Owns:** `apps/api/src/{main,test}/java/com/kyro/platform/verification/**`.

**Build:**
- Endpoints #20–#24.
- An `@EventListener` on `EducationRequestSubmitted` opens a case (OPEN, submissionNumber) and audits CASE_OPENED. `EducationRequestWithdrawn` closes any open case with outcome WITHDRAWN.
- Assign (claim for self):
  - OPEN → ASSIGNED and calls `markInReview`
  - assigned to someone else: 409 `CASE_ALREADY_ASSIGNED`
  - the request's recipient is the caller: 403 `SELF_REVIEW_FORBIDDEN`
- Decision:
  - only the assignee may decide; anyone else gets 409 `CASE_NOT_ASSIGNED_TO_YOU`
  - self-review: 403
  - messageToRecipient is required for REJECT and REQUEST_CHANGES (422)
  - writes `verification_decisions`, closes the case with the outcome, calls `applyDecision`, and audits VERIFICATION_DECIDED with `{decision, caseId}`
  - all in one transaction
- view-url delegates to `issueDocumentViewUrl`; a document that does not belong to the case's request returns 404.
- Queue listing with cursor pagination ordered by `opened_at ASC`; list enrichment via `getReviewSummaries` and `UserDirectory`.

**Acceptance:**
- Unit tests with a mocked facade cover every guard.
- Integration tests (after API-2 is merged; rebase first) cover:
  - submit → case appears in UNASSIGNED
  - claim → request is IN_REVIEW
  - approve → request APPROVED and present in the view
  - REQUEST_CHANGES → recipient edits and resubmits → a new case with submissionNumber 2, and both decisions appear in the detail
  - a RECIPIENT or DONOR calling `/admin/**` gets 403
  - view-url issuance is audited
  - a verifier who is also the request owner is blocked

**Verify:** `./gradlew :apps:api:test --tests 'com.kyro.platform.verification.*'`.

### API-4 — Discovery (Phase 3)
**Owns:** `apps/api/src/{main,test}/java/com/kyro/platform/discovery/**`.

**Build:**
- Endpoints #26–#30, implemented with JdbcTemplate over `discovery_public_requests` and `swipes` only.
- Feed:
  - shows requests from the view with `due_date >= current_date` (or null)
  - when the caller is an authenticated donor, excludes requests the donor has any swipe on and the donor's own requests (`recipient_user_id`)
  - ordered by `approved_at DESC, id DESC`
  - keyset cursor is base64url of `approved_at|id`
  - `storyExcerpt` is the first 240 characters
  - funding fields are `pledgedMinor=0`, `confirmedMinor=0`
- Detail: 404 unless the request is in the view; `viewerAction` comes from swipes for a donor.
- Swipe: idempotent upsert (`INSERT ... ON CONFLICT (donor_user_id, request_id) DO UPDATE`). A request not in the view returns 404. Swiping on your own request returns 422 `VALIDATION_FAILED`.
- Saved: actions SAVED or INTERESTED, ordered by `updated_at DESC`. DELETE removes the row and is idempotent (204).

**Acceptance (integration tests using `DbFixtures`):**
- Feed returns only APPROVED requests; DRAFT, IN_REVIEW, REJECTED and WITHDRAWN fixtures are absent.
- The JSON contains no `feeReference`, `documents` or email (assert on the raw JSON keys).
- Anonymous feed works.
- Paging returns no duplicates or gaps across 3 pages.
- Swiping the same action twice gives one row.
- A swiped request disappears from the feed.
- SAVED appears in saved; DELETE works; anonymous swipe returns 401; a recipient-only user swiping gets 403.

**Verify:** `./gradlew :apps:api:test --tests 'com.kyro.platform.discovery.*'`.

### WEB-1 — Web app foundation, public, auth, recipient (Phase 2)
**Owns:** `apps/web/**` except `apps/web/src/app/(donor)/**` and `apps/web/src/features/discovery/**`.

**Build:**
- Next.js (App Router, TS strict, Tailwind v4 importing `@kyro/design-system/theme.css` with `@source` pointed at the design-system src).
- `next.config.ts` rewrites (§4); `transpilePackages: ['@kyro/design-system','@kyro/api-client']`.
- `dev` script on port 3000; `app/manifest.ts` (PWA manifest with name KYRO, theme navy, icons). The service worker is deferred.
- Shared internals from §11.
- Routes:
  - `(public)/page.tsx` landing: hero "Verified needs. Meaningful impact.", "Browse verified requests" linking to /discover, how verification works, donor and recipient entry points, institutional partnership section, trust links.
  - `(public)/trust/page.tsx`: privacy and safety, static text.
  - `(auth)/register`: role choice, consent checkboxes showing versions from `GET /policies/current`, Zod validation mirroring the spec.
  - `(auth)/register/check-email`, `(auth)/verify-email` (reads `?token`, POSTs, shows the result, links to login).
  - `(auth)/login`: supports `?next`.
  - `(recipient)/recipient/dashboard`: list of my requests with status badges and empty state.
  - `(recipient)/recipient/requests/new`: create draft; the money input uses `parseMoneyInput`.
  - `(recipient)/recipient/requests/[id]`:
    - edit form only when `editable`, sending `version`
    - document uploader: client-side type and size checks, then upload-url, then `fetch(PUT uploadUrl, {headers: requiredHeaders, body: file})`, then complete; with progress and error states
    - document list with delete
    - submit button showing field errors from `REQUEST_INCOMPLETE`
    - status timeline and reviewer feedback
    - withdraw with a confirm dialog
  - `/profile`: PATCH `/me/profile`.
- Mutations go through TanStack Query and invalidate the keys from §11.

**Acceptance:**
- `pnpm --filter @kyro/web lint typecheck test build` passes.
- Vitest tests: register form validation, money input, uploader state machine (mocked client).
- Pages render at 320px.
- All interactive elements are keyboard reachable and labelled.
- No page ever renders a storage key or URL for a recipient's documents.
- Against a running API-1 (and API-2 when available): register → Mailpit link → verify → login works manually.

### WEB-2 — Donor discovery: swipe feed, detail, saved (Phase 3)
**Owns:** `apps/web/src/app/(donor)/**`, `apps/web/src/features/discovery/**`.

**Build:**
- `(donor)/discover/page.tsx`: card stack.
  - Infinite feed query; prefetch the next page when 3 or fewer cards remain.
  - Card shows title, recipient public name, institution and city, expense type, amount (MoneyText), due date, a "Verified" badge with date, and progress (pledged and confirmed shown separately; both 0).
  - Drag with `motion`: x > +120px means INTERESTED, x < −120px means PASS.
  - Always-visible buttons Pass, Save, Interested, with aria-labels.
  - Keyboard: ←=Pass, →=Interested, S=Save. Show these hints on screen.
  - `useReducedMotion` disables fly-out and drag animation.
  - Optimistic removal; restore the card and show an Alert on error.
  - Anonymous users can browse; action buttons go to `/login?next=/discover`. Logged-in users without the DONOR role see a read-only notice.
- `(donor)/discover/[id]/page.tsx`:
  - full story, institution, a verification explanation ("Reviewed by the KYRO verification team on <date>. Supporting documents stay private.")
  - funding breakdown: target, pledged, confirmed, remaining
  - "Fund this request" button disabled, with "Pledges are coming soon. KYRO does not take payments in this sandbox."
  - action buttons reflecting `viewerAction`
- `(donor)/saved/page.tsx` (RequireAuth DONOR): list of saved and interested cards with a remove button.

**Acceptance:**
- `pnpm --filter @kyro/web lint typecheck test build` passes.
- Vitest: keyboard actions call the swipe mutation with the right action; buttons work without gestures; reduced-motion path; a duplicate rapid click sends only one request per card.
- No private fields are referenced; there are no `feeReference` usages in `features/discovery`.

### ADM-1 — Admin verification app (Phase 2; live verification after API-3)
**Owns:** `apps/admin/**`.

**Build:**
- Next.js app on port 3001, using the same rewrites, design system and api-client.
- README notes to open it at `http://127.0.0.1:3001`.
- Shows SandboxBanner plus an "Operations — restricted" header.
- Routes:
  - `/login`
  - `/` redirects to `/queue`
  - `/queue`: tabs Unassigned, Mine, Closed, All; table with title, recipient public name, amount, submission number, opened at, assignee.
  - `/cases/[caseId]`:
    - request details, including private fields (fee reference, recipient email)
    - documents list: "View" POSTs view-url and then `window.open(url, '_blank', 'noopener')`; copy explains that access is logged
    - duplicate indicators panel
    - status history and prior decisions
    - "Claim case" button
    - decision form shown only when `canDecide`: radio Approve, Request changes, Reject; required rationale (10–2000) and a recipient message required for non-approve; confirmation Dialog
    - 409 and 403 Problem codes shown as friendly Alerts
  - `/audit`: filter by entity id; table of audit events.
- A role guard requires VERIFIER or ADMIN via `/me`; otherwise shows a "not authorised" screen and a logout link.

**Acceptance:**
- `pnpm --filter @kyro/admin lint typecheck test build` passes.
- Vitest: decision form validation rules; role guard.
- Manual check against API-3: claim, view a document, approve; the request appears in the web feed.

### INF-1 — CI and container images (Phase 2)
**Owns:** `.github/**` except `.github/workflows/e2e.yml`; `infrastructure/docker/**` except `infrastructure/docker/local/**`; `docker-compose.apps.yml`.

**Build:** `.github/workflows/ci.yml` triggered on push to main and on pull requests, with `concurrency` cancel-in-progress. Jobs:
1. `contracts`: pnpm (cached), spec lint, client regen plus `git diff --exit-code`.
2. `api`: Temurin 25 via setup-java, `gradle/actions/setup-gradle`, `./gradlew build` (Testcontainers on ubuntu-latest). Upload test reports on failure.
3. `migrations`: `docker compose up -d --wait postgres`, flyway migrate plus validate via the compose tools profile, then run verify.sql.
4. `frontend`: `pnpm install --frozen-lockfile`, then `pnpm -r lint typecheck test build` for design-system, api-client, web and admin.
5. `security`: `actions/dependency-review-action` (PR only) and gitleaks.

Also:
- `infrastructure/docker/api.Dockerfile`: multi-stage; Gradle bootJar on a JDK 25 image, run on `eclipse-temurin:25-jre` as a non-root user.
- `web.Dockerfile` and `admin.Dockerfile`: Next `output: 'standalone'`, a node:24-alpine runner, non-root. Note: WEB-1 and ADM-1 must set `output: 'standalone'`; this is stated in their specs.
- `docker-compose.apps.yml`: api, web and admin under profile `apps`, using compose-internal hostnames (`KYRO_DB_URL=jdbc:postgresql://postgres:5432/kyro`, `KYRO_S3_ENDPOINT=http://minio:9000`, `KYRO_S3_PUBLIC_ENDPOINT=http://localhost:9000`, `KYRO_API_ORIGIN=http://api:8080`).
- `.github/dependabot.yml` for npm, gradle and github-actions.

**Acceptance:**
- `actionlint` is clean (run it locally via its Docker image).
- CI is green on main once Phase 2 tasks merge.
- `docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile apps up -d --build` starts all services, with health UP and web serving on :3000. Final check after API-1, WEB-1 and ADM-1 merge.

### E2E-1 — End-to-end slice, smoke script, runbook (Phase 4)
**Owns:** `e2e/`, `scripts/smoke/`, `docs/runbooks/`, `.github/workflows/e2e.yml`. It may also make small bug fixes in other paths once those tasks are DONE, logging each fix in LOG.md and recording contract changes in DECISIONS.md.

**Build:**
- `scripts/smoke/slice.mjs` (Node fetch with a cookie jar), run against a running stack. It covers:
  - register recipient → fetch the verification link from the Mailpit API (`GET http://localhost:8025/api/v1/search?query=to:<email>`, then the message) → verify → login
  - create request → upload-url → PUT a fictional PDF fixture to MinIO → complete → submit
  - log in as `verifier@kyro.local` → queue → assign → view-url → approve
  - log in as `donor.demo@kyro.local` → feed contains the request → swipe SAVED → saved contains it
  - negative checks: a second recipient GET on the first's request returns 404; an anonymous swipe returns 401
- `e2e/` (`@kyro/e2e`, Playwright, Chromium): the same journey through the web UI (3000) and admin UI (`127.0.0.1:3001`), plus a keyboard-only swipe test and an axe accessibility check on landing, discover and the case page.
- `.github/workflows/e2e.yml`: compose infra, then API, web and admin; run Playwright; upload traces.
- `docs/runbooks/local-dev.md`: setup, reset (`docker compose down -v`), seed accounts, troubleshooting (ports, JDK and Gradle, MinIO CORS, cookie isolation).

**Acceptance:** the smoke script exits 0; `pnpm --filter @kyro/e2e test` passes locally; the e2e workflow is green.

### LATER-1 — Non-binding pledges (Phase 5; re-plan before starting)
- `V6__create_funding.sql`: `campaigns`, `pledges` (`idempotency_key` unique, `amount_minor`, `currency`, status ACTIVE/CANCELLED). No payments and no `campaign_contributions` writes.
- `funding` module: `POST /funding/requests/{id}/pledges`, `GET /funding/pledges/{id}`, `GET /funding/requests/{id}/summary`.
- Web pledge UI that explicitly says "A pledge is not a payment".
- Pledged amounts appear in `pledgedMinor` and never in `confirmedMinor`.
- Must add spec changes through DECISIONS.md.

### LATER-2 — Terraform AWS staging foundation (Phase 5; re-plan before starting)
`infrastructure/terraform/` with modules for VPC, ECR, ECS Fargate (api, web, admin), RDS Postgres, private S3 documents bucket (SSE-KMS, versioning, block public access), Secrets Manager, CloudFront plus WAF, and GitHub OIDC role. Only `terraform validate` and `plan` with no apply credentials in CI. Nothing is applied without human approval.

---

## 13. Decisions to record in DECISIONS.md (orchestrator: copy these as D-002…D-011)

- **D-002 Stack versions:**
  - Spring Boot 4.x GA
  - Gradle wrapper with Kotlin DSL; `options.release=21` on JDK 27, with the daemon-JVM fallback to JDK 25; CI on JDK 25
  - Next.js App Router, Tailwind v4, pnpm 12
  - Postgres 17, pinned MinIO image
- **D-003 Migration numbering:** audit is V5; the blueprint's V5–V8 shift to V6 funding, V7 payments, V8 ledger, V9 operations.
- **D-004 Auth:** Spring Security session cookie `KYRO_SESSION`, email and password, Mailpit email verification, SPA CSRF cookie and header, register always returns 202. OIDC and MFA are deferred.
- **D-005 Contract-first:** `packages/api-spec/openapi.yaml` is the source of truth. Java interfaces and models are generated with openapi-generator; TS uses openapi-typescript plus openapi-fetch. Paths carry the `/api/v1` prefix.
- **D-006 Discovery reads the privacy-safe DB view `discovery_public_requests`**, so discovery has no Java dependency on education.
- **D-007 Feed and public detail are anonymous-readable** (public-safe fields only); actions require DONOR.
- **D-008 Errors use RFC 9457 Problem Details** with `code`, `correlationId` and `errors[]`.
- **D-009 Next apps proxy `/api/*` to the API (same origin).** Admin is opened at `127.0.0.1:3001` to isolate cookies locally.
- **D-010 Verification consumes education via the frozen `EducationReviewFacade` port and synchronous Spring events.** Modules are enforced by ArchUnit.
- **D-011 Builders use a branch or worktree per task;** `pnpm-lock.yaml` conflicts are resolved by regenerating it.

## 14. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Gradle or Kotlin DSL cannot run on JDK 27 | Daemon JVM criteria plus foojay auto-provisioning of JDK 25 (§3) |
| openapi-generator output does not compile on Boot 4 or Jackson 3 | Generator options in §3. If it still fails: hand-written DTOs named after the spec schemas, plus a DECISIONS.md entry. |
| MinIO community images are no longer published | Pin an existing RELEASE tag; use the same tag in Testcontainers |
| Presigned URL host mismatch (docker vs host) | Separate presigner on `KYRO_S3_PUBLIC_ENDPOINT` |
| API-3 integration tests depend on API-2 | Unit tests against the port plus `DbFixtures`; final integration tests after API-2 merges |
| Hibernate `validate` fails on column types | varchar everywhere; jsonb accessed only via JdbcTemplate |
| Windows line endings break `minio-init.sh` and `gradlew` | `.gitattributes` with `eol=lf` |

