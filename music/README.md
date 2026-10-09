# Foldcade home music

Tracks live in `tracks/<id>/` with their own `compose.py`. `tracks/manifest.json` lists each track's id, title, composer, license, and packaged file. The setting stores the track id and defaults to `lanternlight`. There is no picker in the app yet. L1 shows the current title.

The shipped track is **Lanternlight**, composed by the Foldcade project and licensed under GPLv3 with this repository, the same choice as the theme art.

## Lanternlight

`tracks/lanternlight/compose.py` is the original piece: D major, 72 bpm, 4/4, 24 bars, 80.000 s. CI renders variant `a` (gentle retro). `sketch1` and `b` stay in that file and are not rendered by CI.

`./render.sh lanternlight` renders it. `music/gradle-render.sh` renders every id in the manifest, checks the result, and packages each `file` (Lanternlight is `music/lanternlight.ogg`).

Do not commit the SoundFont, WAVs, or encoded audio. `tracks/lanternlight/checks.json` is the approved measurement. A fresh render writes `tracks/lanternlight/out/a/checks.json`, which is not committed.

`theme.json` may still set an optional `backgroundMusic` path. Choosing a theme track, and picking among home tracks, is not in this change.

SoundFont: MuseScore_General.sf3 v0.2, sha256
`5b85b6c2c61d10b2b91cddd41efcce7b25cd31c8271d511c73afafbef20b6fa3`,
from https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/ — MIT licensed.
The acknowledgements are in `MuseScore_General_License.md`.
