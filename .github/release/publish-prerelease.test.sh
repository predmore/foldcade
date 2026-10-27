#!/usr/bin/env bash
# Runs publish.sh against a fake gh. Success must move pre-release without
# deleting the tag or the release, update that release in place, then read
# the public tag endpoint. A release that stays missing there must fail.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cd "$root"

if [ "$(git rev-parse --is-shallow-repository)" = "true" ]; then
  echo "need a full history so versionCode can be counted" >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
printf 'not a real apk\n' >"$tmp/foldcade.apk"

cat >"$tmp/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
log="${FOLDCADE_GH_LOG:?}"
printf '%s\n' "$*" >>"$log"
joined="$*"
mode="${FOLDCADE_GH_MODE:?}"
if [[ "$joined" == *"/releases/tags/pre-release"* ]]; then
  if [ "${FOLDCADE_GH_MODE:?}" = "stuck" ]; then
    echo "Not Found" >&2
    exit 1
  fi
  cat <<JSON
{"draft":false,"prerelease":true,"tag_name":"pre-release","target_commitish":"${GITHUB_SHA}","assets":[{"name":"foldcade.apk"}]}
JSON
  exit 0
fi
if [[ "$joined" == *"/git/ref/tags/pre-release"* ]]; then
  printf '%s\n' '{"object":{"type":"commit","sha":"0000000000000000000000000000000000000000"}}'
  exit 0
