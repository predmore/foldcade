# Persistence audit

Settings and confirmed Home edits are restored from the app-private `foldcade` shared preferences. `SessionStore` owns those keys, and `ShellController.publish` saves changes made through controller input or the settings screen. Preferences use Android's asynchronous `apply`; save reconciliation markers and pending returns use `commit` before handing control to a player. Android flushes pending preference writes during component lifecycle transitions ([SharedPreferences documentation](https://developer.android.com/reference/android/content/SharedPreferences)).

## Saved choices

- Personalization: theme name, whether the background was explicitly selected, background motion, motion speed, and controller confirmation/dismissal choices per device.
- Sound: music enabled, volume, and track id. Theme and track selections fall back when unavailable without overwriting the saved choice during startup. Nonfinite volume values fall back to the default.
- Library: source folder URIs, RomM origin, listed/recent order, automatic placement of new games, and the artwork switch.
- Home: tile positions and empty slots, collections (folders the user made in older releases become collections), confirmed names and icons, hidden tiles, removed system folders with the games still filed in them, when Recently played may first appear, and All's selected tab, sort, system filter, and destination folder.
- Apps: games/apps shelf overrides, hidden apps, favorites, and overrides that show player apps.
- Players: save folder URIs, skipped folder offers, save hashes, and pending save returns.
- Onboarding: Home, library-folder, usage-access, and controller prompt answers.

Brightness, the Home role, notification access, and usage access belong to Android. The shell reads their current state rather than restoring an app-owned copy. Folder URIs are saved after acquiring persistable access. Removing or replacing one folder reference keeps its grant while another saved source or player still uses that tree.

## Home snapshots and startup

The Home format is versioned. Version 2 encodes each field separately as Base64url, so opaque game ids or names containing record separators cannot damage neighboring fields. Version 1 layouts still load and are upgraded on the next saved snapshot. Invalid version 2 records are skipped individually. Set and folder ordering is deterministic, and saving an unchanged snapshot skips the preference write.

A lifted tile is serialized at its last confirmed position. Confirming a move saves the new position; canceling or restarting during an unfinished move restores the original position. Open dialogs, rename drafts, editing state, and active external-app sessions are transient.

Saved Android, GameNative, Moonlight, DroidDeck, and library placements are retained while their respective catalogs load. An empty startup catalog does not establish that a game was removed, so a folder whose games are still loading keeps its place and its name. A reported catalog can then remove games that are actually gone.

No games, no folder. A folder Foldcade makes, for a platform or for Android, GameNative, Moonlight, or DroidDeck, appears with its first game and is removed once no game is left in it, including when its games are hidden or moved into another folder. Folders hold games, not the apps that run them: an installed app a player plugin covers, such as Azahar, Dolphin, GameNative, Moonlight, or DroidDeck, goes in its own Emulators folder, under the same rule. Releases before the Emulators folder filed those apps under Android Games or Android Apps; each moves to Emulators once, and after that stays wherever the user puts it. A game added back from All, or a newly pinned one, brings its folder back. Folders the user made stay, empty or not.

## Files, secrets, and transfer

Confirmed GameNative catalogs use the existing durable-file writer: write a temporary sibling, sync its bytes, replace the destination, and attempt to sync the parent directory. Play history is appended and synced under `files/play-sessions`. RomM's durable device/save data and replaced player-save backups remain under `filesDir`; downloaded games and regenerable artwork bytes use cache storage.

RomM also keeps a server-scoped catalog under `files/romm/servers/<server key>/catalog.json`. It restores titles, platform metadata, artwork addresses, and complete platform listings after a cold offline start. Searches and interrupted pagination retain the last complete listing. Network recovery reconnects and ingests the refreshed catalog in the running shell. Authentication failures still require sign-in; an unavailable server can use its saved catalog. Verified downloaded ROMs can launch without a network request.

RomM image bytes use a bounded cache under that server's ROM cache root. Cache hits still require a credential-free address on the configured origin. Both screens share image requests, and temporary files keep unfinished images out of the cache. Android may evict ROMs and artwork from cache storage; the catalog is kept separately so their metadata survives that eviction.

RomM and SteamGridDB credentials use the existing encrypted DataStore, with encryption keys in Android Keystore. The backup rules include `sharedpref/foldcade.xml` and ordinary files, and exclude the credential DataStore directory. The manifest's existing `allowBackup=false` policy still disables cloud backup. Device transfer behavior with that flag depends on the device; the transfer rules now include saved settings and Home edits ([Android backup documentation](https://developer.android.com/identity/data/autobackup)). Credentials must be entered again, and provider-specific folder grants may need to be reacquired on another device.

## Verification

`SettingsPersistenceTest` recreates stores/controllers after settings changes and covers startup catalog timing, folder edits, saved sources, shelf choices, controller mappings, shared folder references, unavailable themes/tracks, and invalid values. `HomePersistenceTest` covers version 1 migration, unusual identifiers, browsing choices, pending moves, malformed records, and renamed folders during startup. `PlaySessionBackupTest` parses each backup mode and checks settings/file inclusion and credential exclusion.

Run all unit tests and API 33 checks with:

```sh
./gradlew testDebugUnitTest :plugins:moonlight:test --configuration-cache
```

These tests cover application save/restore logic and file round trips. Preference tests use an in-memory Android interface implementation; they do not simulate an interrupted Android disk write. Physical-device force-stop/relaunch, power-loss, and device-transfer checks remain separate validation steps.
