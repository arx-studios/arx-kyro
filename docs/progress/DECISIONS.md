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

## D-017 Phase 5 contract and migration renumbering (2026-11-10, builder-CON-2)
- Phase 5 adds 21 operations (total 51) only under NEW tags (Funding, Reports, Operations, Account, Consents, Mfa) because codegen is interfaceOnly + useTags: a new operation on an existing tag would break its implementing controller; new tags yield interfaces with no implementor. New properties on existing schemas are optional-only (`CurrentUser.mfa`, `EducationRequestUpdate.clearFields`) so generated constructors do not change. `KyroProperties` is positional and untouched; new config uses new prefixes and per-module `@ConfigurationProperties`.
- New problem codes: 401 SESSION_REVOKED; 403 MFA_REQUIRED, MFA_ENROLLMENT_REQUIRED; 409 ACTIVE_PLEDGE_EXISTS, PLEDGE_EXCEEDS_REMAINING, REPORT_ALREADY_OPEN, REPORT_ALREADY_ASSIGNED, REPORT_NOT_ASSIGNED_TO_YOU, MFA_ALREADY_ENROLLED; 422 PLEDGE_NOT_ALLOWED, IDEMPOTENCY_KEY_REUSED, MFA_CODE_INVALID, CURRENT_PASSWORD_INCORRECT (TOKEN_INVALID reused). The shared `Problem*` response descriptions were not edited; codes are documented per operation.
- Supersedes D-003 numbering: V6 funding, V7 operations, V8 identity security, V9 append-only triggers; payments and ledger move to V10+ (Flyway outOfOrder=false, no gaps).
- Details: `rate-limits.rules[].per-minute` values are quoted placeholders in YAML (an unquoted `${..}` inside a flow mapping does not parse). Pledge amounts reuse `Money` (amountMinor >= 1). `funding_request_progress` has one row per (request, currency) with at least one pledge row; LEFT JOIN it.

