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
