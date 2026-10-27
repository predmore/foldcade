#!/usr/bin/env bash
# Attach foldcade.apk to a GitHub Release.
# Every push to main moves the pre-release tag "pre-release" onto the built
# commit, then updates that release in place, so the source archive matches
# the binary. The tag is not deleted. Deleting it queues a GitHub event that
# can land after the new release is published and force it back into a draft.
# The release object is not deleted either. GitHub has returned HTTP 500 for
# that delete and the job then exits before a replacement exists. A delete
# that does land also opens a gap while the next create is still racing it.
# A push that changes versionName in app/build.gradle.kts also cuts tag vX.Y.Z
# and a non-prerelease release. An existing tag is not recreated.
# A push of an existing tag v0.x.y that matches versionName publishes that
# full release and does not move the rolling pre-release tag.
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
# A v0.x.y tag publishes the full release. Main still owns the rolling pre-release.
stable_tag=false
if [ "${GITHUB_REF_TYPE:-}" = "tag" ]; then
  if ! [[ "${GITHUB_REF_NAME:-}" =~ ^v0\.[0-9]+\.[0-9]+$ ]]; then
    echo "::error::Refusing tag ${GITHUB_REF_NAME:-unknown}. A full release tag is v0.x.y."
    exit 1
  fi
  stable_tag=true
elif [ "${GITHUB_REF_TYPE:-}" != "branch" ] || [ "${GITHUB_REF_NAME:-}" != "main" ]; then
  echo "::error::Publishing runs on main or a v0.x.y tag, not ${GITHUB_REF_TYPE:-unknown} ${GITHUB_REF_NAME:-unknown}."
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

if [ "$stable_tag" = true ] && [ "$GITHUB_REF_NAME" != "$tag" ]; then
  echo "::error::Tag ${GITHUB_REF_NAME} does not match versionName ${VERSION_NAME}. Expected ${tag}."
  exit 1
fi

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

# present, absent, or error. HTTP 500 is an error, not proof the release is
# gone. Treating it as absent would try to create a second release while the
# first one is still on the tag.
prerelease_object_state() {
  local pre_tag="$1"
  local err
  if err="$(gh release view "$pre_tag" 2>&1 >/dev/null)"; then
    printf '%s\n' present
    return 0
  fi
  if printf '%s\n' "$err" | grep -Eq 'release not found|HTTP 404'; then
    printf '%s\n' absent
    return 0
  fi
  printf '%s\n' error
  return 0
}

# Point the rolling tag at the built commit. Create the ref when it is missing.
# A ref update does not queue the tag-deleted event that drafts the new release.
point_prerelease_tag() {
  local pre_tag="$1"
  local sha attempt
  if sha="$(existing_tag_commit "$pre_tag")"; then
    if [ "$sha" = "$GITHUB_SHA" ]; then
      echo "Tag ${pre_tag} already points at ${GITHUB_SHA}."
      return 0
    fi
    echo "Moving ${pre_tag} from ${sha} to ${GITHUB_SHA}."
    for attempt in 1 2 3 4 5; do
      if gh api --method PATCH "repos/${GITHUB_REPOSITORY}/git/refs/tags/${pre_tag}" \
        -f sha="$GITHUB_SHA" \
        -F force=true >/dev/null
      then
        return 0
      fi
      echo "Moving ${pre_tag} failed (attempt ${attempt})."
      sleep $((attempt * 2))
    done
    echo "::error::Could not move ${pre_tag} to ${GITHUB_SHA}."
    return 1
  fi
  echo "Creating ${pre_tag} at ${GITHUB_SHA}."
  gh api --method POST "repos/${GITHUB_REPOSITORY}/git/refs" \
    -f ref="refs/tags/${pre_tag}" \
    -f sha="$GITHUB_SHA" >/dev/null
}

# The tag already points at this commit. --verify-tag refuses to invent one.
# A create that returns an error after the release exists is success: the
# next step uploads the APK onto that object instead of starting another.
create_prerelease_draft() {
  local pre_tag="$1"
  local attempt err state
  for attempt in 1 2 3 4 5; do
    if err="$(gh release create "$pre_tag" \
      --draft \
      --target "$GITHUB_SHA" \
      --title "Pre-release ${VERSION_NAME}" \
      --notes-file "$notes" \
      --prerelease \
      --latest=false \
      --verify-tag 2>&1)"; then
      printf '%s\n' "$err"
      return 0
    fi
    printf '%s\n' "$err" >&2
    state="$(prerelease_object_state "$pre_tag")"
    if [ "$state" = "present" ]; then
      echo "Release ${pre_tag} exists. Not creating another."
      return 0
    fi
    echo "Creating draft ${pre_tag} failed (attempt ${attempt})."
    sleep $((attempt * 2))
  done
  echo "::error::Could not create the ${pre_tag} draft."
  return 1
}

