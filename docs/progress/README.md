# KYRO Shared Agent Coordination

Every agent (planner or builder) MUST:
1. Read `../../KYRO_startup_plan_v1.2.md` (source of truth for product/architecture), `PLAN.md`, `TASKS.md`, and the latest entries of `LOG.md` before starting.
2. Claim a task in `TASKS.md` by setting Status to `IN_PROGRESS` and Owner to your agent name. Only touch files inside the directories your task owns.
3. Append a dated entry to `LOG.md` when you finish (or stop): what you built, files/dirs touched, how to run/verify, known gaps.
4. Record any architectural/contract decision in `DECISIONS.md` (ADR-lite). Never silently change a shared contract (OpenAPI spec, DB migrations, package names).
5. Mark the task `DONE` (or `BLOCKED` with reason) in `TASKS.md`.

Files:
- `PLAN.md` — phased build plan & work packages (owned by planner agents).
- `TASKS.md` — task board: ID | Title | Owner | Status | Owned paths | Depends on.
- `LOG.md` — append-only progress log, newest at bottom.
- `DECISIONS.md` — decisions & contract changes.

Hard constraints (from plan §16): no live payments, no real money flows, sandbox/fictional data only, no wallet/pooled funds. Money = integer minor units + ISO 4217 code.

## Worktree rule (orchestrator, 2026-11-10)
Builders write code in their own git worktree/branch (`task/<ID>`), and commit there. The orchestrator merges into `main`.
The coordination docs are SHARED LIVE in the main checkout: always read/write them at the absolute path `D:\kyro\docs\progress\` (NOT the copy inside your worktree), and do NOT commit docs/progress changes on your task branch.
