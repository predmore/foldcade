#!/usr/bin/env bash
# Install the locked fluidsynth and ffmpeg packages for the home-music render.
#
# ubuntu-24.04 runners do not ship these. `apt-get install` pulls about 70 MB
# of ffmpeg libraries from the Azure Ubuntu mirror, and a slow mirror missed
# the step timeout on release run 40237749671. The debs are cached after the
# first successful download. Later runs hash the cache and install from disk.
# A cold cache races several mirrors and keeps the first one that sustains
# 300 KB/s. At that rate the 70 MB set takes about four minutes, and with the
# two-minute render the step stays inside its ten-minute backstop. A mirror
# as slow as the failed Azure fetch (tens of KB/s) loses the race.
#
# The package bytes are the set from the green release run 39882256680.
# qsynth and fluid-soundfont-gm stay out. Playback is unchanged.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCK="$ROOT/packages.lock"
CACHE="${RENDER_DEB_CACHE:-${HOME:?HOME is unset}/.cache/foldcade/render-debs}"
MIN_SPEED=300000
MIRRORS=(
  "http://azure.archive.ubuntu.com/ubuntu"
  "https://mirrors.edge.kernel.org/ubuntu"
  "http://archive.ubuntu.com/ubuntu"
  "http://mirrors.kernel.org/ubuntu"
)

if [[ ! -f "$LOCK" ]]; then
  echo "missing package lock: $LOCK" >&2
  exit 1
fi
if grep -E '(^|/)(qsynth|fluid-soundfont-gm)_' "$LOCK"; then
  echo "lockfile must not include qsynth or fluid-soundfont-gm" >&2
  exit 1
fi
for needle in '/ffmpeg_' '/fluidsynth_' '/libsndfile1_' '/libmp3lame0_' '/libvorbisenc2_'; do
  if ! grep -q "$needle" "$LOCK"; then
    echo "lockfile is missing ${needle}" >&2
    exit 1
  fi
done

mkdir -p "$CACHE"
rm -f "$CACHE"/.partial.*

lock_count() {
  grep -cE '^[0-9a-f]{64} ' "$LOCK"
}

