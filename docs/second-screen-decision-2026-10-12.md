# Second-screen architecture research

Research date: 12 October 2026. Target: AYN Thor running Android 13 / API 33. Foldcade reference: `bc9d77b80bfb41b63ccc857843fb88f4d0c2f986`. This is a recommendation and validation plan; it does not record an implemented architecture change.

## Recommendation

**Keep a native Home activity on each physical display. Prototype a narrowly scoped `Presentation` renderer for returning from emulators that leave a second-screen window behind. Adopt that renderer only after the Thor validation below passes.**

Use activities for normal rendering, Android Home integration, forms, and launches. Use a presentation as a compatibility tool when Foldcade has explicitly reclaimed an affected panel. Keep the same session and UI model behind either renderer, with only one active UI composition for that panel.

This preserves the behavior Foldcade needs: browse on one screen while a game uses the other, open games on either screen, and recover independently through Home. It also addresses the specific weakness of an activity-only implementation: an emulator's presentation can sit above a resumed Home activity.

The presentation proposal is not a proven cure. It reaches the same window layer as Azahar, but another presentation created later can cover it again. A finite recovery sequence needs a hardware experiment. A permanent timer that repeatedly raises our window would create a contest with other apps and can cover a game the user deliberately opened.

In parallel, prepare an upstream Azahar lifecycle fix: a stopped emulator must not recreate a physical-display presentation in response to display changes. That removes the cause rather than covering its result. Neither submitting that fix nor changing the installed emulator is part of this research.

## What we verified

### The current blank screen

Earlier in this thread, the physical bottom panel reported `ON`. Foldcade's companion activity was resumed, visible, and had a surface. An Azahar `TYPE_PRESENTATION` window was also visible on that display, above Foldcade. A screenshot of the bottom panel was black while the top showed Foldcade. Recalling Home on the bottom and waking the displays did not produce a visible library; Azahar's presentation was present again in the final capture.

The recorded window layers were 31,000 for Azahar's presentation and 21,000 for Foldcade's activity. These are observations on this Thor, not portable layer numbers. Local diagnostic evidence remains under ignored `captures/bottom-screen-check/`, including `windows-final.txt`, `top-awake.png`, and `bottom-final.png`.

Android 13's window policy independently places application windows in layer 2 and presentations in layer 3. This explains why moving an activity to the front of its task cannot put it above a presentation. [Android 13 window policy](https://github.com/aosp-mirror/platform_frameworks_base/blob/android13-release/services/core/java/com/android/server/policy/WindowManagerPolicy.java#L506-L519).

### A likely Azahar trigger

