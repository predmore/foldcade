#!/usr/bin/env bash
# versionName in app/build.gradle.kts is the stable-release name.
# versionCode in that file is the local fallback. CI passes
# -PfoldcadeVersionCode from git rev-list --count HEAD.

read_version_file() {
  local file="$1"
  local code name
  if [ ! -f "$file" ]; then
    return 1
  fi
  code="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\)[[:space:]]*$/\1/p' "$file" | head -1)"
  name="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)"[[:space:]]*$/\1/p' "$file" | head -1)"
  if [ -z "$code" ] || [ -z "$name" ]; then
    return 1
  fi
  printf '%s\n%s\n' "$code" "$name"
}

require_version_name() {
  local name="$1"
  if ! [[ "$name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "::error::versionName must be X.Y.Z so the stable tag is vX.Y.Z, was '${name}'." >&2
    return 1
  fi
}

# Prints "unchanged" or "bumped".
# A stable bump is a versionName change. The file's versionCode is only the
# local fallback, so a versionCode-only edit is not a bump.
compare_versions() {
  local base_file="$1"
  local head_file="$2"
  local pair base_name="" head_name
  if [ -s "$base_file" ] && pair="$(read_version_file "$base_file")"; then
    base_name="${pair#*$'\n'}"
  fi
  if ! pair="$(read_version_file "$head_file")"; then
    echo "::error::${head_file} must set versionCode and versionName in one place." >&2
    return 1
  fi
  head_name="${pair#*$'\n'}"
  require_version_name "$head_name" || return 1
  if [ "$head_name" = "$base_name" ]; then
    printf 'unchanged\n'
    return 0
  fi
  printf 'bumped\n'
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  read_version_file "${1:-app/build.gradle.kts}"
fi
