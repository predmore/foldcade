# Thor save QA: saves-more-emulators-2

The isolated build was app.foldcade.saves_more_emulators_2. Its temporary debug QA activity called the production ContentResolver/PlayerSaves/RommClient pipeline with seeded throwaway bytes, not emulator gameplay.

Nine routes passed: Flycast, GBA.emu, GBC.emu GB and GBC, NES.emu FDS, Snes9x EX+, and MD.emu Genesis, Master System and Sega CD. Each resolved one archive, isolated the game's files, uploaded to a unique foldcade-probe slot on Gain Ground (ROM 23), downloaded and hash-checked it, rejected a replacement over unseen local progress, handed that progress over, backed it up, restored the downloaded archive and checked the original bytes. GBC bundles contained .rtc. Foreign saves and the shared B1 VMU survived. Nine replacement backups were exported to save-backups.tar.

Official free installed apps, left installed:
- Flycast v2.7-1-g44e4c7b50: com.flycast.emulator, official v2.7 GitHub APK.
- GBA.emu 1.5.85: com.explusalpha.GbaEmu.
- GBC.emu 1.5.85: com.explusalpha.GbcEmu.
- NES.emu 1.5.85: com.explusalpha.NesEmu.
- Snes9x EX+ 1.5.85: com.explusalpha.Snes9xPlus.
- MD.emu 1.5.85: com.explusalpha.MdEmu.
All EX APKs are from the project's official Pre-release archives at source 1c12fac5. No store login, payment, mirrored APK, root or real release-app reconfiguration was used.

GBC.emu's own Options > File Paths > Saves was set to the newly created shared GBC.emu directory through Android's real picker. Foldcade then discovered that exact directory with all-files access and the emulator's own held grant; the same folder was also granted to Foldcade through SAF. The finder unit tests separately cover both access routes, ambiguous copies and missing emulator access. Flycast's seeded emu.cfg was read through its manually chosen QA root; the official Flycast menu was opened, but native menu input did not activate Settings, so active-home selection in the official app was not claimed as a completed device check.

UI screenshots:
- gbc-found.png: automatically found path in Settings.
- persistent-badges.png: GBA.emu and Flycast remain amber after restarting with no reachable folder, while configured GBC.emu stays green.
- flycast-shared-warning.png: shared-VMU configuration remains Not syncing.
- server-gba-y.png and gba-saves.png: GBA.emu Y > Saves reads an existing RomM save without changing it.
The setup-dialog copy was subsequently corrected from General to the source-verified Controls tab; the older dialog screenshot is not published.

Cleanup: deleted exactly probe saves 99–107 and the two QA device registrations. All 33 baseline RomM rows retained their content hash, filename, slot, emulator, size and update time. All 136 baseline device-save SHA-256 hashes matched after cleanup; final reachable-save file count also remained 136. The isolated app and its throwaway fixtures were removed, with all save backups retained locally. HOME stayed app.foldcade; stayon was restored to false and the Thor turn released.

The Settings > Emulators shelf layout is unchanged and belongs to the separate layout task.