The installed Azahar version was `2126.2-vanilla`. Its matching source dismisses the presentation in `EmulationActivity.onStop()`. However, its display listener remains registered until final virtual-display teardown. Added, removed, and changed displays all call `updateDisplay()`, which checks whether the activity is finishing or destroyed, but does not check whether it is stopped. It can therefore show a new presentation while the emulator is in the background. [Emulation activity](https://github.com/azahar-emu/azahar/blob/2126.2/src/android/app/src/main/java/org/citra/citra_emu/activities/EmulationActivity.kt#L220-L233), [secondary-display manager](https://github.com/azahar-emu/azahar/blob/2126.2/src/android/app/src/main/java/org/citra/citra_emu/display/SecondaryDisplay.kt#L82-L155).

The source supports this explanation, and the observed sleep/wake behavior is consistent with it. We did not instrument Azahar to prove the exact callback sequence that first caused the user's symptom.

### Foldcade already has the right Home foundation

`PrimaryHomeActivity` declares `HOME`; `CompanionHomeActivity` declares `SECONDARY_HOME`, uses `singleTop`, and has its own task affinity. Both share application-level session state. Separate activities therefore do not require IPC or separate copies of the library model. `Session.surfaceOn()` already describes which panel is available to Foldcade. [Manifest](https://github.com/predmore/foldcade/blob/bc9d77b80bfb41b63ccc857843fb88f4d0c2f986/app/src/main/AndroidManifest.xml#L81-L108), [session](https://github.com/predmore/foldcade/blob/bc9d77b80bfb41b63ccc857843fb88f4d0c2f986/app/src/main/kotlin/app/foldcade/Session.kt), [home lifecycle](https://github.com/predmore/foldcade/blob/bc9d77b80bfb41b63ccc857843fb88f4d0c2f986/app/src/main/kotlin/app/foldcade/HomeActivity.kt#L260-L364).

Android's multi-display launcher model explicitly provides a Home stack per eligible display and the `SECONDARY_HOME` category. Secondary Home must allow multiple instances; `singleTask` and `singleInstance` are unsuitable there. This validates the current companion declaration. [AOSP secondary Home](https://source.android.com/docs/core/display/multi_display/system-decorations#launcher).

## Options and tradeoffs

| Option | Main advantages | Main disadvantages | When we would choose it |
| --- | --- | --- | --- |
| **Two native Home activities** | Android handles each panel's Home task, activity lifecycle, and normal focus. Existing forms and activity results fit naturally. Smallest migration cost. | A foreign presentation can cover a resumed activity. Better task reuse or relaunch flags do not change the window layer. | **Default for Foldcade.** Best foundation for two independently usable displays; sufficient when external apps release their windows correctly. |
| **Primary activity plus a standalone presentation** | Direct control over second-screen rendering and placement. Can create a window at the same layer as an emulator's presentation. Useful for a mostly visual companion panel. | A presentation is a dialog, not a secondary Home task. Requires explicit handoff, Home routing, focus, keyboard, and Compose lifecycle work. Can cover ordinary games until dismissed. Primary activity stopping must not freeze the other panel. | A dashboard or media companion, or a launcher whose presentation ownership and focus rules have been proven on its target hardware. A broad replacement is costly for Foldcade. |
| **Native Home activities plus targeted presentation rendering** | Retains Android Home integration and normal rendering while giving us a recovery mechanism for known presentation conflicts. Reuses the existing session model. | Adds a second rendering path and more window transitions. Must avoid duplicate compositions and races. Same-layer recovery has no universal priority guarantee. | **Preferred compatibility direction for Foldcade**, following a successful Thor prototype. Restrict activation to explicit recovery from affected apps and supported firmware. |
| **Service-hosted application overlay** | An overlay is above presentations in Android 13's policy. Its lifetime can be separate from either activity. | Requires draw-over-other-apps access and its own input, rendering, and lifetime rules. It is not an Android Home task. Easy to obscure unrelated apps. More permission and platform-policy burden. | A deliberately floating utility with an overlay-centered product requirement. Poor fit for a full-screen home launcher. |

The overlay ordering and permission requirement are defined by [Android 13 window policy](https://github.com/aosp-mirror/platform_frameworks_base/blob/android13-release/services/core/java/com/android/server/policy/WindowManagerPolicy.java#L514-L539) and the [overlay API](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY). A service by itself does not solve layering; the window it owns determines that.

Automatically killing the previous emulator is another possible symptom workaround, but it ends the user's session and may lose progress. It is not a second-screen architecture. The background-process API is also restricted to the caller's own processes on Android 14 and later. [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String)).

Mirroring, virtual displays, `SurfaceView`, and `TextureView` solve different problems. They do not replace the need to decide which app owns each physical display. Changing Compose rendering technology alone would not change the window ordering observed here.

## Why the native activities remain valuable

Foldcade's second screen is an interactive launcher, not just artwork. It runs a grid, menus, setup forms, and a command center beside a running app. The user can also launch another app onto that panel. A native activity gives these interactions an Android-managed task and lifecycle.

Android 10 and later support multiple resumed activities. A game on the top panel and Foldcade on the bottom can both remain active; losing the globally top-resumed position does not itself mean a panel has become invisible. [AOSP multi-resume](https://source.android.com/docs/core/display/multi_display/multi-resume), [Android activity focus behavior](https://developer.android.com/about/versions/10/behavior-changes-10#multi-window).

The distinction we should keep in code is: **session ownership, actual visibility, input focus, and display power are different facts.** A resumed activity alone cannot prove its pixels are visible, as this bug demonstrated. A display-change callback alone cannot prove that Foldcade should reclaim a panel. Losing controller focus to the other panel should not stop an otherwise visible library from updating.

Launching an app onto a specific public display is supported through `ActivityOptions.setLaunchDisplayId()`. Continue using discovered IDs and display-scoped contexts. Improve selection of eligible physical panels rather than treating the lowest non-default ID as sufficient once HDMI or virtual displays are also present. A powered-off physical panel should remain assigned even while rendering is suspended. [Launch-display API](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)), [activity launch policy](https://source.android.com/docs/core/display/multi_display/activity-launch).

## What a presentation changes

### Rendering and lifecycle

A presentation uses the target display's context and metrics. It supplies another window, but does not supply the activity ownership Foldcade currently relies on. Our compatibility renderer would need a distinct composition lifecycle and explicit teardown, while continuing to read the shared session model. [Android 13 Presentation implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android13-release/core/java/android/app/Presentation.java), [Compose composition disposal](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views#viewcompositionstrategy).

The inspected Loki implementation is a useful warning: it provides its presentation with a separate lifecycle, saved-state owner, and recomposer so the grid keeps updating when the primary activity stops behind a game. It disposes the composition when the presentation is dismissed. Copying only `Presentation.show()` would omit that machinery. [Loki presentation](https://github.com/Prof-Mags/Thor-Launcher/blob/dcd74faafe6b6c384bb279da9fa4cb8a9b6416b9/core/display/src/main/kotlin/com/thor/core/display/ThorPresentation.kt), [its lifecycle owner](https://github.com/Prof-Mags/Thor-Launcher/blob/dcd74faafe6b6c384bb279da9fa4cb8a9b6416b9/core/display/src/main/kotlin/com/thor/core/display/PresentationHost.kt).

### Controller focus and keyboard

A nonfocusable presentation can receive touch, but is not the keyboard/controller key target. A focusable one can take key focus from a game on the other screen. We must explicitly route input according to which surface the user is operating, forward actions exactly once, and make text fields focusable when needed. [Loki input handling](https://github.com/Prof-Mags/Thor-Launcher/blob/dcd74faafe6b6c384bb279da9fa4cb8a9b6416b9/core/display/src/main/kotlin/com/thor/core/display/ThorPresentation.kt#L64-L114).

IME placement is also a platform/display-policy choice: Android can show the keyboard on the focused display, the default display, or nowhere. A presentation does not inherently repair an OEM keyboard-routing problem. [AOSP IME support](https://source.android.com/docs/core/display/multi_display/ime-support).

### Firmware compatibility

The Thor supports the presentations observed in this investigation. Android 13's admission check accepts a public display carrying `FLAG_PRESENTATION`; our bottom panel has that capability. Use that version-specific behavior and runtime capability checks. [Android 13 admission check](https://github.com/aosp-mirror/platform_frameworks_base/blob/android13-release/services/core/java/com/android/server/wm/WindowManagerService.java#L1509-L1521), [display eligibility](https://github.com/aosp-mirror/platform_frameworks_base/blob/android13-release/core/java/android/view/Display.java#L1625-L1628).

The current Presentation documentation includes newer restrictions for internal displays and task focus. These are a reason to test each supported firmware, not to assume today's API documentation exactly describes the Thor's Android 13 behavior. If presentation creation is rejected, retain native rendering and report the failed recovery. Do not mutate an activity's window type to imitate a presentation; Android 13's Presentation source warns about window-context/type mismatches. [Current Presentation documentation](https://developer.android.com/reference/android/app/Presentation), [Android 13 context/type contract](https://github.com/aosp-mirror/platform_frameworks_base/blob/android13-release/core/java/android/app/Presentation.java#L175-L185).

## What other launchers establish

**Loki/Thor Launcher:** its source uses a presentation and deliberately controls when it is shown or dismissed. It restores missing presentation content on resume and keeps the other panel alive while the main activity is covered. This proves a different rendering architecture exists, not that it wins every race against the Azahar version installed here. [Loki window orchestration](https://github.com/Prof-Mags/Thor-Launcher/blob/dcd74faafe6b6c384bb279da9fa4cb8a9b6416b9/core/display/src/main/kotlin/com/thor/core/display/DualScreenHost.kt#L137-L244).

**Cocoon 3.07:** read-only inspection of the installed APK found both `MainActivity` and `ExternalDisplayActivity`, with resume/session recovery paths. Its public repository does not provide the complete application implementation. We did not prove that it uses a presentation workaround, terminates Azahar, or handles the exact same sleep/wake reproduction. Its observed success should be compared under a matched sequence before attributing it to a particular technique. [Cocoon public repository](https://github.com/inssekt/CocoonFE).

There is no evidence here that presentations use less CPU, less memory, or fewer dropped frames than two activities. Both normal architectures need two display surfaces. Compare equivalent builds and rendering settings before making a performance claim.

## Proposed implementation boundary

1. **Harden the native path.** Make display assignment observable; handle removal and reattachment; reset the companion-start guard when a start fails; retain exactly one Home instance per display. Preserve the distinction between a live companion and a visible one. These changes improve recovery but cannot outrank a foreign presentation.
2. **Extract window-independent panel rendering.** Replace the UI's assumption that a panel always has an activity with explicit panel/context inputs and a small host contract for launches, result registration, input, focus, and brightness. Keep session ownership as the source of intent. Separate actual window visibility from that intent.
3. **Prototype one compatibility renderer.** Enable it only for an affected dual-display launch followed by an explicit Home/return event, on an eligible panel. Use the public Presentation API with the correct context. Suspend native UI composition on that panel while the presentation renders it. Keep forms/results and Android Home routing attached to the native hosts.
4. **Yield before launching or covering that panel.** Dismiss the presentation before starting an external app there; dismiss on ownership loss, role loss, destruction, and display removal. If only the other panel is occupied, the visible Foldcade panel may continue running. Pause rendering for display-off and reconcile on wake without treating wake itself as user permission to reclaim a game's panel.
5. **Bound recovery.** Tie any delayed display-transition repair to the current session generation. Cancel it on a new launch or ownership change. Do not repeatedly raise the window indefinitely. Normal app APIs cannot enumerate or dismiss every other app's windows, so do not design an automatic foreign-window detector around the privileged ADB evidence used here.
6. **Prepare the Azahar fix separately.** Gate display-driven presentation creation by emulator visibility/lifecycle, and test restart, display removal, hidden-surface handling, and Vulkan recovery. The existing stopped-emulator behavior needs a fix regardless of Foldcade's chosen renderer.

The presentation renderer would be a real panel surface, not an invisible input-blocking mask. The prototype must prove that its grid, controls, forms, and brightness all operate together before adoption.

## Adoption gates on the physical Thor

| Scenario | Required result |
| --- | --- |
| Home or launcher-icon entry, including repeated entry | One Home host per physical display; usable grid; no duplicate presentation or composition. |
| Single-screen game on top | Bottom browsing and command center continue; controller focus follows user interaction without stealing game input. |
| Single-screen game on bottom, including a launch from Recents | Foldcade's presentation is dismissed; the game is visible and receives input; the top panel remains usable. |
| Two-screen Azahar and melonDS launch | Both Foldcade rendering surfaces yield; no grid flashes or controls remain over the game. |
| Return from Azahar, then sleep/wake and change brightness/refresh rate | Library stays visible and interactive despite delayed display callbacks; no endless window reassertion. |
| Home on either panel while a one-screen game uses the other | Recover only the requested panel; preserve the other game's session. |
| Text input, keyboard, folder picker, settings, and Back | Correct display and focus; results return once; no closed or frozen grid after form dismissal. |
| Process death, role switch, swapped panel setting, display removal/reattachment | Restore state and proper panel assignment; no abandoned window. |
| Rendering comparison | Equivalent settings; frame/CPU observations before and after; only one renderer active per panel. |

Use a separate test identity and preserve the user's active game/session during experimentation. Include emulator tests for lifecycle and rendering coverage, but use the Thor for actual window ordering, Home, controller, and IME acceptance.

**Adopt the compatibility renderer only if it passes these gates without an unbounded reassertion loop.** If it fails, keep native activities as the default and pursue the emulator lifecycle correction. A full presentation migration should require evidence of benefits beyond hiding this one symptom.

## Confidence and limits

- **High confidence:** native Home activities are Android's intended multi-display launcher integration; the observed presentation window can cover Foldcade's activity; relaunch flags alone cannot fix that layer difference.
- **Moderate confidence:** Azahar's ungated display listener explains recreation while stopped; instrumentation would establish the exact first-trigger sequence.
- **Unproven until prototyped:** a targeted Foldcade presentation reliably recovers the affected Thor panel through display changes, input focus, and external launches.
- **Unknown:** Cocoon's exact mitigation for this reproduction; relative performance of alternative Foldcade renderers.

No application implementation or installed APK was changed during this research. The deliverable is this decision report and its HTML comparison. The recommendation deliberately separates the preferred architecture from the experiments required before shipping its compatibility path.
