# Startup and return performance on Thor

Measured 2026-11-03 on Thor firmware 377. Base is `c202a11f`; both APKs use
the isolated `app.foldcade.perfstartup` id and the non-debuggable `benchmark`
build type. Theme and music assets match. Coarse tracing was temporarily enabled
in these APKs; production tracing remains debug-only. Installs used the ordinary
APK path, without forced compilation or a system Perfetto trace.

`/sdcard/ROMs` was added through Android's picker. It held only two Moonlight
shortcuts, so a second picker grant added an isolated fixture: 49 synthetic ROM
records across seven system folders, each 32 bytes. These measure directory
listing and catalog publication, not ROM contents or a connected RomM server.
Base and branch share the same suffix, grants, preferences and cached Home.
Afterglow / Ribbons / Slow and the default test-build music setting were used.

## Final comparison

Five runs per build and condition. Times are median / worst, in milliseconds.

| Measurement | Base | Branch |
| --- | ---: | ---: |
| Process start to first local library publication, unrecorded | 708 / 721 | 617 / 668 |
| Bottom grid recognizable after force-stop, dual-panel recording | 745 / 759 | 707 / 756 |
| Primary `am start -W` TotalTime, unrecorded | 287 / 288 | 274 / 293 |

Library publication improved by 91 ms at the median. First-grid improvement is
modest (38 ms); ranges overlap and its worst run is essentially unchanged.
These are true process-cold starts (`LaunchState: COLD`), unlike the original
release measurements prewarmed by NoticeListener. They do not establish a
release-build improvement against that earlier 49-game library.

Unrecorded starts force-stop the test package, wait 800 ms, then start Primary
on display 0. Publication is logged after ingestion on Main, measured from
`Process.getStartElapsedRealtime()`. Separate immediate force-stop starts record
both displays, including recording overhead. The first-grid method follows the
original frame/bugreport-clock script, with a crop over this fixture's NES tile
to distinguish the test grid from the release Home underneath. Extracted frames
and contact sheets were inspected. Allow about one captured frame of timing
uncertainty. Cached Home can appear before the fresh catalog is published;
these are separate measurements.

## Work on returning Home

Counts are folder walks / save discovery / save health.

| Workload | Base | Branch |
| --- | --- | --- |
| Initial resumes of both activities, every measured cold start | 1 / 3 / 5 | 1 / 1 / 1 |
| Return from Android Settings, each of three runs | 1 / 1 / 2 | 1 / 1 / 1 |
| Three rapid Settings returns, total | 3 / 3 / 6 | 1 / 3 / 3 |

The rapid branch walk counts were 0, 1, 0. Save checks still ran on every
foreground transition. Two explicit Library > Sync games actions 1.3 seconds
apart each performed a walk, bypassing the five-second interval.

The changed-snapshot regression test also verifies that a quiet rescan's new
platform/game listing performs no second walk and keeps the same media snapshot.
Incomplete-forest and restore-source checks remain in place.

## History and themes

A scale supplement used 1,000 ended sessions (2,000 events and 49 remembered
names) plus an inactive Afterglow-derived pack. Four valid logged benchmark
starts per build measured history opening at 12 ms median on base. Preparing it
on IO during theme loading reduced the later Main wait to 0 ms; IO preparation
itself took 13 ms median. Main still awaits complete history before constructing
the shell, preserving initial Recent order, totals and recovery.

The required active/fallback theme load measured about 35 ms. The synthetic
inactive pack added about 16 ms; theme recovery and loaded-ledger bookkeeping
cost about 10 ms combined. Lazy theme-catalog and durable-bookkeeping changes
were skipped in this PR: the normal configuration already has only the active
fallback pack, and deferring other packs would also need to preserve validation
and the complete background-choice catalog. Trial begin/read/loaded ordering
and durable writes retain their existing behavior.

## Correctness and interaction checks

An earlier implementation published the catalog faster but delayed the first
cached grid (725 to 839 ms in that first session). The final startup scan keeps
saved Home visible rather than replacing it with Loading. It ingests results
without resetting an already usable Home, preserving open menus, Find and its
query. Explicit reloads retain their existing loading behavior.

On-device input opened Find before publication; its log precedes publication by
688 ms, and its keyboard remained open afterward. L1 input was also sent during
startup and the drawer remained visible after publication. Unit tests pin query
and tile-menu preservation, fallback from a loading grid, and explicit reloads.

`./gradlew testDebugUnitTest --rerun-tasks` passed. Other new tests cover refresh
coalescing, changed grants during an active read, cancellation, an actual game
return after a companion pause, interval bypass, and snapshot reuse. The ARMSX2
blank-card fixture performs 129 bounded reads through one resolved/open handle,
closes it on failure, and still treats an unreadable card as needing setup.
No on-device emulator card timing or game/save flow was exercised.

Both device sessions finished within the 25-minute lock limit. The test package
was uninstalled, the isolated public fixture removed, stay-awake disabled, and
the release Primary and Companion Home activities verified on both panels.
The final cleanup was at 09:10 PST. Release preferences and the HOME role were
not changed.

Raw APKs, workload scripts, logs, videos, extracted frames, summaries and cleanup
evidence are outside the repository in `/tmp/foldcade-perf/startup/`. Final
results are `summary.json`, `log-analysis.json` and `video-analysis.json`.
`session1/` retains the earlier regression and history/theme scale samples;
`aborted-before-fixture/` contains an excluded setup run made before the picker
result persisted. Every reported library run published 49 local records.
