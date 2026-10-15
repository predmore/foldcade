# Button calibration recovery — 14 October 2026

[Open the tested recovery proof](https://poof.i.socia.dev/poof-4tyfckuf/) — seven stages, 14 native-resolution Thor screenshots, the tested source patch, and verification metadata. Default Poof retention is 30 days. [Original UX tour](https://poof.i.socia.dev/poof-v2z7umpk/).

The fix is prepared on `fix/button-calibration-recovery`, based on `1927447d`. **The signed production app has not been updated and still uses B as Confirm and A as Back.** Restoring A in that installation requires a signed app update. Its preferences were retained.

## Implementation

- Settings → Look → Button labels remains available after calibration or dismissal. Its value shows the current Confirm button; the existing one-time offer becomes a Set up hint.
- Capture explains that any face button can become Confirm and Start cancels. Directional navigation, switching settings, closing Settings, or changing controllers disarms capture without saving a new mapping.
- The Settings header follows the saved Back mapping and shows Start during capture. Help sits beside the header so it does not push the selected row off Thor's screen.
- A selected face button is consumed until release, preventing a held new Confirm from immediately reopening capture. Stale calibration callbacks are ignored after capture ends.
- Existing per-controller preferences remain compatible. Upgrading preserves the saved mapping; the user can revisit Button labels to change it.

## Automated verification

The persistence regression first failed against the original source because Button labels was missing after a saved calibration. It passes with the fix.

Regression coverage includes restoring A from saved B, persistence after controller recreation, repeat calibration, all four face-button labels, dismissal, cancellation paths, stale callbacks, and controller replacement.

```sh
JAVA_HOME='/tmp/foldcade-jdk/jdk-21.0.12.1+1/Contents/Home' \
ANDROID_HOME=/Users/caleb/Library/Android/sdk \
./gradlew -I captures/calibration-recovery-2026-10-14/calibrationqa.gradle \
  testDebugUnitTest :app:assembleDebug \
  -PfoldcadeHomeMusicAssets=/tmp/foldcade-music
```

All 1,128 tests across 141 suites passed, including cached results, with zero failures, errors, or skips. The normal API 33 signature checks and debug build passed. The version-bump guard and `git diff --check` passed. The downloadable patch applies cleanly against baseline source.

## Physical Thor upgrade verification

Used the isolated `app.foldcade.calibrationqa` package on AYN Thor, Android 13, with the same package identity and signing key for the old and fixed builds:

1. Installed the baseline build and reproduced saved B with the calibration option missing.
2. Installed the fixed build as an update, preserving app data. B remained Confirm and Button labels returned.
3. Opened calibration with B and selected A using a long press. A saved, the option remained available, and capture did not immediately reopen.
4. Force-stopped and reopened the app. A remained Confirm and the calibration option was still available.
5. Verified Start cancellation preserves A. Also armed capture, navigated away, then pressed B: B acted as Back and did not change the mapping.

The final screenshots were captured after correcting the help layout. Top display: 1920 × 1080; bottom: 1240 × 1080.

## Artifact verification and cleanup

The proof was checked at 1440 px and 390 px with Poof's opaque-origin sandbox: all seven stages, 14 images, patch download, no browser exceptions, and no horizontal mobile overflow. Published HTML, verification JSON, patch, and all 14 screenshots matched local files byte for byte over HTTPS; the root returned 200 with the CSP sandbox header. A credential scan passed. APKs, raw logs, and preference XML were excluded from publication.

The isolated QA app was removed. The production APK was verified unchanged, existing debug preferences were retained, Foldcade remained default Home, Wi-Fi remained enabled, stay-awake returned to `0`, and both screens returned to sleep. The original tour's temporary collection was removed and its artwork key restored.

Local evidence: `captures/calibration-recovery-2026-10-14/` (Git-ignored), including build/test logs, baseline source, device screenshots, cleanup results, browser checks, publication hashes, and the Poof source. The other two bugs and two UX findings in the [tour report](ux-tour-thor-2026-10-14.md) remain for follow-up.
