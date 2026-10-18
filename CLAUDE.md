# CLAUDE.md

## Git workflow

Default branch: main. Commit straight to local main after checks pass; no branches or PRs. Never push — the user pushes main themselves.

## Commits and versioning

Conventional Commits (feat:, fix:, chore:, docs:, ci:, refactor:, test:, perf:; ! or BREAKING CHANGE for breaking). They drive the version.

Versioning: semver, always 0.x.y. feat or breaking → minor, anything else → patch.
