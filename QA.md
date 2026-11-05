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
