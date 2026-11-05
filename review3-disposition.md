# Review round 3 disposition

The held-Back P2 is skipped: it cannot reach the proposed failing path. `PanelKeyActivity.onKeyDown` calls `meaningOf(keyCode, event.repeatCount, faceMap(), homeKeys())`; `Meaning.kt` returns null for every repeated calibrated Back, system Back, and Escape, before calling `HomeActivity.onMeaningRepeated`. This rule predates this branch (main commit 479616f3, #349).

An existing regression test, `LicensesRowTest.heldBackCannotStepOutOfSettingsAfterLicensesFinishes`, already covers repeats 1..20, both standard and swapped face maps, system Back and Escape, and a later fresh press.

Verified on the Thor with the previous pushed APK 43efc3b9 (which has no extra held capture): `input -d 4 keyevent --longpress KEYCODE_BUTTON_B` closes Themes to the focused Theme row and Licenses to the focused Licenses row. `held-baseline-themes-after-top.png` and `held-baseline-licenses-after-top.png` show the intact row focus. A later normal B steps out to the categories (`held-baseline-next-back-top.png`).

The proposed additional ShellController guard was initially added, then removed after this verification. It duplicated the input map's rule and tested a direct Meaning.Back path that hardware repeat events cannot take. No extra guard/API/test remains. Final device checks repeat both cases on the freshly integrated APK.
