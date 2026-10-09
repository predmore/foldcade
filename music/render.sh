#!/usr/bin/env bash
# Usage: ./render.sh [sketch1|a|b]   (default a)
# Render the 4x MIDI with FluidSynth + MuseScore General, then cut/post/normalize/check/encode.
set -euo pipefail
cd "$(dirname "$0")"
V="${1:-a}"
SF=MuseScore_General.sf3
echo "5b85b6c2c61d10b2b91cddd41efcce7b25cd31c8271d511c73afafbef20b6fa3  $SF" | sha256sum -c -
.venv/bin/python compose.py --variant "$V"
fluidsynth -ni -q -r 48000 -g 0.5 -z 64 -T wav -O float \
  -o synth.reverb.active=1 -o synth.chorus.active=1 \
  -o synth.reverb.room-size=0.75 -o synth.reverb.damp=0.35 \
  -o synth.reverb.width=0.9 -o synth.reverb.level=0.8 \
  -o synth.chorus.depth=6 -o synth.chorus.level=1.2 -o synth.chorus.nr=3 -o synth.chorus.speed=0.25 \
  -F "out/$V/raw_4x.wav" "$SF" "out/$V/foldcade_home_${V}_render4x.mid"
.venv/bin/python process.py --variant "$V"
