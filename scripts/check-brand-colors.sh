#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

# Keep palette definitions in ui/theme; consumers use MaterialTheme.colorScheme.
pattern='(^|[^[:alnum:]_])Color[[:space:]]*\(|Color[[:space:]]*\.[[:space:]]*(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)([^[:alnum:]_]|$)'
status=0
git -c submodule.recurse=false grep --untracked -nE "$pattern" -- app/src/main ':!app/src/main/**/ui/theme/**' || status=$?

case "$status" in
  0)
    echo 'Use MaterialTheme.colorScheme outside ui/theme (see docs/brand.md).' >&2
    exit 1
    ;;
  1) exit 0 ;;
  *) exit "$status" ;;
esac
