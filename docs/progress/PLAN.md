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


---

# Phase 5 Plan (planner-opus-2)

Goal: move KYRO from a working first slice to something ready for a **private beta**. That means closing the blueprint §6 P0 gaps that the slice left open, plus the P1 items that are safe with no live money. Nothing in this phase integrates a payment provider, takes a payment or holds money. The hard constraints in PLAN §0 and the header still apply unchanged.

This section is written against the code on `main` at commit `5974375`, not against the original plan. The builder workflow in PLAN §0 still applies: one branch or worktree per task, coordination docs edited live in `D:\kyro\docs\progress\`, and owned paths kept disjoint.

---

## P5.0 What the code looks like now (facts this plan relies on)

These facts come from reading the repo. Builders should rely on them.

1. **Contract codegen breaks on non-additive changes.** `apps/api/build.gradle.kts` generates with `interfaceOnly=true`, `useTags=true` and `skipDefaultInterface=true`, and models get required-argument constructors (for example `new FundingProgress(amount, 0L, 0L)` in `DiscoveryService`, and `new CurrentUser(...)` in `identity/api/UserMapper`). This gives two rules:
   - **New operations go only under new tags.** That produces new Java interfaces with no implementor, which still compiles. An operation added to an existing tag would break the controller that already implements that interface.
   - **New properties on existing schemas must be optional.** A new required property changes the generated constructor and breaks compilation.
2. **`KyroProperties` is a positional record** and is built positionally in `EducationDevSeederIT`. Do not add fields to it. New configuration uses new `@ConfigurationProperties` records with new prefixes, each owned by its feature module. If `PlatformApplication` does not use `@ConfigurationPropertiesScan`, each module registers its record with `@EnableConfigurationProperties` in its own config class.
3. **`RateLimiter` only covers three auth POST paths** (a hard-coded `switch`). It keys on `getRemoteAddr()`. Behind an AWS load balancer that address is the load balancer, so every client would share one bucket. This must be fixed before staging (SEC-2 and INF-2).
4. **`consent_records` already exists** (V1) and gets two rows at registration. What SEC-006 still needs is: reading consent history, re-accepting after a policy version change, and append-only protection.
5. **No code path updates or deletes** `verification_decisions`, `funding_request_status_history` or `consent_records`. I checked main and test sources. So a database trigger that forbids UPDATE and DELETE on them is safe to add now.
6. **The discovery detail UI computes remaining = target − pledged − confirmed** (D-011). This contradicts blueprint §7: a pledge must never count as money raised. WEB-3 fixes it.
7. **Suspended users keep their sessions.** Login rejects SUSPENDED users, but nothing re-checks an existing session.
8. **Route-level 403s are not audited.** `common` must not depend on any module, which is enforced by `ModuleBoundaryTest.commonDoesNotDependOnModules`.
9. **`S3ObjectStorage` always uses an endpoint override plus static keys.** On ECS it should use the task IAM role and the default AWS endpoint. This is a staging blocker and is assigned to INF-2.
10. **Sessions and rate-limit counters are in memory.** Staging must therefore run **one API task** until Spring Session JDBC lands (deferred, see P5.12).

---

## P5.1 Scope decisions

**In Phase 5:**

| Item | Blueprint | Package |
|---|---|---|
| Non-binding pledges, funding summary, pledge cap with a row-lock guard | PAY-001 (P0), §7, Screen 4 | CON-2, FUND-1, WEB-3 |
| Reservations and overfunding design (documented only, no table yet) | PAY-005, §7, §12 | P5.4 (design) |
| User reports, admin report queue, moderation holds | OPS-002 (P0), Screen 4 | CON-2, OPS-1, WEB-3, ADM-2 |
| Admin operational dashboard (counts) | OPS-001 (P0) | OPS-1, ADM-2 |
| Admin MFA (TOTP) required for `/admin/**` | ID-003 (P0) | SEC-2, ADM-2 |
| Session revocation: password change/reset, suspension | ID-002 (P0) | SEC-2, ACCT-1 |
| Rate limiting for discovery and other sensitive endpoints | SEC-004 (P0), D-013 | SEC-2 |
| Trusting forwarded client IP behind a load balancer | SEC-004 | SEC-2, INF-2 |
| Password reset and change password | ID-002 (P0, account recovery) | ACCT-1, WEB-4 |
| Consent history and re-acceptance | SEC-006 (P0, consent part) | ACCT-1, WEB-4 |
| Email to the recipient on APPROVED / CHANGES_REQUESTED / REJECTED | OPS-004 (P1, safe) | ACCT-1 |
| PATCH can clear optional fields | D-010, D-015 | ACCT-1, WEB-4 |
| Append-only `verification_decisions`, status history and consents at DB level | D-014, TRUST-004 | CON-2 |
| Audit of route-level 403s on admin paths | TRUST-004, SEC-001 | SEC-2 |
| E2E: reject, request-changes → resubmit, withdraw, pledge, report, MFA, reset | §19 | E2E-2 |
| Terraform AWS staging (validate and offline plan only) | PLAT-003, §18 | INF-2 |
| Staging profile: structured logs, forwarded headers, IAM-role S3 | PLAT-005 (logs part) | INF-2 |

**Dropped or deferred, with reasons:**

| Item | Reason |
|---|---|
| Payment provider, checkout, webhooks, ledger, reconciliation, refunds (PAY-002…007) | No live money until the §16 compliance gates are passed. They get their own phase. |
| `funding_reservations` and `campaign_contributions` tables | They have meaning only once payments exist. The design is in P5.4 and they will be created in the payments migration. Empty tables would be dead schema that nothing tests. |
| Disputes (OPS-003) | These are payment disputes, so there is nothing to dispute yet. Complaints about requests go through user reports. |
| Appeals (TRUST-007, P1) | Needs a separation-of-duties policy for who reviews appeals. The correction loop plus reports covers the beta. Phase 6. |
| Malware scanning and EXIF stripping (SEC-003 / EDU-003) | Local ClamAV is heavy and needs network for signatures. INF-2 enables the GuardDuty S3 malware protection plan in Terraform. App-side gating (`scan_status` must be clean before a view URL is issued) is Phase 6, the first item to do before real uploads. |
| `apps/worker`, transactional outbox, SQS | Notification emails are sent best-effort after commit. A crash can lose an email; the state change itself is always durable. Revisit when payments need reliable async work. |
| Spring Session JDBC and Redis rate limits | Staging runs one API task (INF-2 enforces `desired_count = 1`). Phase 6, before any scale-out. |
| Account deletion and data export (ID-006, rest of SEC-006) | Needs a legally reviewed retention schedule first. The V9 append-only triggers mean deletion will be anonymisation, not `DELETE`. |
| Staff role management API (granting VERIFIER/ADMIN) | Few staff during the beta. A runbook covers it. Phase 6. |
| Server-side consent enforcement (blocking mutations until re-accepted) | Policy versions only change with a deploy. WEB-4 gates the UI and the API exposes the status. Server enforcement comes in Phase 6, before the first policy change. |
| Phone verification, OIDC, MFA for donors and recipients | Need external providers or are not P0. |
| Filters and ranking (MATCH-004/005), progress updates (EDU-008), impact dashboard | P1/P2. Not needed to validate the beta. |
| Metrics and tracing (OTel, Prometheus) | Needs Gradle dependencies and a collector. Structured logs land now in the staging profile. Phase 6. |
| Pledge lapsing on the due date (`LAPSED`) | Needs a scheduler. The status value is reserved in the CHECK constraint but not used. |

---

## P5.2 Phases and parallel groups

| Group | Packages | Rule |
|---|---|---|
| **5a** | **CON-2** ∥ INF-2 | CON-2 is the gate for every other package except INF-2. It is small, should merge fast, and needs no feature logic. INF-2 is independent. |
| **5b** | FUND-1 ∥ OPS-1 ∥ SEC-2 ∥ ACCT-1 ∥ WEB-3 ∥ WEB-4 ∥ ADM-2 | All start once CON-2 is merged. The web and admin packages build against the regenerated client and mocked tests. Each one's **live check** waits for its API counterpart to merge (listed under each package). |
| **5c** | E2E-2 | After all of 5b. Flips admin MFA enforcement on and extends smoke and Playwright. |

**Phase 5 exit criterion:**
- `./gradlew build`, `pnpm lint`, `pnpm typecheck` and `pnpm test` are green.
- `pnpm smoke` and `pnpm test:e2e` are green with `KYRO_ADMIN_MFA_ENFORCED=true`.
- The CI `ci.yml`, `e2e.yml` and `terraform.yml` workflows are green on GitHub (`origin`: `arx-studios/arx-kyro`).
- No package has applied any Terraform.

---

## P5.3 Shared-file ownership in Phase 5

| Shared file | Owner in Phase 5 | Others |
|---|---|---|
| `packages/api-spec/openapi.yaml`, `packages/api-client/**` | **CON-2** (all additions in P5.5, client regenerated in the same commit) | After CON-2 merges, any further change needs a DECISIONS entry, must stay additive under §P5.0 rule 1, regenerates the client in the same commit, and is announced in LOG before merging. |
| `db/migration/V6`–`V9` | **CON-2** | Feature packages add no migrations. If one is unavoidable, take the **next free number at merge time** (V10+) after rebasing. Never leave a gap, never reserve a number and never merge out of order. Flyway runs with `outOfOrder=false`. |
| `scripts/db/verify.sql` | CON-2 | – |
| `application.yml`, `application-local.yml` | **CON-2** (adds every Phase 5 key in P5.6) | E2E-2 may change exactly one default (`KYRO_ADMIN_MFA_ENFORCED` from `false` to `true`). Anyone else who needs a key writes a LOG note and the orchestrator applies it after merge. |
| `application-staging.yml` (new) | INF-2 | – |
| `common/security/SecurityConfig.java` | CON-2 adds route matchers first, then **SEC-2** owns it | – |
| `common/security/**` (rest) | SEC-2 (CON-2 creates `SessionAttributes.java` only) | – |
| `audit/application/AuditActions.java` | CON-2 (adds every constant in P5.8) | – |
| `education/application/port/**` (frozen) | CON-2 (adds `EducationRequestStatusChanged.java`) | – |
| `ModuleBoundaryTest.java` | CON-2 | – |
| `.env.example`, `.github/gitleaks.toml` | CON-2 | – |
| `apps/api/src/test/java/com/kyro/platform/support/**`, `apps/api/src/test/resources/**` | SEC-2 | Others may only use the support classes. |
| `pnpm-lock.yaml` | anyone | Same rule as before: take main's version and run `pnpm install` again. |

---

## P5.4 Funding design: pledges now, reservations later

**Three concepts (blueprint §7), each with its own storage:**
- **Interest** is stored in `swipes` (exists).
- **Pledge** is stored in `pledges` (new, non-binding).
- **Payment** will be stored in `payments` and `campaign_contributions` (a later phase).

`confirmedMinor` is computed only from confirmed contributions, so it is **0 for the whole of Phase 5**. `pledgedMinor` is the sum of ACTIVE pledges. **Pledges are never added to confirmed amounts, to "raised", or to the progress bar.** The outstanding need is `remainingMinor = target − confirmed`. Pledges do not reduce it.

**Campaign row as the lock anchor.**
- Each APPROVED request gets at most one `campaigns` row (mode GROUP; DIRECT is reserved). It is created lazily on the first pledge with `INSERT … ON CONFLICT (request_id) DO NOTHING`, taking `target_minor` and `currency` from `discovery_public_requests`.
- An APPROVED request can never be edited (PLAN §8), so the target snapshot stays valid.
- The funding module locks its own campaign row. It never locks rows owned by the education module.

**Phase 5 pledge guard, one transaction (FUND-1):**
1. Read the request from `discovery_public_requests`. Not found (not approved, withdrawn or on hold) → 404. `due_date < current_date` → 422 `PLEDGE_NOT_ALLOWED`. Caller is the recipient → 422 `PLEDGE_NOT_ALLOWED`.
2. Validate: `amount.currency` must equal the request currency, otherwise 422 `UNSUPPORTED_CURRENCY`. `amountMinor ≥ kyro.funding.min-pledge-minor`, otherwise 422 `VALIDATION_FAILED`. `acknowledgeNonBinding == true`, otherwise 422 `VALIDATION_FAILED` on that field.
3. Upsert the campaign, then `SELECT … FROM campaigns WHERE request_id=? FOR UPDATE`.
4. Check idempotency: look up `(donor_user_id, idempotency_key)`. If found with the same request and amount, return **200** with the existing pledge. If found with a different body, return 422 `IDEMPOTENCY_KEY_REUSED`.
5. If the donor already has an ACTIVE pledge on this request → 409 `ACTIVE_PLEDGE_EXISTS`. Partial unique index as a backstop.
6. Compute `pledgeable = target − confirmed(0) − sum(active pledges)`. If `amount > pledgeable` → 409 `PLEDGE_EXCEEDS_REMAINING`; the detail states the pledgeable amount.
7. Insert the pledge (ACTIVE), audit `PLEDGE_CREATED`, return **201** with a `Location` header.
8. If the insert hits a unique violation on the idempotency key (concurrent retry), re-read and return 200.

This pledge cap is the same algorithm that payments will later run on reservations. The concurrency test for it is the pledge version of mandatory scenario 1 in blueprint §19.

**Withdrawal.** FUND-1 listens synchronously to the existing `EducationRequestWithdrawn` event inside the education transaction. It cancels every ACTIVE pledge on the request with `cancel_reason = REQUEST_WITHDRAWN` and audits `PLEDGE_CANCELLED` once per pledge.

**Moderation hold.** A held request drops out of the view, so new pledges get 404. Existing pledges stay ACTIVE. In "My pledges" the `request` field is null, shown as "temporarily unavailable".

**Reservations: design only, nothing is built in Phase 5.** These are created in the payments migration (V10 or later).
- Table `funding_reservations`:
  - `id`, `campaign_id` FK, `donor_user_id` FK, `pledge_id` FK nullable, `amount_minor > 0`, `currency`
  - `status` CHECK (HELD, CONSUMED, RELEASED, EXPIRED)
  - `idempotency_key` (UNIQUE with the donor), `expires_at` (now + 15 min), `created_at`, `updated_at`
  - Partial index `(campaign_id) WHERE status = 'HELD'`
- **Reserve** (before provider checkout): lock the campaign `FOR UPDATE`, then compute `available = target − confirmed − sum(HELD and not expired)`. If `amount > available` → 409 `FUNDING_EXCEEDS_REMAINING`. Otherwise insert HELD. Checkout may only start with a HELD reservation, and the amount is fixed server-side (Screen 5: client-side amount changes are rejected).
- **Provider success webhook** (signature verified, unique event id): in one transaction, mark the reservation CONSUMED, insert the contribution and post the ledger entries. Failure or expiry → RELEASED or EXPIRED. A worker sweeper expires stale HELD rows.
- **Late success after expiry:** accept only if the amount is still available. Otherwise flag for ops and refund automatically. The money is never kept and never over-counted.
- **Belt and braces:** a deferred constraint trigger asserts `sum(confirmed) ≤ target` per campaign.
- **Pledges and payments:** pledges do not hold capacity against payments, because payments take priority. When confirmed reaches the target, remaining ACTIVE pledges move to a future status `FULFILLED_BY_OTHERS`, added together with payments.

---

## P5.5 Contract additions (CON-2 owns all of them; all additive)

Base path `/api/v1`. Security conventions follow D-002: mutating operations need `sessionCookie` + `csrfHeader`; public mutating ones need only `csrfHeader`. Every 4xx/5xx response references the existing `components/responses/Problem*`. That brings the total to **51 operations**.

**New components:**
- **Parameters:** `PledgeId`, `ReportId`, `UserId` (path, uuid). `IdempotencyKey` (header `Idempotency-Key`, required, string 8–64, pattern `^[A-Za-z0-9-]+$`).
- **Enums:**
  - `PledgeStatus`: ACTIVE, CANCELLED, LAPSED (LAPSED reserved, not used)
  - `PledgeCancelReason`: DONOR_CANCELLED, REQUEST_WITHDRAWN
  - `ReportReason`: SUSPECTED_FRAUD, ALTERED_DOCUMENT, DUPLICATE_REQUEST, INAPPROPRIATE_CONTENT, PRIVACY_CONCERN, OTHER
  - `ReportStatus`: OPEN, IN_REVIEW, RESOLVED
  - `ReportResolution`: DISMISSED, ACTION_TAKEN
  - `ReportQueue`: OPEN, MINE, RESOLVED, ALL
  - `PolicyType`: TERMS, PRIVACY
  - `ClearableRequestField`: story, institutionName, institutionCity, courseName, feeReference, dueDate

### Tag `Funding` → `FundingApi`, implemented by FUND-1

| Method | Path | operationId | Auth | Success |
|---|---|---|---|---|
| POST | /funding/requests/{requestId}/pledges | createPledge | DONOR | 201 Pledge + Location `/api/v1/funding/pledges/{id}`; 200 Pledge on idempotent replay |
| GET | /funding/pledges/{pledgeId} | getPledge | DONOR, owner (others 404 + ACCESS_DENIED audit) | 200 Pledge |
| POST | /funding/pledges/{pledgeId}/cancel | cancelPledge | DONOR, owner | 200 Pledge (409 `INVALID_STATE_TRANSITION` if not ACTIVE) |
| GET | /me/funding/pledges | listMyPledges | DONOR | 200 PledgePage (`cursor`, `limit`) |
| GET | /funding/requests/{requestId}/summary | getFundingSummary | public (adds `viewerPledge` for a donor session) | 200 FundingSummary; 404 if not in the public view |

Schemas:
- `PledgeCreate {amount: Money, acknowledgeNonBinding: boolean}`. Both required. The server requires `true`.
- `Pledge {id, requestId, amount: Money, status: PledgeStatus, cancelReason: PledgeCancelReason|null, nonBinding: boolean (always true), createdAt, cancelledAt|null}`
- `PublicRequestRef {id, title, recipientPublicName}`
- `PledgeListItem {pledge: Pledge, request: PublicRequestRef|null}`. `request` is null when the request is no longer public.
- `PledgePage {items[], nextCursor}`
- `FundingSummary {requestId, target: Money, confirmedMinor, pledgedMinor, remainingMinor, pledgeableMinor, activePledgeCount, pledgesAreNonBinding: boolean (always true), viewerPledge: Pledge|null}`
  - All `*Minor` fields are int64 ≥ 0.
  - Description: `remainingMinor = target − confirmed`; pledges never reduce it. Donor identities are never exposed.

Existing `FundingProgress`: update the **description only**: "pledgedMinor = sum of ACTIVE non-binding pledges; confirmedMinor = confirmed payments (0 until payments exist); pledges are never money raised." No shape change.

### Tag `Reports` → `ReportsApi`, implemented by OPS-1

| Method | Path | operationId | Auth | Success |
|---|---|---|---|---|
| POST | /reports | createReport | authenticated | 202 ReportReceipt |

- `ReportCreate {requestId: uuid, reason: ReportReason, details?: string ≤2000}`. `details` is required (non-blank) when reason is OTHER; the server returns 422.
- `ReportReceipt {id, status, createdAt}`.
- Errors:
  - 404: the request is not in the public view
  - 422 `VALIDATION_FAILED`: reporting your own request
  - 409 `REPORT_ALREADY_OPEN`: the same reporter already has an open report on the same request

### Tag `Operations` → `OperationsApi`, implemented by OPS-1

Paths are under `/admin/` and need VERIFIER or ADMIN unless marked otherwise.

| Method | Path | operationId | Auth | Success |
|---|---|---|---|---|
| GET | /admin/reports | listReports | VERIFIER/ADMIN | 200 ReportPage (`queue`: ReportQueue, default OPEN = OPEN+IN_REVIEW; `cursor`; `limit`) |
| GET | /admin/reports/{reportId} | getReport | VERIFIER/ADMIN | 200 ReportDetail |
| POST | /admin/reports/{reportId}/assign | assignReport | VERIFIER/ADMIN | 200 ReportDetail |
| POST | /admin/reports/{reportId}/resolve | resolveReport | assignee | 200 ReportDetail |
| POST | /admin/requests/{requestId}/hold/release | releaseRequestHold | **ADMIN only** | 204 |
| GET | /admin/dashboard | getAdminDashboard | VERIFIER/ADMIN | 200 AdminDashboard |

Schemas:
- `ReportSummary {id, requestId, requestTitle, reason, status, reporter: UserSummary, assignedTo: UserSummary|null, createdAt, resolvedAt|null, resolution: ReportResolution|null, requestOnHold: boolean}`
- `ReportPage {items[], nextCursor}`
- `RequestHold {requestId, reportId|null, reason, placedBy: UserSummary, placedAt}`
- `ReportDetail`: the ReportSummary fields (allOf) plus `{details|null, resolutionNote|null, resolvedBy: UserSummary|null, requestStatus: RequestStatus, latestCaseId: uuid|null, hold: RequestHold|null, otherReports: ReportSummary[], canResolve: boolean}`
- `ReportResolutionInput {resolution, note: string 10–2000, holdRequest: boolean, default false}`. `holdRequest=true` is allowed only with ACTION_TAKEN; otherwise 422.
- `AdminDashboard {unassignedCases, myOpenCases, openReports, unassignedReports, activeHolds, generatedAt}`

### Tag `Account` → `AccountApi`, implemented by ACCT-1

| Method | Path | operationId | Auth | Success |
|---|---|---|---|---|
| POST | /auth/password-reset | requestPasswordReset | public (csrf) | 202 AcceptedMessage, always |
| POST | /auth/password-reset/confirm | confirmPasswordReset | public (csrf) | 204 |
| POST | /me/password | changePassword | authenticated | 204 |

Schemas: `PasswordResetRequest {email}`, `PasswordResetConfirm {token, newPassword 12–128}`, `ChangePasswordRequest {currentPassword 1–128, newPassword 12–128}`.

### Tag `Consents` → `ConsentsApi`, implemented by ACCT-1

| Method | Path | operationId | Auth | Success |
|---|---|---|---|---|
| GET | /me/consents | getMyConsents | authenticated | 200 ConsentStatus |
| POST | /me/consents | acceptCurrentPolicies | authenticated | 200 ConsentStatus (body: existing `Consents`) |

Schemas:
- `ConsentRecord {policyType: PolicyType, policyVersion, acceptedAt}`
- `ConsentStatus {current: PolicyVersions, acceptedTermsVersion|null, acceptedPrivacyVersion|null, reacceptRequired: boolean, history: ConsentRecord[]}` (history newest first)

### Tag `Mfa` → `MfaApi`, implemented by SEC-2

| Method | Path | operationId | Auth | Success |
|---|---|---|---|---|
| POST | /auth/mfa/totp/enrollment | startTotpEnrollment | VERIFIER/ADMIN | 200 TotpEnrollment |
| POST | /auth/mfa/totp/enrollment/confirm | confirmTotpEnrollment | VERIFIER/ADMIN | 200 MfaRecoveryCodes |
| POST | /auth/mfa/verify | verifyMfa | VERIFIER/ADMIN | 200 CurrentUser |
| POST | /admin/users/{userId}/mfa/reset | resetUserMfa | **ADMIN only**, not on self | 204 |

Schemas:
- `TotpEnrollment {secret (base32), otpauthUri, issuer, accountName}`
- `TotpCodeInput {code: ^[0-9]{6}$}`
- `MfaVerifyRequest {code?: ^[0-9]{6}$, recoveryCode?: ^[A-Z0-9]{5}-[A-Z0-9]{5}$}`. Exactly one is required; otherwise 422.
- `MfaRecoveryCodes {codes: string[] (10 items)}`
- `MfaStatus {required, enrolled, verified}`

### Optional additions to existing schemas (no constructor change)

- `CurrentUser.mfa?: MfaStatus` (SEC-2 populates it). Present when the user has VERIFIER or ADMIN.
- `EducationRequestUpdate.clearFields?: ClearableRequestField[]` (uniqueItems, maxItems 6; ACCT-1 implements it). A field named in `clearFields` is set to NULL. A field that is both cleared and given a value → 422 `VALIDATION_FAILED`. Title, expenseType, amount and institutionCountry cannot be cleared.

### New problem codes (add to PLAN §6)

| Status | Codes |
|---|---|
| 401 | `SESSION_REVOKED` (optional detail; plain `UNAUTHENTICATED` is also acceptable after revocation) |
| 403 | `MFA_REQUIRED`, `MFA_ENROLLMENT_REQUIRED` |
| 409 | `ACTIVE_PLEDGE_EXISTS`, `PLEDGE_EXCEEDS_REMAINING`, `REPORT_ALREADY_OPEN`, `REPORT_ALREADY_ASSIGNED`, `REPORT_NOT_ASSIGNED_TO_YOU`, `MFA_ALREADY_ENROLLED` |
| 422 | `PLEDGE_NOT_ALLOWED`, `IDEMPOTENCY_KEY_REUSED`, `MFA_CODE_INVALID`, `CURRENT_PASSWORD_INCORRECT` (`TOKEN_INVALID` is reused for reset tokens) |

A missing `Idempotency-Key` header gives 400 `MALFORMED_REQUEST`. FUND-1 confirms that `GlobalExceptionHandler` maps `MissingRequestHeaderException` that way. If it does not, the fix goes in a LOG note to SEC-2, which owns common.

---

## P5.6 Migrations (CON-2 writes all of them)

D-003's numbering (V7 payments, V8 ledger, V9 operations) is **superseded**, because Flyway cannot leave gaps for later. Payments and ledger move to V10 and later. CON-2 records this in DECISIONS.

**V6__create_funding.sql**
- `campaigns`:
  - `id`, `request_id` FK→funding_requests UNIQUE
  - `mode varchar(16)` DEFAULT 'GROUP', CHECK IN (GROUP, DIRECT)
  - `target_minor bigint` CHECK > 0, `currency varchar(3)` CHECK `^[A-Z]{3}$`
  - `status varchar(16)` DEFAULT 'OPEN', CHECK IN (OPEN, CLOSED)
  - `created_at`, `closed_at`, `version`
- `pledges`:
  - `id`, `campaign_id` FK, `request_id` FK→funding_requests, `donor_user_id` FK→users
  - `amount_minor bigint` CHECK > 0, `currency varchar(3)` CHECK
  - `status varchar(16)` DEFAULT 'ACTIVE', CHECK IN (ACTIVE, CANCELLED, LAPSED)
  - `cancel_reason varchar(32)` CHECK NULL or IN (DONOR_CANCELLED, REQUEST_WITHDRAWN)
  - CHECK `(status = 'CANCELLED') = (cancel_reason IS NOT NULL)`
  - `idempotency_key varchar(64)` NOT NULL, `created_at`, `cancelled_at`, `updated_at`, `version`
  - UNIQUE `(donor_user_id, idempotency_key)`
  - partial UNIQUE `(donor_user_id, request_id) WHERE status = 'ACTIVE'`
  - indexes `(request_id, status)`, `(donor_user_id, created_at DESC, id DESC)`, `(campaign_id)`
- VIEW `funding_request_progress`: `request_id`, `currency`, `pledged_minor` (COALESCE of the sum of ACTIVE pledges), `active_pledge_count`. One row per request that has at least one pledge row; consumers LEFT JOIN it.

**V7__create_operations.sql**
- `user_reports`:
  - `id`, `reporter_user_id` FK→users
  - `target_type varchar(16)` DEFAULT 'REQUEST', CHECK IN (REQUEST)
  - `request_id` FK→funding_requests
  - `reason varchar(32)` CHECK (ReportReason), `details varchar(2000)`
  - `status varchar(16)` DEFAULT 'OPEN', CHECK (ReportStatus)
  - `assigned_to` FK nullable, `assigned_at`
  - `resolution varchar(32)` CHECK NULL or (ReportResolution), `resolution_note varchar(2000)`, `resolved_by` FK nullable, `resolved_at`
  - `created_at`, `version`
  - CHECK `(status = 'RESOLVED') = (resolution IS NOT NULL)`
  - partial UNIQUE `(reporter_user_id, request_id) WHERE status <> 'RESOLVED'`
  - indexes `(status, created_at, id)`, `(request_id)`, `(assigned_to)`
- `request_moderation_holds`:
  - `id`, `request_id` FK, `report_id` FK nullable, `reason varchar(500)` NOT NULL
  - `placed_by` FK, `placed_at`, `released_by` FK nullable, `released_at`
  - partial UNIQUE `(request_id) WHERE released_at IS NULL`
- `CREATE OR REPLACE VIEW discovery_public_requests`: **same column list in the same order** as V4, plus `AND NOT EXISTS (SELECT 1 FROM request_moderation_holds h WHERE h.request_id = r.id AND h.released_at IS NULL)`.

**V8__identity_security.sql**
- `ALTER TABLE users ADD COLUMN credentials_version bigint NOT NULL DEFAULT 0`
- `password_reset_tokens`: `id`, `user_id` FK ON DELETE CASCADE, `token_hash varchar(64)` UNIQUE, `expires_at`, `consumed_at`, `requested_ip varchar(45)`, `created_at`; index `(user_id)`
- `user_mfa_credentials`:
  - `user_id` PK FK
  - `method varchar(16)` DEFAULT 'TOTP', CHECK IN (TOTP)
  - `secret_ciphertext varchar(512)` NOT NULL, `key_id varchar(32)` NOT NULL
  - `confirmed_at` (NULL means enrollment is pending), `last_used_step bigint`
  - `created_at`, `updated_at`, `version`
- `user_mfa_recovery_codes`: `id`, `user_id` FK, `code_hash varchar(64)` NOT NULL, `used_at`, `created_at`; index `(user_id)`

**V9__append_only_history.sql**
- Function `forbid_update_delete()` raises `'% is append-only (% not allowed)', TG_TABLE_NAME, TG_OP`.
- `BEFORE UPDATE OR DELETE` triggers on `verification_decisions`, `funding_request_status_history` and `consent_records`.
- A comment notes that future account deletion must anonymise, not delete, because the `users` ON DELETE CASCADE into `consent_records` will now raise.

`scripts/db/verify.sql` additions (each a DO-block expected failure, in the existing style):
- negative or zero pledge amount
- a second ACTIVE pledge for the same donor and request
- a duplicate `(donor, idempotency_key)`
- a CANCELLED pledge without a reason
- a second open report by the same reporter on the same request
- a second active hold on the same request
- a held APPROVED request is absent from `discovery_public_requests`, and the view still has no fee or document column
- `funding_request_progress` ignores CANCELLED pledges
- UPDATE and DELETE on `verification_decisions`, `funding_request_status_history` and `consent_records` all raise

---

## P5.7 Configuration keys (CON-2 adds them; feature packages bind their own records)

`application.yml` (add; keep the existing `kyro.auth.*-rate-per-minute` keys as they are):

```yaml
kyro:
  funding:            # FundingProperties (FUND-1, funding module)
    min-pledge-minor: 100
    currencies: [INR]
  password-reset:     # PasswordResetProperties (ACCT-1)
    token-ttl: 30m
  notifications:      # NotificationProperties (ACCT-1)
    email-enabled: ${KYRO_NOTIFICATIONS_EMAIL_ENABLED:true}
  mfa:                # MfaProperties (SEC-2)
    admin-enforced: ${KYRO_ADMIN_MFA_ENFORCED:false}   # E2E-2 flips the default to true
    issuer: "KYRO Sandbox"
    encryption-key: ${KYRO_MFA_ENCRYPTION_KEY:}         # base64 32 bytes; required outside local/test
    recovery-code-count: 10
    allowed-skew-steps: 1
  sessions:           # SessionProperties (SEC-2)
    revalidate-interval: ${KYRO_SESSION_REVALIDATE_INTERVAL:60s}
  rate-limits:        # RateLimitProperties (SEC-2, common.security)
    enabled: ${KYRO_RATE_LIMITS_ENABLED:true}
    rules:            # key: IP | USER (USER falls back to IP when anonymous); window = 1 minute
      - { id: discovery-read,       method: GET,  pattern: /api/v1/discovery/feed/**,                               per-minute: ${KYRO_RATE_DISCOVERY_READ:120}, key: IP }
      - { id: funding-summary,      method: GET,  pattern: /api/v1/funding/requests/*/summary,                      per-minute: ${KYRO_RATE_FUNDING_SUMMARY:120}, key: IP }
      - { id: swipe,                method: POST, pattern: /api/v1/discovery/requests/*/swipes,                     per-minute: ${KYRO_RATE_SWIPE:60}, key: USER }
      - { id: pledge-create,        method: POST, pattern: /api/v1/funding/requests/*/pledges,                      per-minute: ${KYRO_RATE_PLEDGE:10}, key: USER }
      - { id: pledge-cancel,        method: POST, pattern: /api/v1/funding/pledges/*/cancel,                        per-minute: ${KYRO_RATE_PLEDGE_CANCEL:10}, key: USER }
      - { id: report-create,        method: POST, pattern: /api/v1/reports,                                         per-minute: ${KYRO_RATE_REPORT:5}, key: USER }
      - { id: password-reset,       method: POST, pattern: /api/v1/auth/password-reset,                             per-minute: ${KYRO_RATE_PASSWORD_RESET:3}, key: IP }
      - { id: password-reset-confirm, method: POST, pattern: /api/v1/auth/password-reset/confirm,                   per-minute: ${KYRO_RATE_PASSWORD_RESET_CONFIRM:10}, key: IP }
      - { id: password-change,      method: POST, pattern: /api/v1/me/password,                                     per-minute: ${KYRO_RATE_PASSWORD_CHANGE:5}, key: USER }
      - { id: mfa-verify,           method: POST, pattern: /api/v1/auth/mfa/verify,                                 per-minute: ${KYRO_RATE_MFA_VERIFY:5}, key: USER }
      - { id: mfa-enroll-confirm,   method: POST, pattern: /api/v1/auth/mfa/totp/enrollment/confirm,                per-minute: ${KYRO_RATE_MFA_ENROLL:5}, key: USER }
      - { id: upload-url,           method: POST, pattern: /api/v1/education/requests/*/documents/upload-url,       per-minute: ${KYRO_RATE_UPLOAD_URL:20}, key: USER }
      - { id: document-view-url,    method: POST, pattern: /api/v1/admin/verification/cases/*/documents/*/view-url, per-minute: ${KYRO_RATE_VIEW_URL:30}, key: USER }
