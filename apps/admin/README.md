# @kyro/admin

KYRO operations / verification console (Next.js App Router). Sandbox only: fictional data, no real money.

**Open it at `http://127.0.0.1:3001`** (not `localhost`), so its session cookie jar stays separate from the web app on `:3000`.

```
pnpm --filter @kyro/admin dev      # port 3001; needs the API on KYRO_API_ORIGIN (default http://localhost:8080)
pnpm --filter @kyro/admin lint | typecheck | test | build
```

Local sign-in (profile `local`): `verifier@kyro.local` or `admin@kyro.local`, password `KyroLocal!2026`.

## Routes

| Route | Purpose |
|---|---|
| `/login` | Session login (`POST /auth/session`), supports `?next=` (same-origin paths only) |
| `/` | Redirects to `/queue` |
| `/queue` | Tabs Unassigned / Mine / Closed / All, cursor "Load more" |
| `/cases/[caseId]` | Private request view, documents (audited view URLs), duplicate indicators, history, prior decisions, claim, decision form (only when `canDecide`) |
| `/audit` | Audit events, filter by entity id |

## Security notes

- The role guard (VERIFIER or ADMIN via `/me`) is a UI convenience. Every `/api/v1/admin/**` call is authorised by the API.
- Documents are opened only via `POST .../view-url`; the URL is used once and never stored in state, query cache or storage. Responses are `Cache-Control: no-store`; query `gcTime` is 0.
- Every decision needs a rationale; 403 `SELF_REVIEW_FORBIDDEN` and 409 `CASE_*` problems are shown as friendly alerts.
- `KYRO_API_ORIGIN` is read at build time by the rewrites (also a Docker build arg).
