I found one issue: pressing A on the bottom-screen Settings while Themes' ZIP picker is open starts a second Themes. The other changes hold up.

### Finding: a second Themes opens from the moved Settings

**Repro:** Settings › Look › Theme (A) opens Themes on top. Pick Add, and the ZIP picker covers the top screen. Themes stops, `settingsPageCovered` swaps its marker for an Android hand-off, and Settings moves to the bottom screen with **Theme still focused** (`after-zip-live-keys-bottom.png` shows that starting row; d-pad down moved focus to Icons). Press A on Theme there.

**What happens in the code:**
- `Row.Theme` sends `Effect.OpenThemes`, and `openSettingsPage` (`HomeActivity.kt:3131`) uses `settingsActivityScreen`, which is now Bottom.
- It calls `noteSettingsPage` on the bottom Home and starts a new standard-mode `ThemesActivity` on the bottom screen.
- The first Themes is still alive under the picker on top, so two Themes are open and `settingsPageActivity` points at the new one.

**How the player gets stuck:** cancel the picker.
1. The first Themes starts again. It takes `settingsPageActivity` back and marks the top screen. Both screens are now Themes, with no companion.
2. Press B on the top Themes. Top Home resumes and the top Themes is destroyed, which sets the reference to null.
3. The bottom Themes is left with nothing pointing at it:
   - Top Home's `pageKeyTarget(Top)` still matches the bottom marker, but the resumed check fails on the null reference. Keys go to the top Settings instead of the bottom Themes.
   - Open an app's `settingsPageActivity?.finish()` can't close the bottom Themes either.
   - Without touch, the gamepad can't drive or close it.

The same happens with the Licenses row, through the same `openSettingsPage` path.

**Simplest fix:** allow one owned page at a time. At the start of `openSettingsPage`, finish any `settingsPageActivity` that is still alive before launching the new one. The picker then sits over Home, and cancelling it returns there, as `final-covered-picker-cancel-top.png` already shows for Open an app. Blocking the row instead would make A do nothing, so finishing the old page is better.

### Checked and clean
- **Normal B out of Themes:** top Home's `onResume` clears the marker before Themes' `onStop`, which then does nothing because the page is no longer marked current. Recreation (stop → destroy → create → start) also ends in the right state, since the old page only clears the reference if it is still the current one.
- **Open an app while Themes is covered:** finishing the covered Themes leaves the hand-off marker, which is correct while the picker holds the top screen. Cancelling resumes Home, and the curated captures match.
- **Key gate:** forwarding only to a resumed page doesn't break normal companion input across screens; the Licenses scroll and return captures confirm it.
- **Launch rollback:** `handoffAndroidSetting` rolls back on a false return or an exception and still rethrows. It does nothing when the screen is unknown.
- **Wi-Fi claim:** I agree with skipping it. `onResume` reaches `foldcadeResumed(panel)` unconditionally; the only early return is a pending player restart, which has nothing to do with Wi-Fi. The Done → Start captures confirm it on the Thor.
- **Docs:** the README and architecture text match the new behaviour.

### Not tested on the Thor (not a finding)
Sleeping the Thor with Themes open now runs `onStop`, which moves Settings to the bottom screen, and `onStart` on wake brings it back. Whether the bottom panel briefly shows Settings on wake is worth one capture next time someone has a turn. I couldn't confirm it without the device.
