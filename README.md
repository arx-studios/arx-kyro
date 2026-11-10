# KYRO

Education-funding platform. This repo is the first vertical slice: a recipient registers, submits a tuition bill, an admin verifies it, and the approved bill appears in the donor swipe feed.

> **Sandbox only.** Fictional data, no real money, no payment providers, no wallets or pooled funds.

See `KYRO_startup_plan_v1.2.md` (product/architecture) and `docs/progress/PLAN.md` (build plan).

## Prerequisites

- Node 24 (`.nvmrc`) and pnpm 12 (`corepack enable` or `npm i -g pnpm`)
- Docker (Desktop) with Compose v2
- JDK 21+ (the API compiles with `--release 21`; Gradle wrapper is added by the API task)

## Quickstart

```sh
pnpm install
pnpm infra:up                                   # postgres, minio (+ bucket), mailpit
docker compose --profile tools run --rm flyway migrate   # apply Flyway V1-V5
./gradlew :apps:api:bootRun                     # API on :8080 (once API-1 is merged)
pnpm --filter web dev                           # web on http://localhost:3000
pnpm --filter admin dev                         # admin on http://127.0.0.1:3001
```

Copy `.env.example` to `.env` only if you need to override defaults.

Stop: `pnpm infra:down` (keep data) or `docker compose down -v` (wipe volumes).

Verify the schema: `docker compose exec -T postgres psql -U kyro -d kyro -v ON_ERROR_STOP=1 -f - < scripts/db/verify.sql`

## Ports

| Service | Host port | Notes |
|---|---|---|
| web (Next.js) | 3000 | `http://localhost:3000` |
| admin (Next.js) | 3001 | open at `http://127.0.0.1:3001` so its cookies stay separate from web |
| api (Spring Boot) | 8080 | `/api/v1/**`, `/actuator/health` |
| postgres | 5433 | db `kyro`, user `kyro`, password `kyro_local` |
| minio S3 / console | 9000 / 9001 | `kyro-minio` / `kyro-minio-secret`, bucket `kyro-documents-local` (private) |
| mailpit SMTP / UI | 1025 / 8025 | dev email inbox |

## Seed accounts (profile `local`, created by the API; all fictional)

Password for all: `KyroLocal!2026`

| Email | Roles |
|---|---|
| `admin@kyro.local` | ADMIN, VERIFIER |
| `verifier@kyro.local` | VERIFIER |
| `donor.demo@kyro.local` | DONOR |
| `recipient.demo@kyro.local` | RECIPIENT |

## Layout

`apps/api` (Spring Boot, Flyway migrations in `src/main/resources/db/migration`), `apps/web`, `apps/admin`, `packages/*` (shared TS), `infrastructure/`, `scripts/`, `docs/`.

Root scripts: `lint`, `typecheck`, `test`, `build`, `gen:client`, `spec:lint`, `infra:up`, `infra:down`.