server:
  forward-headers-strategy: ${KYRO_FORWARD_HEADERS_STRATEGY:none}   # staging: native (ALB X-Forwarded-For)
```

`application-local.yml` adds `kyro.mfa.encryption-key: <dev-only base64 key generated by CON-2>`. `.env.example` documents every new variable. `.github/gitleaks.toml` allowlists by path: `application-local.yml`, `apps/api/src/test/resources/application-test.yml`, and the dev TOTP secret literal `KYROLOCALDEVTOTPAAAAAAAAAAAAAAAA` (base32, local seed only, used by SEC-2, smoke and E2E).

---

## P5.8 Audit actions (CON-2 adds these constants)

- Funding: `PLEDGE_CREATED`, `PLEDGE_CANCELLED`
- Operations: `REPORT_CREATED`, `REPORT_ASSIGNED`, `REPORT_RESOLVED`, `REQUEST_HOLD_PLACED`, `REQUEST_HOLD_RELEASED`
- Account: `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`, `PASSWORD_CHANGED`, `CONSENT_ACCEPTED`
- MFA and session: `MFA_ENROLLMENT_STARTED`, `MFA_ENROLLED`, `MFA_VERIFIED`, `MFA_FAILED`, `MFA_RECOVERY_CODE_USED`, `MFA_RESET`, `SESSION_REVOKED`

Entity types: `PLEDGE`, `USER_REPORT`, `FUNDING_REQUEST` (holds), `USER`. Details never contain secrets, tokens, codes or passwords.

---

## P5.9 Work packages

### CON-2: Phase 5 contracts, migrations, config and route rules (group 5a; gate)

**Owns:**
- `packages/api-spec/`, `packages/api-client/`
- `apps/api/src/main/resources/db/migration/V6__create_funding.sql`, `V7__create_operations.sql`, `V8__identity_security.sql`, `V9__append_only_history.sql`
- `scripts/db/verify.sql`
- `apps/api/src/main/resources/application.yml`, `application-local.yml`
- `apps/api/src/main/java/com/kyro/platform/common/security/SecurityConfig.java` (route matchers only), `common/security/SessionAttributes.java` (new)
- `apps/api/src/main/java/com/kyro/platform/audit/application/AuditActions.java`
- `apps/api/src/main/java/com/kyro/platform/education/application/port/EducationRequestStatusChanged.java` (new)
- `apps/api/src/test/java/com/kyro/platform/ModuleBoundaryTest.java`
- `.env.example`, `.github/gitleaks.toml`

**Build:**
1. Everything in P5.5 (spec), P5.6 (migrations and verify.sql), P5.7 (yml keys, `.env.example`, gitleaks) and P5.8 (audit constants). Use fictional INR examples. Add `Location` headers. Mark the new ADMIN-only operations in their descriptions.
2. `pnpm gen:client` in the same commit (`schema.d.ts` and `types.ts` aliases).
3. `SessionAttributes` (final class, string constants): `CREDENTIALS_VERSION = "KYRO_CREDENTIALS_VERSION"`, `MFA_VERIFIED_AT = "KYRO_MFA_VERIFIED_AT"`, `LAST_REVALIDATED_AT = "KYRO_LAST_REVALIDATED_AT"`.
4. Port record, with a `// FROZEN CONTRACT` header: `public record EducationRequestStatusChanged(UUID requestId, UUID recipientUserId, String title, String fromStatus, String toStatus, Instant occurredAt) {}`. CON-2 only adds the record; ACCT-1 publishes it.
5. `SecurityConfig` matchers. Specific rules go **before** the existing `/api/v1/admin/**` and `anyRequest` rules:
   - permitAll: `POST /api/v1/auth/password-reset`, `POST /api/v1/auth/password-reset/confirm`, `GET /api/v1/funding/requests/*/summary`
   - `hasRole(ADMIN)`: `/api/v1/admin/requests/*/hold/release`, `/api/v1/admin/users/*/mfa/reset`
   - `hasRole(DONOR)`: `POST /api/v1/funding/requests/*/pledges`, `/api/v1/funding/pledges/**`, `/api/v1/me/funding/**`
   - `hasAnyRole(VERIFIER, ADMIN)`: `/api/v1/auth/mfa/**`
   - `/reports`, `/me/consents` and `/me/password` fall through to `authenticated`
