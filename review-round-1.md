Opus round 1 at PR 385 / 600ae16b: no correctness bugs; two P3 findings.

1. Saved names were only resolved on A, while games and board.ranks are absent on cold disk startup. Fix: give HomeSession a memory-only name resolver backed by loaded PlayNames and RomM cached metadata; retain successful names and invalidate visible faces if a direct lookup learns one. Catalog wait uses that same resolved title. Added a disk-loaded, rank-free board + durable/reopened PlayNames test that deletes the underlying names file before shell use and asserts one title before/during/after A. Also cover metadata-only cold startup and face cache refresh. Kept the in-memory ranks test for its existing runtime case, but it is no longer the sole kept-name coverage.

2. Fresh Play activity lacked No play yet. Fix: choose that copy when PlaySessions.hasPlay() is false; keep No play this week when history exists. Added copy regression coverage and prepared separate fresh/quiet-week Thor fixtures.

Reviewer accepted all remaining assignments at 9/10 and confirmed 237-F2 is the approved scene shimmer, not an empty-slot/loading bug. Round 2 recheck must verify these two fixes and assess convergence.
