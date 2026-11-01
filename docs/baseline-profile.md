# App baseline profile

`app/src/main/baseline-prof.txt` is generated from the first minute on a Thor: both
Home screens, individual and held grid moves, Y's menu, Settings rows, L1's
Home/Games/Apps places (including All games), R1, and Find. AGP merges these rules
with the library profiles and packages `assets/dexopt/baseline.prof` and `.profm`.
The release and unsigned dry-run both check the merged app rules and the APK.
Minification remains off; there is no separate startup/dex-layout profile.

## Regenerate

Use JDK 21 and the SDK from [building.md](building.md). Connect the Thor by USB,
close any games, and leave `app.foldcade.benchmark` unused. The script builds before
waiting for `/tmp/foldcade-thor.lock`, then owns the device through cleanup:

```sh
tools/profile/generate.sh -PfoldcadeHomeMusicAssets=/path/to/ci-render
```

It installs only `.benchmark` and the separate instrumentation package, uses the
debug key with `debuggable=false`, and never changes the Home role. On the fresh
install it dismisses the Home explainer once with B. Every later input first
requires Primary on display 0 and Companion on display 4, both in `.benchmark`.
Never use this package for real games or save sync: Benchmark resets it by
uninstalling/reinstalling. The script refuses an existing install, uninstalls its
test packages on exit, restores release Home on both panels, and disables stay-awake.
Review the generated diff and run:

```sh
./gradlew testDebugUnitTest --configuration-cache --rerun-tasks
./gradlew :app:assembleRelease -PfoldcadeAllowUnsigned=true -PfoldcadeHomeMusicAssets=/path/to/ci-render
python3 tools/profile/check-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

The plain `com.android.test` module uses built-in Kotlin on AGP 9.4.1 and Benchmark
1.5.0, without the Baseline Profile Gradle plugin. It configures without a device
and has no tasks in CI's unit-test or release task graphs. The `benchmark` app
variant alone is shell-profileable; release signing and application id are unchanged.

Thor firmware 377 prints a libselinux warning on stderr even when `pm install`
succeeds. `foldcade.thorShellWorkaround=true` temporarily filters exactly that
warning in Benchmark 1.5's shell wrapper and restores the wrapper after each test.
All other stderr and Benchmark's success checks remain active. This opt-in is
restricted to that firmware and belongs only to the test harness.

## Measure

`HomeBenchmarks` contains five-iteration cold `StartupTimingMetric` and Home d-pad
`FrameTimingMetric` runs under `CompilationMode.None` and
`Partial(BaselineProfileMode.Require)`. Invoke each test through `am instrument`
with the same shell workaround, or use `:baselineprofile:connectedBenchmarkAndroidTest`
with `-Pandroid.testInstrumentationRunnerArguments.class=app.foldcade.baselineprofile.HomeBenchmarks`.
Keep the Thor lock for the entire run and keep raw JSON and traces outside the repo.
Compare APKs built from the same source: save the library-only APK before adding
the app profile, then rebuild with the committed profile. Do not compare debug
with release, and do not disable compilation while claiming a compilation comparison.

A plain Obtainium APK has no `.dm` sidecar. ProfileInstaller writes the packaged
profile after first launch; background dexopt later compiles it when Android is
idle/charging. The first launch still pays interpreter/JIT costs. To exercise the
actual install path, install just the APK with `adb install`, cold-launch it, wait
for ProfileInstaller, inspect `dumpsys package <package>` (or ProfileVerifier), run
`cmd package bg-dexopt-job <package>`, and verify `status=speed-profile`. The
instrumentation's forced profile-install/compile path alone does not prove this.

Firmware 377 also crashes `traced_probes` while parsing the kernel's
`f2fs_truncate_partial_nodes.nid` field. The normal startup/frame benchmarks cannot
produce valid metrics on that firmware. `HomeGfxBenchmarks` is an explicitly named
five-iteration `FrameTimingGfxInfoMetric` fallback with a SurfaceFlinger-only trace.
Report `am start -W` separately for cold starts; it measures the primary first
frame and is not `StartupTimingMetric` or a measurement of both panels drawn.
Never treat an empty trace or frameCount=0 as a performance improvement.