6. `ModuleBoundaryTest`: add `funding`, `operations` and `notification` to `MODULES` and to `devSeedDependsOnlyOnCommon`. `PUBLIC_APPLICATION` is unchanged; no new module exposes its application package.
7. DECISIONS: **D-017** covers the Phase 5 contract (operation list, the new-tag and optional-field rule from P5.0, error codes) and the migration renumbering that supersedes D-003.

**Acceptance:**
- Redocly reports 0 errors and 0 warnings.
- Exactly 51 operations, with the operationIds and tags above.
- The client has no drift.
- `./gradlew build` is green: the Testcontainers database applies V1–V9, existing tests still pass, ArchUnit passes, and the new interfaces compile without implementations.
- verify.sql passes.
- Web and admin `typecheck` still pass (optional fields only).
- Existing `pnpm smoke` and `pnpm test:e2e` still pass against a running stack.

**Verify:**
```bash
pnpm install && pnpm spec:lint && pnpm gen:client && git diff --exit-code packages/api-client
pnpm lint && pnpm typecheck && pnpm test
./gradlew build
pnpm infra:up
docker compose --profile tools run --rm flyway migrate && docker compose --profile tools run --rm flyway validate && docker compose --profile tools run --rm flyway info
docker compose exec -T postgres psql -U kyro -d kyro -v ON_ERROR_STOP=1 -f - < scripts/db/verify.sql
./gradlew :apps:api:bootRun   # then, with web+admin dev servers: pnpm smoke && pnpm test:e2e
```

