# GameNative

Foldcade adds games from [GameNative](https://github.com/utkarshdalal/GameNative) to the **GameNative** folder on Home. Standard and Gold builds are supported.

## Add a game with a shortcut

1. Install and configure a game in GameNative and check that it plays there.
2. Make Foldcade the default home app. Foldcade offers **Use as Home** when GameNative is installed; **Settings › System › Set as Home** is always available.
3. In the game's options in GameNative, choose **Add to home screen**.
4. Return to Foldcade. The game appears in **GameNative**, even with **New games on Home** off.

No export or folder selection is needed. Pins belong to the launcher that accepted them; request the shortcut again after making Foldcade Home. Foldcade reads enabled pinned shortcuts at startup and when you return Home.

Pins launch GameNative's original shortcut, preserving its store and whether it came from the standard or Gold build. **Y › Hide** keeps the Android shortcut. Hold **A** on **Delete shortcut** to remove Foldcade's pin; the installed game stays in GameNative. A matching export keeps the game in All.

## Discover exported games

Foldcade reads `.steam`, `.epic`, `.gog`, `.amazon`, and `.pcgame` exports. Each file holds GameNative's numeric game ID; its extension names the store. Keep the original extension.

With **Settings › Library › Find games** enabled, these folders are found automatically on internal storage and mounted SD cards:

- `ROMs` / `roms` and `Emulation/roms`, including `GameNative`, `steam`, `epic`, `gog`, `amazon`, or `pcgame` folders inside them.

Foldcade names the games it found in R1. A removed folder stays removed. Folders already added through **Settings › Library › Add › Add a folder** continue working without Find games, including exports in their subfolders. Use **Add a folder** for any other location.

For one game, choose **Export for frontend** in GameNative and save the file in one of those folders. Builds with **Frontend Sync** can maintain exports as games are installed or removed: choose a directory for each store in GameNative's Frontend Sync settings and confirm **OK**. Return to Foldcade after exports change.

GameNative has **no default export folder**: each store's directory starts empty. The top-level `GameNative` folder is its PC installation directory, and `Download/GameNative` holds container exports; neither is scanned automatically. Use a separate ROM library folder for frontend exports. Foldcade cannot read those private settings or enumerate the whole installed library. [Library API support remains tracked in #100](https://github.com/predmore/foldcade/issues/100). Pinning needs the least setup; automatic exports still need their directory chosen once inside GameNative.

## Launching, art, and saves

Press **A** or **X** to launch on that screen. The game must already be installed and configured. The app tile in **Emulators** opens GameNative itself.

Steam exports use Steam app IDs for art. Other stores and pins use title matches from RomM or SteamGridDB; without a match they keep the PC mark. See [artwork setup](README.md#artwork). An unambiguous pin and export with the same ID and title produce one tile. It keeps the existing tile’s identity and Home history, adds Steam art when the export identifies Steam, and uses the original pin to launch.

GameNative handles saves and cloud sync; Foldcade does not sync them through RomM.

## When DroidDeck is installed too

Keep exports in separate folders. DroidDeck's `.droiddeck-sync.json` inventory identifies its `.steam` exports. Unmarked Steam exports use GameNative when installed, or DroidDeck when only DroidDeck is installed. Other stores always use GameNative. Standard has priority over Gold for exports when both are installed; pins keep their actual publisher.

## If something is missing

- **No shortcut:** Make Foldcade Home, then request **Add to home screen** again in GameNative. Pins from another launcher are not imported.
- **No exported games:** Enable Find games for the conventional folders above, or add the export folder manually. Return Home to rescan. For a game already in All, check [Home placement](README.md#a-game-is-in-all-but-missing-from-home).
- **Game is unavailable:** Open GameNative and check its installation and account. A shortcut or export does not install a game.
- **A Steam export opens in DroidDeck:** Keep GameNative exports separate from DroidDeck's sync inventory.

## Upstream contract

Verified against upstream [`a2a121bb`](https://github.com/utkarshdalal/GameNative/tree/a2a121bb477070026fed8f6ee66f8d3e8b350971). The previously linked TheReal-Flo repository is a fork.

- [`ShortcutUtils.kt:88–166`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/utils/ShortcutUtils.kt#L88-L166) uses `ShortcutManager.requestPinShortcut`, ID `game_<id>`, action `app.gamenative.LAUNCH_GAME`, integer `app_id`, and string `game_source`. The ID omits the store, and Android hides shortcut intents from launchers, so Foldcade uses `LauncherApps.startShortcut`.
- [`FrontendSyncManager.kt:107–114,166–217`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt#L107-L217) maps extensions to `STEAM`, `EPIC`, `GOG`, `AMAZON`, and `CUSTOM_GAME` and writes decimal integer IDs.
- [`IntentLaunchManager.kt:37–106`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt#L37-L106) accepts those stores with `LAUNCH_GAME`, or `gamenative://run?appid=<id>&gamesource=<source>` through `ACTION_VIEW`. Non-positive IDs are rejected.
- [`PrefManager.kt:1626–1658`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/PrefManager.kt#L1626-L1658) defaults all export paths to empty. Individual exports use Android's document picker ([`BaseAppScreen.kt:578–583`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt#L578-L583)).
- [`StorageUtils.kt:133–156`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/utils/StorageUtils.kt#L133-L156) uses `<volume>/GameNative` as a PC installation root. [`ContainerFileExporter.kt:27`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/java/app/gamenative/utils/ContainerFileExporter.kt#L27) uses `Download/GameNative` for container files, rather than frontend references.
- [`AndroidManifest.xml:255–263`](https://github.com/utkarshdalal/GameNative/blob/a2a121bb477070026fed8f6ee66f8d3e8b350971/app/src/main/AndroidManifest.xml#L255-L263) declares a non-exported FileProvider, with no library content provider. AndroidX's startup provider also exposes no game catalog.

[All integration guides](README.md)
