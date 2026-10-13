# GameNative

Foldcade adds Steam games from [GameNative](https://github.com/TheReal-Flo/gamenative) through exported `.steam` files. They appear in the **GameNative** folder.

Standard and Gold builds are supported; standard has priority when both are installed.

## Export individual Steam games

1. Set up GameNative, install a Steam game, and check that it plays there.
2. Open that game's options and choose **Export for frontend**.
3. Save its `.steam` file in a shared folder, such as `ROMs/GameNative`.
4. In Foldcade, open **L1 › Settings › Library › Add a folder** and select that folder.
5. Find the game in **GameNative** on Home or in **All**.

Use the same folder for more exports. These files are launch references; GameNative keeps the installed games. Its **Add to home screen** option is not currently a supported setup path in Foldcade.

## Automatic exports

Builds with **Frontend Sync** can update exports as games are installed or removed:

1. Open **Frontend Sync** in GameNative's settings.
2. Select an export directory for **Steam** and confirm with **OK**.
3. Wait for export to finish, or use **Resync all** if needed.
4. Add that directory to Foldcade with **Settings › Library › Add a folder**.

After exports change, return to Foldcade; it [rescans folders in the background](README.md#discovery-and-updates).

GameNative [exports a format for each store](https://github.com/TheReal-Flo/gamenative/blob/master/app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt). Foldcade reads only `.steam`; `.epic`, `.gog`, `.amazon`, and `.pcgame` are not supported. Renaming them does not supply the correct store information.

## Launching and saves

Press **A** or **X** to launch in GameNative on that screen. The game must already be installed and configured. The app tile in **Emulators** opens GameNative itself.

GameNative handles saves and cloud sync; Foldcade does not sync them through RomM. See the shared [artwork setup](README.md#artwork) for more art.

## When DroidDeck is installed too

Keep exports in separate folders. DroidDeck's sync inventory marks its files as DroidDeck games. Ordinary Steam app ID exports open in GameNative when it is installed, or DroidDeck if only DroidDeck is installed.

## If a game is missing or opens the wrong app

- **Missing game:** Select the folder containing `.steam` exports, then return to Foldcade. If it is in All, check [Home placement](README.md#a-game-is-in-all-but-missing-from-home).
- **Game is unavailable:** Check its installation and account in GameNative; an export does not install it.
- **Opens in DroidDeck:** Check that GameNative is installed and its export folder does not contain DroidDeck's sync inventory.

[All integration guides](README.md)
