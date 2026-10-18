# CLAUDE.md

## Git workflow

Default branch: main. Commit straight to local main after checks pass; no branches or PRs. Never push — the user pushes main themselves.

## Project memory lives in docs/

Start every session by reading `docs/STATUS.md` (current state, next steps, TODOs, open questions),
then `docs/DECISIONS.md` and `docs/PLAN.md` as needed.

Every decision, plan change and TODO must be written to the repo and committed — nothing durable may
live only in a chat. In the same commit as the change:
- new or changed decision → append to `docs/DECISIONS.md` (next D-number, dated, with the why),
- plan change → update `docs/PLAN.md`,
- progress, new TODOs, results, open questions → update `docs/STATUS.md`.

## Commits and versioning

Conventional Commits (feat:, fix:, chore:, docs:, ci:, refactor:, test:, perf:; ! or BREAKING CHANGE for breaking). They drive the version.

Versioning: semver, always 0.x.y. feat or breaking → minor, anything else → patch.
