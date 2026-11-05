# uxr-pages QA

Baseline: main 3ba347f5, round-1 Thor captures 56, 58, 68, 73 copied here as before-*.
Build: app.foldcade.uxrpages, valid Android package spelling of turn slug uxr-pages.
Full testDebugUnitTest --rerun-tasks passed (1m30s); focused PlayUsageTest passed.

Decisions:
- Current effective Tile/Hero/Logo art comes first. Choosing current art is a no-op, retaining Automatic or the saved override; content/local provider addresses are never written as HTTPS overrides.
- Alternatives still come from the selected SteamGridDB match. No-alternative state names the source and offers another match or Automatic.
- Usage replaces launch time for that package within the queried window; older lifecycle stretches remain. Android tile wins over a whole-app stand-in so the package is counted once in the week. ROMs retain their own recorded launches.
- Only Foldcade launches have removable session ids. Usage outside a launch counts on the week but does not invent sessions or offer A Sessions on an empty launch day.
- Brightness “Modify system settings” quotes agreed with uxr-dialogs.

Device QA: pending FIFO turn.

Verification: 2,674 tests across 316 result suites, zero failures/errors. APK identity checked with aapt2 badging; versionName unchanged. Android usage-event semantics checked against https://developer.android.com/reference/android/app/usage/UsageEvents.Event#ACTIVITY_RESUMED (cited in code).

Thor QA, 2026-11-04 ~20:10–20:18 PST:
- Final week shows Pokémon HOME 2m (Fri 2m, Sat <1m); Recent hero shows Played 2m · Oct 31. No emulator or game was launched for these checks.
- Previous week (Oct 22–28) reads 0m with “No play this week” on one line. Bottom footer is Week / Back; A on a usage-only day leaves the week in place.
- Current provider Tile/Hero/Logo is visible first with a check. L1/R1 switch kinds; L2/R2 changes the named match; X Automatic works. A on current art left all Tile overrides absent in files/art/choices.json.
- About’s facts/summary card ends after the text. Companion shows Sonic’s cover, game name, and Nintendo 3DS.
- Brightness body uses curly double quotes.
- First usage-overlay build included non-game launchers; found a 31h daily chip during QA. Chose game-only outside usage (same appKind used by Games, honoring manual classification), excluding emulators/launchers. Final build recaptured week/hero/current-art/brightness; new regression test covers classification.
- Original Wi-Fi 1 and HOME app.foldcade. HOME never changed; no release data/grants modified. No save prompts accepted, save/BIOS grants changed, streams started, or PC games touched.
- Existing routing observation passed to uxr-routing: page D-pad week moves worked from display 4; injected display-0 left was ignored. B on top still closed About.

Cleanup: no save-backups directory existed; deleted only the .uxrpages RomM device (HTTP 204), uninstalled .uxrpages, set stay-on false, and released. The next holder acquired immediately; subsequent device settings belong to its turn. HOME never changed and Wi-Fi stayed enabled.

Review round 1 verified findings and follow-up:
- Fixed game page Total to shown(game), the displayed source, rather than launch-only played().
- Chose one eligibility policy across hero, totals and activity: non-game apps/emulators retain launch history everywhere. Catalog changes invalidate cached totals/week/today after a game is filed as an app or vice versa.
- Only alternatives offer Apply. Current and empty lists offer Automatic / Back.
- Companion cover takes its own fitted image bounds with the same theme corner as About; clipping the square container alone would leave portrait-cover corners square.
- An animated 44dp match band below the islands reserves space in the cover/title-screen layout.
- Verified an additional reachability gap: shell.anyPlay used launch-only hasTrackedSession(), hiding Y › Play time for a usage-only game. Added hasPlay() for that menu gate, retaining the actual-session gate for the usage-permission prompt.
- One settled candidate is now a larger centered art tile; the transition between single-art and alternatives uses the existing theme crossfade. Multi-candidate navigation remains the three-row strip.
- Full testDebugUnitTest --rerun-tasks passes after all review fixes (2,676 tests).


Thor QA round 2, 2026-11-04 21:15:30–21:22 PST:
- Pokémon HOME’s Y › Play time is available without a Foldcade launch. Its row, week and Total all read 2m. The empty sources line reads “No other play yet” on one line.
- Calendar’s hero no longer imports outside usage; its launch log is empty, matching Play activity’s policy.
- r2-art-tile/hero/logo show the larger checked current image with Automatic / Back and no dead Apply hint. Current images request 1024px to stay sharp at the larger size.
- r2-art-cover-layout-top uses a match with no wide art to exercise cover + screenshot: the match chip is above both with clear space.
- r2-about shows a content-sized facts/summary card, and the companion’s fitted portrait cover has theme-rounded corners.
- No game/app/stream was launched. HOME stayed app.foldcade and Wi-Fi stayed 1. No save-backups directory existed; own test RomM device deleted HTTP204, .uxrpages removed, stay-on set false, turn released. The next holder acquired immediately.
- Round 1 Opus findings addressed: Total source, non-game consistency, Apply hint, match-chip overlap, companion corners. An additional usage-only page gate and one orphaned “yet” were fixed during verification.