# The public tag endpoint 404s for drafts. gh release view does not: it falls
# back to the draft, which is how a green job left Obtainium with a 404.
prerelease_is_public() {
  local pre_tag="$1"
  local json target asset
  if ! json="$(gh api "repos/${GITHUB_REPOSITORY}/releases/tags/${pre_tag}" 2>/dev/null)"; then
    return 1
  fi
  target="$(printf '%s\n' "$json" | jq -r '.target_commitish // empty')"
  if [ "$target" != "$GITHUB_SHA" ]; then
    if git cat-file -e "${target}^{commit}" 2>/dev/null; then
      target="$(git rev-parse "${target}^{commit}")"
    fi
  fi
  asset="$(printf '%s\n' "$json" | jq -r '([(.assets // [])[].name] == ["foldcade.apk"])')"
  [ "$(printf '%s\n' "$json" | jq -r '.draft')" = "false" ] \
    && [ "$(printf '%s\n' "$json" | jq -r '.prerelease')" = "true" ] \
    && [ "$(printf '%s\n' "$json" | jq -r '.tag_name')" = "$pre_tag" ] \
    && [ "$target" = "$GITHUB_SHA" ] \
    && [ "$asset" = "true" ]
}

# Publish again when the release is still a draft, then require the public
# tag endpoint to show a non-draft pre-release with foldcade.apk. The waits
# cover the tag-deleted event arriving after the first publish.
confirm_prerelease() {
  local pre_tag="pre-release"
  local attempt state names
  local -a waits=(0 2 4 8 8 8 10 10 10)
  for attempt in "${!waits[@]}"; do
    if [ "${waits[$attempt]}" -gt 0 ]; then
      sleep "${waits[$attempt]}"
    fi
    if prerelease_is_public "$pre_tag"; then
      echo "pre-release is public at ${pre_tag} on ${GITHUB_SHA} with foldcade.apk."
      return 0
    fi
    echo "pre-release is missing or still a draft (check $((attempt + 1)))."
    state="$(prerelease_object_state "$pre_tag")"
    if [ "$state" = "absent" ]; then
      if create_prerelease_draft "$pre_tag"; then
        state=present
      fi
    fi
    if [ "$state" = "present" ]; then
      names="$(gh release view "$pre_tag" --json assets --jq '.assets[].name' 2>/dev/null || true)"
      if [ "$names" != "foldcade.apk" ]; then
        replace_prerelease_asset "$pre_tag" || echo "Replacing foldcade.apk failed."
      fi
      edit_prerelease "$pre_tag" || echo "Publishing ${pre_tag} failed."
    fi
  done
  echo "::error::Release ${pre_tag} is missing, still a draft, or has no foldcade.apk."
  return 1
}

# Upload foldcade.apk and drop every other asset. --clobber replaces an
# existing foldcade.apk; it does not remove a differently named file.
replace_prerelease_asset() {
  local pre_tag="$1"
  local names name found=0
  gh release upload "$pre_tag" "$apk" --clobber || return 1
  names="$(gh release view "$pre_tag" --json assets --jq '.assets[].name')" || return 1
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    if [ "$name" = "foldcade.apk" ]; then
      found=1
      continue
    fi
    echo "Removing extra asset ${name} from ${pre_tag}."
    gh release delete-asset "$pre_tag" "$name" --yes || return 1
  done <<< "$names"
  [ "$found" -eq 1 ]
}

# Keep the release on this commit, public, and not Latest. v0.x.y stays Latest.
edit_prerelease() {
  local pre_tag="$1"
  gh release edit "$pre_tag" \
    --target "$GITHUB_SHA" \
    --title "Pre-release ${VERSION_NAME}" \
    --notes-file "$notes" \
    --draft=false \
    --prerelease=true \
    --latest=false
}

# Five attempts. GitHub sometimes returns HTTP 500 for a release write that
# did not stick. Upload and edit are safe to repeat.
update_prerelease() {
  local pre_tag="$1"
  local attempt
  for attempt in 1 2 3 4 5; do
    if replace_prerelease_asset "$pre_tag" && edit_prerelease "$pre_tag"; then
      return 0
    fi
    echo "Updating ${pre_tag} failed (attempt ${attempt})."
    sleep $((attempt * 2))
  done
  echo "::error::Could not update the ${pre_tag} release."
  return 1
}

# Create only when the tag has no release. A lookup error is retried so a
# 500 is not mistaken for a missing release.
ensure_prerelease_object() {
  local pre_tag="$1"
  local attempt state
  for attempt in 1 2 3 4 5; do
    state="$(prerelease_object_state "$pre_tag")"
    case "$state" in
      present)
        echo "Release ${pre_tag} already exists. Updating it in place."
        return 0
        ;;
      absent)
        echo "Release ${pre_tag} is missing. Creating a draft."
        create_prerelease_draft "$pre_tag"
        return $?
        ;;
      *)
        echo "Looking up ${pre_tag} failed (attempt ${attempt})."
        sleep $((attempt * 2))
        ;;
    esac
  done
  echo "::error::Could not tell whether ${pre_tag} exists."
  return 1
}