### FUND-1: Funding module (non-binding pledges) plus discovery progress (5b)

**Owns:** `apps/api/src/{main,test}/java/com/kyro/platform/funding/**` and `apps/api/src/{main,test}/java/com/kyro/platform/discovery/**`.

**Build:**
- `FundingController implements FundingApi`.
- `FundingService` and `PledgeService` follow P5.4 exactly. Use JdbcTemplate. Read the request from the `discovery_public_requests` view and never import education code.
- `FundingProperties` (`kyro.funding`).
- `FundingEventListener`: an `@EventListener` on `EducationRequestWithdrawn` that cancels ACTIVE pledges with REQUEST_WITHDRAWN and audits, in the same transaction.
- Keyset cursor for `listMyPledges`: `(created_at, id)` DESC.
- **Object-level access:** a non-owner gets 404 plus an `ACCESS_DENIED` audit row. Write the audit in `REQUIRES_NEW`, the same pattern as D-015.
- `getFundingSummary`: anonymous gets `viewerPledge = null`; a donor gets their own ACTIVE pledge or null. `pledgeableMinor = max(0, target − confirmed − pledged)`. `confirmedMinor` is the constant 0, behind a single method `confirmedMinor(requestId)` with a TODO for payments.
- **Discovery:** feed, detail and saved queries `LEFT JOIN funding_request_progress` and fill `pledgedMinor`. `confirmedMinor` stays 0.
- Responses: `Cache-Control: no-store` on pledge endpoints.
- DECISIONS entry for anything not covered here.

