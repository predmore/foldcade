# Local game media and facts

`local.media` is a metadata provider in `:plugins:local-folder`. It supplies existing
art and info for `local-folder` records. It makes no network requests and works with
Game art off. RomM records use RomM's metadata provider instead.

Add the ROM folders and, if separate, the ES-DE data folder through the existing
Add a folder flow. A granted tree can contain `ES-DE`, `downloaded_media`, or
`gamelists` below its root. Sources with media and no games show “art and info” in their folder
label. No additional Android grant or all-files access is needed.

The ROM walk records media locations without opening their images or gamelists.
The app builds the media index on IO after that walk, without awaiting it before
listing games. Returning Home rebuilds it only when the scan changes; reload the
folders to pick up scraper edits that leave the scan unchanged.
Publication checks that the forest and ROM snapshot are still current. A changed
index forgets local games' held artwork and advances the artwork and facts
Compose revisions. Facts are read directly from the current provider snapshot.

Supported layouts are ES-DE's `downloaded_media/<system>/<role>/<relative stem>.*`
and `gamelists/<system>/gamelist.xml`, system-side `gamelist.xml`,
`media/<role>/<stem>.*`, and direct role folders such as `Imgs`. Role names and
matching are case insensitive. Gameplay screenshots take precedence over title
screens. Gamelist box art and thumbnail references precede role-folder covers, with the
ambiguous `image` tag last (it may contain a mix image or screenshot);
a missing reference does not prevent a later one from working. Videos, manuals,
back covers, 3D boxes and mixed images are ignored.

Matching stays within the existing platform catalog. Relative stems preserve ROM
subdirectories and playlist identities. Exact stems come before gamelist paths and
normalized titles. A normalized title must pick one entry, with a matching region
allowed to break a tie. There is no fuzzy search. A dumped absolute ROM path can
identify a filename; it never authorizes reading an absolute image path.

Gamelists use a small flat-field parser, not an XML engine. DTDs and external
entities are never evaluated. Reads are limited to 16 MiB, fields are bounded,
and `normalizeGameDetails` caps a summary at 1,200 characters. Media paths resolve
through enumerated documents inside their system root. Image loading requires
membership in the current index and the matching configured library tree. Opaque
SAF document ids work without guessing their filesystem paths. The resolver does
the actual grant enforcement. Image reads use IO and refuse bodies over 20 MiB.

## On screen

The top screen's facts line adds the release year and first genre after the platform,
and drops genre, then year, then "Both screens", before it would cut the play line. When the focused game has
a summary, the grid's hint row names R3 Info. R3, the right stick's click, grows the name
card to show up to three lines of it, and R3 again folds it. Moving on, or B, closes it at
once, so each game starts closed. Top only has no hero, so it has no summary there. A
game with no facts looks exactly as before. The old `motion.durationSummaryDwell` token
is retired: a pack that still sets it is told it does nothing.

Y › About (ROM games only) opens a page on the layout's page screen: cover, title,
platform, facts, a screenshot when one is known, and the summary cut to fit. Nothing
scrolls. With no facts it says where they come from.

## App lookup

UI code calls `rememberGameDetails(app, record)` from `app.foldcade.ui`, alongside
`rememberArt` or `rememberScene`, with the library `Game` it shows, such as
`shell.artRecord(focusedGame)`. This reads available facts immediately, then launches
a cancellable lookup. It observes `app.infoRevision` and the signed-in connections;
changing focus cancels the prior lookup.

Non-Compose callers use `app.gameInfo.cached(record)` and suspending
`app.gameInfo.fetch(record)`. These return the first nonempty normalized
`GameDetails` across `plugins.metadataIds()`. Null records return null. The lookup holds no results or misses;
`gameInfo.forget(backendId, which)` remains a compatibility no-op.
`PluginHost.cachedDetails(providerId, game)` stays on the caller, while
`fetchDetails(providerId, game)` runs on the host's IO dispatcher.

`GameMeta` is unchanged. API minor 20 adds defaulted details members and the new
frozen `GameDetails`. RomM uses `summary` and aggregate `metadatum` fields, with
explicit developer and publisher lists. Details restore the disk catalog before
reading, without opening a network session. Library and artwork reads populate
that catalog, so focusing a game adds no network requests. RomM's default player
count of `1` is omitted because it cannot be distinguished from missing facts.
The aggregate release date is Unix
milliseconds, unlike individual scraper metadata dates.

## Cold index cost

On a Thor, adding an ES-DE folder with 1,000 ROMs and 5,000 images showed the first
local hero art about 3.3 s later, and a cold start showed it after about 5 s,
including opening All games. Held browsing on that 1,015-game grid measured 18.0%
janky frames (p95 29 ms) against 19.0% (p95 32 ms) on the base commit.

The unit fixture below measures the walk itself.

`LocalMediaIndexTest.realisticColdIndexQueriesDirectoriesOnceAndKeepsOnlyMatchedGames`
builds 2,000 ROM records across 20 systems, with four images per game and 20 ES-DE
gamelists. Summaries are 599 characters, and image URIs use realistic external
storage document paths. The fixture keeps 8,000 image addresses, with no image bytes.

The measured logical document walk performs 23 ROM-directory listings and 122
additional media-directory listings, plus 20 bounded gamelist opens. Each directory
is queried at most once during the index build, including resolution of gamelist
references. With a single granted parent tree, add one initial root metadata query:
146 SAF queries for the library and index together. The extra media work is 122
queries and 20 document opens; there is no query per ROM or image.

A full uncached test run on the development Mac took 130 ms for the in-memory
index build. This excludes Android IPC, storage latency and image decoding. For
example, 5–20 ms per SAF query would add 0.6–2.4 seconds for media listings alone,
plus text-open latency and parsing.

The test's conservative object-graph estimate is 8,547,336 bytes total, or 4,273
bytes per game (about 4.2 KiB). It assumes 16-byte headers, four-byte references,
eight-byte alignment and UTF-16 string storage, and includes lookup collections,
URI keys and the image-address set. This is an estimate, not an ART heap capture;
shared existing ROM keys and compact strings can reduce the incremental cost.
With every summary at the 1,200-character cap the same fixture would add roughly
1,202 bytes per game. Directory listings, parser records and matching tables are
temporary and are released after publication; rebuilding briefly also holds the
previous index. Coil owns decoded image memory as it does for other artwork.

## Limits and source references

A gamelist image must remain inside a granted system root. Absolute or home-relative
legacy media paths need to be made relative; Foldcade does not add storage grants
or follow them outside that root. Only systems already recognized by the folder
platform catalog participate.

The design does not specify image freshness when a scraper overwrites bytes at the
same document URI. This implementation retains the existing URI-based Coil cache
keys: new or remapped documents and changed facts refresh, while an in-place image
replacement may remain cached. A future change can carry document modification
versions into image cache keys without rereading image bytes during a scan.

The parser and matching code are original Kotlin based on public format documentation:

- [ES-DE user guide: copying game media](https://gitlab.com/es-de/emulationstation-de/-/blob/master/USERGUIDE.md#manually-copying-game-media-files)
- [EmulationStation gamelist format](https://github.com/Aloshi/EmulationStation/blob/master/GAMELISTS.md)
- [RomM 5.4.1 response schema](https://github.com/rommapp/romm/blob/5.4.1/backend/endpoints/responses/rom.py)

RomM's aggregate fields were also checked with read-only requests to the configured
server. The test fixture contains only selected display facts and synthetic ROM
identity fields; no server address or credential is included.
