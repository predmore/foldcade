# Second-screen recovery implementation and Thor validation

Validated on 12 October 2026 on the physical AYN Thor, Android 13 / API 33. The default display was logical display 0; the bottom panel was display 4. This records the implemented compatibility fix and its limits. The earlier [architecture research](second-screen-decision-2026-10-12.md) remains a historical recommendation.

## Architecture

Keep the existing primary and companion Home activities. Each activity creates one `ComposeView` containing the existing `PanelHost`. `PanelRecoveryWindow` moves that view between its native activity container and a `Presentation` on the same physical display. There is one panel renderer and one application session model.

The adapter reuses the companion activity's lifecycle, ViewModel store, saved-state registry, back dispatcher, controller handling, and result handlers. It copies brightness and uses the existing activity focus handoff before focusing the Presentation for text input. Activities and presentations share the form-key interception method. It has no independent navigation or activity lifecycle.

The view first attaches to the native activity window, then explicitly retains that window's parent composition context. `DisposeOnLifecycleDestroyed` ties its lifetime to the activity. Its single composition survives a window move and is disposed when the activity is destroyed. Window replacement moves the view directly into the new Presentation, without an intermediate attachment to the native container. This uses the documented [ComposeView lifecycle disposal strategy](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views) and [view-tree composition context](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary#findViewTreeCompositionContext(android.view.View)). Android's [Presentation documentation](https://developer.android.com/reference/android/app/Presentation) describes the display context, cancellation, and hosting-activity responsibilities; using the companion on the target display preserves the panel's existing display configuration.

One panel-level `FocusRequester` saves and restores the focused child during replacement, using the standard [Compose focus restoration APIs](https://developer.android.com/reference/kotlin/androidx/compose/ui/focus/FocusRequester). Restoration is consumed once per replacement and only restores a saved child when that replacement actually saved one. Keyboard focus waits observe the current Presentation, so replacement cannot leave a waiter tied to a dismissed window. There are no form-specific recovery flags or custom recomposers.

`RecoveryLease` is the small policy boundary that can be tested without Android windows:

- Arm recovery when primary Home resumes, including a fresh launcher-icon start. Reassert an existing window only for an explicit Home request or a reclaimed external panel; ordinary focus changes leave it in place. A secondary Home created behind an emulator cannot arm recovery on its own. Retain the request for the process, since a stopped emulator can recreate its window on later display changes.
- Show only while Foldcade owns the panel, its activity is resumed, and its non-default display is on and eligible for presentations.
- Allow the free panel beside a known single-screen app. Yield conservatively when the other panel has an app opened outside Foldcade, whose second-screen use is unknown.
- Show immediately, then repair once after one second. New Home requests supersede the earlier repair; yielding cancels it. There is no periodic reassertion loop.
- On sleep, app handoff, or destruction, dismiss and restore the native view. On unexpected Presentation dismissal, restore the view and reconcile ownership.
- Primary Home resuming also restores recovery after process recreation. No separate saved-state recovery flag is needed.

This keeps window compatibility in one adapter and transition timing in one pure helper. Activities continue to own normal Android behavior. The tradeoff is a brief recovery transition and a finite workaround for a foreign window whose lifecycle Foldcade does not control.

## Automated checks

The final normal build, without the injected hardware fixture, passed:

- 804 unit tests across 100 suites; zero failures, errors, or skips.
- Clean debug APK assembly.
- Unsigned release APK assembly.
- Release lint and `git diff --check`.

Nine recovery tests cover the single repair, repeated visibility updates, app handoff, stale callbacks after wake, repeated Home, failed-show/failed-repair fallback, conservative external-app ownership, and secondary resume behind a two-screen app.

Build command:

```sh
./gradlew testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintRelease \
  -PfoldcadeAllowUnsigned=true \
  -PfoldcadeHomeMusicAssets="$PWD/captures/bottom-screen-check/prebuilt-music"
```

The local build used JDK 21 and the existing music-assets override. Packaged music assets were extracted from the installed Foldcade APK because the repository's macOS music-rendering invocation failed. The music-rendering pipeline itself was not validated by these builds.

## Physical-device checks

The installed Azahar 2126.2-vanilla had an existing paused game and a stale bottom-screen Presentation. Its process remained running throughout testing. Native Foldcade was black underneath that real window; recovery displayed the actual grid above it.

An ignored, injected debug fixture additionally reproduced an emulator that recreates its Presentation 100 ms after stopping and again on wake. The fixture was excluded from the final APK. Checks on the Thor passed:

- Explicit Home recovery above both the real stale Azahar window and the fixture's delayed window.
- Fresh primary Home with no remembered dual-screen session, recovering above the real stale Azahar window.
- Fresh launcher-icon start with no remembered session, recovering above the same window.
- Clean secondary Home started alone beside a top-screen app: recovery remained unarmed until primary Home was requested.
- A newly created secondary Home with a `SECONDARY_HOME` intent beside a known two-screen fixture: the emulator retained its panel until primary Home returned.
- Touch selection, D-pad navigation, and L1/R1 menu behavior.
- Gboard on display 4, typing into a folder-name draft, and cancelling the edit. Input-method state confirmed `mInputShown=true` and `mCurTokenDisplayId=4`.
- First controller keyboard request in the bottom RomM form, text entry surviving an immediate replacement and its delayed repair, and form row navigation with the keyboard dismissed. Gboard's fullscreen editor handles D-pad keys while that editor is open.
- Repeated Home and three sleep/wake cycles with recovery restored.
- A dual-screen relaunch before the one-second repair: recovery stayed dismissed.
- An untracked dual-screen fixture opened directly: recovery yielded and stayed absent after wake.
- A known single-screen top fixture: bottom command center remained usable.
- A known single-screen bottom fixture: recovery yielded and returned after Back.
- Real Settings launched from Foldcade's app grid: the top launch left the recovered bottom command center visible; the bottom launch dismissed recovery; bottom Home recovered Foldcade.
- Killing only the QA process, then returning primary Home: a new process restored recovery above the real Azahar window.
- One primary and one companion QA Home activity after the transitions and process recreation.
- No Foldcade crash in the device crash buffer during the final checks.

Local screenshots and window/input/task dumps are under the ignored `captures/bottom-screen-check/` directory. Key evidence: `qa-native-bottom.png`, `qa-review-final-icon-bottom.png`, `qa-review-final-form-repaired-bottom.png`, `qa-review-final-parked-form-bottom.png`, `qa-review-final-secondary-behind-game-bottom.png`, `qa-review-final-unknown-wake-bottom.png`, `qa-final-real-settings-top-bottom.png`, and `qa-review-final-process-return-bottom.png`.

## Dedicated review

The review removed saved-state recovery bookkeeping and the separate known-emulator return helper in favor of primary Home's existing resume/ownership signals. It also resolved form keys reaching text views ahead of Foldcade's navigation rules, composition recreation cancelling the first keyboard-focus request, and loss of the active field during the one delayed repair. Initial secondary Home creation now uses resume reconciliation rather than treating creation as an explicit Home reclaim. The final architecture keeps those fixes in the shared activity/window adapter rather than each form.

## Scope and remaining limits

This fixes Foldcade's panel recovery when primary Home comes forward. It does not dismiss another application's Presentation. The real stale Azahar window still obscured Settings when Settings was subsequently launched on the bottom, even though Foldcade correctly yielded. The same limitation can leave native Foldcade obscured beside an untracked app, because recovery deliberately yields there. An Azahar lifecycle fix is still needed for those cases.

A foreign Presentation created later than the one bounded repair can cover Foldcade again. No automatic polling is used to compete with it.

Active Azahar gameplay, every emulator, physical display removal/reconnection, and swapped display assignments were not exercised. These tests do not establish that every adoption gate from the research document has passed.

The final clean debug APK is installed as `app.foldcade.debug`. The original production Home role and the original stay-awake setting are restored at handoff. The production APK and paused Azahar game were preserved; the release artifact is unsigned validation output.