**Acceptance (integration tests, `IntegrationTestBase` plus `DbFixtures`):**
- **Create and replay:** create → 201 with Location, and an audit row. Replay with the same key and body → 200 with the same id. Same key with a different amount → 422 `IDEMPOTENCY_KEY_REUSED`. Missing header → 400.
- **Rejections:**
  - a second ACTIVE pledge → 409 `ACTIVE_PLEDGE_EXISTS`
  - over the pledgeable amount → 409 `PLEDGE_EXCEEDS_REMAINING`
  - own request → 422 `PLEDGE_NOT_ALLOWED`
  - past-due request → 422 `PLEDGE_NOT_ALLOWED`
  - non-INR → 422 `UNSUPPORTED_CURRENCY`
  - `acknowledgeNonBinding=false` → 422
  - request not APPROVED or held (insert an active hold row via JDBC) → 404
- **Roles:** RECIPIENT-only → 403; anonymous POST → 401.
- **Concurrency:** two donors run concurrently (CountDownLatch, 2 threads), each pledging the full pledgeable amount. Exactly one 201 and one 409. The sum of active pledges never exceeds the target.
- **Cancel:** owner → CANCELLED and audit. A second cancel → 409. Another donor → 404 with ACCESS_DENIED.
- **Withdrawal:** withdrawing an APPROVED request through the real education endpoint cancels all of its ACTIVE pledges with REQUEST_WITHDRAWN.
- **Pledges never count as money:** after a pledge, the feed card has `pledgedMinor = X` and `confirmedMinor = 0`, and the summary's `remainingMinor` equals the target. Assert this explicitly.
- **Privacy:** summary and feed JSON contain no donor id, email or idempotency key (raw-key scan as in DiscoveryIT).
- Existing DiscoveryIT stays green.

**Verify:** `./gradlew :apps:api:test --tests 'com.kyro.platform.funding.*'`, `./gradlew :apps:api:test --tests 'com.kyro.platform.discovery.*'`, then `./gradlew build`. Manual curl against `bootRun` as `donor.demo@kyro.local` on a seeded request.

### OPS-1: User reports, moderation holds, admin report queue, dashboard (5b)

**Owns:** `apps/api/src/{main,test}/java/com/kyro/platform/operations/**`.

**Build:**
- `ReportsController implements ReportsApi` and `OperationsController implements OperationsApi`.
- Use JdbcTemplate. Read `discovery_public_requests` to check that a request is reportable. Read `funding_requests` (title, status, recipient id) and `verification_cases` (latest case id, counts) through read-only SQL. Record this in DECISIONS as a **reporting read exception**, the same idea as discovery reading a view: no writes, no Java imports.
- Use `UserDirectory` for display names.
- **Report state machine:** OPEN → IN_REVIEW (assign) → RESOLVED (resolve, assignee only). Use conditional UPDATEs with status and assignee in the WHERE, as API-3 does (D-014).
- **Guards:**
  - claim held by someone else → 409 `REPORT_ALREADY_ASSIGNED`
  - resolve by anyone other than the assignee → 409 `REPORT_NOT_ASSIGNED_TO_YOU`
  - any action on a RESOLVED report → 409 `INVALID_STATE_TRANSITION`
  - staff acting on a report about their own request, or a report they filed → 403 `SELF_REVIEW_FORBIDDEN` plus `ACCESS_DENIED` audit (`noRollbackFor` pattern from D-014)
- **Resolve with `holdRequest=true`:** insert `request_moderation_holds`, idempotent if a hold is already active, and audit `REQUEST_HOLD_PLACED`. The request disappears from the feed, detail, saved list and pledge creation through the V7 view.
- `releaseRequestHold` (ADMIN): sets `released_*` and audits. No active hold → 404.
- **Queues:** OPEN and MINE oldest first; RESOLVED and ALL newest first. Keyset cursor; bad cursor → 400.
- Dashboard: COUNT queries.
- **Never shown to the recipient:** the reporter's identity and any report text.

**Acceptance (integration tests):**
- A donor reports a public request → 202 and audit `REPORT_CREATED`. Same reporter again → 409. A different reporter → 202.
- OTHER without details → 422. Reporting your own request → 422. A non-public request → 404. Anonymous → 401.
- The 6th report in a minute → 429. This assertion only applies after SEC-2 merges; until then mark it `@Disabled("needs SEC-2")` and remove the annotation when rebasing after SEC-2.
- A verifier claims and resolves with ACTION_TAKEN and a hold. The request is gone from `GET /discovery/feed` and `/feed/{id}` (404). ADMIN releases → it is back. A VERIFIER calling release → 403.
- DISMISSED combined with `holdRequest` → 422. A short note → 422. Self-review guard → 403 plus an audit row.
- RECIPIENT or DONOR calling `/admin/reports` → 403.
- Dashboard counts match the fixtures.
- Paging returns no duplicates across pages.

**Verify:** `./gradlew :apps:api:test --tests 'com.kyro.platform.operations.*'`, then `./gradlew build`.

### SEC-2: Admin MFA, session revocation, rate limiting, forwarded IP, admin-denial audit (5b)

**Owns:**
- `apps/api/src/main/java/com/kyro/platform/common/security/**` (`SecurityConfig` after CON-2, `RateLimiter`, `RateLimitFilter`, new filters and properties)
- `identity/api/AuthController.java`, `identity/api/MeController.java`, `identity/api/UserMapper.java`, `identity/api/MfaController.java` (new)
- `identity/application/mfa/**`, `identity/application/session/**`, `identity/infrastructure/mfa/**`, `identity/infrastructure/session/**` (all new)
- `audit/infrastructure/AuditSecurityEventSink.java` (new)
- `devseed/**`
- Tests: `apps/api/src/test/java/com/kyro/platform/support/**`, `apps/api/src/test/java/com/kyro/platform/common/security/**`, `apps/api/src/test/java/com/kyro/platform/identity/mfa/**`, `.../identity/session/**`, `apps/api/src/test/resources/**`

**Build:**
- **Rate limiting:**
  - `RateLimitProperties` binds the `kyro.rate-limits` rules. The existing three auth limits become built-in rules that read the existing `kyro.auth.*` keys, so `KYRO_AUTH_*_RATE_PER_MINUTE` keeps working.
  - Match with Spring `PathPatternParser`. The bucket key is `ruleId|clientKey`. USER resolves to the principal's user id (the `SecurityContext` is already loaded at the filter's position before `CsrfFilter`) or falls back to the IP.
  - On limit: 429 `RATE_LIMITED` with `Retry-After` set to the seconds left in the window.
  - `enabled=false` disables only the non-auth rules.
- **Forwarded IP:** with `server.forward-headers-strategy=native`, Tomcat's RemoteIpValve gives the real client IP to `getRemoteAddr()`, which the rate limiter and audit use. Add an IT that sends `X-Forwarded-For` from a trusted proxy address with the strategy set, and one without it.
- **Session revalidation** (common defines the interface, identity implements it):
  - `common.security.SessionValidator` interface: `boolean stillValid(UUID userId, long credentialsVersionAtLogin)`. Identity implements it with a JDBC read of `users.status` and `credentials_version`.
  - `SessionGuardFilter` runs after the security context is loaded. At most once per `kyro.sessions.revalidate-interval` per session (timestamp kept in `LAST_REVALIDATED_AT`), it checks the user. If the user is SUSPENDED or the version changed, it invalidates the session, clears the context and audits `SESSION_REVOKED` through the identity implementation. Protected endpoints then return 401.
  - Login (`AuthController`) stores `CREDENTIALS_VERSION` in the session.
