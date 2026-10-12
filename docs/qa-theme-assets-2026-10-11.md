# Theme assets — Thor validation, 11 October 2026

Validated the resource-theme implementation at `cb331e7` against main `f5efb7b` on AYN Thor (Android 13). The physical displays were 1920 × 1080 and 1240 × 1080 at 120 Hz. Functional checks used the isolated debug identity `app.foldcade.themeqa`; performance used matched, non-debuggable release builds under `app.foldcade.themebench`, signed only for local testing.

## Native acceptance

- Afterglow rendered on both launcher surfaces. All 166 stock vectors decoded and compared against the original Compose painters: mean premultiplied RGBA difference 0.132/255 across icons; largest per-icon mean 0.261/255. No decode errors.
- Independent base, icon, and background providers worked in the preview and launcher. Controller navigation through Personalization changed Icons and Background separately; saved IDs retained the base theme and other provider selection.
- Native shader/texture/image fixtures filled both displays at render scales 0.5, 1, and 2. A viewport-dependent scheduler started at 0.5 scale without relying on an earlier draw.
- Direct and chained path variables placed the test glow at 60% of each render target width, matching its expressions.
- Invalid scene variables and scheduler expressions stopped their surfaces with one diagnostic each. A failed layer was disabled while the remaining layer continued. Selecting an invalid scene in the launcher left the controls and icon providers usable.
- Invalid padding, corner, typography, easing, duration, scale, glow, and focus-ring tokens produced defaults and source-linked diagnostics without a native control exception. Focus still moved between tiles.
- A duplicate pack was rejected before its different font asset could create a cache file.
- Legacy PNG marks used the icon provider and retained Screen blending and the base theme’s art scale.
- Paused and reduced-motion preview screenshots were pixel-identical over two-second intervals. Shoulder menus, controller Back, and sleep/wake retained both themed launcher surfaces.

## Release comparison

Each sample used the same fresh local library state, Afterglow, music disabled, Slow motion, and 30 alternating controller focus movements between the All library and PC tiles. Builds shared the same test application ID and preferences. Five seconds of warm-up preceded each 25–27-second sample. Foreground ownership of both displays was checked before every event and at the end: 248 successful checks across eight samples. Build order alternated by background.

The table uses Android graphics summaries for both windows. CPU is the process CPU time divided by wall time, expressed as a percentage of one core. These are short smoke measurements, not a statistical, battery, or thermal study. Frames still miss deadlines; this comparison found no material increase from the theme migration.

| Background | Build | Duration | Frames / missed deadlines | p95 / p99 | CPU |
| --- | --- | --- | --- | --- | --- |
| Off | Main | 26.43 s | 1,645 / 90 (5.47%) | 21 / 30 ms | 30.53% |
| Off | Theme assets | 26.40 s | 1,641 / 90 (5.48%) | 22 / 30 ms | 31.10% |
| Ribbons | Main | 26.75 s | 3,386 / 51 (1.51%) | 9 / 19 ms | 46.98% |
| Ribbons | Theme assets | 26.30 s | 3,375 / 52 (1.54%) | 9 / 18 ms | 47.64% |
| Embers | Main | 25.21 s | 2,859 / 40 (1.40%) | 8 / 17 ms | 47.32% |
| Embers | Theme assets | 26.79 s | 2,956 / 33 (1.12%) | 8 / 16 ms | 46.35% |
| Static | Main | 27.05 s | 1,641 / 94 (5.73%) | 24 / 31 ms | 30.57% |
| Static | Theme assets | 26.86 s | 1,642 / 95 (5.79%) | 24 / 31 ms | 30.67% |

## Source checks and review

All 713 unit tests passed with zero failures, errors, or skips after integration with main. Debug/release assembly, release lint, API 33 signature verification for test APKs, the version policy, and the full diff whitespace check passed. Afterglow packaging was reproducible (166 icons, 3 scenes, 191 assets). Independent correctness and simplification review found no remaining actionable issues after the six original findings and two integration regressions were fixed.

## Cleanup and scope

Only the test identities `app.foldcade.themeqa` and `app.foldcade.themebench` were removed. Wi-Fi remained off, stay-awake remained 0, both displays were turned off, and Cocoon remained Home. Existing Foldcade installs and game files were preserved. Screenshots, fixture scripts, graphics/CPU samples, ownership checks, and logs are retained locally under ignored `captures/theme-final/`.

This pass covers the presentation/theme runtime and a controlled navigation workload. External theme downloading, arbitrary screen-layout authoring, long-running thermal/battery behavior, and gameplay were outside this change’s validation.
