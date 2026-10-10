# Architecture

Foldcade is an Android launcher for the AYN Thor. The shell is Compose. Plugins supply libraries, players, and metadata. Display ids are discovered at runtime. Nothing in the display map is a Thor panel id.

## Modules

| Module | Role |
| --- | --- |
| `:app` | Android shell. Application id `app.foldcade`. Debug suffix `.debug`. Session, panels, display assignment, and the RomM credential ids. |
| `:api` | Plugin contract. Unstable: see [plugins.md](plugins.md#stability). |
| `:language` | Shared UI language: panels, the built-in theme, motion, copy, and the home-music setting. |
| `:host` | `PluginHost`. Loads entries and calls library and metadata I/O off the main thread. |
| `:net` | The only HTTP stack. Builds every OkHttp client; `PublicHttps` reaches a fixed list of public hosts over HTTPS only. `HttpStackGuardTest` fails a client built anywhere else. |
| `:romm` | RomM HTTP client, on a client from `:net`. |
| `:artwork` | Game art from public sources: Steam's CDN, then libretro's thumbnails, then SteamGridDB with the user's key. Covers for tiles, and for the focused game a scene: wide art, a logo, and a title screen. Keeps what it found, and its misses, on disk. Not a plugin. The Game art setting turns it off. |
| `:plugins:emulators` | Standalone emulators for the other catalog platforms. One screen. Saves stay in the emulator. |
| `:plugins:gamenative` | PC player and library for GameNative. Saves stay in GameNative. |
| `:plugins:local-folder` | Library backed by a folder on the device, plus the platform ids that folder scan uses. |
| `:plugins:melonds` | Nintendo DS player for upstream melonDS Android. |
| `:plugins:moonlight` | Moonlight player and library. Official client, one screen. |
| `:plugins:romm` | Built-in RomM library and metadata. |
| `:plugins:sample` | In-tree sample plugin. |
| `:samples:out-of-tree` | Sample that compiles against `:api` only. |
| `build-logic` | `foldcade.jvm-api33`, the Animal Sniffer check for API 33. |

`:api`, `:host`, `:net`, `:artwork`, `:romm`, and every `:plugins:*` module must apply `foldcade.jvm-api33`. A new plugin module inherits that check the same way. `settings.gradle.kts` fails configuration if one of those modules skips it.

## Screens

`assignDisplays` maps the default display and one other display onto a top panel and a bottom panel. Which physical display is on top is `defaultDisplayIsTop`.

With both screens free, the top panel is the hero for the game you have selected, and the bottom panel is the picker. A game can take one screen or both. Android Home on a panel clears that panel only.

The home-music loop plays while Foldcade is in front and pauses when you leave it. The setting (on, volume, track id) lives in `:language`. Playback lives in `:app`. The default track is Lanternlight.

## Plugins

The shell does not scan for installed plugin packages. `PluginHost.load` reads `ServiceLoader` entries from a class loader. The contract, the reserved RomM ids, and the minify keep rules are in [plugins.md](plugins.md).

## CI

Release publishing is specified in [releases.md](releases.md). Four workflows run the rest.

### Unit tests

`.github/workflows/unit-tests.yml` runs on pull requests and on pushes to `main`. It checks `versionName` with `.github/release/check-version-bump.sh`, then runs `./gradlew testDebugUnitTest`. The Gradle configuration cache is read-only on pull requests and writable on pushes to `main`. A new push cancels a stale pull-request run. A push to `main` does not cancel. The Release workflow also keeps `cancel-in-progress` false.

### Release

`.github/workflows/release.yml` runs on pushes to `main`, on pull requests, and on `workflow_dispatch`. Signing and publishing run on a push to `main` only, after the unit tests in that workflow. Pull requests and manual runs assemble an unsigned release to prove `versionCode`. They do not publish. This workflow does not boot an emulator. It does not run on tag pushes.

### Thor-sized emulator

`.github/workflows/emulator.yml` runs on pull requests, on a nightly schedule from `main` (`0 8 * * *`), and on `workflow_dispatch`. GitHub runs the schedule only from the default branch. A pull request skips the boot unless the diff touches `app/`, `api/`, `language/`, `plugins/`, `gradle/`, a `src/test` or `src/androidTest` tree, the Gradle wrapper or a root Gradle file, `.github/emulator/`, or this workflow. The nightly run and a manual run always boot. A green run is a Thor-sized emulator pass. It is not a pass on Thor hardware. `cancel-in-progress` is true for this workflow.

### Home music

`.github/workflows/home-music.yml` runs on pull requests. It renders only when the diff against `main` touches `music/`, `app/build.gradle.kts`, that workflow, or `.github/actions/render-home-music/`. Otherwise the check is not left pending. The signed release and the unsigned dry-run render with the same composite action, inside the job, before any keystore is decoded.