- **MFA (TOTP, RFC 6238):** SHA-1, 6 digits, 30-second step, ±`allowed-skew-steps`, using only JDK crypto.
  - **Replay protection:** reject a code whose step ≤ `last_used_step`.
  - **Secret storage:** AES-256-GCM with `kyro.mfa.encryption-key`, `key_id` "v1". Startup fails if the key is blank and the profile is neither `local` nor `test`.
  - **Recovery codes:** 10 codes of the form `XXXXX-XXXXX`, stored as SHA-256 hashes, single use. They are returned once at confirmation.
  - **Enrollment:** start replaces any unconfirmed secret; a confirmed one → 409 `MFA_ALREADY_ENROLLED`. Confirm checks the code, sets `confirmed_at`, marks the session MFA-verified (`MFA_VERIFIED_AT`) and rotates the session id.
  - **Verify:** succeeds with a code or a recovery code, rotates the session id, returns CurrentUser. A wrong code → 422 `MFA_CODE_INVALID` plus `MFA_FAILED` audit.
  - `resetUserMfa`: ADMIN only, not on self (403 `FORBIDDEN`). Deletes the credential and recovery codes, bumps `credentials_version` so the target's sessions die, and audits `MFA_RESET`.
  - **Enforcement:** when `kyro.mfa.admin-enforced=true`, a filter rejects `/api/v1/admin/**` for VERIFIER or ADMIN sessions that lack `MFA_VERIFIED_AT`. Not enrolled → 403 `MFA_ENROLLMENT_REQUIRED`; enrolled → 403 `MFA_REQUIRED`. The `/auth/mfa/**` endpoints are not affected.
  - `UserMapper` fills `CurrentUser.mfa` (required = staff role and enforced; enrolled; verified).
- **Admin denial audit:**
  - `common.security.SecurityEventSink` interface; `audit/infrastructure/AuditSecurityEventSink` implements it.
  - The access-denied handler reports 403s on `/api/v1/admin/**` and MFA denials as `ACCESS_DENIED {resource, reason: ROLE_MISSING | MFA_REQUIRED}`.
  - 401s are not audited (too noisy).
- **Dev seed (local only):** `admin@` and `verifier@` get a confirmed TOTP credential with the base32 secret `KYROLOCALDEVTOTPAAAAAAAAAAAAAAAA`, encrypted with the local key. Document it in `docs/runbooks` through a LOG note (E2E-2 owns runbooks).
- **Test support:**
  - `TestUsers.createActiveUser(...)` sessions for VERIFIER or ADMIN get `MFA_VERIFIED_AT` and `CREDENTIALS_VERSION` by default.
  - Add `createActiveUserWithoutMfa(Role...)`.
  - `application-test.yml` sets a test encryption key, `kyro.mfa.admin-enforced=true` and `kyro.sessions.revalidate-interval=0s`, so every IT runs with enforcement. Existing Verification and Audit ITs must stay green through the default session helper.
- DECISIONS entry.

**Acceptance (tests):**
- **Rate limits:** the 121st feed GET from one IP → 429 with `Retry-After`. Swipe limit per user: two users from the same IP are independent. Existing `AuthFlowIT` 429 test still passes.
- **MFA flow:**
  - enroll → confirm with a computed TOTP gives 10 codes
  - `/admin/verification/cases` → 200 after verify, 403 `MFA_REQUIRED` before, 403 `MFA_ENROLLMENT_REQUIRED` when not enrolled
  - the same code reused → 422
  - a recovery code works once
  - ADMIN reset of another user works and kills their session (401 next call); reset on self → 403
  - a DONOR calling `/auth/mfa/verify` → 403
- **Sessions:**
  - setting `users.status = 'SUSPENDED'` → the next request is 401
  - bumping `credentials_version` → 401
  - the revalidation interval is honoured (unit test with `Clock`)
- **Denial audit:** a DONOR hitting `/admin/**` writes an `ACCESS_DENIED` audit row with reason ROLE_MISSING.
- `ModuleBoundaryTest` passes, including `commonDoesNotDependOnModules`.
- With enforcement off (the yml default), `pnpm smoke` and `pnpm test:e2e` still pass unchanged.

**Verify:** `./gradlew :apps:api:test --tests 'com.kyro.platform.common.security.*'`, `./gradlew :apps:api:test --tests 'com.kyro.platform.identity.*'`, then `./gradlew build`. Manual check: `bootRun`, then the cookie-jar curl flow from API-1 for `verifier@kyro.local` with `KYRO_ADMIN_MFA_ENFORCED=true`, computing the TOTP from the dev secret.

### ACCT-1: Account recovery, consents, recipient notifications, education follow-ups (5b)

**Owns:**
- `apps/api/src/main/java/com/kyro/platform/identity/**` **except** SEC-2's files: `api/AuthController.java`, `api/MeController.java`, `api/UserMapper.java`, `api/MfaController.java`, `application/mfa/**`, `application/session/**`, `infrastructure/mfa/**`, `infrastructure/session/**`
- `notification/**` (main + test)
- `education/**` except `application/port/**` (main + test)
- Tests: `apps/api/src/test/java/com/kyro/platform/identity/account/**`, `.../identity/consent/**` (new); the existing `AuthFlowIT` may only be touched if a shared helper needs it

**Build:**
- **Password reset:**
  - `AccountController implements AccountApi`.
  - Requesting a reset always returns 202. For an ACTIVE user it creates a token (32 random bytes, base64url, SHA-256 stored, TTL `kyro.password-reset.token-ttl`) and mails `${KYRO_WEB_BASE_URL}/reset-password?token=…`. In profile `local` the link is also logged with the `DEV-ONLY` prefix. Unknown emails and non-ACTIVE users get no mail. Audit `PASSWORD_RESET_REQUESTED` only for real users.
  - Confirm: the token must be valid and unused, otherwise 422 `TOKEN_INVALID`. Then set the new hash, increment `credentials_version`, consume all of the user's outstanding tokens, audit `PASSWORD_RESET_COMPLETED`, and send a "your password was changed" mail.
- **Change password:**
  - Check the current password; wrong → 422 `CURRENT_PASSWORD_INCORRECT` plus `LOGIN_FAILED`-style audit details.
  - Set the new hash and increment `credentials_version`.
  - **Keep the caller's session alive:** set `SessionAttributes.CREDENTIALS_VERSION` on the current session to the new value and change the session id. Every other session of that user dies at its next revalidation (SEC-2).
  - Audit `PASSWORD_CHANGED`.
- **Consents:** `ConsentsController implements ConsentsApi`. Read history from `consent_records`. `reacceptRequired` is true when the latest accepted TERMS or PRIVACY version differs from `kyro.policy.*`. Accepting requires the current versions (422 `CONSENT_VERSION_MISMATCH`), inserts two rows with IP and user agent, and audits `CONSENT_ACCEPTED`.
- **Education:**
  - (a) PATCH `clearFields` per P5.5. Cleared fields become NULL and `fee_reference_normalized` is updated too. Bump the version; the `REQUEST_UPDATED` audit details list `clearedFields`.
  - (b) Publish `EducationRequestStatusChanged` from the single transition path (`RequestTransitions.apply`) for every transition, inside the transaction.
- **Notification module:**
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` on `EducationRequestStatusChanged`, only for `toStatus` in APPROVED, CHANGES_REQUESTED or REJECTED.
  - Gets the email through `UserDirectory.findRecipient` and sends with `JavaMailSender` from `kyro.mail-from`.
  - **Mail content:** fictional-safe and minimal. Request title, the new status in plain words, and a link to `${web}/recipient/requests/{id}`. **It never contains the internal rationale, the recipient message text, fee references or any `token=` string.**
  - Failures are logged at WARN with the correlation id and never rethrown.
  - Controlled by `kyro.notifications.email-enabled`.
- `PasswordResetProperties` and `NotificationProperties` records.
- DECISIONS entry.

**Acceptance (integration tests; mails via the existing `TestMailConfig.CapturingMailer`, or a capturing `JavaMailSender` for notifications):**
- **Reset:** unknown email → 202 and no mail. Known ACTIVE email → 202, mail with the link, audit row. Confirm → 204; old password fails login; new one works. Token reuse → 422. Expired token → 422. A pre-existing session for that user gets 401 on its next call (test profile sets the revalidate interval to 0s). A short new password → 422.
- **Change password:** correct current → 204 and the calling session still works. Another session → 401. Wrong current → 422.
- **Consents:** GET shows two history rows after registration and `reacceptRequired=false`. With `kyro.policy.terms-version` overridden in the test (`@TestPropertySource`): `true`, then POST with the right versions → `false`; a wrong version → 422.
- **PATCH clearFields:** clears `courseName` and `dueDate` (the GET shows null). Clearing and setting the same field → 422. Clearing `title` → 422 (enum validation). A later submit gives `REQUEST_INCOMPLETE` listing `courseName`.
- **Notifications:** REQUEST_CHANGES through the real verification flow sends exactly one mail to the recipient after commit. It contains no rationale text and no `token=`. A rolled-back transaction sends nothing. Submit and withdraw send nothing.
- Existing `EducationRequestsIT`, `VerificationIT` and `AuthFlowIT` stay green.

**Verify:** `./gradlew :apps:api:test --tests 'com.kyro.platform.identity.*'`, `./gradlew :apps:api:test --tests 'com.kyro.platform.education.*'`, `./gradlew :apps:api:test --tests 'com.kyro.platform.notification.*'`, then `./gradlew build`. Manual: `bootRun` plus a Mailpit UI check at `http://localhost:8025`.

### WEB-3: Donor pledge UI, my pledges, report action, funding semantics fix (5b)

