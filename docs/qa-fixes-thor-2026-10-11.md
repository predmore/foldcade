# Thor QA fixes — 11 October 2026

Five functional and UX findings from [the QA audit](qa-audit-thor-2026-10-11.md) are fixed in this patch. The two performance findings remain open: tested rendering changes did not reliably improve the Thor measurements and were reverted.

## Fixes

| Finding | Result | Verification |
| --- | --- | --- |
| AUD-01: SteamGridDB save ignores cancellation | Shared validation tickets prevent duplicate submissions and reject results after edits, cancellation, or reopening. The form shows a busy state and clears it safely. | Valid-key Save followed immediately by Back leaves **Add key** in Settings. Regression covers duplicate, edited, cancelled, and overlapping visits. Normal save succeeds. |
| AUD-02: expired/denied RomM codes still invite approval | Terminal outcomes replace the old challenge with an explanation and **Get a new code**. Both displays share a visit generation and address revision, so superseded attempts cannot update reopened or edited forms. Token validation retires polling on both displays and keeps retry available through edits. | Controlled API fixture: expiry → controller retry → denial → touch retry → expiry. Exactly three heartbeats, three challenge initializations, and three terminal polls. Additional regressions cover equivalent-address edits, edits back to the original address, and token validation from either display. |
| AUD-03: toolbar focus disables shoulders | Toolbar focus forwards L1, R1, and Options to the shared reducer. | Regression covers all three menu inputs at all four toolbar controls. Injected controller key events on Thor open both menus while the sort control remains focused. |
| AUD-04: partial scenes cannot gain missing artwork | Partial hits retain their useful parts, record source IDs and retry state, and refresh after source changes, expiry, explicit invalidation, or an outage. Existing cache formats remain readable. The UI fetches incomplete scenes and prefers enriched results. | Eight recovery regressions cover persistence, source additions, invalidation, outages, legacy hits, age, disabled artwork, concurrency, and cancellation. On Thor, saving the real key enriches Sonic’s previously cached screenshot scene with a hero and logo. Both restore after an offline process cold start. |
| AUD-07: All Games excludes other game types | All Games contains ROM, Android, PC/GameNative, and Moonlight games. All Apps contains applications. Shared eligibility excludes setup/navigation placeholders from both tabs and excludes app-only platforms from game system choices. | Kind/filter regressions and physical All Games inspection. Installed Android games appear alongside ROMs; importing `/sdcard/Steam` exposes all five exported PC games under All Games → PC. |

## Reviews

The source correctness review checked shared validation tickets, duplicate submits, edit/close/reopen behavior, cleanup in cancellation paths, device-code terminal states, retries from either activity, cache-file compatibility, preserved partial artwork, and library filtering. Regression tests cover these paths.

The final code review found additional RomM state edges: editing a terminal form to an equivalent normalized address could hide retry while the loop still considered that address handled; a stale approval could stop the loop for a newly edited address; and a pasted token on the other display could leave an old challenge active after validation finished. Address revisions now invalidate each challenge while the visit guard controls the polling loop. Token validation retires that visit on both displays, and retry remains available if the address is edited during or after validation. Superseded approvals cannot terminate the current visit. Two added state regressions cover these transitions.

The simplification review centralized device-code guards in the shared shell, removed redundant origin/instance checks from polling callbacks, shared the All Games eligibility predicate with system choices, and expanded nested UI/cache conditionals into readable branches. Sharing eligibility also fixes app-only platforms appearing as empty game filters; a regression covers that case. No further correctness findings remain from this review.

The integration and rendering review found that waiting for partial-scene enrichment during hero staging delayed navigation. That proposed change was removed: cached images warm immediately, while `rememberScene` refreshes missing parts after arrival. Device checks confirmed successful enrichment without deleting the previously useful scene.