# Return 0 when every locked deb is present and matches its sha256.
cache_complete() {
  local sha size path base
  while read -r sha size path; do
    [[ -z "${sha:-}" || "$sha" == \#* ]] && continue
    base="$CACHE/$(basename "$path")"
    if [[ ! -f "$base" ]] || ! echo "$sha  $base" | sha256sum -c --status; then
      return 1
    fi
  done < "$LOCK"
}

missing_debs() {
  local sha size path base missing=0
  while read -r sha size path; do
    [[ -z "${sha:-}" || "$sha" == \#* ]] && continue
    base="$CACHE/$(basename "$path")"
    if [[ ! -f "$base" ]] || ! echo "$sha  $base" | sha256sum -c --status; then
      missing=$((missing + 1))
    fi
  done < "$LOCK"
  echo "$missing"
}

download_file() {
  local mirror="$1" sha="$2" size="$3" path="$4"
  local base tmp actual
  base="$CACHE/$(basename "$path")"
  if [[ -f "$base" ]] && echo "$sha  $base" | sha256sum -c --status; then
    return 0
  fi
  tmp="$(mktemp "$CACHE/.partial.XXXXXX")"
  if curl -fL --silent --show-error --connect-timeout 10 --max-time 180 \
      --speed-limit "$MIN_SPEED" --speed-time 15 \
      -o "$tmp" "$mirror/$path" \
    && echo "$sha  $tmp" | sha256sum -c --status; then
    actual="$(stat -c %s "$tmp")"
    if [[ "$actual" == "$size" ]]; then
      mv -f "$tmp" "$base"
      return 0
    fi
    echo "size mismatch for $path: got $actual, lock has $size" >&2
  fi
  rm -f "$tmp"
  echo "failed $(basename "$path") from $mirror" >&2
  return 1
}

run_batch() {
  local mirror="$1"
  shift
  local line sha size path pids=() pid failed=0
  for line in "$@"; do
    read -r sha size path <<< "$line"
    download_file "$mirror" "$sha" "$size" "$path" &
    pids+=("$!")
  done
  for pid in "${pids[@]}"; do
    if ! wait "$pid"; then
      failed=1
    fi
  done
  return "$failed"
}

fetch_with_mirror() {
  local mirror="$1"
  local -a batch=()
  local sha size path
  while read -r sha size path; do
    [[ -z "${sha:-}" || "$sha" == \#* ]] && continue
    if [[ -f "$CACHE/$(basename "$path")" ]] \
      && echo "$sha  $CACHE/$(basename "$path")" | sha256sum -c --status; then
      continue
    fi
    batch+=("$sha $size $path")
    if (( ${#batch[@]} >= 8 )); then
      run_batch "$mirror" "${batch[@]}" || return 1
      batch=()
    fi
  done < "$LOCK"
  if (( ${#batch[@]} > 0 )); then
    run_batch "$mirror" "${batch[@]}" || return 1
  fi
}

probe_mirror() {
  local mirror="$1" outfile="$2" speed
  if speed="$(curl -fL --silent --show-error --connect-timeout 5 --max-time 8 \
      --speed-limit "$MIN_SPEED" --speed-time 5 \
      -r 0-1048575 -o /dev/null -w '%{speed_download}' \
      "$mirror/$PROBE_PATH" 2>"$outfile.err")"; then
    speed="${speed%%.*}"
  else
    speed=0
  fi
  [[ "$speed" =~ ^[0-9]+$ ]] || speed=0
  printf '%s %s\n' "$speed" "$mirror" > "$outfile"
  if (( speed >= MIN_SPEED )); then
    echo "mirror probe ${speed} B/s $mirror"
  else
    echo "mirror probe failed ${speed} B/s $mirror" >&2
  fi
}

fill_cache() {
  if cache_complete; then
    echo "render packages: $(lock_count) debs verified in cache, no download"
    return 0
  fi
  local before
  before="$(missing_debs)"
  echo "render packages: $before of $(lock_count) debs missing from cache"

  PROBE_PATH="$(awk '$1 ~ /^[0-9a-f]{64}$/ && $3 ~ /\/libavcodec60_/ { print $3; exit }' "$LOCK")"
  if [[ -z "${PROBE_PATH:-}" ]]; then
    echo "lockfile has no libavcodec60 deb to probe mirrors with" >&2
    exit 1
  fi

  local tmp i=0 pid
  local -a pids=() ranked=()
  tmp="$(mktemp -d)"
  for mirror in "${MIRRORS[@]}"; do
    probe_mirror "$mirror" "$tmp/$i.rank" &
    pids+=("$!")
    i=$((i + 1))
  done
  for pid in "${pids[@]}"; do
    wait "$pid" || true
  done

  while read -r speed mirror; do
    [[ -z "${speed:-}" ]] && continue
    if (( speed >= MIN_SPEED )); then
      ranked+=("$mirror")
    fi
  done < <(cat "$tmp"/*.rank | sort -nr)
  rm -rf "$tmp"

  if (( ${#ranked[@]} == 0 )); then
    echo "every Ubuntu mirror stalled below ${MIN_SPEED} B/s; not downloading" >&2
    exit 1
  fi

  local mirror
  for mirror in "${ranked[@]}"; do
    echo "downloading render packages from $mirror"
    if fetch_with_mirror "$mirror" && cache_complete; then
      break
    fi
    echo "mirror incomplete, trying the next one: $mirror" >&2
  done

  if ! cache_complete; then
    echo "locked render packages are still incomplete after every mirror" >&2
    exit 1
  fi
  local after
  after="$(missing_debs)"
  echo "render packages: downloaded $((before - after)), $after still missing"
}

install_cached_debs() {
  local -a debs=()
  local expected
  mapfile -t debs < <(find "$CACHE" -maxdepth 1 -name '*.deb' -type f -printf '%p\n' | sort)
  expected="$(lock_count)"
  if (( ${#debs[@]} != expected )); then
    echo "expected $expected debs in $CACHE, found ${#debs[@]}" >&2
    exit 1
  fi
  local deb
  for deb in "${debs[@]}"; do
    if [[ "$deb" != /* ]]; then
      echo "refusing to install a relative deb path: $deb" >&2
      exit 1
    fi
  done

  # dpkg installs these files and does not contact a mirror. apt-get can turn
  # a later dependency into a bare package name and then try the network;
  # that is the failure this step is removing.
  sudo DEBIAN_FRONTEND=noninteractive dpkg \
    --force-confdef --force-confold \
    -i "${debs[@]}"
}

verify_tools() {
  local status encoders decoders libs
  # Capture command output before grepping. A pipe into grep -q raises SIGPIPE
  # under pipefail once grep finds a match, and that would fail the step.
  status="$(dpkg-query -W -f '${Package} ${Status}\n' qsynth fluid-soundfont-gm 2>/dev/null || true)"
  if grep -q 'install ok installed' <<< "$status"; then
    echo "qsynth or fluid-soundfont-gm is installed; the render does not use them" >&2
    exit 1
  fi
  command -v fluidsynth >/dev/null
  command -v ffmpeg >/dev/null
  fluidsynth -h >/dev/null
  encoders="$(ffmpeg -hide_banner -encoders 2>/dev/null || true)"
  decoders="$(ffmpeg -hide_banner -decoders 2>/dev/null || true)"
  grep -q libvorbis <<< "$encoders"
  grep -q libmp3lame <<< "$encoders"
  grep -q ' vorbis' <<< "$decoders"
  libs="$(ldconfig -p || true)"
  if ! grep -q 'libsndfile.so.1' <<< "$libs"; then
    echo "libsndfile.so.1 is not installed" >&2
    exit 1
  fi
  python3 -c 'import venv'
  local version
  version="$(ffmpeg -version 2>/dev/null || true)"
  echo "render tools ready: ${version%%$'\n'*}"
}

fill_cache
install_cached_debs
verify_tools
