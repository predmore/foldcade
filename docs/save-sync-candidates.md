# Save-sync candidate audit (4 November 2026)

This covers all 21 emulator rows in the original shared table. Foldcade collects no Thor emulator-usage counts, so **demand below is an estimate**, not a measured player count. PSP and GameCube come first as requested; next priority balances likely platform demand against source-verifiable, isolated saves. On the shared Thor, only NetherSX2/AetherSX2 and EmuCoreX from this table were installed before QA. Neither PSP nor GameCube exists in its current RomM catalog (38 games).

| Rank | Emulator | Estimated demand / complexity | Verified save unit and next step |
| --- | --- | --- | --- |
| 1 | PPSSPP | High / medium | **Implemented:** `PSP/SAVEDATA/<DISC_ID…>/` folders, one prefix bundle. ISO, CSO v1 and PBP PARAM.SFO; no title/filename guesses. [PSP source](#psp). |
| 2 | Dolphin | High / medium | **Implemented:** GameCube ISO/GCM, default Slot A, per-game GCI files. Shared raw cards remain untouched. Next: RVZ blob reader and Wii TMD/title-ID resolution. [Dolphin source](#dolphin). |
| 3 | Flycast | Medium / medium | Per-game A1 VMU `<sanitized gameId>_vmu_save_A1.bin` when `PerGameVmu` is on; other ports/shared mode use `vmu_save_<port>.bin`. Read `Dreamcast.VMUPath`, data/config fallbacks, and legacy ROM-name paths; never assign a shared VMU to one game. [Flycast source](#flycast). |
| 4 | M64Plus FZ | High / medium-high | `.eep`, `.sra`, `.fla`, `.mpk` named from the core's `goodname`, below the game's `SramData`, or flat mode. Game directory uses header name + country + ROM MD5, with MD5-only fallback. Current app keeps GameData private; export/import access must be solved before auto-sync. [N64 source](#n64). |
| 5 | Mupen64Plus AE (M64Plus) | Lower / medium-high | Same four SRAM/controller-pak formats and directory derivation in its own package; do not infer FZ's private folder from the AE package. [N64 source](#n64). |
| 6 | GBA.emu | Medium / low-medium | `<contentName>.sav`; selected save directory, writable content directory, or `EmuEx/<shortSystemName>/saves` fallback. Need active-directory discovery before declaring sync. [EX source](#ex-family). |
| 7 | GBC.emu | Medium / low-medium | `<contentName>.sav`, same directory policy. [EX source](#ex-family). |
| 8 | Snes9x EX+ | Medium / low-medium | `<contentName>.srm`, same directory policy. [EX source](#ex-family). |
| 9 | NES.emu | Medium / low-medium | `.sav` battery file, `.fds.sav` for FDS disk changes, same directory policy. [EX source](#ex-family). |
| 10 | MD.emu | Medium / medium | `.srm` for cartridges; `.brm` for CD backup RAM, same directory policy. Check any auxiliary files and ROM naming before packaging. [EX source](#ex-family). |
| 11 | Pizza Boy GBA | High / unverified | No public upstream implementation was available to verify the current Basic/Pro save format, active folder or RTC companions. Investigate official APK behavior; do not ship a guessed `.sav` path. |
| 12 | Pizza Boy GBC | Medium / unverified | Same source/access blocker; verify RTC and Basic/Pro behavior together. |
| 13 | Pizza Boy SC | Lower / unverified | Same source/access blocker; verify cartridge and CD behavior separately. |
| 14 | My Boy! | Medium / unverified | No verifiable current upstream save writer; active storage and companion files need an official-app probe. |
| 15 | My OldBoy! | Medium / unverified | Same blocker; RTC handling needs verification too. |
| 16 | Dolphin MMJR | Lower / medium-high | Fork-specific card configuration and storage must be audited independently; official Dolphin's settings are not evidence for every fork. |
| 17 | Saturn.emu | Lower / high | `.bkr` backup RAM, `.smpc` RTC, cartridge-dependent NV files; Mednafen filename suffixes can include MD5. Not a single battery file. [EX source](#ex-family). |
| 18 | Yaba Sanshiro 2 | Medium / unverified | The developer's published upstream URL was unavailable during this audit. No path/format claim is made. Verify its backup-memory implementation and per-game mode before sync. [Developer's source statement](https://www.yabasanshiro.com/terms-of-use). |
| 19 | Redream | Medium / unverified | No current public upstream writer was available. VMU containers may be shared; require an official-app probe before a per-game declaration. |
| 20 | NetherSX2 / AetherSX2 | Medium / high | On this Thor, verified PCSX2-lineage cards are `files/memcards/*.ps2`; they can hold many games. No current upstream Android writer/configuration source is available to verify a safe per-game implementation. ARMSX2 already provides supported PS2 folder-card sync. |
| 21 | EmuCoreX | Lower / high | Same shared-card problem; its current Android writer/configuration lacks verifiable public upstream source. ARMSX2 remains the supported PS2 route. |

Do not turn the unverified rows into declarations based on these estimates. A format without a reachable active folder still cannot sync safely. No implementation in this PR reads another app's private files by bypassing Android permissions.

## PSP

Sources checked at PPSSPP commit `3bc60270eae3069f13dfbfdb7292a7e1375de6f6`:

- [SavedataParam.cpp](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/Dialog/SavedataParam.cpp): `ms0:/PSP/SAVEDATA/`, game name + save suffix, PARAM.SFO companions.
- [ParamSFO.cpp](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/ELF/ParamSFO.cpp), [PBPReader.cpp](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/ELF/PBPReader.cpp), [BlockDevices.cpp](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/Core/FileSystems/BlockDevices.cpp): metadata and CSO v1 layout/DEFLATE handling.
- [MemStickScreen.cpp](https://github.com/hrydgard/ppsspp/blob/3bc60270eae3069f13dfbfdb7292a7e1375de6f6/UI/MemStickScreen.cpp): picked PSP folder, legacy storage-root choice, app external-files choice. A game's nonstandard savedata alias cannot safely be derived from DISC_ID alone, so this revision does not invent alias mappings.
- [Argosy PrefixBundleFolderHandler](https://github.com/rommapp/argosy-launcher/blob/dbe576f3cfa5dfb9db7001f3d2de7f473e5238fe/app/src/main/kotlin/com/nendo/argosy/data/sync/platform/PrefixBundleFolderHandler.kt): all prefix-matching folders are zipped under their original names.

## Dolphin

Sources checked at Dolphin commit `df4bd441dec5d6f3cff606eea76f183963e97a44`:

- [VolumeGC.cpp](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/DiscIO/VolumeGC.cpp) and [VolumeDisc.cpp](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/DiscIO/VolumeDisc.cpp): ID/header and authoritative GC region word. This implementation requires the uncompressed disc image rather than inferring region from an RVZ filename or country letter.
- [MainSettings.cpp](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/Core/Config/MainSettings.cpp): default folder card, region directories, custom path rules and raw-card paths. [EXI_Device.h](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/Core/HW/EXI/EXI_Device.h) defines device type 8 as `MemoryCardFolder`.
- [GCMemcardDirectory.cpp](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/Core/HW/GCMemcard/GCMemcardDirectory.cpp): GCI filenames, identity headers, shared `MC_SYSTEM_AREA`. [GameConfigLoader.cpp](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/Core/ConfigLoaders/GameConfigLoader.cpp): game/system/region/revision INI precedence.
- [DirectoryInitialization.kt](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Android/app/src/main/java/org/dolphinemu/dolphinemu/utils/DirectoryInitialization.kt) and [DocumentProvider.kt](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Android/app/src/main/java/org/dolphinemu/dolphinemu/features/DocumentProvider.kt): package external-files versus legacy `dolphin-emu`, provider authority `<package>.user`, document root `root`.
- [Argosy GciSaveHandler](https://github.com/rommapp/argosy-launcher/blob/dbe576f3cfa5dfb9db7001f3d2de7f473e5238fe/app/src/main/kotlin/com/nendo/argosy/data/sync/platform/GciSaveHandler.kt): flat GCI bundle layout. Foldcade always uses a ZIP for this slot; loose GCI imports are not yet transformed automatically.

Wii's `Wii/title/<type>/<title-id>/data` is a different save unit ([upstream NandPaths.cpp](https://github.com/dolphin-emu/dolphin/blob/df4bd441dec5d6f3cff606eea76f183963e97a44/Source/Core/Common/NandPaths.cpp)). A correct follow-up must read the actual title ID (TMD/partition), preserve NAND metadata, and verify encrypted ISO, WBFS and RVZ handling against upstream. It must not assume the first four disc-ID characters are the NAND title ID.

## Flycast

Checked at `7adbf3caef251b153e348df3f469a0f35878339d`: [oslib.cpp](https://github.com/flyinghead/flycast/blob/7adbf3caef251b153e348df3f469a0f35878339d/core/oslib/oslib.cpp) `getVmuPath` owns the filename and fallback policy; [option.cpp](https://github.com/flyinghead/flycast/blob/7adbf3caef251b153e348df3f469a0f35878339d/core/cfg/option.cpp) defines `Dreamcast.VMUPath` and per-game VMU defaults. An active per-game VMU is promising; a shared VMU is not one game's save.

## N64

FZ `f3c381bcc58e4adce897aba323193d3f407e30e3`: [GamePrefs.java](https://github.com/fzurita/mupen64plus-ae/blob/f3c381bcc58e4adce897aba323193d3f407e30e3/app/src/main/java/paulscode/android/mupen64plusae/persistent/GamePrefs.java) defines `SramData`, MD5/header/country directory naming and flat mode; [AppData.java](https://github.com/fzurita/mupen64plus-ae/blob/f3c381bcc58e4adce897aba323193d3f407e30e3/app/src/main/java/paulscode/android/mupen64plusae/persistent/AppData.java) puts active GameData in internal app files; [main.c](https://github.com/fzurita/mupen64plus-ae/blob/f3c381bcc58e4adce897aba323193d3f407e30e3/mupen64plus-core/upstream/src/main/main.c) names `.eep`, `.sra`, `.fla`, `.mpk`. AE checked at [33bf7021549d7873e976884370fbd1a8e7333bc7](https://github.com/mupen64plus-ae/mupen64plus-ae/blob/33bf7021549d7873e976884370fbd1a8e7333bc7/app/src/main/java/paulscode/android/mupen64plusae/persistent/GamePrefs.java). Do not confuse an exported `GameData` copy with the active private saves.

## EX family

Checked at `1c12fac5ce49badaadff2e2f210dcc30b89f4943`:

- [EmuSystem.cc](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/EmuFramework/src/EmuSystem.cc): chosen directory, writable content directory and `EmuEx/<shortSystemName>/saves` fallback; content basename + extension.
- [GBA.emu](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/GBA.emu/src/main/Main.cc), [GBC.emu](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/GBC.emu/src/main/Main.cc): `.sav`.
- [Snes9x](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/Snes9x/src/main/Main.cc): `.srm`; [NES.emu](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/NES.emu/src/main/Main.cc): `.sav` / `.fds.sav`.
- [MD.emu](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/MD.emu/src/main/Main.cc): `.srm` / `.brm`.
- [Saturn.emu](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/Saturn.emu/src/main/Main.cc) and [MDFNUtils.hh](https://github.com/Rakashazi/emu-ex-plus-alpha/blob/1c12fac5ce49badaadff2e2f210dcc30b89f4943/EmuFramework/src/shared/mednafen-emuex/MDFNUtils.hh): backup/RTC/cartridge saves and optional MD5 filename components.