fi
if [[ "$joined" == api\ repos/*/git/ref/tags/* ]]; then
  echo "missing tag" >&2
  exit 1
fi
if [[ "$joined" == *"--method PATCH"* || "$joined" == *"--method POST"* ]]; then
  printf '%s\n' '{"ref":"refs/tags/pre-release"}'
  exit 0
fi
if [[ "$joined" == *"--json isPrerelease,assets"* ]]; then
  printf '%s\n' "true"
  exit 0
fi
if [[ "$joined" == *"--json body"* ]]; then
  printf '\n'
  exit 0
fi
if [[ "$joined" == "release view pre-release" ]]; then
  if [ "$mode" = "lookup500" ]; then
    count="$(grep -c '^release view pre-release$' "$log" || true)"
    if [ "$count" -eq 1 ]; then
      echo "HTTP 500 (https://api.github.com/repos/predmore/foldcade/releases/1)" >&2
      exit 1
    fi
  fi
  if [ "$mode" = "missing" ] && ! grep -q '^release create ' "$log"; then
    echo "release not found" >&2
    exit 1
  fi
  exit 0
fi
if [[ "$joined" == "release view pre-release --json assets "* ]]; then
  if [ "$mode" = "extra" ] && ! grep -q '^release delete-asset ' "$log"; then
    printf '%s\n' "foldcade.apk" "notes.txt"
    exit 0
  fi
  printf '%s\n' "foldcade.apk"
  exit 0
fi
if [[ "$joined" == release\ view\ * ]]; then
  exit 0
fi
if [[ "$joined" == release\ delete-asset\ * ]]; then
  exit 0
fi
if [[ "$joined" == release\ delete\ * ]]; then
  echo "refusing to delete a release" >&2
  exit 1
fi
if [[ "$joined" == release\ upload\ * ]]; then
  exit 0
fi
if [[ "$joined" == release\ edit\ * ]]; then
  if [ "$mode" = "edit500" ]; then
    count="$(grep -c '^release edit ' "$log" || true)"
    if [ "$count" -eq 1 ]; then
      echo "HTTP 500 (https://api.github.com/repos/predmore/foldcade/releases/1)" >&2
      exit 1
    fi
  fi
  printf '%s\n' "https://example.test/releases/tag/pre-release"
  exit 0
fi
if [[ "$joined" == release\ create\ * ]]; then
  printf '%s\n' "https://example.test/releases/tag/pre-release"
  exit 0
fi
echo "unhandled gh: $joined" >&2
exit 1
EOF

cat >"$tmp/sleep" <<'EOF'
#!/usr/bin/env bash
printf 'sleep %s\n' "$*" >>"${FOLDCADE_GH_LOG:?}"
exit 0
EOF
chmod +x "$tmp/gh" "$tmp/sleep"

run_publish() {
  local mode="$1"
  local out="$tmp/out-$mode"
  local err="$tmp/err-$mode"
  local log="$tmp/gh-$mode.log"
  : >"$log"
  set +e
  env -u GH_TOKEN -u GITHUB_TOKEN \
    PATH="$tmp:$PATH" \
    FOLDCADE_GH_LOG="$log" \
    FOLDCADE_GH_MODE="$mode" \
    GITHUB_EVENT_NAME=push \
    GITHUB_REF_TYPE=branch \
    GITHUB_REF_NAME=main \
    GITHUB_SHA="$(git rev-parse HEAD)" \
    GITHUB_REPOSITORY=predmore/foldcade \
    BEFORE_SHA="$(git rev-parse HEAD^)" \
    bash "$here/publish.sh" "$tmp/foldcade.apk" >"$out" 2>"$err"
  local code=$?
  set -e
  printf '%s\n' "$code"
}

assert_kept_release() {
  local log="$1"
  if grep -q 'cleanup-tag' "$log"; then
    echo "deleted the pre-release tag" >&2
    exit 1
  fi
  if grep -q -- '--method DELETE' "$log"; then
    echo "deleted a git ref" >&2
    exit 1
  fi
  if grep -E -q '^release delete( |$)' "$log"; then
    echo "deleted the pre-release object" >&2
    exit 1
  fi
  grep -q -- '--method PATCH repos/predmore/foldcade/git/refs/tags/pre-release' "$log"
  grep -q -- '-F force=true' "$log"
  grep -q -- '--latest=false' "$log"
  grep -q -- '--target ' "$log"
  grep -q 'release upload pre-release ' "$log"
  grep -q -- '--clobber' "$log"
  grep -q 'repos/predmore/foldcade/releases/tags/pre-release' "$log"
}

success_code="$(run_publish success)"
if [ "$success_code" -ne 0 ]; then
  echo "public pre-release was treated as a failure" >&2
  cat "$tmp/out-success" "$tmp/err-success" "$tmp/gh-success.log" >&2
  exit 1
fi
assert_kept_release "$tmp/gh-success.log"
if grep -q '^sleep ' "$tmp/gh-success.log"; then
  echo "a public pre-release still waited to retry" >&2
  exit 1
fi
if grep -q '^release create ' "$tmp/gh-success.log"; then
  echo "an existing pre-release was created again" >&2
  exit 1
fi
grep -q 'Updating it in place' "$tmp/out-success"
grep -q 'pre-release is public at pre-release' "$tmp/out-success"
grep -q '^release edit pre-release ' "$tmp/gh-success.log"
grep -q -- '--draft=false --prerelease=true --latest=false' "$tmp/gh-success.log"
if ! grep -q 'Version is unchanged. No stable tag.' "$tmp/out-success"; then
  echo "versionName changed on HEAD; stable publish path still exited 0"
fi

stuck_code="$(run_publish stuck)"
if [ "$stuck_code" -eq 0 ]; then
  echo "a missing public pre-release was treated as success" >&2
  exit 1
fi
assert_kept_release "$tmp/gh-stuck.log"
edits="$(grep -c '^release edit ' "$tmp/gh-stuck.log")"
if [ "$edits" -lt 2 ]; then
  echo "draft was not published again (edits=$edits)" >&2
  exit 1
fi
grep -q 'still a draft, or has no foldcade.apk' "$tmp/out-stuck"

missing_code="$(run_publish missing)"
if [ "$missing_code" -ne 0 ]; then
  echo "a missing pre-release was not created" >&2
  cat "$tmp/out-missing" "$tmp/err-missing" "$tmp/gh-missing.log" >&2
  exit 1
fi
assert_kept_release "$tmp/gh-missing.log"
grep -q 'release create pre-release' "$tmp/gh-missing.log"
grep -q -- '--verify-tag' "$tmp/gh-missing.log"
grep -q 'Creating a draft' "$tmp/out-missing"

lookup_code="$(run_publish lookup500)"
if [ "$lookup_code" -ne 0 ]; then
  echo "a lookup HTTP 500 was not retried" >&2
  cat "$tmp/out-lookup500" "$tmp/err-lookup500" "$tmp/gh-lookup500.log" >&2
  exit 1
fi
assert_kept_release "$tmp/gh-lookup500.log"
if grep -q '^release create ' "$tmp/gh-lookup500.log"; then
  echo "a lookup HTTP 500 was treated as a missing release" >&2
  exit 1
fi
grep -q '^sleep ' "$tmp/gh-lookup500.log"
grep -q 'Looking up pre-release failed' "$tmp/out-lookup500"

edit_code="$(run_publish edit500)"
if [ "$edit_code" -ne 0 ]; then
  echo "an edit HTTP 500 was not retried" >&2
  cat "$tmp/out-edit500" "$tmp/err-edit500" "$tmp/gh-edit500.log" >&2
  exit 1
fi
assert_kept_release "$tmp/gh-edit500.log"
edits="$(grep -c '^release edit ' "$tmp/gh-edit500.log")"
if [ "$edits" -lt 2 ]; then
  echo "edit was not retried (edits=$edits)" >&2
  exit 1
fi
grep -q '^sleep ' "$tmp/gh-edit500.log"

extra_code="$(run_publish extra)"
if [ "$extra_code" -ne 0 ]; then
  echo "an extra asset was not removed" >&2
  cat "$tmp/out-extra" "$tmp/err-extra" "$tmp/gh-extra.log" >&2
  exit 1
fi
assert_kept_release "$tmp/gh-extra.log"
grep -q 'release delete-asset pre-release notes.txt --yes' "$tmp/gh-extra.log"

if grep -nE 'gh release delete( |$)|--cleanup-tag' "$here/publish.sh"; then
  echo "publish.sh still deletes a release or a tag" >&2
  exit 1
fi

echo "publish-prerelease tests passed"
