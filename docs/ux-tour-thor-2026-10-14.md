# Thor UX tour — 14 October 2026

[Open the Poof tour](https://poof.i.socia.dev/poof-v2z7umpk/) — 60 stops, 120 full-resolution display captures, review prompts, per-page notes, zoom, and Markdown/JSON export. Default retention is 30 days.

The tour records the installed release (`app.foldcade`, version 0.1.0, versionCode 176) on a physical AYN Thor running Android 13. Top: 1920 × 1080. Bottom: 1240 × 1080. The checkout is `1927447d`; its change after the release app code is documentation only.

Conditional dialogs and permissions were captured with an isolated debug build, `app.foldcade.uxtour`, from this source. Example save data comes from the built-in preview fixture. These are marked separately from the live release. The isolated app was removed after capture.

## Device state requiring follow-up

Calibration testing left **B as Confirm and A as Back** in production. The setting disappears after a calibration, so the installed UI provides no way to restore A. The fix is now implemented on `fix/button-calibration-recovery` and verified through an isolated upgrade on Thor. The signed production app still needs an update; its data and B mapping were retained. See the [tested recovery proof and patch](https://poof.i.socia.dev/poof-4tyfckuf/) and [implementation report](calibration-recovery-thor-2026-10-14.md). The original immutable tour contains the earlier proposal; the follow-up contains the tested implementation.

## Findings

### B1 — Calibration can leave the buttons swapped with no reset

Bug · High · Live release · reproduced

Opening Button labels, then pressing the current Back face button, saves that button as Confirm. The setting disappears immediately. B became Confirm and A became Back; the Settings header still shows B as Back.

Reproduce:

1. Settings → Look → Button labels.
2. Press the face button currently used for Back.
3. Reopen Look: Button labels is gone.
4. On Home, B now opens All and A acts as Back.

Expected: A user should be able to revisit calibration or reset it after any choice. Cancellation should be explicit and consistent.

Implemented follow-up: Button labels stays available, shows the current Confirm button, and lets the user restore A or choose another face button. Start cancels; leaving the row also cancels. The header follows the active button mapping. See the [tested recovery proof](https://poof.i.socia.dev/poof-4tyfckuf/).

Status: Fixed in the working branch; unit tests and an isolated Thor upgrade passed. Pending signed production update. Production Confirm remains B after this review. No app data was cleared.

[Screenshot evidence](https://poof.i.socia.dev/poof-v2z7umpk/#button-labels)

### B2 — The save-conflict choices do not fit the bottom display

Bug · High · Current source · isolated fixture

On the 1240 × 1080 bottom display, the right save card wraps more than the left. Keep both is compressed at the lower edge and Decide later is outside the visible screen.

Reproduce:

1. Build the current source with a debug identity.
2. Start PrimaryHomeActivity with --es foldcade.saveConflict preview.
3. Inspect the physical bottom display at 1240 × 1080.

Expected: Both save cards and all resolution actions should remain readable and reachable on Thor.

Suggested: Give the cards equal widths and make the body scroll or adapt its spacing and type size. Keep the safe action and cancellation visible.

Status: Open. Reproduced in the built-in rendering fixture, not with a real server conflict.

[Screenshot evidence](https://poof.i.socia.dev/poof-v2z7umpk/#preview-save-conflict)

### B3 — Play activity exposes document URIs as game names

Bug · Medium · Live release · reproduced

Two entries in Most played show content://com.android.e… rather than readable game titles. The same list also contains correctly resolved names.

Reproduce:

1. Open L1 → Play activity on the current installation.
2. Read the lower two rows in Most played.

Expected: Historical play records should preserve a readable title even when their original catalog entry is no longer available.

Suggested: Persist a display name with each tracked game, or provide a human-readable fallback instead of the internal ID.

Status: Open. Visible on repeated visits.

[Screenshot evidence](https://poof.i.socia.dev/poof-v2z7umpk/#play-activity)

### U1 — An empty collection loses its identity and next step

UX · Medium · Live release · observed

Opening a new zero-game collection leaves both the top-screen title area and the collection grid empty. Only a focus ring and Options / Back hints remain.

Reproduce:

1. Create an empty collection.
2. Open it before adding a game.

Expected: The collection name and a short Add games instruction should orient the user.

Suggested: Show the collection name, an empty-state message, and an obvious Add games action.

Status: Review decision. The temporary Playing collection was removed.

[Screenshot evidence](https://poof.i.socia.dev/poof-v2z7umpk/#collection-empty)

### U2 — Forget key removes the artwork key in one press

UX · Medium · Live release · observed

Activating the SteamGridDB row while it reads Forget key removes the saved key immediately, with no confirmation or Undo. This happened during navigation; the key was restored from the existing local configuration and Forget key was verified again.

Reproduce:

1. With a key saved, open Settings → Library → SteamGridDB.
2. Activate the row once.
3. The value immediately changes from Forget key to Add key.

Expected: A credential-removal action should be deliberate and recoverable.

Suggested: Use a separate Remove action with confirmation, a hold, or an Undo window.

Status: Key restored and verified; UX behavior remains for review.

[Screenshot evidence](https://poof.i.socia.dev/poof-v2z7umpk/#settings-library-bottom)

## Source pointers

- Calibration dispatch: [PanelKeyActivity.kt](../language/src/main/kotlin/app/foldcade/language/PanelKeyActivity.kt#L85); setting visibility: [Settings.kt](../language/src/main/kotlin/app/foldcade/language/Settings.kt#L56); persistence: [ShellController.kt](../app/src/main/kotlin/app/foldcade/ShellController.kt#L1349).
- Save-conflict layout: [SaveConflictScreen.kt](../app/src/main/kotlin/app/foldcade/ui/SaveConflictScreen.kt#L85).
- History fallback: [PlayActivityScreen.kt](../app/src/main/kotlin/app/foldcade/ui/PlayActivityScreen.kt#L139).
- Artwork key removal: [HomeActivity.kt](../app/src/main/kotlin/app/foldcade/HomeActivity.kt#L1587).

## Coverage

- Browse (10): Home, Inside a system folder, All games, Android Games, Android apps, Foldcade drawer, Device drawer, Play activity, An app on the other display, Browse beside a running app.
- Find (4): Find, before typing, Find, with a query, Find, results focused, A search result’s actions.
- Organize (16): A game’s actions, Add to a collection, Carrying a tile, Select several tiles, Batch actions, An empty Home slot, Create a collection, A collection on Home, Collection actions, Collection icon editor, Rename a collection, Inside an empty collection, Removal and Undo, Edit home, Finding Hidden, Hidden, empty.
- Artwork (3): Tile art, Hero art, Logo art.
- Settings (12): Look, Sound, Library, sources, Library, folder list, Library, behavior, Saves, System, About, Licenses, Button calibration, After calibration, Changed button behavior.
- Setup (5): An existing RomM connection, Add a RomM server, Typing in setup, Add an artwork key, Choose a library folder.
- Conditional (10): Use Foldcade as Home, Folder access explanation, Save-folder explanation, Missing emulator, Close the other game, Sign in again, Choose between two saves, Usage permission, Brightness permission, Notification permission.

Not exercised: real RomM device-code approval/denial/expiry, additional-server sign-in, transfer progress/cancellation, server-backed save synchronization or conflict resolution, RetroAchievements during gameplay, offline recovery, sustained performance, every platform’s contents, and every theme configuration. The tour covers page families and representative states rather than every runtime outcome.

## Verification

- Preview APK assembled successfully with cached music assets and an isolated application suffix.
- Visually inspected the 120 curated images; rejected display sleep and OEM pixel-refresh frames.
- Headless Chromium checks passed at 1440 px desktop and 390 px mobile: filtering, search, empty search, navigation, 100% zoom, review-note retention between stops, Markdown/JSON export, JSON import, and an opaque-origin CSP sandbox. No browser exceptions or horizontal mobile overflow remained.
- Poof HTTPS returned 200 with its CSP sandbox. Published HTML and all 120 WebP assets matched the local artifact; the proposed patch was also fetched successfully.
- Credential scan passed. No tokens, app APKs, private preferences, or raw logs were uploaded.
- No Foldcade crash appears in the captured crash buffer.

## Cleanup

The temporary Playing collection was removed. Move, icon, rename, and batch action explorations were cancelled. No additional server or library-folder grant was saved. The SteamGridDB key was restored from the existing local configuration, validated by the app, and verified as saved at the end. Existing debug preferences were unchanged.

Foldcade remained the default Home app. Wi-Fi remained enabled, temporary stay-awake returned to `0`, and both physical displays returned to OFF. Initial Home focus was restored to Nintendo 3DS. The calibration mapping change above remains unresolved in the installed release.

Local evidence and source artifact: `captures/ux-tour-2026-10-14/` (Git-ignored). It contains raw device screenshots, the Poof site source, inventory, proposed patch, build log, browser checks, publication verification, and cleanup evidence.
