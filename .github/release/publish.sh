#!/usr/bin/env bash
# Attach foldcade.apk to a GitHub Release.
# Every push to main recreates the pre-release tag "pre-release" on the built
# commit before the APK is uploaded, so the source archive matches the binary.
# A push that changes versionName in app/build.gradle.kts also cuts tag vX.Y.Z
# and a non-prerelease release. An existing tag is not recreated.
# versionCode is git rev-list --count HEAD, the same value Gradle was given.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=version.sh
source "$here/version.sh"

apk="${1:?path to foldcade.apk}"
base="$(basename "$apk")"

if [ "${GITHUB_EVENT_NAME:-}" != "push" ]; then
  echo "::error::Release publishing only runs on push."
  exit 1
fi
if [ "$base" != "foldcade.apk" ]; then
  echo "::error::Refusing to upload '$base'. The Obtainium asset name is foldcade.apk."
  exit 1
fi
case "$apk" in
  *unsigned*)
    echo "::error::Refusing to upload an unsigned APK."
    exit 1
    ;;
esac
if [ ! -s "$apk" ]; then
  echo "::error::APK is missing or empty: $apk"
  exit 1
fi
if [ -z "${GITHUB_SHA:-}" ] || [ -z "${GITHUB_REPOSITORY:-}" ]; then
  echo "::error::GITHUB_SHA and GITHUB_REPOSITORY are required."
  exit 1
fi
if [ "${GITHUB_REF_TYPE:-}" != "branch" ] || [ "${GITHUB_REF_NAME:-}" != "main" ]; then
  echo "::error::Stable releases are cut by a merge to main, not by ${GITHUB_REF_TYPE:-unknown} ${GITHUB_REF_NAME:-unknown}."
  exit 1
fi

root="$(cd "$here/../.." && pwd)"
cd "$root"

pair="$(read_version_file app/build.gradle.kts)" || {
  echo "::error::app/build.gradle.kts must set versionCode and versionName."
  exit 1
}
VERSION_NAME="${pair#*$'\n'}"
require_version_name "$VERSION_NAME"
if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
  echo "::error::Release checkout must be a full history. versionCode is git rev-list --count HEAD."
  exit 1
fi
VERSION_CODE="$(git rev-list --count HEAD)"
if ! [[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
  echo "::error::git rev-list --count HEAD did not return a versionCode."
  exit 1
fi

parent_file="$(mktemp)"
notes="$(mktemp)"
trap 'rm -f "$parent_file" "$notes"' EXIT

before="${BEFORE_SHA:-}"
if [ -z "$before" ] || [ "$before" = "0000000000000000000000000000000000000000" ]; then
  : >"$parent_file"
else
  if ! git cat-file -e "${before}^{commit}" 2>/dev/null; then
    git fetch origin "$before"
  fi
  if git cat-file -e "${before}^{commit}" 2>/dev/null; then
    parent_count="$(git rev-list --count "${before}^{commit}")"
    if [ "$VERSION_CODE" -le "$parent_count" ]; then
      echo "::error::Computed versionCode ${VERSION_CODE} is not higher than ${parent_count} at ${before}."
      exit 1
    fi
  fi
  if git cat-file -e "${before}:app/build.gradle.kts" 2>/dev/null; then
    git show "${before}:app/build.gradle.kts" >"$parent_file"
  else
    : >"$parent_file"
  fi
fi

bump="$(compare_versions "$parent_file" app/build.gradle.kts)"
echo "version ${VERSION_NAME} (${VERSION_CODE}) against parent ${before:-none}: ${bump}"

tag="v${VERSION_NAME}"

# Commit the tag points at, or empty when the tag does not exist.
existing_tag_commit() {
  local name="$1"
  local json type sha
  if ! json="$(gh api "repos/${GITHUB_REPOSITORY}/git/ref/tags/${name}" 2>/dev/null)"; then
    return 1
  fi
  type="$(printf '%s\n' "$json" | jq -r '.object.type')"
  sha="$(printf '%s\n' "$json" | jq -r '.object.sha')"
  if [ "$type" = "tag" ]; then
    sha="$(gh api "repos/${GITHUB_REPOSITORY}/git/tags/${sha}" --jq '.object.sha')"
  fi
  printf '%s\n' "$sha"
}

if [ "$bump" = "bumped" ]; then
  if sha="$(existing_tag_commit "$tag")"; then
    if [ "$sha" != "$GITHUB_SHA" ]; then
      echo "::error::Tag ${tag} already exists at ${sha}. Refusing to recreate it. versionName changed, so it must be a new X.Y.Z."
      exit 1
    fi
  fi
fi

cat >"$notes" <<EOF
versionName: ${VERSION_NAME}
versionCode: ${VERSION_CODE}
commit: ${GITHUB_SHA}

Asset name: foldcade.apk
EOF

# Drop the old pre-release, including its git tag, then create it again on the
# built commit before the APK upload. GitHub's source archive is generated from
# the tag. The new release stays a draft until the APK is uploaded.
recreate_prerelease() {
  local pre_tag="pre-release"
  if gh release view "$pre_tag" >/dev/null 2>&1; then
    gh release delete "$pre_tag" --yes --cleanup-tag
  fi
  gh release create "$pre_tag" \
    --draft \
    --target "$GITHUB_SHA" \
    --title "Pre-release ${VERSION_NAME}" \
    --notes-file "$notes" \
    --prerelease \
    --latest=false
  gh release upload "$pre_tag" "$apk"
  gh release edit "$pre_tag" --draft=false --prerelease=true --latest=false
}

# Generated notes do not include the computed versionCode. Put it on the release
# after --generate-notes, without dropping the generated text.
ensure_stable_notes() {
  local body combined
  body="$(gh release view "$tag" --json body --jq '.body // ""')"
  if printf '%s\n' "$body" | grep -qx "versionCode: ${VERSION_CODE}"; then
    return 0
  fi
  combined="$(mktemp)"
  cat >"$combined" <<EOF
versionName: ${VERSION_NAME}
versionCode: ${VERSION_CODE}
commit: ${GITHUB_SHA}

${body}
EOF
  gh release edit "$tag" --notes-file "$combined"
  rm -f "$combined"
}

# Create the stable release once. A re-run that finds the tag on this commit
# leaves the tag in place and only fills in a missing APK.
publish_stable() {
  local sha
  if sha="$(existing_tag_commit "$tag")"; then
    if [ "$sha" != "$GITHUB_SHA" ]; then
      echo "::error::Tag ${tag} already exists at ${sha}. Refusing to recreate it."
      exit 1
    fi
    echo "Tag ${tag} already points at ${GITHUB_SHA}. Not recreating it."
    if gh release view "$tag" >/dev/null 2>&1; then
      if ! gh release view "$tag" --json assets --jq '.assets[].name' | grep -qx 'foldcade.apk'; then
        gh release upload "$tag" "$apk" --clobber
      fi
      ensure_stable_notes
    else
      gh release create "$tag" "$apk" \
        --verify-tag \
        --target "$GITHUB_SHA" \
        --title "$tag" \
        --generate-notes \
        --latest \
        --prerelease=false
      ensure_stable_notes
    fi
    return 0
  fi
  gh release create "$tag" "$apk" \
    --target "$GITHUB_SHA" \
    --title "$tag" \
    --generate-notes \
    --latest \
    --prerelease=false
  ensure_stable_notes
}

recreate_prerelease
if [ "$bump" = "bumped" ]; then
  publish_stable
else
  echo "Version is unchanged. No stable tag."
fi
