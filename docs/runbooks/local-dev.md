# Local development runbook

KYRO is a sandbox: fictional data, no real money. Everything below runs on your machine.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Node.js | 24+ | `.nvmrc` |
| pnpm | 12 | `corepack enable` or `npm i -g pnpm@12` |
| JDK | 25 or newer (27 works) | Gradle wrapper (9.9.0) is in the repo, no Gradle install needed |
| Docker | Docker Desktop (Windows/macOS) or Docker Engine + compose plugin (Linux) | Start Docker Desktop first on Windows |

Ports used: 3000 (web), 3001 (admin), 8080 (API), 5433 (Postgres), 9000 (MinIO API), 9001 (MinIO console), 1025/8025 (Mailpit SMTP/UI).

## 1. Install and start infrastructure

Git Bash / Linux / macOS:

```bash
pnpm install
pnpm infra:up          # postgres, minio (+ private bucket), mailpit
```

PowerShell:

```powershell
pnpm install
pnpm infra:up
```

`infra:up` waits for postgres, minio and mailpit to be healthy, then runs the one-shot `minio-init` that creates the private bucket `kyro-documents-local`.

## 2. Start the API (profile `local`, seeds demo data)

```bash
./gradlew :apps:api:bootRun        # Linux, macOS, Git Bash
```

```powershell
.\gradlew.bat :apps:api:bootRun    # PowerShell
```

Flyway runs V1-V9 on startup. Ready when `http://localhost:8080/actuator/health` returns `{"status":"UP"...}`.

## 3. Start web and admin (two more terminals)

```bash
pnpm --filter @kyro/web dev        # http://localhost:3000
pnpm --filter @kyro/admin dev      # http://127.0.0.1:3001  (use 127.0.0.1, see "Cookie isolation")
```

Both proxy `/api/*` to the API (`KYRO_API_ORIGIN`, default `http://localhost:8080`).

## 4. Seed accounts

Password for every seed account: `KyroLocal!2026`.

| Email | Roles | Use |
|---|---|---|
| `admin@kyro.local` | ADMIN, VERIFIER | admin app, audit log |
| `verifier@kyro.local` | VERIFIER | claim and decide cases |
| `donor.demo@kyro.local` | DONOR | swipe feed, saved list |
| `recipient.demo@kyro.local` | RECIPIENT | owns 3 seeded, already approved fictional requests |

Verification and other emails are caught by Mailpit: http://localhost:8025. In profile `local` the API also logs the verification and password-reset links. Mails you will see there: "Verify your KYRO email address", "Reset your KYRO password", "Your KYRO password was changed", and "Update on your KYRO request" (approved / changes needed / not approved; it carries no internal rationale). The seeded users have already accepted the current policies.

### Admin two-step sign-in (MFA is enforced by default)

`KYRO_ADMIN_MFA_ENFORCED` defaults to `true`: VERIFIER and ADMIN accounts must verify a TOTP code after the password before the `/admin/*` API works (403 `MFA_REQUIRED`, or `MFA_ENROLLMENT_REQUIRED` when no authenticator is linked yet). Set `KYRO_ADMIN_MFA_ENFORCED=false` to switch it off locally.

- **Seeded staff** (`admin@kyro.local`, `verifier@kyro.local`) already have a confirmed authenticator with the dev-only base32 secret `KYROLOCALDEVTOTPAAAAAAAAAAAAAAAA`. Add it to any authenticator app, or compute a code (SHA-1, 6 digits, 30 s): `node -e "import('./scripts/smoke/totp.mjs').then(async m => console.log(await m.nextTotp(m.DEV_TOTP_SECRET)))"`.
- **A code can be used once** (replay protection). Codes of the previous and next 30 s step are accepted, so two MFA logins of the same user need two different steps: wait for the next step, or use the adjacent one. `scripts/smoke/totp.mjs` and `e2e/support/helpers.ts` do this automatically and share the last used step in a temp file (`kyro-totp-state.json` in the OS temp dir), so `pnpm smoke` and `pnpm test:e2e` can run back to back.
- **A new staff user** is provisioned with SQL during the beta (there is no role-management API yet): register normally, then `docker compose exec postgres psql -U kyro -d kyro -c "INSERT INTO user_roles (user_id, role_code) SELECT id, 'VERIFIER' FROM users WHERE email = 'someone@kyro.local'"`. At the next admin login they are sent to `/mfa/setup` (QR code or manual key, confirm a code, save the 10 one-time recovery codes).
- **Reset a lost authenticator:** an ADMIN calls `POST /api/v1/admin/users/{userId}/mfa/reset` (CSRF header and an MFA-verified ADMIN session needed); the user's sessions end and they enrol again at the next login.

## 5. Try the slice by hand