Performance review rejected timer scheduling, lower resolution shader layers, extra compositing layers, lower idle cadence, dynamic-array/texture shader lookups, reduced blur layers, cached CPU blur, and skipping duplicate ribbon geometry. None is in the final patch. The shader, backdrop clock/cadence, and blur rendering match the original implementation. Two experimental shader candidates failed at startup because of AGSL compiler restrictions; those candidates were removed.

These were source, automated regression, and device reviews performed in this session. No independent reviewer or delegated review was used. The final PC import check caught setup placeholders entering the broader game filter; the patch now tags and excludes those entries, with a regression for game rows and system choices.

## Performance findings still open

CPU percentages represent one CPU core; frame totals aggregate both Foldcade surfaces. These short samples do not measure battery life or sustained thermal behavior.

- **AUD-05:** the original Ribbons/Slow cycle used 30.5% CPU over 24.27 s with 2,064 frames and zero missed deadlines. A later original-build repeat used 55.4% over 24.21 s with 2,060 frames and 27 misses (1.31%). The substantial cost and run-to-run variation remain. Idle slowing reduced one sample to 28.4% but missed 609/1,680 deadlines (36.25%); texture lookup used 63.4–63.5% CPU. Neither was accepted.
- **AUD-06:** with Background Off, Nintendo 3DS filtering, and 24 alternating events between Sonic and Bravely Second, original-build repeats missed 50/1,738 (2.88%) and 53/1,742 (3.04%) deadlines; CPU was 44.0% and 39.3%, p95 24/22 ms, and p99 32/29 ms. Cached blur repeats missed 59/1,733 (3.40%) and 54/1,732 (3.12%), with essentially the same tail timings. The change was removed because it did not demonstrate an improvement.

The expanded All Games tab initially changed the benchmark’s second tile to an Android test application. Those comparisons were excluded and repeated with the Nintendo 3DS filter. Samples taken under the wrong filter and samples interrupted by another application were also excluded. Raw data, exclusions, rejected candidate APKs, and screenshots remain in `captures/qa-fixes-2026-10-11/` (ignored by Git).

## Final validation

- **702 tests passed**, with zero failures, errors, or skips, including Gradle cache results. Seventeen regression tests were added to the original 685-test suite.
- Debug and non-debuggable unsigned release builds assembled; release lint passed with existing warnings. `git diff --check` and the CI version-bump check passed.
- Final isolated package: `app.foldcade.qaaudit`, serial `c013eb4e`, logical displays 0 and 4. Production installs and source game files were preserved; Cocoon remained Home.
- Device-tested APK before the final code review: `fixes-final.apk`, SHA-256 `64d2181735911ffd213452249d0d592a77f48e0bd23fe461b71880c5fa32a35f`. Music/theme assets match the baseline APK. The subsequent review fixes were verified by regressions, assembly, and lint after releasing the device; they have not received another physical-device pass.
- Real device-code approval, save upload/conflict flows, Moonlight streaming, PC gameplay, and long-duration power/thermal testing remain outside this fix pass.

## Evidence

Relevant captures include `art-key-cancel-*`, fixture request logs and expiry/denial captures, `final-toolbar-focused-*`, `final-toolbar-l1-*`, `final-toolbar-r1-*`, `final-sonic-enriched-confirm-*`, and `final-offline-sonic-restored-*`, `final-steam-folder-files-*`, and `final-all-games-pc-filter-*`, `final-pc-no-placeholder-*`, and `final-pc-export-fifth-*`. Device-tested build output is retained in `/tmp/foldcade-qa-final-build.log`; final code-review build output is in `/tmp/foldcade-qa-pr-build.log`. Performance rows and raw `gfx-*` files are in the ignored evidence directory.

## Cleanup and reservation

Stopped the fixture, removed its ADB reverse port, and uninstalled only `app.foldcade.qaaudit`. The temporary credentials and folder grant belonged to that isolated installation. Production/test installs and game files were retained. Cocoon remained Home. Wi-Fi returned to off, stay-awake to `0`, and both physical displays to off; `cleanup.json` records the checks. Thor’s reservation was released to the other device-testing thread. The final code and simplification review did not interact with the device.
