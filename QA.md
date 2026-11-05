# Routing lifecycle follow-up

Builder: GPT 6.1 Sol. Branch uxr-routing-fix, based on merged main 0cea2fe8. Isolated QA application: app.foldcade.uxrroutingfix. No credentials or emulator changes required.

## Claims and implementation

- Dropped input: verified from code. Themes' ZIP picker stops Themes, which previously nulled its activity reference but kept the page marker. The fix replaces that marker with an Android hand-off onStop and restores it onStart. The Settings page moves to the free screen and takes its own inputs while Android covers Themes. Companion forwarding is restricted to a resumed native page. The reference is retained until onDestroy so both page and ordinary command-center Home exits can finish a covered native page.
- Stuck Wi-Fi return: the claimed missing resume restore is not present in current main. FoldcadeHomeActivity.onResume unconditionally calls foldcadeResumed for its own panel. A pause without a stop is enough. Verified on Thor via Done, then Start opening Settings on top.
- Throwing Android launch: fixed. The hand-off wrapper rolls its marker back in finally on a false return or any exception, while preserving exception propagation. Tests inject SecurityException and IllegalStateException on either panel and keep the other panel's app intact.

## Automated

`./gradlew testDebugUnitTest --rerun-tasks`: BUILD SUCCESSFUL, 196 tasks executed, 2733 tests, zero failures/errors, two skipped. `/tmp/uxr-routing-fix-tests.log`.

`assembleDebug`: BUILD SUCCESSFUL with CI music/branding and .uxrroutingfix suffix. `/tmp/uxr-routing-fix-build.log`. APK `/tmp/uxr-routing-fix-qa/foldcade-fix.apk`.

New tests: covered native page displacement/restoration and D-pad handling; stopped-page update cannot overwrite another app or a resumed Home; translucent panel return without onStop; false/throwing launch rollback on either screen.

## Thor

Turn held approximately 22:59–23:08 Pacific, then fully cleaned up and released.

Curated, visually inspected evidence:
- `before-zip-dead-keys-bottom.png`: baseline APK babcf82b, top Android ZIP picker, bottom Settings companion unchanged after display-4 D-pad down and B. `before-zip-cancel-after-open-app-top.png` and its state prove Themes remained after touching Open an app and cancelling the picker.
- `after-zip-live-keys-bottom.png`: fixed APK 18d9341f, Settings now occupies bottom and D-pad down moved focus from Theme to Icons. Fully opaque after entry animation settles.
- `after-zip-cancel-{top,bottom}.png`: cancellation returns to Themes plus bright companion. `after-zip-settings-return-top.png`: companion B returns to Settings on top with Icons still selected.
- `final-covered-home-control-bottom.png`: B backs out of displaced Settings, then out of its browsing grid, to the ordinary Open an app control while the ZIP picker is still on top. `final-covered-open-app-*` then `final-covered-picker-cancel-top.png` and the adjacent state file prove opening Home finished the covered Themes activity: cancelling returns to PrimaryHomeActivity, not Themes.
- `final-wifi-return-{top,bottom}.png`: pressed the Android Wi-Fi panel's Done button, dismissed the fresh empty-library ask, and both Home screens returned. `final-wifi-settings-top.png`: Start from display 4 opens Settings on top, proving the top panel's external marker was cleared. Missing resume-cleanup claim skipped; existing onResume hook is sufficient.
- `final-licenses-scroll-top.png` / `final-licenses-return-top.png`: display-4 down scrolls Licenses, then B returns to its original row on top.

Exploratory files are not evidence: the early `before-wifi-return` used Android Back, which did not dismiss this firmware's non-cancelable Wi-Fi panel. The definitive return uses Done. The early `after-covered-page-*` stopped at a browsing grid and opened All games; the final covered-page sequence above explicitly reached and activated Open an app. Early `after-licenses-*` were interrupted by the empty-library ask; final-licenses-* is the valid native-page pass. No game was launched.

Cleanup: baseline and fixed QA packages uninstalled. No sign-in or game launch, no save backups or RomM device registered. Restored original HOME app.foldcade, retained original Wi-Fi on, set stay-on false, released Thor. No release data/grants or emulator settings changed.