## D-018 WEB-4 account/recipient UI details (2026-11-10, builder-WEB-4)
- Closes the D-010 gap: `toUpdatePayload(values, version, initial)` lists optional fields the user emptied (that had a value) in `clearFields`; untouched empty fields are omitted; set/changed fields are sent as values. No faked values.
- Route mapping: WEB-1 used real route groups, so the plan's owned paths apply as-is: `(auth)/forgot-password`, `(auth)/reset-password`, `(account)/profile` (change password form added), `(recipient)/recipient/requests/[id]` (funding panel).
- ConsentGate is rendered from `app/layout.tsx` after AppShell (inside Providers). It calls GET /me/consents only when signed in and not on auth paths (/login, /register*, /verify-email, /forgot-password, /reset-password); blocking Dialog (no Escape/backdrop close), Accept posts the current versions reported by the server. UI-only gate; server enforcement stays Phase 6.
- Forgot-password shows the same neutral success on any outcome except RATE_LIMITED. Reset success message says all sessions were signed out. Change-password maps CURRENT_PASSWORD_INCORRECT to the current-password field.
- Query keys defined locally (lib/queryKeys.ts is WEB-3's): `consentStatusKey` in features/account/consents.ts, `recipientFundingKey` in features/education/useFundingSummary.ts. Recipient funding panel: Target / Confirmed / Still needed (= remainingMinor) with pledged in a separate box ("Pledges are not payments"); shown only when APPROVED.

## D-019 Funding pledge details (2026-11-10, builder-FUND-1)
- Pledge guard follows P5.4 with one addition: after locking the campaign row (`FOR UPDATE`) the request is re-read from `discovery_public_requests`; a request withdrawn/held while waiting gives 404. The withdrawal listener takes the same campaign lock before cancelling, so create vs withdraw is serialised.
- Idempotent replay (same donor+key, same request+amount) returns 200 even when the pledge has since been cancelled. Same key with a different request or amount: 422 IDEMPOTENCY_KEY_REUSED. A DuplicateKeyException on insert is mapped to ACTIVE_PLEDGE_EXISTS / IDEMPOTENCY_KEY_REUSED (the same key on a different request cannot be serialised by a campaign lock).
- Missing `Idempotency-Key` gave 500 in `GlobalExceptionHandler` (MissingRequestHeaderException unhandled). FUND-1 handles it with a controller-local `@ExceptionHandler` returning 400 MALFORMED_REQUEST. SEC-2/common owner may add the same mapping globally; the local handler can then be removed. A malformed key (pattern/size) is 422 VALIDATION_FAILED via existing bean-validation handling.
- Summary is permitAll; `viewerPledge` is read from the session (donor role only). Summary/pledge responses are `Cache-Control: no-store`. Pledges on cancelled/held requests stay visible to their donor in My pledges (`request` null when not public).
- Min pledge and currencies come from `FundingProperties` (`kyro.funding.*`, bound by `@ConfigurationPropertiesScan`). The funding bean for ACCESS_DENIED is named `pledgeAccessAuditor` to avoid clashing with education's `AccessAuditor`.
- Discovery LEFT JOINs `funding_request_progress` (on request_id and currency) in feed, detail and saved; `confirmedMinor` stays 0.

## D-020 WEB-3 donor pledge/report UI details (2026-11-10, builder-WEB-3)
- Funding panel semantics (supersedes the D-011 remaining formula): Outstanding need = target - confirmed; Confirmed drives the progress bar; Pledged (not yet paid) is a separate line (with active pledge count from getFundingSummary) and never reduces outstanding. Feed/saved cards show confirmed always, and "pledged (not yet paid)" only when > 0. Detail page uses detail funding until getFundingSummary loads; a failed summary disables pledging with a retry.
- Pledge dialog: Idempotency-Key is crypto.randomUUID() created when the dialog opens and reused on retries; it is replaced only if the donor changes the amount after an attempt (same key with a different body would be 422 IDEMPOTENCY_KEY_REUSED). Client checks: >= 1 whole unit, <= pledgeableMinor, required acknowledgement checkbox. No payment fields. Summary/detail/myPledges/feed are invalidated on pledge settle (also on failure) and cancel.
- Report dialog: radio reasons, details required for OTHER (max 2000). 409 -> "already reported", 429/RATE_LIMITED -> wait message. Anonymous users get a login link. Report action is on the detail page only (no card overflow). /pledges also allows cancelling ACTIVE pledges. Nav: "My pledges" (DONOR). FUND-1 and OPS-1 live check still pending.

## D-021 ADM-2 admin MFA, dashboard and reports details (2026-11-10, builder-ADM-2)
- `qrcode` (+ `@types/qrcode`) added to apps/admin only; the QR is a local data URL (no network, no QR service). Secret, QR, and recovery codes are held in component state only (plain `api.POST` calls, not useQuery/useMutation), never in cache/storage/URL; QueryClient `gcTime: 0` for queries and mutations.
- MFA pages `/mfa/setup` and `/mfa/verify` live outside the `(console)` group (own `MfaGate`: login redirect, staff-role check, routes to the correct step, or to `/` when MFA is not required/already verified). RoleGuard redirects `mfa.required && !mfa.verified` to verify (enrolled) or setup, with `?next=`. A global QueryCache/MutationCache handler hard-redirects on 403 `MFA_REQUIRED` / `MFA_ENROLLMENT_REQUIRED`.
- Setup starts enrollment on an explicit "Begin setup" click (avoids creating a new secret on every mount/StrictMode). "Continue" stays disabled until the "I have saved these recovery codes" checkbox is ticked; it then refetches `/me` and navigates.
- `/` is now the dashboard (Operations `getAdminDashboard`); `/queue` unchanged. Nav: Dashboard, Verification queue, Reports, Audit log.
- ADMIN-only Release hold: the control is not rendered for non-ADMIN (UI convenience; API enforces). Resolve form: hold checkbox disabled and cleared unless ACTION_TAKEN; note 10 to 2000.

## D-023 OPS-1 reporting read exception and moderation holds (2026-11-10, builder-OPS-1)
- **Reporting read exception:** `operations` reads `discovery_public_requests`, `funding_requests` and `verification_cases` through read-only SQL in `JdbcReportRepository` (no Java imports of those modules, no writes to their tables), the same idea as discovery reading a view. It writes only `user_reports` and `request_moderation_holds`.
- Reportable = present in `discovery_public_requests` (approved and not on hold), else 404. Own request -> 422 VALIDATION_FAILED; OTHER needs non-blank details (422). Duplicate unresolved report by same reporter -> 409 REPORT_ALREADY_OPEN (INSERT ... ON CONFLICT DO NOTHING on the V7 partial unique index).
- Holds: placed only via resolve with ACTION_TAKEN + holdRequest=true (DISMISSED + hold, or note outside 10-2000 chars after strip -> 422). Idempotent when a hold is active (no second row, no second audit). Held requests drop out of feed/detail/saved/pledge creation via the V7 view. Release is ADMIN-only (route matcher), 404 when no active hold.
- Self-review guard on assign/resolve: staff who own the reported request OR filed the report get 403 SELF_REVIEW_FORBIDDEN plus ACCESS_DENIED audit (noRollbackFor ForbiddenException). `canResolve` is false for them. GET is not blocked. Audit entity types: USER_REPORT, FUNDING_REQUEST (holds); details never include report text or reporter identity.
- Reporter identity is exposed only on admin endpoints; no recipient-facing endpoint returns reports.
- Rate limit for POST /reports is SEC-2's config rule `report-create`; the IT for the 6th request is `@Disabled("needs SEC-2")`, to be enabled when rebasing after SEC-2.

## D-022 Parallel decision IDs (2026-11-10, orchestrator)
With 7+ builders in parallel, D-numbers collided (D-019 ×3). From now on builders title their entry `## D-<TASKID> ...`, for example `## D-SEC-2`. The orchestrator assigns the numeric ID at merge.

## D-024 Account recovery, consents, notifications, clearFields (2026-11-10, builder-ACCT-1)
- Password reset: token = 32 random bytes base64url, only SHA-256 stored (`password_reset_tokens`), TTL `kyro.password-reset.token-ttl`. Request always 202; only ACTIVE users get a token/mail/audit. Confirm consumes atomically, sets hash, bumps `credentials_version`, consumes all other outstanding tokens, sends a "password changed" mail; non-ACTIVE or bad/expired/used token -> 422 `TOKEN_INVALID`. Rate limiting is SEC-2's config-driven limiter (not implemented here).
- New `AccountMailer` interface (+ `SmtpAccountMailer`) instead of extending `VerificationMailer`, so SEC-2's `TestMailConfig.CapturingMailer` still compiles. Tests capture mail with a `@Primary` capturing `JavaMailSender` (`identity.account.CapturingMailSenderConfig`).
- Change password: wrong current -> 422 `CURRENT_PASSWORD_INCORRECT` with a `LOGIN_FAILED` audit (reason CURRENT_PASSWORD_INCORRECT). Success bumps `credentials_version`, then the controller sets `SessionAttributes.CREDENTIALS_VERSION` on the caller's session to the new value and calls `changeSessionId()`. `UserAccount` is unchanged; `UserAccountRepository.credentialsVersion(userId)` is available for SEC-2's login to seed the session attribute.
- Consents: `reacceptRequired` = latest accepted TERMS or PRIVACY differs from `kyro.policy.*` (or missing). POST requires current versions (422 `CONSENT_VERSION_MISMATCH`) and appends rows only for types whose latest accepted version differs (idempotent, no history growth on repeat), audit `CONSENT_ACCEPTED` only when rows were added.
- Notifications: `notification.application.RequestStatusEmailNotifier` listens `AFTER_COMMIT` for `EducationRequestStatusChanged` (toStatus APPROVED, CHANGES_REQUESTED, REJECTED only), reads the address via `UserDirectory.findRecipient`, mail has title, plain-words status and `${web}/recipient/requests/{id}` only. Failures logged WARN, never rethrown. Switch: `kyro.notifications.email-enabled`. Published from `RequestTransitions.apply` for every transition (inside the transaction).
- clearFields: cleared field -> NULL (`fee_reference_normalized` too, via the existing store update), also-given value -> 422 `VALIDATION_FAILED`. A non-clearable name (e.g. `title`) is rejected by the generated enum at deserialisation (400 or 422 depending on GlobalExceptionHandler; test accepts either). Audit details gain `clearedFields`.

## D-025 Security hardening choices (2026-11-10, builder-SEC-2)
- TOTP: SHA-1, 6 digits, 30s, +-`allowed-skew-steps`; replay = code step <= `last_used_step` refused, advanced with a conditional UPDATE. Secrets AES-256-GCM (`common.security.SecretCipher`, key id v1; blank key fails startup outside local/test). Recovery codes `XXXXX-XXXXX` (alphabet without 0/O/1/I), SHA-256 hashed, single use via conditional UPDATE. MFA failure audits are written outside the failing transaction.
- `MfaProperties`, `SessionProperties`, `RateLimitProperties`, `SecretCipher` live in `common.security` (devseed may only depend on common). Common-defined ports: `SessionValidator`, `MfaEnrollmentLookup` (identity implements), `SecurityEventSink` (audit implements).
- Session revalidation treats only SUSPENDED (or a missing user) and a changed `credentials_version` as invalid; a session without the version attribute counts as version 0. Revocation audits `SESSION_REVOKED`.
- Rate limiting: rules from `kyro.rate-limits.rules` (IP|USER keys, fixed 1-minute window, `Retry-After` = seconds left); the three auth limits remain built-in and ignore `enabled=false`. Covers discovery feed, swipes, pledge create/cancel, reports, password reset/change, MFA, uploads, view-url.
- Forwarded IP: relies on `server.forward-headers-strategy=native`; Tomcat's RemoteIpValve only honours X-Forwarded-For from `server.tomcat.remoteip.internal-proxies` (default private ranges). INF-2 should set that to the ALB subnet in staging. Default `none` ignores the header (tested).
- ACCESS_DENIED audit for route-level admin 403s (entity type ENDPOINT, details resource+reason ROLE_MISSING|MFA_REQUIRED); 401s not audited.
- `GlobalExceptionHandler` now maps `MissingRequestHeaderException` to 400 MALFORMED_REQUEST (needed for Idempotency-Key).
- Test profile: `application-test.yml` enforces MFA, revalidates on every request, has a test AES key.

## D-026 Terraform AWS staging foundation (2026-11-10, builder-INF-2)
- **Versions:** Terraform `>= 1.10.0` (plan said >= 1.9; 1.10 is needed for S3-native state locking via `use_lockfile`, so no DynamoDB lock table). Validated with 1.13.5. AWS provider `~> 6.0` (resolved 6.72.0 in `envs/staging/.terraform.lock.hcl`, committed with hashes for linux/darwin/windows). Backend is `backend "s3" {}` (partial); real values come from a git-ignored `backend.hcl` (`backend.hcl.example`).
- **Layout:** `modules/{network,kms,s3_documents,ecr,rds,secrets,ecs_cluster,ecs_service,alb,edge,github_oidc,observability,ses}` (ecs_cluster is an extra small module for the cluster plus Service Connect namespace) and `envs/staging`. No credential-dependent data sources (only `aws_iam_policy_document`, which is local compute). Account id, region, AZs, certificate ARNs, CloudFront prefix list id are variables.
- **Offline plan:** provider flags `skip_credentials_validation`, `skip_requesting_account_id`, `skip_metadata_api_check`, `skip_region_validation` are tied to variable `offline_plan` (CI and local only). `terraform plan` refuses an s3 backend after `init -backend=false`, so the offline plan writes a git-ignored `backend_override.tf` selecting a local backend. Result against `staging.tfvars.example`: `Plan: 132 to add, 0 to change, 0 to destroy`. The only "read during apply" items are the `aws_iam_policy_document` data sources (pure computation, no AWS call) because they depend on unknown ids.
- **Single api task:** variable `api_desired_count` has a validation `== 1` (in-memory sessions and rate limits until Spring Session JDBC / shared limits). Do not relax without that change.
- **Security posture:** S3 documents bucket: public access blocked, BucketOwnerEnforced, versioning, SSE-KMS (CMK), policy denies non-TLS and non-KMS puts, CORS PUT from the web origin only, GuardDuty malware protection plan (tags; app gating is Phase 6). RDS Postgres 17 in isolated subnets with no internet route, encrypted, managed master secret, `rds.force_ssl=1`, deletion protection, 7-day PITR. Secrets Manager secrets created without values. ALB reachable only from the CloudFront origin-facing prefix list. WAF (Common, KnownBadInputs, SQLi, rate-based, admin hostname IP allowlist) on CloudFront in us-east-1. ECS tasks run in private subnets with no public IP.
- **GitHub OIDC:** `kyro-staging-tf-plan` trusted for `repo:<github_repo>:pull_request`, ReadOnlyAccess plus explicit denies on `secretsmanager:GetSecretValue`, `kms:Decrypt` and document objects; `kyro-staging-deploy` trusted for `repo:<github_repo>:environment:staging` only (ECR push, ECS UpdateService, PassRole to ecs-tasks). Nothing assumes either role in Phase 5; `github_repo` is a variable (example `arx-studios/arx-kyro`).
- **Staging profile (`application-staging.yml`):** ECS-format structured logs, native forwarded headers, `server.tomcat.remoteip.internal-proxies` limited to 10.x (the ALB), secure cookie, seed off, SMTP STARTTLS on 587 from `KYRO_MAIL_USERNAME/PASSWORD`, S3 endpoint and keys default to blank. Ties into D-025 (SEC-2 asked INF-2 to set internal-proxies).
- **`S3ObjectStorage`:** blank access key selects `DefaultCredentialsProvider` (task role); blank endpoint means no override and virtual-host addressing; blank public endpoint means the default presigner endpoint. MinIO local config is unchanged (static keys, override, path-style, WHEN_REQUIRED checksums). Package-private static builder helpers are unit-tested in `S3ObjectStorageBuilderTest`. The `ObjectStorage` port is unchanged.
- **Accepted scanner findings:** trivy HIGH/CRITICAL clean after documented inline `#trivy:ignore` on (a) `aws-ec2-no-public-egress-sgr` for ECS task egress 443 and 587 to 0.0.0.0/0 via the single NAT (AWS APIs and the SMTP relay), (b) `aws-elb-alb-not-public` for the CloudFront-origin ALB (security group admits only the CloudFront prefix list). Lower-severity ignores: S3 server access logging, CloudFront access logging (deferred, Phase 6).
- **Known gaps, documented in the runbook:** CloudFront edge IP becomes the rate-limit key (CloudFront ranges are not trusted proxies); no ALB origin-verify header; single NAT and single-AZ RDS (staging cost); S3 scan tag not yet enforced by the app.

## D-027 Phase 5 integration: MFA enforcement on and small cross-cutting fixes (2026-11-10, builder-E2E-2)
- **Admin MFA enforcement is now the default:** `kyro.mfa.admin-enforced: ${KYRO_ADMIN_MFA_ENFORCED:true}` (and `.env.example`). `KYRO_ADMIN_MFA_ENFORCED=false` still switches it off locally. Smoke and Playwright log staff in with password + TOTP (dev secret `KYROLOCALDEVTOTPAAAAAAAAAAAAAAAA`). Because a TOTP step is single use, `scripts/smoke/totp.mjs` and `e2e/support/helpers.ts` hand out the next unused step (waiting for the clock when needed) and share the last used step per secret in a temp file; Playwright caches signed-in staff sessions (`test-results/staff-*.json`) so specs do not burn steps.
- **Bugs fixed (no contract change, no security/privacy weakening):**
  1. `apps/api/build.gradle.kts`: D-016's strip of the lone generated `produces = { "application/problem+json" }` only matched the form with a trailing comma. Operations where `produces` is the last attribute (e.g. `POST /admin/requests/{id}/hold/release`, 204) still had it, so `Accept: application/json` clients got 500 `HttpMediaTypeNotAcceptableException`. Both forms are stripped now.
  2. `devseed/IdentityDevSeeder`: seeded users had no `consent_records`, so since ACCT-1 the web ConsentGate blocked every dev login (donor.demo etc.) behind the re-accept dialog. The seeder now inserts the current TERMS/PRIVACY versions when a seeded user has none (also back-fills existing local databases).
  3. `apps/admin/.../DashboardView.tsx`: tile links had `aria-label="Unassigned cases: 2. Open list"`, which differs from the visible text (axe `label-content-name-mismatch`, serious). The aria-label was removed (the visible label + number is the name).
  4. `funding/api/FundingController`: removed the controller-local `MissingRequestHeaderException` handler; `GlobalExceptionHandler` (SEC-2) maps it to 400 `MALFORMED_REQUEST`.
- **Observed, not changed:** after `POST /me/password` commits, another request of the SAME session that races in before the response (rotated session id + new `CREDENTIALS_VERSION`) can be treated as revoked (401). It is a millisecond window and only matters to concurrent requests from the changing browser; tests wait for the 204 first. Candidate hardening: set the new version in the same transaction/filter pass.
- Rate limits for runs: the e2e workflow raises `KYRO_AUTH_*`, password-reset(-confirm), password-change, MFA verify/enroll and pledge limits to 1000 and sets `KYRO_SESSION_REVALIDATE_INTERVAL=0s`; the report limit stays 5/min (smoke asserts the 429 on the 6th report).
