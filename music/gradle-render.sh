#!/usr/bin/env bash
# Download MuseScore General and render every track in tracks/manifest.json.
# Local builds and CI both call this so the renders match.
# Usage: gradle-render.sh <asset-dir> <preview-dir>
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
  echo "ffmpeg is required to encode Foldcade home music (Vorbis and the MP3 preview)." >&2
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

mapfile -t IDS < <(.venv/bin/python - <<'PY'
import json
manifest = json.load(open("tracks/manifest.json"))
ids = [track["id"] for track in manifest["tracks"]]
if not ids:
    raise SystemExit("tracks/manifest.json has no tracks")
print("\n".join(ids))
PY
)

mkdir -p "$ASSETS_DIR/music" "$PREVIEW_DIR"
cp -f tracks/manifest.json "$ASSETS_DIR/music/manifest.json"

for id in "${IDS[@]}"; do
  ./render.sh "$id"
  file="$(.venv/bin/python - "$id" <<'PY'
import json, sys
track_id = sys.argv[1]
manifest = json.load(open("tracks/manifest.json"))
print(next(track["file"] for track in manifest["tracks"] if track["id"] == track_id))
PY
)"
  src="tracks/$id/out/a/foldcade_home_a_loop"
  dest="$ASSETS_DIR/$file"
  mkdir -p "$(dirname "$dest")"
  cp -f "$src.ogg" "$dest"
  cp -f "$src.ogg" "$PREVIEW_DIR/$id.ogg"
  cp -f "$src.mp3" "$PREVIEW_DIR/$id.mp3"
  cp -f "$src"_x2_preview.mp3 "$PREVIEW_DIR/${id}_preview.mp3"
done
