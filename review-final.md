**NO FINDINGS.** I reviewed `babcf82b` read-only. Nothing was edited, posted or run on the Thor.

**The held-B objection from round 3 doesn't hold up.** Skipping it was right.
- **Where repeats stop:** every hardware key goes through `PanelKeyActivity.onKeyDown` (`language/.../PanelKeyActivity.kt:103`), which calls `meaningOf(keyCode, event.repeatCount, …)` before anything else. For any repeat, `meaningOf` returns null when the key is Back, Escape or the calibrated back face (`Meaning.kt:136-141`). So the B repeats on the companion Home never reach `onMeaningRepeated`, the forwarding branch, `ShellController.onMeaning` or `applySettings`. The repeat path the objection described can't start.
- **System Back:** `KEYCODE_BACK` returns early at `:102`. It only acts through `OnBackPressedCallback`, which fires once per press.
- **Evidence:** `held-final-themes-after-top.png` and `held-final-licenses-after-top.png` both land on the parent row (Theme and Licenses focused, About and Look categories intact). The existing `LicensesRowTest` covers repeats 1 to 20 with both face maps. Dropping the extra guard was right; it would only have duplicated a rule the input map already enforces.

**Native forwarding** (`HomeActivity.kt:363-369`) is fine:
- **Order:** it runs after `meaningOf`, `onInputKey` and the focus gate, and before the shell.
- **Parent Settings:** it never sees a forwarded key.
- **Held keys reaching Themes and Licenses:** Themes keeps its own filter that passes only D-pad repeats. Licenses scrolls on a held D-pad. Neither overrides `onMeaningReleased`, so not forwarding key-ups loses nothing.
- **After a page closes:** for the moment after `finish()` and before `onStop`, keys go to an activity that is already finishing, which is harmless. After `onStop`, `settingsPageActivity` is null and the `as?` cast drops the key.

**Where this branch overlaps with the other merged work:**
- **RomM subpage:** `SettingsInner.Romm` opens from `Row.RommConnection`. Its rows use the same labels as before (Sign in and Remove when signed out). Its detail comes from `rommPageDetail`.
- **Settings, sign-out and forms:** the stand-in panel path keeps Settings open behind the sign-out dialog. `connectReturnPanel` is cleared when a form opens from a page, so B from the form goes back to the subpage. `afterRommAdded` still applies only when `returnToPage` is set. The tests cover sign-out, Edit connection followed by B, and Open an app from a form.
- **Folders and pages:** `ForgetFolder` keeps its early return before the generic path. `pageBeside` still passes `game`/`platform` for About.
- **Folder guide:** every key is blocked at three layers (Home, `ShellController.onMeaning` and `reduce`), touch is blocked, and the guide is cleared on either picker result and kept across activity recreation.

**UX score: the companion and guide surfaces meet 9/10.**
- The bright companion reads cleanly at arm's length: lit gear, large clock, "Settings", and one Open an app chip with no stale hints (`held-final-themes-before-bottom.png`).
- The root Library guide shows a real folder object, the "Library" title and accurate copy: "Pick your games folder. Tap Use this folder, then Allow."
- The return path keeps both the row focus and the parent category.

**Remaining limits (none is a finding for this PR):**
- The companion is deliberately sparse, so it's a calm 9 rather than anything livelier.
- Themes' own Add a theme document picker is started by Themes, not by `launchFolderPicker`, so the free screen keeps the plain Settings companion with its keys dropped instead of showing a guide. That predates this branch and is outside routing scope. It could join the guide later if someone wants it.