**Owns:** `apps/web/src/app/(donor)/**`, `apps/web/src/features/discovery/**`, `apps/web/src/features/funding/**` (new), `apps/web/src/features/reports/**` (new), `apps/web/src/components/AppShell.tsx`, `apps/web/src/lib/queryKeys.ts`.

**Build:**
- **Request detail funding panel:** show Target, **Confirmed** (₹0, "No payments are taken in this sandbox"), **Pledged (not yet paid)** with the pledge count, and **Outstanding need = target − confirmed**. The progress bar uses confirmed only. Pledged has its own separately labelled line, not stacked on the bar. This replaces the D-011 remaining formula.
- **Pledge flow:**
  - "Pledge (non-binding)" replaces the disabled Fund button for DONOR users.
  - It opens a Dialog with:
    - a money input (`parseMoneyInput`) with client-side checks: at least ₹1 and at most `pledgeableMinor` from `getFundingSummary`
    - a required checkbox: "I understand a pledge is not a payment. No money is collected."
    - the persistent text "A pledge is not a payment."
  - An idempotency key is generated with `crypto.randomUUID()` when the dialog opens and **reused for retries** of the same submission.
  - Map `ACTIVE_PLEDGE_EXISTS`, `PLEDGE_EXCEEDS_REMAINING` (with the refreshed amount) and `PLEDGE_NOT_ALLOWED` to friendly Alerts.
  - After success, show the viewer's pledge with a "Cancel pledge" action behind a confirm Dialog.
  - Anonymous users get a login link; non-donors get a read-only note.
- **`/pledges` page** (`RequireAuth DONOR`): list of my pledges with status badges, cancel reason text, "Request no longer available" when `request` is null, and Load more. AppShell nav gets "My pledges" (DONOR).
- **Report action** on the detail page (and an optional overflow on the card): a Dialog with a reason radio group and details (required for OTHER). Success copy: "Thanks. Our team will review this request." 409 → "You have already reported this request." Anonymous → login link.
- Cards show `₹X pledged (not yet paid)` only when the amount is greater than 0.
- Query keys: `funding.summary(id)`, `funding.myPledges`. Pledge and cancel invalidate the summary, myPledges and the detail.

**Acceptance:**
- `pnpm --filter @kyro/web lint`, `typecheck`, `test` and `build` pass.
- Vitest:
  - the pledge dialog blocks submit without the checkbox and above the pledgeable amount
  - a retry after a network error sends the **same** `Idempotency-Key`
  - outstanding need ignores pledges
  - a source scan finds no wording that pledges are "raised" or "funded", and no `feeReference` in `features/funding` or `features/reports`
  - report dialog validation
  - cancel confirm
- Keyboard reachable and labelled.
- Renders at 320px.

**Live check (after FUND-1 and OPS-1 merge):** pledge, then cancel, then report, against `bootRun` as `donor.demo@kyro.local`.

### WEB-4: Account recovery UI, consent gate, recipient edit fix, recipient funding view (5b)

**Owns:** `apps/web/src/app/(auth)/**`, `apps/web/src/app/(account)/**`, `apps/web/src/app/(recipient)/**`, `apps/web/src/app/layout.tsx`, `apps/web/src/components/Providers.tsx`, `apps/web/src/components/ConsentGate.tsx` (new), `apps/web/src/features/authentication/**`, `apps/web/src/features/education/**`, `apps/web/src/features/account/**` (new).

**Build:**
- **Password reset pages:**
  - `/forgot-password` (always shows "If an account exists, we sent a link")
  - `/reset-password?token=` (new password plus confirm, with Zod rules matching the spec, success links to login)
  - a "Forgot password?" link on `/login`
- `/profile` gains a "Change password" form.
- **`ConsentGate`** (rendered from the root layout, logged-in users only): calls `GET /me/consents`. When `reacceptRequired`, it shows a blocking accessible Dialog with links to the policies and an "Accept" action. It is not shown on the auth pages.
- **Recipient edit:** the update payload builder sends `clearFields` for optional fields the user emptied that had a value before. It stops faking values. Closes D-010.
- **Recipient request detail:** when APPROVED, show a funding panel from `getFundingSummary` with the same semantics as WEB-3: confirmed and outstanding first, pledged separately as non-binding. Use a local hook in `features/education`, not `features/funding`.

**Acceptance:**
- `pnpm --filter @kyro/web lint`, `typecheck`, `test` and `build` pass.
- Vitest: reset form validation; `clearFields` payload builder (emptied vs untouched vs changed); ConsentGate shows and hides; the change-password form maps `CURRENT_PASSWORD_INCORRECT`.
- 320px.

**Live check (after ACCT-1 and FUND-1 merge):** reset via the Mailpit link, change password, clear a field on a draft.

### ADM-2: Admin MFA screens, reports queue, dashboard (5b)

**Owns:** `apps/admin/**`.

**Build:**
- **MFA:**
  - `/mfa/setup`: start enrollment; show the otpauth QR, rendered client-side with the `qrcode` package (no network), plus the secret as copyable text; confirm the code; show the 10 recovery codes **once* with an "I have saved these" checkbox before continuing.
  - `/mfa/verify`: a 6-digit code or a "use a recovery code" toggle.
  - RoleGuard: after `/me`, if `mfa.required && !mfa.verified`, redirect to verify (enrolled) or setup (not enrolled). Any 403 `MFA_REQUIRED` or `MFA_ENROLLMENT_REQUIRED` from a query also redirects.
  - Codes and secrets are never put in query cache, storage or URLs (`gcTime 0`, component state only).
- **Dashboard:** `/` shows the dashboard counts and links to the queue and reports; it no longer just redirects.
- **Reports:**
  - `/reports`: tabs Open, Mine, Resolved, All; a table; Load more.
  - `/reports/[reportId]`: reason, details, reporter, other reports on the request, request status, a link to `/cases/{latestCaseId}`, Claim, a resolve form (radio DISMISSED or ACTION_TAKEN, note 10–2000, "Hide request from discovery" checkbox enabled only for ACTION_TAKEN, confirm Dialog).
  - The hold panel with "Release hold" is shown only to ADMIN.
  - 403 and 409 codes become friendly Alerts.
- Nav entries for Dashboard, Queue, Reports and Audit.

**Acceptance:**
- `pnpm --filter @kyro/admin lint`, `typecheck`, `test` and `build` pass.
- Vitest:
  - RoleGuard MFA redirects
  - setup flow cannot continue before the recovery-code acknowledgement
  - resolve-form rules (the hold checkbox is disabled for DISMISSED; note length)
  - Release hold is hidden for VERIFIER-only users

**Live check (after SEC-2 and OPS-1 merge):** with `KYRO_ADMIN_MFA_ENFORCED=true`, `verifier@kyro.local` verifies with a TOTP from the dev secret, claims and resolves a report with a hold; `admin@kyro.local` releases it.

### INF-2: Terraform AWS staging foundation plus staging API profile (5a; replaces LATER-2)

**Owns:** `infrastructure/terraform/**` (including its own `.gitignore` for `.terraform/`, `*.tfstate*`, `*.tfvars` except `*.tfvars.example`), `.github/workflows/terraform.yml`, `docs/runbooks/terraform-staging.md`, `apps/api/src/main/resources/application-staging.yml` (new), `apps/api/src/main/java/com/kyro/platform/common/storage/S3ObjectStorage.java`, and `apps/api/src/test/java/com/kyro/platform/common/storage/**`.

**Build:**
- **Layout:** `infrastructure/terraform/modules/{network,kms,s3_documents,ecr,rds,secrets,ecs_service,alb,edge,github_oidc,observability,ses}` and `envs/staging/` (`main.tf`, `variables.tf`, `outputs.tf`, `versions.tf`, `backend.hcl.example`, `staging.tfvars.example` with the fictional account `000000000000`).
- **Versions:** Terraform ≥ 1.9, AWS provider pinned to the newest stable major. Record both in DECISIONS.
- **No data sources** that need credentials. Availability zones, account id and region are variables, so an offline plan works.
- **Modules:**
  - **network:** VPC across 2 AZs with public (ALB), private-app and isolated-db subnets; one NAT gateway (staging cost); S3 gateway endpoint; interface endpoints for ECR, Secrets Manager and Logs.
  - **kms:** customer-managed keys for documents, RDS and secrets, with rotation on.
  - **s3_documents:** block all public access; versioning; SSE-KMS; a bucket policy that denies non-TLS and unencrypted puts; CORS PUT only from the web origin variable; noncurrent-version expiry; `aws_guardduty_malware_protection_plan` (tags objects; app gating is Phase 6).
  - **ecr:** 3 repositories, immutable tags, scan on push, lifecycle policy.
  - **rds:** Postgres 17, `manage_master_user_password = true`, encrypted, 7-day backups and PITR, deletion protection, private subnets, security group allowing only the api tasks.
  - **secrets:** `kyro/staging/mfa-encryption-key` and `kyro/staging/mail-smtp`. Created **without values**; they are set out of band.
  - **ecs_service:** Fargate cluster; services api, web and admin using the Dockerfiles from INF-1 with Service Connect; **api `desired_count = 1`, enforced by a variable validation, until Spring Session JDBC lands**; least-privilege task roles (api: S3 actions on the bucket, KMS on the documents key, read on its secrets); a log group per service.
  - **Api task env:** `SPRING_PROFILES_ACTIVE=staging`, `KYRO_FORWARD_HEADERS_STRATEGY=native`, `KYRO_ADMIN_MFA_ENFORCED=true`, `KYRO_SEED_ENABLED=false`, `KYRO_S3_*` with no static keys, `KYRO_WEB_BASE_URL`, and secrets injected from Secrets Manager.
  - **Web and admin images:** built with `KYRO_API_ORIGIN` pointing to the Service Connect name (D-006).
  - **alb:** HTTPS listener with an ACM certificate ARN variable and HTTP→HTTPS redirect. Host routing: web host → web, admin host → admin, and `/api/*` on either host → api.
  - **edge:** CloudFront in front of the ALB with a WAFv2 web ACL in us-east-1 (provider alias): AWS managed Common, KnownBadInputs and SQLi rule groups, plus a rate-based rule. The admin hostname is restricted to `admin_allowed_cidrs`.
  - **github_oidc:** OIDC provider plus two roles: a read-only `kyro-staging-tf-plan` role trusted for `repo:arx-studios/arx-kyro:pull_request`, and a `kyro-staging-deploy` role trusted only for `environment:staging`. **Nothing assumes either role in Phase 5.**
  - **observability:** log retention; alarms for ALB 5xx, ECS CPU and memory, and RDS free storage and CPU; an SNS topic variable.
  - **ses:** domain identity plus DKIM outputs, behind `enable_ses = false`.
