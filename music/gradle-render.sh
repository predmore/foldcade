#!/usr/bin/env bash
# Download MuseScore General, render variant A, and copy the packaged asset.
# Local builds and CI both call this so the renders match.
# Usage: gradle-render.sh <asset-ogg> <preview-dir>
set -euo pipefail
cd "$(dirname "$0")"

ASSETS_DIR="${1:?pass the generated assets directory}"
PREVIEW_DIR="${2:?pass the preview directory}"

if ! command -v fluidsynth >/dev/null 2>&1; then
  echo "fluidsynth is required to render Foldcade home music." >&2
  echo "Install it, then rebuild. On Debian/Ubuntu: sudo apt-get install fluidsynth ffmpeg libsndfile1 python3-venv" >&2
  echo "The MuseScore General SoundFont is downloaded by this build. It is not committed." >&2
  exit 1
fi
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required to encode the Foldcade home loop (Vorbis and the MP3 preview)." >&2
  echo "Install it, then rebuild. On Debian/Ubuntu: sudo apt-get install ffmpeg" >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to render Foldcade home music." >&2
  exit 1
fi

SHA=5b85b6c2c61d10b2b91cddd41efcce7b25cd31c8271d511c73afafbef20b6fa3
URL=https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf3
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/foldcade"
mkdir -p "$CACHE"
SF_CACHE="$CACHE/MuseScore_General.sf3"

verify() {
  echo "$SHA  $1" | sha256sum -c --status
}

if [[ ! -f "$SF_CACHE" ]] || ! verify "$SF_CACHE"; then
  echo "Downloading MuseScore_General.sf3"
  tmp="$(mktemp)"
  curl -fL --retry 3 --retry-delay 2 -o "$tmp" "$URL"
  if ! verify "$tmp"; then
    echo "MuseScore_General.sf3 sha256 mismatch. Expected $SHA. This build will not use the download." >&2
    rm -f "$tmp"
    exit 1
  fi
  mv "$tmp" "$SF_CACHE"
fi
ln -sfn "$SF_CACHE" MuseScore_General.sf3

if [[ ! -x .venv/bin/python ]]; then
  if ! python3 -m venv .venv; then
    echo "python3-venv is required to render Foldcade home music." >&2
    echo "On Debian/Ubuntu: sudo apt-get install python3-venv" >&2
    exit 1
  fi
fi
.venv/bin/pip install --disable-pip-version-check -r requirements.txt

# render.sh defaults to variant a and keeps its .venv/bin/python path.
./render.sh a
.venv/bin/python check_render.py

mkdir -p "$ASSETS_DIR" "$PREVIEW_DIR"
cp -f out/a/foldcade_home_a_loop.ogg "$ASSETS_DIR/foldcade_home_loop.ogg"
cp -f out/a/foldcade_home_a_loop.ogg "$PREVIEW_DIR/foldcade_home_loop.ogg"
cp -f out/a/foldcade_home_a_loop.mp3 "$PREVIEW_DIR/foldcade_home_loop.mp3"
cp -f out/a/foldcade_home_a_loop_x2_preview.mp3 "$PREVIEW_DIR/foldcade_home_loop_preview.mp3"
