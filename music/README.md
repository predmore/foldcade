# Foldcade home music

Tracks live in `tracks/<id>/` with their own `compose.py`. `tracks/manifest.json` lists each track's id, title, composer, license, and packaged file. The setting stores the track id and defaults to `nightroute`. L1's Track row cycles those tracks. A activates the next one. Left and right move through them and stay on the row.

The shipped tracks are **Night Route** (the default) and **Lanternlight**, composed by the Foldcade project and licensed under MIT with this repository, the same choice as the original theme art.

Every track masters to -12 LUFS integrated with a -1 dBTP ceiling, so changing tracks keeps the level. A track sets its own loop length: `process.py` reads it from the `loopEnd` marker in the one-loop MIDI, and `check_render.py` holds it to `length_s` in the track's committed `checks.json`.

`theme.json` may set an optional `backgroundMusic` path to a file inside that zip. While the theme is selected, that file is the last choice on the Track row. Leaving the theme drops it. A home track stays selected.

## Lanternlight

`tracks/lanternlight/compose.py` is the original piece: D major, 72 bpm, 4/4, 24 bars, 80.000 s. CI renders variant `a` (gentle retro). `sketch1` and `b` stay in that file and are not rendered by CI.

`./render.sh lanternlight` renders it. `music/gradle-render.sh` renders every id in the manifest, checks the result, and packages each `file` (Lanternlight is `music/lanternlight.ogg`).

## Night Route

`tracks/nightroute/compose.py` is built to stay in the background: D minor, 60 bpm, 180 beats, 180.000 s. Nothing keeps a beat and nothing attacks hard. Each layer runs on its own clock (chords of 16 to 24 beats, chip motifs every 36 and 45 beats, a bell every 60, an arpeggio that fades in and out over 90), and the clocks only line up once a loop, so no stretch of it comes round again inside the loop. The motifs use the D minor pentatonic, which sits well over every chord, so wherever two clocks cross the notes agree.

A pedalled, low-velocity piano and a warm pad render through fluidsynth. `tracks/nightroute/mix.py` low-passes them and synthesizes the chip voices from the same score: NES-style triangles for the motifs, arpeggio, bass, and a detuned pad, a soft pulse for the second motif, and SNES-style echo. It mixes them by loudness against the piano and pad, then thins the low mids and lifts around 1 kHz, so the track sounds as loud as Lanternlight on the Thor's speakers. `render.sh` runs a track's `mix.py`, when it has one, between fluidsynth and `process.py`.

## Rendering

Do not commit the SoundFont, WAVs, or encoded audio. `tracks/<id>/checks.json` is the approved measurement. A fresh render writes `tracks/<id>/out/a/checks.json`, which is not committed.

`process.py` ships copy 2 or copy 3 of the four-copy render, whichever matches the copy after it. Fluidsynth's first copy can sound slightly different, and its reverb carries into copy 2. A tempo that is not whole microseconds per beat (Lanternlight's 72 bpm) drifts, so its later copies stop matching.

SoundFont: MuseScore_General.sf3 v0.2, sha256
`5b85b6c2c61d10b2b91cddd41efcce7b25cd31c8271d511c73afafbef20b6fa3`,
from https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/ — MIT licensed.
The acknowledgements are in [licenses/MuseScore_General_License.md](../licenses/MuseScore_General_License.md).

`assembleRelease` packages the same assets as debug. The home-music workflow and both release jobs render inside the job with `.github/actions/render-home-music` (fluidsynth, ffmpeg, python3-venv, libsndfile1, and the pinned SoundFont). Pass `-PfoldcadeHomeMusicAssets=<dir>` only to copy an already-rendered `music/` directory instead of running fluidsynth.
