# Decisions (ADR-lite)

Format: `## D-### Title (date, author)` — Context / Decision / Consequences.

## D-001 Coordination via docs/progress (2026-11-10, orchestrator)
Agents coordinate through PLAN.md / TASKS.md / LOG.md / DECISIONS.md. Directory ownership per task avoids edit conflicts between parallel builders.

## D-002 FND-2 contract details and root wiring (2026-11-10, builder-FND-2)
Context: FND-2 implemented PLAN §7/§11 with small, additive interpretations.
Decisions:
- Nullable refs use `type` + `allOf:[$ref]` + `nullable: true` (redocly `nullable-type-sibling` requires `type`).
- Security: global default `sessionCookie`; public ops use `security: []`; mutating ops require `sessionCookie`+`csrfHeader` (public mutating ones, e.g. register/login/verify, only `csrfHeader`).
- Added shared schemas not named in PLAN: `FieldError` (Problem.errors item), `Consents`, `ProfileSummary`. Query/path params are `components/parameters` (RequestId, DocumentId, CaseId, Cursor, Limit). Every op has 4xx/5xx refs to `components/responses/Problem*`.
- `redocly.yaml` extends `recommended`, with `no-server-example.com: off` (localhost server is required by PLAN).
- `@kyro/api-client` `generate` = `openapi-typescript ... -o src/schema.d.ts && node scripts/gen-aliases.mjs`; the second step writes `src/types.ts` (aliases for every schema) so aliases cannot drift. `index.ts` re-exports `types.ts`. Client also falls back to the token in the `/auth/csrf` body if the XSRF-TOKEN cookie is unreadable; `createKyroClient` accepts optional `fetch`, `getCookie`, `generateCorrelationId` (for tests/SSR). In Node (no document) a non-empty `baseUrl` is needed.
- Package-local `.gitignore` (node_modules, pnpm-lock.yaml) added in both packages because root .gitignore is FND-1's.
Root wiring needed (FND-1 / orchestrator):
- root package.json scripts: `"gen:client": "pnpm --filter @kyro/api-client generate"`, `"spec:lint": "pnpm --filter @kyro/api-spec lint"`.
- pnpm-workspace.yaml must include `packages/*` and allow the esbuild build script (e.g. `onlyBuiltDependencies: [esbuild]` / pnpm 12 equivalent) — vitest pulls esbuild.
- Optional: have packages/api-client use @kyro/config tsconfig base later; currently its own strict tsconfig (ES2023, bundler resolution).

## D-004 MinIO image source changed (2026-11-10, builder-FND-1)
Context: `minio/minio` and `minio/mc` are no longer pullable from Docker Hub (pull access denied; quay.io also 401). PLAN section 4 pinned `minio/minio:RELEASE.2025-09-07T16-13-09Z`.
Decision: docker-compose uses `bitnamilegacy/minio:2025.5.24-debian-12-r5` (data volume `/bitnami/minio/data`, image default entrypoint) and `bitnamilegacy/minio-client:2025.5.21-debian-12-r4` for minio-init. Env vars (MINIO_ROOT_USER etc.) unchanged; S3 API 9000 / console 9001 as planned.
Consequences: Testcontainers (API-1 IntegrationTestBase) should use the same image with its default entrypoint and MINIO_ROOT_USER/PASSWORD env, or another S3-compatible image. The legacy image is frozen/unpatched: dev/test only. `minio-init.sh` retries alias setup because MinIO reports healthy before mc can connect.

## D-005 Root workspace wiring for FND-2 (2026-11-10, builder-FND-1)
pnpm 12 uses `allowBuilds` (map, true = allow) in pnpm-workspace.yaml; `onlyBuiltDependencies` is ignored with a warning. Allowed: esbuild, sharp, @tailwindcss/oxide, unrs-resolver. Removed the `pnpm-lock.yaml` line from packages/api-spec/.gitignore and packages/api-client/.gitignore; the single root `pnpm-lock.yaml` is committed. Root scripts `gen:client` and `spec:lint` present. Verified after rebase on main: install, spec:lint, gen:client (no drift), typecheck, test (8 tests) pass.

