# Foldcade Home – ambient loop (sketch 1)

Original composition. Sources: `compose.py` (writes `out/foldcade_home_loop.mid`), `render.sh`, `process.py`.
Rebuild: `./render.sh` (needs fluidsynth, ffmpeg, python venv with mido numpy scipy soundfile pyloudnorm).

SoundFont: MuseScore_General.sf3 v0.2 (VERSION file 0.2.0, 13 May 2020), sha256
5b85b6c2c61d10b2b91cddd41efcce7b25cd31c8271d511c73afafbef20b6fa3,
from https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/ — MIT licensed.
Rendered audio is a derivative work: MuseScore_General_License.md (acknowledgements, copyright
notices and MIT permission notice) must be included in Foldcade's credits/licenses file.

Loop: whole file, 0–80.000 s (3,840,000 samples @ 48 kHz). Seamless: cut from the 2nd copy of a 4x render.

## Variants (Oct 8 2026)
`./render.sh a` (gentle retro) / `./render.sh b` (more chiptune) / `./render.sh sketch1` (original arrangement).
`compose.py --variant {sketch1,a,b}`; outputs land in `out/<variant>/`. Sketch 1's original files remain in `out/`.
Variant b applies post-processing in process.py (10-bit crush at 12% wet, 2nd-order 9.5 kHz low-pass) to the
continuous 4x render before cutting, so the seam stays continuous.

## What Foldcade ships

The home loop is variant A. `./render.sh` defaults to `a`, and CI renders only A. The packaged asset name is `foldcade_home_loop.ogg` (copied from `out/a/foldcade_home_a_loop.ogg`). Do not commit the SoundFont, WAVs, or encoded audio.

The composition is original to Foldcade. It is licensed under GPLv3 with this repository, the same choice as the theme art.

`theme.json` may set an optional string `backgroundMusic` to a track inside that theme. When the field is absent, Foldcade plays `foldcade_home_loop.ogg`.
