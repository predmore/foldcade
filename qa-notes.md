# PR376 bot audit follow-up

Fresh branch uxr-pages-fix from origin/main0cea2fe8.

- Confirmed selection jump: withCurrent reset index0 whenever currentArt[activeKind] was null, including updates that only touched other kinds. It now resets only if the active kind itself just gained current art; otherwise it finds the focused URL in the new options.
- Time mismatch skipped: merged shown() already filters the package through usageGames before reading usageByPackage. Existing gamePageTotalAndWeekIncludeUsageWhileNonGamePagesKeepLaunchTime covers utility launch30s vs outside600s; both chart/hero count30s.
- Sessions hint skipped: language playDaySessions filters empty sessionIds before grouping; FoldcadeApp only maps those rows to IDs; the footer and open-day guard consume that filtered result. Extended the usage-only regression through the day query and footer.
- Added pure state tests for unrelated arrivals, empty updates, newly current art, and stable existing current art; shell integration test verifies A applies the sixth browsed Hero URL after Tile/Logo updates.

QA build app.foldcade.uxrpagesfix, turn slug uxr-pages-fix. The external Gradle init script injects a debug-only receiver from this folder; no QA hook is in the repository or PR. It stages eight distinct public Steam CDN Hero choices in the actual picker using existing shell APIs, selects index5, delivers other-kind current art via artCurrent, and invokes the ordinary Apply action. This makes late delivery deterministic without delaying a real service. No RomM/SteamGridDB credentials or service device are needed. Screenshot/state evidence will compare selection before and after the late callback and verify the persisted Hero URL.

Full testDebugUnitTest --rerun-tasks passed before the QA build. Thor capture pending FIFO turn.

Thor 2026-11-04 23:13:20–23:17:30 PST, slug uxr-pages-fix: selected Hero index5 (Steam730), injected Tile620 and Logo220 arrivals while Hero had no current art, and observed index5 and the same URL both after delivery and an additional settled inspection. Sent the real A key; files/art/choices.json saved that same Hero URL. Captured before-late-art, after-late-art, and after-apply on both panels. The first external fixture build omitted the Kotlin source directory; its debug-only receiver was not found. Corrected the external init source set, rebuilt, then captured the successful run. No fixture code is in the PR. No game or stream was launched; no RomM credentials/device existed. No save-backups existed. Test package uninstalled, stay-on false, turn released; HOME unchanged and Wi-Fi restored to its original setting.

Validation: 2732 tests, zero failures, from testDebugUnitTest --rerun-tasks.