- **`application-staging.yml`:**
  - `logging.structured.format.console: ecs`
  - `server.forward-headers-strategy: native`
  - session cookie secure
  - seed disabled
  - `spring.mail` with `username`, `password` and STARTTLS from `KYRO_MAIL_USERNAME` and `KYRO_MAIL_PASSWORD`, port 587
- **`S3ObjectStorage`:**
  - a blank `access-key` → `DefaultCredentialsProvider` (task role)
  - a blank `endpoint` → no endpoint override and `forcePathStyle(false)`
  - a blank `public-endpoint` → the default presigner endpoint
  - local behaviour unchanged, the `ObjectStorage` port signature unchanged (frozen)
  - plus a unit test for the client-builder branches
- **`terraform.yml`** (PRs and pushes touching `infrastructure/terraform/**`, plus `workflow_dispatch`), with `permissions: contents: read` and **no AWS credentials**:
  - `terraform fmt -check -recursive`
  - for every module and env: `terraform init -backend=false` and `terraform validate`
  - `tflint --recursive`
  - `trivy config --severity HIGH,CRITICAL --exit-code 1 infrastructure/terraform`, with any documented ignores carrying a justification
  - an **offline plan** of `envs/staging` with `AWS_ACCESS_KEY_ID=mock`, `AWS_SECRET_ACCESS_KEY=mock`, provider `skip_credentials_validation`, `skip_requesting_account_id` and `skip_metadata_api_check` (set through a variable used only in CI), and `terraform plan -refresh=false -lock=false -input=false -var-file=staging.tfvars.example`
  - **No apply step exists in any workflow.**
- **Runbook:** state bucket bootstrap (manual, versioned and encrypted, with a DynamoDB or S3-native lock); how to fill the secrets; human-approved `plan` with the OIDC plan role; apply only with written approval; cost estimate; the single-task session limit; and the S3 malware-scan app-gating gap.

**Acceptance:**
- The workflow is green.
- actionlint is clean.
- trivy has no HIGH or CRITICAL findings without a justified ignore.
- The offline plan succeeds and creates only resources (no reads).
- gitleaks is clean.
- `./gradlew build` is green, including the storage tests.

**Verify** (local, Docker images, no install needed):
```bash
docker run --rm -v "$PWD/infrastructure/terraform:/tf" -w /tf hashicorp/terraform:1.9 fmt -check -recursive
docker run --rm -v "$PWD/infrastructure/terraform:/tf" -w /tf/envs/staging hashicorp/terraform:1.9 init -backend=false   # then: validate
docker run --rm -v "$PWD:/repo" -w /repo aquasec/trivy config --severity HIGH,CRITICAL --exit-code 1 infrastructure/terraform
docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest
./gradlew :apps:api:test --tests 'com.kyro.platform.common.storage.*'
```

### E2E-2: Phase 5 integration, MFA enforcement on, extended smoke and Playwright (5c)

**Owns:** `e2e/**`, `scripts/smoke/**`, `.github/workflows/e2e.yml`, `docs/runbooks/local-dev.md`. Also exactly one line in `application.yml`: the default of `KYRO_ADMIN_MFA_ENFORCED` from `false` to `true`. Like E2E-1, it may make small logged bug fixes in DONE packages' paths, with DECISIONS entries for anything contract-related.

**Build:**
- **Helpers:** an RFC 6238 TOTP generator (`node:crypto`) in `e2e/support/helpers.ts` and in smoke. Admin login helpers do password, then TOTP verify (dev secret for seeded staff). The smoke self-review check enrolls the psql-granted VERIFIER through the API (start, compute code from the returned secret, confirm, verify) before calling the self-review guard. The Mailpit helper picks the message matching the subject or link pattern, not just the newest one.
- **New Playwright specs** (Chromium; each makes its own fictional users or requests, so they run on a reused database):
  1. **Correction loop:** submit → verifier REQUEST_CHANGES with a message → recipient sees the feedback and the "Changes requested" email arrives (no rationale text in it) → edit → resubmit → case #2 → approve → appears in the feed.
  2. **Reject:** reject with a message → recipient sees REJECTED, the request is not editable, not in the feed, and the email arrives.
  3. **Withdraw:** withdraw while IN_REVIEW (the case closes as WITHDRAWN in the admin Closed tab), and withdraw an APPROVED request that has a donor pledge (gone from the feed; the donor's `/pledges` shows Cancelled, "request withdrawn").
  4. **Pledge:** the donor pledges through the dialog; the detail shows pledged separately, confirmed ₹0 and outstanding unchanged; a second pledge → friendly error; cancel.
  5. **Report and hold:** the donor reports → verifier claims and resolves ACTION_TAKEN with a hold → the request is gone from `/discover` (and the detail is 404) → `admin@` releases → visible again.
  6. **Password reset:** request → Mailpit link → new password → login works; a second context logged in before the reset gets a 401 redirect.
  7. **PATCH clear:** clear a field on a draft and reload; it is empty.
  8. **Admin MFA:** a fresh staff user (granted via psql in the test) goes through setup with QR/secret, recovery codes and verify; a wrong code shows an error.
- **axe** (serious/critical = 0) on `/pledges`, the pledge and report dialogs, `/forgot-password`, `/reset-password`, admin `/`, `/reports`, `/reports/[id]`, `/mfa/setup` and `/mfa/verify`. **320px** checks on the detail page with the funding panel and on `/pledges`.
- **Smoke additions:**
  - API pledge idempotency: replay → same id, different body → 422, over-pledge → 409
  - pledged vs confirmed fields in the feed
  - report → hold → feed exclusion → release
  - `MFA_REQUIRED` before verify
  - one 429 check on the report rule (the 6th report within a minute, spread across 6 fresh requests from the seed or created ones)
- **Workflow env:** `KYRO_ADMIN_MFA_ENFORCED=true`, `KYRO_SESSION_REVALIDATE_INTERVAL=0s`, and the existing raised `KYRO_AUTH_*` limits. Keep the per-rule defaults. Confirm the first real GitHub runs of `ci.yml`, `e2e.yml` and `terraform.yml` are green.
- **Runbook:** dev TOTP secret, MFA reset via the ADMIN API, password-reset mail in Mailpit, new env vars, and "staff provisioning via SQL during beta" until a role API exists.

**Acceptance:**
- `pnpm smoke` exits 0.
- `pnpm test:e2e` passes locally against dev servers, with MFA enforced.
- `e2e.yml` is green on GitHub.
- `./gradlew build`, `pnpm lint`, `pnpm typecheck` and `pnpm test` are green on main after the merge.

**Verify:**
```bash
pnpm infra:up && ./gradlew :apps:api:bootRun   # KYRO_ADMIN_MFA_ENFORCED defaults to true now
pnpm --filter @kyro/web dev ; pnpm --filter @kyro/admin dev
pnpm smoke
pnpm --filter @kyro/e2e exec playwright install chromium && pnpm test:e2e
```

---

## P5.10 Phase 5 risks

| Risk | Mitigation |
|---|---|
| A spec change breaks the Java build (existing tag or required field) | P5.0 rule 1. CON-2's acceptance requires `./gradlew build` green with the new interfaces unimplemented. |
| Turning MFA on breaks smoke and E2E for 5b merges | Enforcement defaults to off until E2E-2 flips it, in the same change that updates smoke and E2E. API ITs run with it on through `application-test.yml`. |
| Two packages edit identity files | File-level split between SEC-2 and ACCT-1 (listed). The contract between them is a database column (`credentials_version`) and `SessionAttributes` constants (CON-2). |
| In-memory sessions and rate limits on more than one API task | Staging is pinned to one task through a variable validation. Spring Session JDBC is the first Phase 6 item. |
| Notification email lost on a crash | Accepted for the beta (the state is durable and visible in the app). The outbox comes with the worker. |
| An append-only trigger blocks a future account deletion | Documented in V9. Deletion must anonymise. |
| A rate limit trips E2E or CI | Every rule has an env override. The E2E workflow raises the auth limits as before. |
| The pledge cap is mistaken for payment logic | The pledge cap is non-binding and is documented as the template for reservations. `confirmedMinor` stays a single hard 0 source until payments. |

## P5.11 Decisions each package records (take the next free D-number; re-read DECISIONS.md first)

- CON-2: D-017, the Phase 5 contract plus migration renumbering (supersedes D-003).
- FUND-1: pledge semantics and idempotency.
- OPS-1: reporting read exception and holds.
- SEC-2: MFA, sessions and rate limits.
- ACCT-1: reset, consent, notifications and clearFields.
- INF-2: Terraform versions, offline plan and single-task constraint.
- E2E-2: MFA enforcement flip.

## P5.12 Phase 6 candidates (not to be built now)

1. Spring Session JDBC, then scale out the api.
2. Malware-scan gating of document view URLs, and EXIF stripping.
3. `apps/worker` with an outbox (notifications, pledge lapsing).
4. Appeals (TRUST-007).
5. Staff role management API.
6. Server-side consent enforcement, data export, and a retention-aware account deletion.
7. Metrics and tracing (OTel).
8. Payment sandbox, reservations, double-entry ledger (V10+), behind the §16 compliance gates.

