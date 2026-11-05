# uxr-routing QA

Builder: GPT 6.1 Sol. Branch uxr-routing. APK app.foldcade.uxrrouting, built with CI music assets and CI branding. Thor turn approximately 20:17–20:33 Pacific. Final app code 89d0d784 (the APK precedes only the final commit, not its code).

## Automated

`./gradlew testDebugUnitTest --rerun-tasks` passed after the on-device correction: 187 Gradle tasks executed, 2675 tests, 0 failures/errors, 2 skips. Log `/tmp/uxr-routing-tests-qa2.log`. Tests cover form companion selection, every R1 tile handing off with no drawer, Settings activity destination independent of input screen, own-activity page preservation and brightness, guide key blocking and restoration, plus RomM subpage navigation and form returns.

## Curated captures (all verified visually)

- `after-licenses-{top,bottom}.png`, `after-licenses-back-top.png`: opened via display 4 while Settings was on top; activity stays top, bottom has bright Settings command center; B on display 0 returns to the same row.
- `after-themes-{top,bottom}.png`, `after-themes-back-top.png`: same rule for Themes.
- `after-romm-actions-top.png`, `after-romm-edit-back-top.png`, `after-romm-source-back-top.png`: actions remain inside Settings; B from editing returns to actions; B from actions focuses the original source tile.
- `after-romm-form-bottom.png`, `after-art-form-bottom.png`: clean page companions, no grid or borrowed form hints. Do not publish the RomM form's top screenshot; it contains the configured host.
- `after-r1-{wifi,bluetooth,battery,settings}-bottom.png`: final captures are after the corrected activity-destination build; all show a clear command center. The matching state files identify Android's actual Wi-Fi panel, ConnectedDeviceDashboard, PowerUsageSummary, and SettingsHomepage activities. Return used Android Home, keeping the original HOME role restored at cleanup.
- `after-library-picker-top.png` + bottom: picker on the bottom, guide on top. `after-library-picker-on-top-bottom.png` + top: picker on top, guide on bottom. Both guide screens show the folder object + Library + Tap Use this folder, then Allow. Cancelling restores the Settings page (`after-library-cancel-top.png`). No folder grant was changed.
- `after-cores-picker-top.png`: RetroArch object and guide while Android's core-folder picker owns bottom. Cancelled without granting or modifying RetroArch.
- `after-backup-picker-bottom.png`: opaque compact Settings while Android's file picker owns top; no Home labels between rows. Export was cancelled.
- `layout-current-settings-{top,bottom}.png`: Layout = Top only on top, bottom black. `after-top-only-themes-verified-{top,bottom}.png`: Themes stays on top with the bottom black. These are the verified Top-only captures; earlier layout-named exploratory captures are not evidence.
- `after-own-page-open-app-{top,bottom}.png`: touching the companion's Open an app returns from Themes to Home.

The first activity check exposed an existing cross-screen side-effect bug: a Settings key received on display 4 launched Licenses on display 4. Fixed by launching from the Home that owns Settings' screen, with a new model test, then rebuilt/reinstalled and repeated the checks. No other product change arose from QA.

## Cleanup

No files/save-backups directory existed. Deleted this test install's registered RomM device (HTTP 204), uninstalled app.foldcade.uxrrouting, restored HOME to app.foldcade (confirmed dumpsys role; preceding pages session's role dump supplied the original because this firmware does not support get-role-holders), Wi-Fi remains its original on state, stay-on restored false. Released the turn; uxr-saves acquired it next. No release preferences, grants, saves or emulator settings were changed.

## Final review fixes and second Thor pass

Final head: 43efc3b99b11df256c62f332a85e9376aecea4ff, rebased onto main including #377 and #381. Required full suite rerun on that base passed: **2692 tests, zero failures/errors, two skipped; 187 tasks executed**. Log: `/tmp/uxr-routing-tests-rebased.log`. The previous independent branch run had 2677 passing tests; the added base contains the remaining tests.

Opus round 1's four findings were all verified and fixed: one-tap form exit to Home; accurate root-folder guidance; animated guide overlay preserving the mounted body; neutral art symbol beside SteamGridDB. Opus round 2 returned NO FINDINGS and rated both surfaces 9/10 pending fresh Thor evidence. That fresh pass confirmed:

- `second-art-form-bottom.png` and `second-art-open-app-bottom.png`: neutral art symbol, then one tap to the Home grid.
- `second-romm-form-bottom.png` and `second-romm-open-app-bottom.png`: one tap from the RomM form to Home, bypassing the return Settings page.
- `rebased-library-guide-{top,bottom}.png`: Android is at its disabled storage root; the free screen accurately says “Pick your games folder. Tap Use this folder, then Allow.” The text fits at the Thor's native size.
- `guide-transition-bottom.mp4` / `guide-motion-sheet.png`: entry from the Home grid through Settings, then the guide rising over the existing companion, then fading back to the companion on cancellation. No hard cut or page reset. The top page returns to its original Add row (`rebased-library-cancel-top.png`).

The second pass also caught a companion input gap beyond round 2: B on display 4 was ignored while Themes occupied the top. `PanelKeyActivity.dispatchPanelMeaning` now forwards decoded meanings to the active owned activity and keeps the parent Settings inactive. Session target assertions cover both physical directions and every layout. Verified on the Thor:

- `final-themes-before-bottom-back-top.png` → `final-themes-after-bottom-back-top.png`: B on display 4 returns Themes to its original Theme row.
- `final-licenses-bottom-scroll-top.png`: D-pad down on display 4 scrolls the top license text.
- `final-licenses-after-bottom-back-top.png`: B on display 4 returns Licenses to its original row.
- `rebased-themes-return-top.png`: the same input rule passed after rebasing and reinstalling with current main.

Second-turn cleanup completed: no RomM device was registered (no credentials were entered), no save-backups directory existed, test package uninstalled, original Home holder restored from dumpsys role, original Wi-Fi state retained, stay-on false, turn released. No pending device work remains. Final small Opus re-check covers the input-forwarding addition and these fresh captures.

## Final integration and long-press check

Head **babcf82b77a4b2fa5056d2801683c4b9314e3332** includes all merged Library, Grid, Dialogs, Saves and Pages work through main c548ccc3. Resolved the About companion overlap by retaining its game cover and platform secondary line; wired Library-owned rommPageDetail(model) into SettingsInner.Romm.

Final required suite: **2,722 tests, zero failures/errors, two skipped; 187 tasks executed**, `./gradlew testDebugUnitTest --rerun-tasks`, BUILD SUCCESSFUL. Log `/tmp/uxr-routing-tests-final.log`. Final APK manifest/hash: `build-final.json`; assembleDebug passed with isolated .uxrrouting suffix, CI music and branding.

Third Thor turn (22:03–22:10 Pacific approximately):
- Compared the previously pushed 43efc3b9 APK with the final integrated APK using display-4 `input keyevent --longpress KEYCODE_BUTTON_B` beside Themes and Licenses. Both retain the focused original Settings row. Final proof: `held-final-themes-after-top.png`, `held-final-licenses-after-top.png`. `held-final-next-back-top.png` proves the later fresh B still returns to categories.
- Round 3's held-Back P2 is **skipped after verification**: existing Meaning.kt returns null for repeated Back before HomeActivity routing. The existing LicensesRowTest checks repeats 1..20 for standard/swapped faces, system Back and Escape, and a fresh press. The initially proposed extra guard/API/direct-Meaning test were removed as redundant. See `review3-disposition.md`.
- Cleanup: no save-backups directory, no RomM device registration (no credentials entered), QA package uninstalled, original HOME app.foldcade restored, original Wi-Fi on retained, stay-on false, turn released. Release data/grants were untouched.

Opus round 4 completed: **NO FINDINGS**, companions and guide **9/10**. It independently confirmed the held-Back objection is blocked by the existing input map and the extra guard should stay removed. It also checked native forwarding and the Library/Pages integration overlap. All scoped findings are resolved. Optional future work: Themes' ZIP document picker continues to show the plain Settings companion; the folder guide intentionally covers tree selection only.

GitHub checks read once at handback: testDebugUnitTest, home music render, versionCode and generated icons passed; lintDebug and unsigned release dry-run were still running. CI/bot-review follow-through and merging belong to the orchestrator; this stream did not watch or merge.
