# Thor QA audit — 11 October 2026

Tested commit `f5efb7b`, following the fixes documented in `qa-thor-2026-10-11.md`. This pass found four functional bugs, two performance concerns, and one library-labeling issue. No crash or ANR was observed in the tested flows.

## Environment and evidence

- Physical AYN Thor, Android 13, 1920 × 1080 top and 1240 × 1080 bottom displays, both configured for 120 Hz.
- Isolated application ID `app.foldcade.qaaudit`. Debug and non-debuggable release APKs were built from this checkout with the same packaged music and theme assets. The release APK used the local development key for the isolated test identity.
- RomM credentials and SteamGridDB key were read from the user's configuration and exercised through setup. The real RomM catalog contains 32 ROMs across five populated systems.
- Screenshots, API response counts, raw graphics samples, CPU measurements, reproduction probes, and build logs are retained under `captures/qa-audit-2026-10-11/`, which Git ignores. Credentials are not included in this report.
- Another session repeatedly opened `app.foldcade.themeqa` on the device. Accepted performance samples checked that this QA install owned both focused displays throughout; a repeat interrupted by that app was discarded. Some folder-picker captures include that other app on the unused screen.

## Findings

### AUD-01 · P2 · SteamGridDB save does not respect cancellation

Open Settings → Library & data → SteamGridDB, enter a valid key, select Save key, and immediately press Back. The form closes, but revisiting Settings shows **Forget key**: the submission still saved the key. The implementation can also publish a late error or close a subsequently opened form. Save key never enters the busy state, so repeated confirmations can submit duplicate checks.

