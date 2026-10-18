#!/usr/bin/env bash
# Markdown release notes from Conventional Commits since the last v0.* tag.
# Reads squash-merge bodies too ("* feat: ..."), so each branch commit counts.
# Skips merge commits, docs/ci/test/other chore noise and co-author trailers.
set -euo pipefail

last=${LAST_TAG:-$(git describe --tags --abbrev=0 --match 'v0.*' 2>/dev/null || true)} # LAST_TAG: for trying it out
range=${last:+$last..}HEAD

# One commit's change lines: a squash merge's "* type: ..." bullets (its PR
# title only summarises them), else its own subject.
changes() {
  git log --no-merges --format='%x00%s%n%b' "$@" | awk '
    function flush() { if (n) { for (i = 1; i <= n; i++) print b[i] } else if (subj != "") print subj; n = 0 }
    /^\x00/ { flush(); subj = substr($0, 2); next }
    /^\* [a-z]+(\([^)]*\))?!?: / { b[++n] = substr($0, 3) }
    END { flush() }' | grep -E '^[a-z]+(\([^)]*\))?!?: ' || true
}

lines=$(changes "$range")
# A squash body also lists branch commits that an earlier release already had.
if [ -n "$last" ]; then
  lines=$(grep -vxF -f <(changes "$last") <<<"$lines" || true)
fi

section() { # title, type regex
  local items
  items=$(grep -E "^($2)(\([^)]*\))?!?: " <<<"$lines" | sed -E 's/^[a-z]+(\([^)]*\))?!?: /- /' | sed -E 's/^- (.)/- \U\1/' | awk '!seen[$0]++' || true)
  [ -n "$items" ] && printf '### %s\n%s\n\n' "$1" "$items"
  return 0
}

{
  section "New" "feat"
  section "Fixes" "fix|revert"
  section "Improvements" "perf|refactor"
  section "Dependencies" "chore\(deps\)"
  echo "Arm64, Android 11+. Installs over the previous version."
  [ -n "$last" ] && echo "Changes since ${last}."
}