## D-006 INF-1 CI and image details (2026-11-10, builder-INF-1)
- Presence detection is a `detect` job (job-level `if` cannot use `hashFiles`); api and image jobs skip until apps exist.
- Next `rewrites()` are resolved at build time, so `KYRO_API_ORIGIN` is a Docker build arg (default `http://api:8080`) in web/admin Dockerfiles and compose, in addition to the runtime env.
- Web/admin Dockerfiles assume `output: 'standalone'` + `outputFileTracingRoot` at the monorepo root (WEB-1/ADM-1 must set it); users run as uid 1000 (node), API as uid 10001.
- Secret scan uses the gitleaks docker image (pinned v8.24.3) rather than gitleaks-action (license requirement for orgs); `.github/gitleaks.toml` allowlists documented local sandbox credentials.
- Added `images` CI job (build only, no push) and a manual `deploy-placeholder.yml` (OIDC pattern, no deploy). Dependabot omits the docker ecosystem because it does not detect `*.Dockerfile` names.

## D-007 Design-system API details (2026-11-10, builder-FND-3)
- parseMoneyInput returns { ok: true, amountMinor } or { ok: false, error, message } (EMPTY, INVALID_FORMAT, TOO_MANY_DECIMALS, NEGATIVE, OUT_OF_RANGE). "," is grouping only (en-US/en-IN), "." is decimal; "12,50" is rejected as ambiguous.
- Field takes a render-function child (props) => node, or a single element cloned with id, aria-describedby, aria-invalid, aria-required, invalid, required. Input/Textarea/Select accept invalid.
- Extra exports beyond PLAN section 11: cn, focusRing, minorUnitDigits, Spinner, subpaths @kyro/design-system/money and /theme.css. RequestStatus/CaseStatus unions are local (no dependency on @kyro/api-client); APPROVED is labelled "Verified". Extra tokens added (navy/warm/charcoal/champagne scales, success/danger/warning tints).
- No build script: TS source is shipped. Dialog is a custom portal modal (not native dialog) so it works in jsdom.

## D-008 Decision ID allocation (2026-11-10, orchestrator)
D-003 is unused; FND-3's design-system entry was renumbered D-006 → D-007 (FND-3's LOG.md entry may still say D-006). Before adding an entry, re-read this file and take the next free number.

## D-009 Admin app details (2026-11-10, builder-ADM-1)
- Next 16 (Turbopack build). Inter is loaded with `@fontsource-variable/inter` (family "Inter Variable", overridden in globals.css `@theme`) instead of next/font/google, so builds (incl. Docker) need no network.
- View URLs: used once straight from the POST response via `window.open(url,'_blank','noopener')`; never in state/cache/storage. React Query default `gcTime: 0` so private case data is dropped on unmount; all responses `Cache-Control: no-store`.
- Extra queue column "Status" (case status badge + outcome) beyond the PLAN list; Load-more cursor paging. Decision form shown only when server `canDecide` is true; after any failed claim/decision the case is refetched.
- apps/admin/AGENTS.md was generated by Next 16 and is committed.

## D-010 Web app foundation details (2026-11-10, builder-WEB-1)
- Next 16.4 (Turbopack build), React 19, Tailwind v4 via `@tailwindcss/postcss`. `next.config.ts` sets `output: 'standalone'`, `outputFileTracingRoot` and `turbopack.root` = monorepo root (server at `.next/standalone/apps/web/server.js`), `transpilePackages`, and `/api/:path*` rewrite to `KYRO_API_ORIGIN` (default `http://localhost:8080`, resolved at build time per D-006).
- Inter is loaded with `next/font/google` (build needs network access to fonts.googleapis.com; ADM-1 used `@fontsource-variable/inter` instead, which is offline-safe: switch if Docker/CI builds are offline). It is exposed as `--font-inter`, applied on `html` ahead of the design-system `--font-sans`.
- ESLint: flat config re-exporting `@kyro/config/eslint` (no eslint-config-next; Next 16 dropped `next lint`). Run `eslint .`.
- Document upload to storage uses XMLHttpRequest PUT (for progress events) with the presigned `requiredHeaders`, not fetch. The upload URL is used in memory only, never rendered or stored in state.
- Request update sends only non-empty optional fields, so a field cannot be cleared back to empty through PATCH (spec has no null semantics). Always sends `institutionCountry` (default IN) since its generated type is required.
- Roles on register come from two checkboxes (recipient, donor); country is a fixed select of 8 countries. Currency is fixed to INR (server accepts only INR).
- Route groups: `(public)`, `(auth)`, `(recipient)` (client layout with `RequireAuth roles=['RECIPIENT']`), `(account)/profile`. `/discover` and `/saved` are linked in nav but owned by WEB-2 (404 until it lands).
- PWA manifest uses a single SVG icon (`public/icon.svg`); no PNG icons and no service worker yet.