[HomeActivity.kt:1398](../app/src/main/kotlin/app/foldcade/HomeActivity.kt#L1398) launches validation in the application scope without the ticket/current-form checks used by `validateRommToken` at line 1224. Use the shared validation guard, loading state, and invalidation on edits/close for this path too.

Evidence: `art-key-cancel-immediate-*.png`, `art-key-cancel-late-*.png`, `art-cancel-settings-top.png`.

### AUD-02 · P2 · Expired and denied device codes still invite approval

In Add a RomM server, enter the address of the controlled RomM API fixture. It returns a normal challenge, then either `expired_token` or `access_denied` on the first poll. Foldcade continues to show **Approve this code on your phone** and the obsolete code. There is no terminal error or retry action, and no replacement challenge is requested. Closing and reopening the form works around it.

Both outcomes were reproduced on the Thor with the release build. The fixture recorded exactly one heartbeat, one initialization, and one token poll per visit. This tests the real app's handling of those responses; it does not claim that a real server's approval flow or full expiry timer was exercised.

[HomeActivity.kt:1360](../app/src/main/kotlin/app/foldcade/HomeActivity.kt#L1360) handles only `Approved`; `pairedFor` subsequently prevents another attempt at the same address. Display expiry/denial and offer a new challenge. The client already distinguishes these outcomes.

Evidence: `pairing_stub.py`, `pairing-requests.jsonl`, `expired-code-still-shown-bottom.png`, `pairing-denied-bottom.png`.

### AUD-03 · P2 · Toolbar focus disables L1 and R1

Open All library, press Up to reach its toolbar, then Right to focus the sort control. L1 and R1 do nothing, although their pills remain visible. Down followed by L1 opens the menu normally. This was reproduced on the release build with both displays owned by Foldcade.

[HomeSession.kt:424](../app/src/main/kotlin/app/foldcade/HomeSession.kt#L424) handles every unrecognized meaning while `allChrome` is focused. Forward the shoulder meanings to the shared menu reducer, as the tile branch already does. A standalone expectation probe also fails with `L1 handled=true, R1 handled=true`.

Evidence: `release-toolbar-before-*.png`, `release-toolbar-after-*.png`, `release-tile-shoulder-control-*.png`, `toolbar-probe.xml`.

### AUD-04 · P2 · Adding an artwork source cannot enrich a partial hero

Browse a game before adding a SteamGridDB key so its scene contains only a title screenshot. Save a valid key and revisit it: the previously cached partial scene stays in use. The release build continued to show the older screenshot/box treatment for 3D Sonic the Hedgehog 2. The supplied key can retrieve a hero and logo for that exact game; Super Mario 3D Land and Tetris DS also have both.

The isolated artwork probe makes the cause deterministic: after adding the source and calling the same `forgetMisses()` operation used by setup, `findScene` still returns a screenshot-only scene and makes **zero** calls to the newly available source. Partial hits have no refresh path in [ArtFinder.kt:54](../artwork/src/main/kotlin/app/foldcade/artwork/ArtFinder.kt#L54) / `ArtIndex.lookup`; [PanelScreen.kt:1229](../app/src/main/kotlin/app/foldcade/ui/PanelScreen.kt#L1229) also fetches only when the entire cached scene is null.

Retain existing parts while retrying missing parts when sources change or an interrupted lookup recovers. Do not discard the useful screenshot.

Evidence: `artwork-probe.xml`, `available-hero-art.json`, `release-off-games-top.png`, `release-toolbar-before-top.png`.

### AUD-05 · P2 · Ribbons consumes substantial CPU at idle

On the unchanged home root, Ribbons / Slow used **39.3–43.0% of one CPU core** across two accepted release samples. Background Off used **0.1–0.3%** across two samples and rendered no frames. Music and the library stayed the same. The animated samples rendered 645–719 frames over roughly six seconds, aggregated across both surfaces.

This is an idle resource cost, not a measured battery-life or thermal result. The clear Off comparison points to the backdrop's continued frame scheduling. Profile its geometry/shader submission and evaluate a lower idle cadence while preserving navigation animations.

### AUD-06 · P3 · Game artwork transitions still miss frame deadlines

With Background Off, 24 alternating controller events between the first two All Games tiles produced **50 missed deadlines / 1,687 frames (2.96%)**, p95 **21 ms**, p99 **31 ms**, and **64.0% of one core**. The root's equivalent Off sample produced 27 / 1,311 misses (2.06%), p95 11 ms, p99 18 ms. The game transition workload still needs profiling, particularly its artwork crossfade and blurred cover treatment.

These are short observations of this build, not a regression comparison with the earlier QA pass, a sustained stress test, or proof of a particular rendering bottleneck. Graphics summaries aggregate both app surfaces; recent frame-buffer samples are separate metrics in the raw data.

Evidence for AUD-05/06: `performance.jsonl`, `gfx-release-*.txt`, `perf.py`.

### AUD-07 · P3 · “All Games” excludes PC, Android, and streamed games

The All Games tab filters to `HomeKind.Rom` only. PC games imported through `.steam` files, Android games, and Moonlight games are categorized under All Apps instead. A successful import can therefore appear to have done nothing when a user checks All Games.

This is intentional in [HomeBoard.kt:414](../language/src/main/kotlin/app/foldcade/language/HomeBoard.kt#L414), but the labels do not explain the distinction. Include the game kinds in All Games, or use labels that describe the existing grouping.

Evidence: `gamenative-folder-bottom.png`, `all-apps-pc-games-bottom.png`, `offline-local-tetris-selected-bottom.png`.

## Validation and limits

The normal `testDebugUnitTest` run passed **685 tests with zero failures, errors, or skips**, including Gradle cache results. Both debug and unsigned release builds assembled, including API 33 signature checks and release lint. Two additional, isolated QA expectation probes deliberately assert the desired toolbar/artwork behavior and fail against this commit; their sources and outputs are retained with the captures, outside the normal suite.

The device pass exercised first-run dismissal, real RomM token setup and catalog loading, SteamGridDB setup, controller navigation and menus, a cancelled remote transfer, a downloaded Super Mario 3D Land handoff that rendered on both screens, folder-picker permission flow, settings changes, and release rendering measurements.

Real device-code approval, save upload/conflict handling, actual Moonlight streaming and GameNative gameplay, and long-duration battery/thermal behavior remain outside this pass. The previous pass's successful Home-role checks are not presented as new measurements here.

## Final recovery checks and cleanup

- The Steam folder imported five exported PC games. They appeared in the GameNative folder and All Apps, including after the offline restart. The DS folder imported its local Tetris DS alongside the two RomM DS games.
- With Wi-Fi off, a release process cold start restored the RomM titles, cached covers, and Tetris DS hero screenshot. Local Tetris DS launched through its document URI and rendered its title/menu across both screens in melonDS. The game was closed through melonDS's native Exit action.
- Re-enabling Wi-Fi retained the same Foldcade process (PID 24668) and usable library. Sleep/wake restored both surfaces.
- Cleanup uninstalled only `app.foldcade.qaaudit`, removed the test reverse port, and stopped the local fixture. The original release/debug/QA/motion Foldcade installs were retained. Cocoon remained the Home app; Wi-Fi, stay-awake, and display power returned to their initial states: off, `0`, and both screens off.
- Application implementation files and source ROMs were not changed. The only repository change is this report; temporary probes and captures remain in the ignored evidence directory.

Final evidence: `offline-remote-art-restored-*.png`, `offline-tetris-rendered-*.png`, `reconnected-library-*.png`, `sleep-wake-*.png`, `cleanup.json`.
