# Thor QA: EX saves, 4 November 2026

Final scope: GBA.emu, GBC.emu GB/GBC, NES.emu/FDS, Snes9x EX+ with S-RTC/SPC7110 RTC, MD.emu Genesis/Master System/Sega CD. Flycast remains launch-only after its real live-folder access check failed. M64Plus and Pizza Boy are deferred for the source-linked limits in the PR.

All QA used the isolated application `app.foldcade.saves_more_emulators_2`, CI music assets, and throwaway save bytes through production ContentResolver, PlayerSaves and RommClient code. The temporary debug activities were removed before the final commit. No library game was launched. The final round ran a self-authored SRAM test program in the official GBA.emu app. The real `app.foldcade` application was never installed over, cleared, reconfigured or uninstalled.

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

## Final review fixes and live emulator check

The branch was rebased onto main after #367 merged as `975ca9c9`. Main uses plugin API minor 22; root archives now use minor 23, and format validation uses minor 24. The full forced suite passed again (187 tasks).

GBA ZIP, GBC ZIP/RTC and SNES ZIP/named-slot routes were repeated. SNES included its 20-byte root `.rtc` and named `auto.srm`. A synthetic `saves/run 2/auto.rtc` also checked filter/placement behavior; upstream never writes a named RTC. GBA/GBC batteries and both clocks always remain at the root across named state slots. Each archive round-tripped and placed twice through production ContentResolver/PlayerSaves/RommClient. The real Y › Saves screen also repeated the raw RetroArch refusal using the final shorter copy. Renamed-ROM opt-out and the distinction from a failed provider write have regression tests; unreadable or hash-invalid bytes do not grant that opt-out.

The official GBA.emu app initially had Content Folder saves and no recent content. Its Android picker was used to select `saves/GBA.emu`, matching Foldcade's root. A self-authored 128 KB GBA test ROM writes a counter and marker to a 32 KB SRAM file. The source is `smoke.s`; it uses no game assets or BIOS. Two copies, `Foldcade SRAM A.gba` and `Foldcade SRAM B.gba`, were opened through the production EX PlayerIntent and Android intent conversion, including NewTask/ClearTop/ClearTask and the FileProvider read grant.

- A loaded seeded counter 0x20 and wrote 0x21 plus marker 0x51 in `Foldcade SRAM A.sav`.
- After returning to Foldcade, its actual archive uploaded to a throwaway ROM 23 slot and downloaded with a matching hash.
- With GBA.emu in the background, B launched successfully, restarted the emulator process, and wrote 0x31 from seed 0x30. That archive also round-tripped.
- Foldcade then backed up A and placed a replacement with counter 0x40. A relaunched and showed EX's older-autosave warning. Declining it produced 0x41 and another verified round-trip.
- An additional same-game check showed that Main Slot can resume its autosave without running the boot path again. The battery stayed at the placed bytes. Selecting Main Slot (No State), as now required by the guide, restarted from those bytes, wrote 0x41 and round-tripped successfully. Foldcade cannot read or rewrite EX's private launch-mode preference. The warning alone is not a substitute for that mode.

The recorded emulator PIDs changed from 30412 (A) to 30598 (B) to 30762 (A reload), confirming real process restarts. `smoke-proof.json` records the counters and probe IDs. This closes the launch-flags and real-write-path QA gaps using throwaway data.

The prior Main Slot and Content Folder choices were restored in the official app. Its initially unused QA installation was then restored from the same official APK, removing the temporary URI grant and test-only recent entries; it remains installed with the original defaults. Other official QA apps remain installed.

Final cleanup deleted exactly rows 111–117 and both QA device registrations. All 33 baseline RomM saves retained their fingerprints. All 137 baseline device-file hashes matched. Backups were exported to the local `review2/save-backups.tar`. The isolated Foldcade app, ROM fixtures, batteries, clock files and test autosave states were removed. HOME stayed `app.foldcade`; stayon was false and the turn released.

Additional images:
- `gba-live-write.png`: self-authored test executing in official GBA.emu.
- `gba-save-root.png`: the official app's chosen GBA.emu save directory.
- `gba-no-state.png`: Main Slot (No State) used for the final fresh battery launch.
- `gba-autosave-warning.png`: actual older-state warning after a placed battery.
- `gba-format-refused.png`: final Y › Saves refusal copy.


## Final small review fixes

Round 3 found no P0–P2 issues and three P3 UI/docs issues. The renamed-ROM launch dialog now names the file-name check and offers only Play without syncing / Not now, initially focused on Not now. Each EX app’s Set up saves tile visibly lists Options › System › Launch Mode › Main Slot (No State), both before and after folder setup. The docs now distinguish NES/SNES SRAM/MD named batteries from GBA/GBC root batteries and root clocks. Tests also label synthetic nested clock fixtures accurately.

A short Thor UI pass used temporary debug-only model seeding with the actual shell renderer, without connecting RomM, launching a game or writing any save. The requirement fits on the top screen in both missing-folder and configured-folder states. The bottom-screen mismatch dialog fits and has the safe focus. The harness was removed before the final forced suite and commit. The isolated build was uninstalled; it had no RomM device registration or save backups. The final 137 paths and hashes exactly equal the prior baseline. HOME remains app.foldcade, stayon is false and the turn is released.

New screenshots:
- `ex-setup-required.png`: required launch mode before picking a folder.
- `ex-setup-found.png`: required launch mode remains visible on the configured tile (seeded UI state).
- `renamed-save.png`: file-name explanation and Not now focused, with no setup dead end (seeded dialog).