## D-011 Donor discovery UI details (2026-11-10, builder-WEB-2)
- Swipe action for Save is `SAVED` (spec enum PASS | SAVED | INTERESTED); UI label stays "Save".
- SwipeDeck is presentational (props: cards, mode, onSwipe promise, paging callbacks) so tests need no QueryClient; DiscoverFeed wires hooks. Mode: anonymous | readonly (non-DONOR) | donor.
- Swipe success does not invalidate the feed (would reset the stack); it updates the detail cache viewerAction and invalidates saved. Removing a saved item invalidates the feed so it can return.
- Detail route is `/discover/[id]` (server page awaits `params`, client view). Remaining funding = target - pledged - confirmed. No report action (not in spec).
- Keyboard handler is on the feed region (keys bubble from focused card/buttons), not window-global; ignored with modifier keys.

## D-012 API-1 build/runtime choices (2026-11-10, builder-API-1)
Context: PLAN section 3 left Gradle/Boot/generator versions open and assumed several generator and Boot 4 behaviours.
Decisions:
- Gradle 9.9.0 (wrapper at repo root) runs fine on JDK 27, so no daemon-JVM/foojay fallback; no `java.toolchain`, `options.release = 21`. The Dockerfile/CI use Temurin 25: same build, no JDK-specific code. Spring Boot 4.1.2 (GA), openapi-generator-gradle-plugin 7.14.0, AWS SDK 2.57.12, ArchUnit 1.5.1, Testcontainers 2.x (managed by Boot BOM); versions in `gradle/libs.versions.toml`.
- Generator output compiles on Boot 4 with ONE post-processing step: the `spring` generator emits `@JsonDeserialize(as = LinkedHashSet.class)` (Jackson 2 `com.fasterxml.jackson.databind`) on `RegisterRequest.roles`; `openApiGenerate` has a `doLast` that strips that import/annotation (Jackson 3 builds the Set itself). No hand-written DTOs needed.
- `RegisterRequest.roles` is an enum of RECIPIENT/DONOR in the spec, so VERIFIER/ADMIN fails Jackson enum deserialisation. `GlobalExceptionHandler` maps a deserialisation error whose JSON path contains `roles` to 422 `ROLE_NOT_ALLOWED` (instead of 400) as PLAN section 5 requires. Contract unchanged.
- Login does not use `AuthenticationManager`: `AuthService.authenticate` checks the BCrypt hash directly (dummy hash for unknown emails, equal timing) and the controller builds the `SecurityContext`, rotates the session id and saves it through `HttpSessionSecurityContextRepository`. Same observable behaviour as PLAN section 5.
- CSRF: `.csrf(c -> c.spa())` (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`). The request-attribute token is BREACH-masked, so `GET /auth/csrf` returns the PLAIN token from `DeferredCsrfToken` (equals the cookie value); clients echo the cookie value in the header.
- Persistence in identity/audit is `JdbcTemplate` only (no JPA entities); `ddl-auto=validate` is still on. API-2 may add JPA entities for education. JdbcTemplate and JPA share one transaction (AuditService participates in the caller's tx).
- Error codes added beyond PLAN (additive): 405 `METHOD_NOT_ALLOWED`, 415 `UNSUPPORTED_MEDIA_TYPE`.
- `/actuator/health` is public; the mail health indicator is disabled (Mailpit absence must not turn the API DOWN). Session cookie `Secure` defaults to true; profile `local` sets false.
- Rate limiting is keyed by `request.getRemoteAddr()` (no X-Forwarded-For trust). Tests reset it via `RateLimiter.reset()` in `IntegrationTestBase`.
- Test MinIO uses `bitnamilegacy/minio:2025.5.24-debian-12-r5` via plain `GenericContainer` (per D-004), bucket `kyro-documents-test` created through the S3 SDK.
Migrations V1-V5 untouched and correct for API-1 (Hibernate validate passes; all identity/audit SQL works).

Note (orchestrator): API-1's entry renumbered D-006 → D-012; LOG/report references to 'D-006' from builder-API-1 mean D-012.

## D-013 Discovery API details (2026-11-10, builder-API-4)
- Cursors are base64url(`<ISO instant>|<uuid>`): feed = (approved_at, id) DESC; saved = (swipes.updated_at, swipes.id) DESC. Bad cursor -> 400 MALFORMED_REQUEST. Page size clamps to kyro.discovery.max-page-size.
- Feed also hides the caller's own requests for ANY authenticated user (not only donors) and requests past due_date (null due date stays visible). Detail stays public for any request in the view even if past due.
- Swipe always returns 200 (spec has no 201); latest action wins; own request -> 422 VALIDATION_FAILED; not in view -> 404. DELETE saved only removes SAVED/INTERESTED rows and is always 204 (idempotent; spec's 404 not used). Saved list hides items no longer in the view.
- No rate limiting on discovery: RateLimiter (API-1) only covers auth POST paths and SecurityConfig/RateLimiter were out of scope. Feed/detail send `Cache-Control: no-store`.
- Swipes are not audited (not in PLAN).

## D-014 Verification API details (2026-11-10, builder-API-3)
- Claim is idempotent for the holder (re-claim returns 200, no second audit); a case held by someone else is 409 CASE_ALREADY_ASSIGNED; a CLOSED case (assign or decide) is 409 INVALID_STATE_TRANSITION; deciding on a case not assigned to the caller is 409 CASE_NOT_ASSIGNED_TO_YOU. Claim and decision use conditional UPDATEs (status/assignee in the WHERE), so concurrent callers cannot both win.
- Self-review (caller is the request's recipient) is checked first, before any write, on assign and decide: 403 SELF_REVIEW_FORBIDDEN plus an ACCESS_DENIED audit row (details resource/reason/action). That audit survives because the service uses `noRollbackFor = ForbiddenException` and nothing else was written. `canDecide` = ASSIGNED && caller is assignee && caller is not the recipient.
- Decision validation (422 VALIDATION_FAILED with errors[]): rationale 10-2000 after trimming, messageToRecipient required (non-blank) for REJECT/REQUEST_CHANGES, max 2000. One decision per case (DB UNIQUE); decisions have no update/delete code path. A decision audits VERIFICATION_DECIDED {decision, caseId, requestId} and CASE_CLOSED {outcome}.
- Queue order: UNASSIGNED and MINE oldest-first (opened_at ASC, id), CLOSED and ALL newest-first (DESC). Keyset cursor = base64url(openedAt|id); bad cursor or unknown `queue` = 400 MALFORMED_REQUEST. MINE = ASSIGNED to the caller.
- Withdrawal closes any non-closed case with outcome WITHDRAWN (CASE_CLOSED audited). Case opening is idempotent per (request, submissionNumber) via ON CONFLICT DO NOTHING.
- view-url: delegates to `EducationReviewFacade.issueDocumentViewUrl`, which owns the DOCUMENT_VIEW_URL_ISSUED audit (one row per issuance) and 404 for a foreign document; response is `Cache-Control: no-store`; TTL = kyro.documents.view-url-ttl (5m). Verification adds no second audit row for it.
- Case detail `decisions` = decisions across all cases of the request, newest first.
- Tests: until API-2 merges, test sources contain `FallbackEducationFacadeConfig` (scanned @Configuration, `@ConditionalOnMissingBean(EducationReviewFacade.class)`, JDBC stand-in over V2 tables). It backs off when API-2's bean exists. If API-2 registers its facade via an unusual path and both beans appear, delete that file. Without any facade bean (API-2 merged without a bean, or the fallback removed) the context fails to start, which is intended.

## D-015 Education API details (2026-11-10, builder-API-2)
- Persistence is JdbcTemplate behind domain port EducationStore (no JPA). PATCH compares client version with a FOR UPDATE row (409 STALE_VERSION) then updates WHERE version=?; every update bumps version. Transitions: UPDATE WHERE id/status/version + history row + REQUEST_STATUS_CHANGED audit in one tx. Document changes do not bump request version.
- PATCH: null/absent fields unchanged (optional fields cannot be cleared, see D-010). Currency must be INR (422 VALIDATION_FAILED, amount.currency). Completeness only checked at submit (422 REQUEST_INCOMPLETE; fields documents, story, dueDate, ...).
- ACCESS_DENIED audit (entity FUNDING_REQUEST, details resource/reason NOT_OWNER) is written in REQUIRES_NEW so it survives the 404 rollback; unknown ids give 404 with no audit. Document audits use entity REQUEST_DOCUMENT with details.requestId.
- Uploads: type/size validated at upload-url (422 DOCUMENT_INVALID); 11th active document gives 422 DOCUMENT_LIMIT_EXCEEDED. complete is idempotent for UPLOADED; on failure (missing object, size/type mismatch, bad magic bytes) REJECTED + DOCUMENT_REJECTED audit are committed, object deleted, then 422 DOCUMENT_INVALID. DELETED documents are hidden everywhere.
- reviewerFeedback = latest note of the current CHANGES_REQUESTED/REJECTED status, else null. Facade applyDecision requires IN_REVIEW and markInReview SUBMITTED (else 409 INVALID_STATE_TRANSITION). Withdraw publishes EducationRequestWithdrawn from every withdrawable status.
- Dev seed EducationDevSeeder (@Order(10), local, needs kyro.seed.enabled): 3 APPROVED fictional requests for recipient.demo with 4 history rows each; no documents seeded.

## D-016 E2E-1 cross-cutting fixes (2026-11-10, builder-E2E-1)
Small fixes made outside the owned paths to get the slice working live. None weakens a security or privacy control; OpenAPI contract unchanged (no client regeneration).
- **API Accept handling (apps/api/build.gradle.kts):** the spring generator emitted `produces = { "application/problem+json" }` on operations whose only response body is an error (all 204 ones, e.g. `POST /auth/verify-email`). Any client sending `Accept: application/json` (Playwright request API, axios, curl -H) got a 500 `HttpMediaTypeNotAcceptableException`. The `openApiGenerate` post-processing step now strips that lone `produces`. Browsers (`Accept: */*`) were unaffected.
- **Admin dev origin (apps/admin/next.config.ts):** `allowedDevOrigins: ['127.0.0.1']`. D-009 opens admin at 127.0.0.1:3001, but Next 16 dev blocks /_next resources for a non-localhost origin, so the page never hydrated and the login form submitted natively. Dev only; production builds unaffected.
- **Web font (apps/web):** `next/font/google` replaced by `@fontsource-variable/inter` (same as admin; offline-safe builds and Docker). `globals.css` uses `'Inter Variable'`.
- **infra:up (root package.json):** `docker compose up -d --wait` exits non-zero on current Docker Compose because the one-shot `minio-init` exits (0). Script is now `up -d --wait postgres minio mailpit && up minio-init`.
- **Root test script:** `pnpm test` now excludes `@kyro/e2e` (`--filter=!@kyro/e2e`), because `@kyro/e2e test` is Playwright and needs a running stack. Use `pnpm test:e2e` (or `pnpm --filter @kyro/e2e test`). Added `pnpm smoke`.
- **Compose overridable values:** `MINIO_API_CORS_ALLOW_ORIGIN` reads `KYRO_CORS_ORIGINS` (default unchanged: `http://localhost:3000,http://127.0.0.1:3001`); docker-compose.apps.yml host ports read `KYRO_API_PORT`, `KYRO_WEB_PORT`, `KYRO_ADMIN_PORT` (defaults unchanged). Needed when 3000 is occupied.
- **Tests:** `ObjectStorageIT` HTTP calls now go through a retrying helper (IOException only, status codes never retried), fixing the intermittent "HTTP/1.1 header parser received no bytes" (also seen on the happy-path PUT, not only the wrong-content-type test). Removed test-only `FallbackEducationFacadeConfig` (real facade exists).
- **Smoke/e2e conventions:** list endpoints cap `limit` at 50 (422 above). Smoke/e2e find the new case via `queue=ALL` (newest first), not UNASSIGNED (oldest first, would page out on a reused database). Auth rate limits (login 10/min, register 5/min per IP) are overridable with `KYRO_AUTH_LOGIN_RATE_PER_MINUTE` / `KYRO_AUTH_REGISTER_RATE_PER_MINUTE` / `KYRO_AUTH_RESEND_RATE_PER_MINUTE`; the e2e workflow raises them.
- **Follow-ups, not implemented (as instructed):** rate limiting for discovery endpoints (D-013); a report-request endpoint (D-011).
