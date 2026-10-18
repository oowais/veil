#!/usr/bin/env bash
# Next version from Conventional Commits since the last v0.* tag.
# Stays 0.x.y: breaking changes and feat bump minor, everything else patch.
# Prints "none" when there is nothing to release: HEAD is already tagged, or
# no commit since the tag changes the app for users (only docs/ci/test/chore,
# dev dependencies). Those wait for the next real release.
set -euo pipefail

last=$(git describe --tags --abbrev=0 --match 'v0.*' 2>/dev/null || true)
if [ -z "$last" ]; then
  echo "0.1.0"
  exit 0
fi
if [ -z "$(git rev-list "$last..HEAD")" ]; then
  echo "none"
  exit 0
fi

IFS=. read -r _ minor patch <<<"${last#v}"
# Squash merges list the branch commits in the body as "* feat: ...".
log=$(git log --format='%s%n%b' "$last..HEAD")
# User-facing: feat, fix, perf, refactor, revert, breaking, app dependencies
# (Dependabot's chore(deps); chore(deps-dev) is build tooling only).
if ! grep -qE '^(\* )?((feat|fix|perf|refactor|revert)(\([^)]*\))?|chore\(deps\)|[a-z]+(\([^)]*\))?!):|BREAKING CHANGE' <<<"$log"; then
  echo "none"
  exit 0
fi
if grep -qE '^(\* )?[a-z]+(\([^)]*\))?!:|BREAKING CHANGE' <<<"$log" || grep -qE '^(\* )?feat(\([^)]*\))?:' <<<"$log"; then
  echo "0.$((minor + 1)).0"
else
  echo "0.$minor.$((patch + 1))"
fi
