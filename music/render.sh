#!/usr/bin/env bash
# Usage: ./render.sh <track-id>
# Renders one track listed in tracks/manifest.json. CI calls this for every track.
# Lanternlight's compose source still accepts --variant; the shipped render is variant a.
set -euo pipefail
cd "$(dirname "$0")"
TRACK="${1:?pass a track id from tracks/manifest.json}"
SF=MuseScore_General.sf3
echo "5b85b6c2c61d10b2b91cddd41efcce7b25cd31c8271d511c73afafbef20b6fa3  $SF" | sha256sum -c -

eval "$(.venv/bin/python - "$TRACK" <<'PY'
import json, shlex, sys
track_id = sys.argv[1]
manifest = json.load(open("tracks/manifest.json"))
match = next((track for track in manifest["tracks"] if track["id"] == track_id), None)
if match is None:
    raise SystemExit(f"unknown track {track_id!r}")
for key in ("title", "composer", "license", "file"):
    if not str(match.get(key, "")).strip():
        raise SystemExit(f"track {track_id} is missing {key}")
print(f"TITLE={shlex.quote(match['title'])}")
print(f"COMPOSER={shlex.quote(match['composer'])}")
print(f"LICENSE={shlex.quote(match['license'])}")
PY
)"

OUT="tracks/$TRACK/out/a"
.venv/bin/python "tracks/$TRACK/compose.py" --variant a --out "$OUT"
fluidsynth -ni -q -r 48000 -g 0.5 -z 64 -T wav -O float \
  -o synth.reverb.active=1 -o synth.chorus.active=1 \
  -o synth.reverb.room-size=0.75 -o synth.reverb.damp=0.35 \
  -o synth.reverb.width=0.9 -o synth.reverb.level=0.8 \
  -o synth.chorus.depth=6 -o synth.chorus.level=1.2 -o synth.chorus.nr=3 -o synth.chorus.speed=0.25 \
  -F "$OUT/raw_4x.wav" "$SF" "$OUT/foldcade_home_a_render4x.mid"
.venv/bin/python process.py --variant a --out "$OUT" \
  --title "$TITLE" --composer "$COMPOSER" --license "$LICENSE"
.venv/bin/python check_render.py --track "$TRACK"
