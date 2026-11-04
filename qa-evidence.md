# Thor QA: EX saves, 4 November 2026

Final scope: GBA.emu, GBC.emu GB/GBC, NES.emu/FDS, Snes9x EX+, MD.emu Genesis/Master System/Sega CD. Flycast remains launch-only after its real live-folder access check failed. M64Plus and Pizza Boy are deferred for the source-linked limits in the PR.

All QA used the isolated application `app.foldcade.saves_more_emulators_2`, CI music assets, and throwaway save bytes through production ContentResolver, PlayerSaves and RommClient code. The temporary debug activities were removed before the final commit. No real game was launched. The real `app.foldcade` application was never installed over, cleared, reconfigured or uninstalled.

The first pass exercised all eight EX player/platform routes: exact basename and companion filtering, upload to unique `foldcade-probe-<uuid>-<index>` slots on ROM 23 Gain Ground, hash-verified download, refusal over unseen local progress, durable backup and restore, and preservation of unrelated files. The original synthetic Flycast-home check was insufficient and is superseded by the live-folder result below.

After review, three EX routes were repeated using ZIP basenames: GBA.emu, GBC.emu with the four-byte RTC companion, and Snes9x EX+ with `Foldcade Review1/saves/run 2/auto.srm`. Each round-tripped through RomM, rejected replacement over unseen local progress, exported a backup, restored successfully, and placed the same archive a second time. The named-slot bytes and RTC were checked exactly. Three replacement backups remain in the local `review1/save-backups.tar`.

A real ServerSaveFlow with the RomM backend refused existing raw RetroArch save 95 before any commit marker for both Play and Copy to the player's own `battery` slot. The GUI then repeated Play on that same save through GBA.emu's Y › Saves page. It displayed the format explanation and did not launch a game or create a battery head. Unit tests separately verify that refusal happens before archive, selection intent, promotion, and `beforeCommit`; foreign server-slot copies remain available.

GBC.emu's actual shared save directory was selected in the official app in the first pass. Foldcade discovered the exact folder with optional all-files access and the emulator's own retained grant; SAF access to the same folder was also checked. Finder tests cover those routes, ambiguity and missing emulator access. Corrected App Folder hints use `GB` and `FC-NES`.

## Flycast access result

Upstream fixes the official Android home to `getExternalFilesDir(null)`; Home Folder Import/Export copies shared trees without changing the live home. Thor's actual Android picker opened `Android/data/com.flycast.emulator/files` and allowed a persistent read/write grant. Discovery found and bound that exact grant. However, production `DocumentsContract.createDocument` failed to create the throwaway `data/FCQA261104_vmu_save_A1.bin` with `Permission denied`. A non-root shell creation was denied too. The VMU was never created, and no baseline file was modified. The picker screenshot and short DocumentsContract log are retained. Because a nominal grant cannot establish writable live storage, Flycast sync code was removed from the final implementation. Shared exports are never presented as active syncing homes.

## Screenshots

- `gbc-found.png`: discovered GBC.emu path in Settings (first pass).
- `gbc-active-folder.png`: the official GBC.emu save-directory choice (first pass).
- `persistent-ex-badges.png`: after force-stop/restart, unconfigured MD.emu and NES.emu remain amber; configured GBA.emu, GBC.emu and Snes9x EX+ remain green. The shelf layout is unchanged.
- `gba-format-refused.png`: actual Y › Saves format refusal with the original RetroArch row still present.
- `flycast-live-picker.png`: real live Android/data tree selected; this grant subsequently failed its write check.

## Cleanup and baseline proof

First pass: deleted exactly probe save IDs 99–107 and its two QA device registrations. All 33 baseline RomM rows and all 136 baseline reachable device-save SHA-256 hashes were unchanged.

Final pass: deleted exactly probe save IDs 108–110, the dedicated probe device, and the isolated app's own device registration. The earlier failed Flycast probe registered one device and zero saves; that device was deleted separately. Final RomM save count is 33. Every baseline row retained its hash, filename, slot, emulator, size and update time. All 137 baseline device-file hashes matched after cleanup: the original 136 files plus Flycast's unchanged empty `emu.cfg`. The final count was also 137. The isolated build and exactly its fixtures were removed. HOME stayed `app.foldcade`; stayon was set back to false and the Thor turn released.

Official free apps installed for QA and left installed:

- Flycast v2.7-1-g44e4c7b50 — `com.flycast.emulator`.
- GBA.emu 1.5.85 — `com.explusalpha.GbaEmu`.
- GBC.emu 1.5.85 — `com.explusalpha.GbcEmu`.
- NES.emu 1.5.85 — `com.explusalpha.NesEmu`.
- Snes9x EX+ 1.5.85 — `com.explusalpha.Snes9xPlus`.
- MD.emu 1.5.85 — `com.explusalpha.MdEmu`.

Flycast came from the project's official v2.7 GitHub release. EX APKs came from the project's official Pre-release archives at source `1c12fac5`. No payment, store login, mirrored APK or root was used.
