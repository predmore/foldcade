#!/usr/bin/env bash
# Fail a pull request that changes versionName or versionCode unless versionCode
# is higher than the base (main). An unchanged version passes.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=version.sh
source "$here/version.sh"

: "${BASE_SHA:?BASE_SHA is required}"

root="$(cd "$here/../.." && pwd)"
cd "$root"

base_file="$(mktemp)"
trap 'rm -f "$base_file"' EXIT

if [ "$BASE_SHA" = "0000000000000000000000000000000000000000" ]; then
  : >"$base_file"
else
  if ! git cat-file -e "${BASE_SHA}^{commit}" 2>/dev/null; then
    git fetch origin "$BASE_SHA"
  fi
  if git cat-file -e "${BASE_SHA}:app/build.gradle.kts" 2>/dev/null; then
    git show "${BASE_SHA}:app/build.gradle.kts" >"$base_file"
  else
    : >"$base_file"
  fi
fi

status="$(compare_versions "$base_file" app/build.gradle.kts)"
echo "version check against ${BASE_SHA}: ${status}"