# Move the rolling tag, then update the release already on it. The APK is
# replaced before the job checks the public tag endpoint.
publish_rolling_prerelease() {
  local pre_tag="pre-release"
  point_prerelease_tag "$pre_tag"
  ensure_prerelease_object "$pre_tag"
  update_prerelease "$pre_tag"
  confirm_prerelease
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

# The rolling pre-release on this commit uses the same computed versionCode.
# A stable tag on an older commit must stay strictly above that pre-release.
assert_ahead_of_prerelease() {
  local json pre_sha pre_body pre_code
  if ! json="$(gh api "repos/${GITHUB_REPOSITORY}/releases/tags/pre-release")"; then
    echo "::error::Rolling pre-release is missing. Refusing to publish a stable release."
    exit 1
  fi
  pre_sha="$(printf '%s\n' "$json" | jq -r '.target_commitish')"
  pre_body="$(printf '%s\n' "$json" | jq -r '.body // ""')"
  pre_code="$(printf '%s\n' "$pre_body" | sed -n 's/^versionCode:[[:space:]]*\([0-9][0-9]*\)[[:space:]]*$/\1/p' | head -1)"
  if ! [[ "$pre_code" =~ ^[1-9][0-9]*$ ]]; then
    echo "::error::Rolling pre-release has no versionCode. Refusing to publish."
    exit 1
  fi
  if git cat-file -e "${pre_sha}^{commit}" 2>/dev/null; then
    pre_sha="$(git rev-parse "${pre_sha}^{commit}")"
  fi
  if [ "$pre_sha" = "$GITHUB_SHA" ]; then
    if [ "$VERSION_CODE" -ne "$pre_code" ]; then
      echo "::error::versionCode ${VERSION_CODE} does not match pre-release versionCode ${pre_code} on ${GITHUB_SHA}."
      exit 1
    fi
    echo "versionCode ${VERSION_CODE} matches the pre-release on this commit."
    return 0
  fi
  if [ "$VERSION_CODE" -le "$pre_code" ]; then
    echo "::error::versionCode ${VERSION_CODE} is not above pre-release versionCode ${pre_code} (${pre_sha})."
    exit 1
  fi
  echo "versionCode ${VERSION_CODE} is above pre-release versionCode ${pre_code}."
}

# Prefer the notes committed for this tag. Otherwise keep generated notes.
create_stable_release() {
  local mode="$1"
  local full=""
  local -a args
  args=(
    "$tag"
    "$apk"
    --target "$GITHUB_SHA"
    --title "$tag"
    --latest
    --prerelease=false
  )
  if [ "$mode" = "verify" ]; then
    args+=(--verify-tag)
  fi
  if [ -f "${root}/.github/release/notes/${tag}.md" ]; then
    full="$(mktemp)"
    {
      printf 'versionName: %s\n' "$VERSION_NAME"
      printf 'versionCode: %s\n' "$VERSION_CODE"
      printf 'commit: %s\n' "$GITHUB_SHA"
      printf '\n'
      cat "${root}/.github/release/notes/${tag}.md"
    } >"$full"
    args+=(--notes-file "$full")
  else
    args+=(--generate-notes)
  fi
  gh release create "${args[@]}"
  if [ -n "$full" ]; then
    rm -f "$full"
  fi
  ensure_stable_notes
}

confirm_stable_release() {
  local ok
  ok="$(gh release view "$tag" --json isPrerelease,assets --jq '(.isPrerelease | not) and (([.assets[].name] | index("foldcade.apk")) != null)')"
  if [ "$ok" != "true" ]; then
    echo "::error::Release ${tag} must be a full release whose asset is foldcade.apk."
    exit 1
  fi
  if [ "$stable_tag" = true ]; then
    local pre_ok
    pre_ok="$(gh release view pre-release --json isPrerelease,tagName --jq '.isPrerelease and .tagName == "pre-release"')"
    if [ "$pre_ok" != "true" ]; then
      echo "::error::Rolling pre-release tag is missing or no longer a pre-release."
      exit 1
    fi
  fi
}

# Create the stable release once. A re-run that finds the tag on this commit
# leaves the tag in place and only fills in a missing APK.
# This does not delete or move the rolling pre-release tag.
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
      create_stable_release verify
    fi
    confirm_stable_release
    return 0
  fi
  create_stable_release new
  confirm_stable_release
}

# A v0.x.y tag is the full release. Leave the rolling pre-release where main put it.
if [ "$stable_tag" = true ]; then
  assert_ahead_of_prerelease
  publish_stable
  exit 0
fi

publish_rolling_prerelease
if [ "$bump" = "bumped" ]; then
  publish_stable
else
  echo "Version is unchanged. No stable tag."
fi