1. http://localhost:3000/register, register as a recipient (new email), open the verification mail in Mailpit, click the link, log in.
2. Create a tuition request, upload a PDF/JPEG/PNG (the browser PUTs straight to MinIO), submit.
3. http://127.0.0.1:3001/login as `verifier@kyro.local`: queue, open the case, Claim, View document, Approve.
4. http://localhost:3000/discover as `donor.demo@kyro.local`: the request is the top card. Pass, Save or Interested with buttons, drag, or keyboard (Left, Right, S). See http://localhost:3000/saved.
5. A reviewer cannot review their own request (403 `SELF_REVIEW_FORBIDDEN`, audited).

Phase 5 flows to try by hand:

- **Request changes / reject / withdraw.** In the admin case form choose Request changes (a message to the recipient is required), then as the recipient edit and resubmit (case #2). Rejected requests are read-only. A recipient can withdraw until the request is rejected; withdrawing an approved request cancels its pledges.
- **Pledges (non-binding, no money).** As `donor.demo@kyro.local` open a request detail and use "Pledge (non-binding)"; the figures show Pledged separately, Confirmed stays 0. See http://localhost:3000/pledges ("My pledges") to cancel.
- **Report and hold.** As a donor use "Report this request"; as staff open http://127.0.0.1:3001/reports, claim, resolve as "Action taken" with "Hide request from discovery". The request leaves the feed until an ADMIN releases the hold on the report page.
- **Password reset.** http://localhost:3000/forgot-password, open the Mailpit link, choose a new password. Changing or resetting a password signs out every other session (they get 401 and the web app returns to log in).
- **Clear an optional field.** Edit a draft, empty "Fee reference" or "Course", save, reload: it stays empty.
- **Consent re-acceptance.** If a user's latest accepted Terms/Privacy version differs from the current one, a blocking dialog asks to accept. Simulate it: `INSERT INTO consent_records (user_id, policy_type, policy_version) SELECT id, t, '2025-01-01' FROM users, (VALUES ('TERMS'), ('PRIVACY')) AS v(t) WHERE email = '...'` (the table is append-only).

## 6. Automated checks

With the stack above running:

```bash
pnpm smoke                                   # node scripts/smoke/slice.mjs, API-level, exits 0 on success
pnpm --filter @kyro/e2e exec playwright install chromium   # once
pnpm test:e2e                                # Playwright: slice via web+admin, axe, 320px
```

Environment overrides: `API_URL`, `MAILPIT_URL` (smoke); `E2E_WEB_URL`, `E2E_ADMIN_URL`, `E2E_MAILPIT_URL` (Playwright).
The smoke script's self-review step grants VERIFIER to a throwaway user through `docker compose exec postgres psql`; set `SMOKE_SKIP_SELF_REVIEW=1` to skip it.

The smoke script (about 175 checks) covers the slice plus MFA (MFA_REQUIRED before verify, staff enrolment, replay refusal), pledges (idempotent replay, key reuse 422, duplicate 409, cap 409, pledged vs confirmed), reports and holds (claim, resolve, feed exclusion, VERIFIER cannot release, ADMIN release), the 6th report in a minute (429), PATCH `clearFields`, withdrawal cancelling pledges, and password change/reset revoking other sessions. Playwright (28 tests) adds the review loops, pledge UI, report-to-hold UI, password reset, consent re-acceptance and admin MFA setup, with axe (no serious/critical) on the new pages and 320px checks on the pledge detail and `/pledges`.

Rate limits: auth endpoints are limited per IP and minute (login 10, register 5, resend 3) and Phase 5 adds per-rule limits (`kyro.rate-limits.rules`: for example password reset 3, report 5, pledge 10, MFA verify 5 per minute). The smoke script waits out a 429 on auth paths once and asserts the report limit itself. To run smoke and Playwright back to back, start the API with higher limits (keep the report limit at its default):

```bash
KYRO_AUTH_LOGIN_RATE_PER_MINUTE=1000 KYRO_AUTH_REGISTER_RATE_PER_MINUTE=1000 KYRO_AUTH_RESEND_RATE_PER_MINUTE=1000 \
KYRO_SESSION_REVALIDATE_INTERVAL=0s KYRO_RATE_PASSWORD_RESET=1000 KYRO_RATE_PASSWORD_RESET_CONFIRM=1000 \
KYRO_RATE_PASSWORD_CHANGE=1000 KYRO_RATE_MFA_VERIFY=1000 KYRO_RATE_MFA_ENROLL=1000 \
KYRO_RATE_PLEDGE=1000 KYRO_RATE_PLEDGE_CANCEL=1000 ./gradlew :apps:api:bootRun
```

```powershell
$env:KYRO_AUTH_LOGIN_RATE_PER_MINUTE=1000; $env:KYRO_AUTH_REGISTER_RATE_PER_MINUTE=1000; $env:KYRO_AUTH_RESEND_RATE_PER_MINUTE=1000
$env:KYRO_SESSION_REVALIDATE_INTERVAL='0s'; $env:KYRO_RATE_PASSWORD_RESET=1000; $env:KYRO_RATE_PASSWORD_RESET_CONFIRM=1000
$env:KYRO_RATE_PASSWORD_CHANGE=1000; $env:KYRO_RATE_MFA_VERIFY=1000; $env:KYRO_RATE_MFA_ENROLL=1000
$env:KYRO_RATE_PLEDGE=1000; $env:KYRO_RATE_PLEDGE_CANCEL=1000; .\gradlew.bat :apps:api:bootRun
```

`KYRO_SESSION_REVALIDATE_INTERVAL` (default 60s) is how often a session is re-checked for suspension or a changed password; `0s` makes revocation immediate, which the tests rely on. The Playwright specs run Docker (`docker compose exec postgres psql`) from the repo root to grant roles and age consent records, so run them from a checkout with the compose stack up.

Other quality gates: `pnpm lint`, `pnpm typecheck`, `pnpm test` (unit tests; excludes Playwright), `./gradlew build` (needs Docker for Testcontainers).

## 7. Everything in containers (optional)

```bash
docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile apps up -d --build
```

Builds and starts api, web and admin next to the infra. Host ports can be moved with `KYRO_API_PORT`, `KYRO_WEB_PORT`, `KYRO_ADMIN_PORT`. If you move the web port, also list its origin for MinIO CORS (see troubleshooting). The API image build downloads Gradle and dependencies inside the container, so it needs internet access to services.gradle.org and github.com.

## 8. Reset

```bash
docker compose down -v      # deletes the database and bucket volumes
pnpm infra:up
```

Seed data is recreated on the next API start. Add `-f docker-compose.yml -f docker-compose.apps.yml --profile apps` when the containerised apps are running.

## Troubleshooting

**Port already in use** (`EADDRINUSE :::3000`, `Port 8080 was already in use`). Another dev server (often a leftover from another worktree) holds it. Windows: `Get-NetTCPConnection -LocalPort 3000 -State Listen | % { Get-Process -Id $_.OwningProcess }`; Linux/macOS: `lsof -i :3000`. Stop it, or run on another port and tell the tools: `pnpm --filter @kyro/web exec next dev --port 3100` plus `E2E_WEB_URL=http://localhost:3100` and add the origin to MinIO CORS (below).

**JDK / Gradle.** `./gradlew --version` must show a JDK 25+ (the build uses `options.release = 21`, so newer JDKs are fine). Set `JAVA_HOME` if Gradle picks an old one. The first run downloads Gradle 9.9.0 and dependencies. Windows line endings are normalised by `.gitattributes` (`gradlew`, `*.sh` stay LF); if `./gradlew` reports `bad interpreter`, run `git add --renormalize .`.

**MinIO CORS / upload fails in the browser** (console shows a CORS error on the `PUT` to `localhost:9000`, or the uploader shows an error). MinIO allows the origins in `MINIO_API_CORS_ALLOW_ORIGIN` (compose default `http://localhost:3000,http://127.0.0.1:3001`). For another origin:

```bash
KYRO_CORS_ORIGINS="http://localhost:3000,http://127.0.0.1:3001,http://localhost:3100" docker compose up -d minio
```

Presigned URLs carry the host from `KYRO_S3_PUBLIC_ENDPOINT` (default `http://localhost:9000`), which the browser must be able to reach. The API talks to MinIO through `KYRO_S3_ENDPOINT` (inside compose: `http://minio:9000`).

**Cookie isolation.** Cookies are per host, not per port. Open web on `localhost:3000` and admin on `127.0.0.1:3001` so a recipient session and a verifier session never overwrite each other in one browser. The admin dev server allows that origin (`allowedDevOrigins`); without it, `127.0.0.1` pages render but never hydrate and the login form submits natively.

**429 RATE_LIMITED on login or register.** In-memory per-IP limit, window is one minute. Wait, restart the API, or raise the limits (section 6).

**Login says EMAIL_NOT_VERIFIED.** Open Mailpit (http://localhost:8025), click the link in the newest mail for that address, or use a seed account.

**`pnpm infra:up` fails.** Docker not running (start Docker Desktop). A previous stack on the same ports: `docker compose down`. Stale volume from an older schema: `docker compose down -v` (migrations V1-V5 are edited in place until the first deployment).

**Testcontainers tests fail to start** (`./gradlew build`). Docker must be running; the first run pulls Postgres 17 and the pinned MinIO image (`bitnamilegacy/minio`, D-004).

**Containers cannot download Gradle** (`./gradlew` inside `docker build` times out on github.com). Corporate or sandbox networks may block it. Build on the host (`./gradlew :apps:api:bootJar`) and run the jar, or fix the network.

## Known gaps (follow-ups)

- No rate limiting on discovery endpoints (D-013): feed, detail, swipe are unthrottled.
- No "report request" endpoint (blueprint OPS-002 / D-011).
- Optional request fields cannot be cleared through PATCH (D-010).
