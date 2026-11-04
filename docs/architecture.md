# Architecture

Foldcade is an Android launcher for the AYN Thor. The shell is Compose. Plugins supply libraries, players, and metadata. Display ids are discovered at runtime. Nothing in the display map is a Thor panel id.

## Modules

| Module | Role |
| --- | --- |
| `:app` | Android shell. Application id `app.foldcade`. Debug suffix `.debug`. Session, panels, display assignment, and the RomM credential ids. |
| `:api` | Plugin contract. Unstable: see [plugins.md](plugins.md#stability). |
| `:language` | Shared UI language: panels, the built-in theme, motion, copy, and the home-music setting. |
| `:host` | `PluginHost`. Loads entries and calls library and metadata I/O off the main thread. |
| `:net` | The only HTTP stack. Builds every OkHttp client; `PublicHttps` reaches a fixed list of public hosts over HTTPS only; `LanHttp` reaches computers on the local network. `HttpStackGuardTest` fails a client built anywhere else. |
| `:romm` | RomM HTTP client, on a client from `:net`. |
| `:gamestream` | The parts of the GameStream protocol Sunshine and Apollo hosts speak that Foldcade uses: host status, pairing, app lists, and box art. On `LanHttp` from `:net`, which reaches LAN hosts only and pins a paired host's certificate. |
| `:artwork` | Artwork lookup and caching. The app tries Steam's CDN, libretro's thumbnails, the connected RomM server's cover search, then SteamGridDB with the user's key. Covers for tiles, and for the focused game a scene: wide art, a logo, and a title screen. Keeps what it found, and its misses, on disk. The Game art setting turns it off. |
| `:plugins:droiddeck` | DroidDeck player and library: Steam on the device. Games from DroidDeck's Add to home screen and synced `.steam` files. |
| `:plugins:emulators` | Standalone emulators for the other catalog platforms. One screen. Saves stay in the emulator. |
| `:plugins:gamenative` | PC player and library for GameNative. Saves stay in GameNative. |
| `:plugins:local-folder` | Library backed by a folder on the device, its platform ids, and a deferred index of existing local art and game facts. See [local media](local-media.md). |
| `:plugins:melonds` | Nintendo DS player for upstream melonDS Android. |
| `:plugins:moonlight` | Moonlight player and library. Official client, one screen. |
| `:plugins:romm` | Built-in RomM library and metadata, plus a cover-search art source for games outside the server's library. |
| `:plugins:sample` | In-tree sample plugin. |
| `:samples:out-of-tree` | Sample that compiles against `:api` only. |
| `build-logic` | `foldcade.jvm-api33`, the Animal Sniffer check for API 33. |

`:api`, `:host`, `:net`, `:artwork`, `:romm`, `:gamestream`, and every `:plugins:*` module must apply `foldcade.jvm-api33`. A new plugin module inherits that check the same way. `settings.gradle.kts` fails configuration if one of those modules skips it.

## Screens

`assignDisplays` maps the default display and one other display onto a top panel and a bottom panel. Which physical display is on top is `defaultDisplayIsTop`.

`Panel` is always the physical screen. Roles come from `ScreenLayout` (Settings › System › Layout), which `Session` holds:

| Layout | Top | Bottom |
| --- | --- | --- |
| Art top (default) | Hero, with the L1 and R1 islands | Picker |
| Art bottom | Picker | Hero, with the islands |
| Top only | Picker, with the islands and the focused name between them | Dark |

With both screens free, `Session.surfaceOn` puts the hero and the picker where the layout says. The islands and their menus open on `ScreenLayout.menuScreen`: the hero's screen, or the grid's with one screen. Y's menu is not an island: it opens beside its tile on `ScreenLayout.gridScreen` (`Side.Tile`), and the hero is left as it is. Settings, Play activity, and the connect form open on the top screen in every layout (`ScreenLayout.pageScreen`): their rows were laid out for its width, and the bottom screen is a third narrower. A page split over two screens (Saves, Set up saves, a save conflict, Y › Art) puts its showcase half on `Session.heroPanel` and its controls on `Session.controlsPanel`; with no hero it shows the controls alone. The dark screen draws nothing, sets its window to the lowest backlight, and passes its keys to the grid. `PickerModel.onShownScreens` moves pages off it, or off a screen a game holds, to one Foldcade shows. When the preferred page screen is free again, pages return there. Dialogs follow their page, or return to the screen that opened them. Islands keep their explicit screen while shown. Changing the layout takes effect at once. Start can open Settings on the free screen beside a game; L1 and R1 require their islands to be visible.

A game can take one screen or both. A opens a one-screen game on `Session.playOn` (Settings › System › Play on), and X on the other screen; a taken screen falls back to the free one. Beside a game the free screen is the command center, except the dark screen, which stays dark. Android Home on a panel clears that panel only.

`Session.defaultDisplayIsTop` is the display mapping and stays true. The old Main screen ‹Bottom› setting moves once to Art bottom with Play on ‹Bottom›, which is what it showed on the Thor.

The home-music loop plays while Foldcade is in front and pauses when you leave it. The setting (on, volume, track id) lives in `:language`. Playback lives in `:app`. The default track is Lanternlight.

Saved settings, Home edits, file durability, credentials, and transfer rules are covered in the [persistence audit](persistence.md).

Save discovery reuses writable SAF trees first. Optional all-files access adds `SharedStorageProvider` at `<applicationId>.storage`, with `primary:<path>` ids over primary shared storage. It has no picker roots and refuses every uid except Foldcade's own, even with a URI grant. Every storage operation requires current Android all-files access; app data (`Android/data`, `Android/obb`), traversal and symbolic links are refused. Save writes take the same `SaveFileLock` as hosted saves; file creation never replaces an existing name, rename refuses a taken name, and deletion only removes files or empty folders. The manifest exports it behind `MANAGE_DOCUMENTS`, as Android requires for a DocumentsProvider. SAF and its pickers work without the optional permission.

The same access lets the library find ROM folders by itself. `findRomRoots` looks at the top of every mounted volume for `ROMs`/`roms` and `Emulation/roms`, in any case, and keeps one only when it holds a folder the local-folder catalog names as a system (ES-DE, Batocera and libretro names included). Android shows another app's folders without any storage access, so Home can offer the access once, naming what it saw. Found folders are stored apart from picked trees (`found_roots`, document ids such as `primary:ROMs`), never overlap a pick, and a removed one (`removed_roots`, backed up) is never found again. Each is read as a tree on `LibraryFilesProvider` at `<applicationId>.library`: document ids are written as the external storage provider writes them, so the scan, the media index, sibling grants for cue sheets, and `SharedPaths` work unchanged. It serves only documents inside a current found folder, only for reading, only with all-files access, with no picker roots, behind `MANAGE_DOCUMENTS`; a player reads a game through its launch's grant. Looks happen off Main before the first folder scan and on every return Home; only a changed set rescans, and R1 announces a folder's games once. With no folder of the player's own, an empty or unreachable found folder never replaces Home with an empty or error grid. The provider reads the stored folders itself, so a player resuming into a cold Foldcade still opens its game.

Resource themes supply icons, styling, and animated scenes through a versioned ZIP contract. `:language` compiles scene expressions and tracks; the app's native renderer reuses shaders, textures, and path buffers. Afterglow's complete background definitions live under `themes/afterglow/backgrounds`. Icon providers and background selections compose independently. See [themes](themes.md) for author tools and the debug-only native preview.

## Plugins

The shell does not scan for installed plugin packages. `PluginHost.load` reads `ServiceLoader` entries from a class loader. The contract, the reserved RomM ids, and the minify keep rules are in [plugins.md](plugins.md).

## CI

Release publishing is specified in [releases.md](releases.md).

### Unit tests

`.github/workflows/unit-tests.yml` runs on pull requests and on pushes to `main`. It checks `versionName` with `.github/release/check-version-bump.sh`, then runs `./gradlew testDebugUnitTest`. The Gradle configuration cache is read-only on pull requests and writable on pushes to `main`. A new push cancels a stale pull-request run. A push to `main` does not cancel. The Release workflow also keeps `cancel-in-progress` false.

### Release

`.github/workflows/release.yml` runs on pushes to `main` and on a `v0.x.y` tag. It does not run on pull requests or `workflow_dispatch`. Signing secrets are read only by the signed release job, and only for a push to `main` or a `v*` tag. Signing and publishing run after the unit tests in that workflow. A push to `main` publishes the rolling pre-release. The `v0.x.y` tag publishes a full release that is not a pre-release, with asset `foldcade.apk`, and does not move the rolling pre-release tag. This workflow does not boot an emulator.

`.github/workflows/unsigned-release.yml` runs on pull requests and on `workflow_dispatch`. It assembles an unsigned release to prove `versionCode`. It does not publish, and it does not read signing secrets.

### Home music

`.github/workflows/home-music.yml` runs on pull requests. It renders only when the diff against `main` touches `music/`, `app/build.gradle.kts`, that workflow, or `.github/actions/render-home-music/`. Otherwise the check is not left pending. The signed release and the unsigned dry-run render with the same composite action, inside the job, before any keystore is decoded.
