# Resource themes

Themes are ZIPs containing JSON, images, Android vector XML, fonts, audio, and AGSL shaders. They contain no Java, Kotlin, DEX, native libraries, or application scripts. The native renderer provides the machinery; packs own their design and performance. There is no automatic quality reduction or cap on scene layers, particles, shader complexity, or requested frame rate.

Afterglow is the reference pack in `themes/afterglow/`. Its icon artwork, colours, focus styling, typography, motion, shapes, component styling, layout metrics, and backgrounds live there, and it declares every [style token](#style-tokens) the engine reads; a test fails if the engine reads one Afterglow leaves out. The app generates its emergency drawable resources from those same icon files. Tests keep a CPU model of the Afterglow backgrounds. It is updated alongside the scenes and checks their uniforms, lit budget, and motion pacing.

The current version exposes the launcher's presentation: colours, type, icons, shapes, motion, sounds, and the look of each component. It does not let a pack replace the controller's grid/navigation topology, author arbitrary screen layouts, or change layout proportions such as row heights and the keyboard's key pitch. A player adds a pack from **Settings › Look › Themes › Add a theme**, which installs a zip from the file picker for the next start. [Trials](#releases-and-trials) keep a pack that crashes Foldcade from staying. The same page lists every pack with what it says about itself and what Foldcade found while loading it, so an author can read a pack's report on the device.

## Compatibility

A pack is written against a contract: the manifest fields, style tokens, icon slots, scene inputs, and sound cues of one Foldcade. [`themes/contract.json`](../themes/contract.json) lists every one, with each token's kind, unit, accepted values, the `base` scale it follows, and Afterglow's value. [`themes/theme.schema.json`](../themes/theme.schema.json) is a JSON Schema for `theme.json`. Point a manifest's `"$schema"` at it and an editor completes and checks the manifest as you write it. Both files are written from the engine by its tests, and a build fails when either is stale.

What Foldcade promises a pack:

- **Nothing in the contract disappears silently.** A test fails when a token or slot leaves it, unless the engine says where it went. A renamed token or slot keeps working under its old name, and the pack's report says what it became. A retired one is reported as doing nothing, and its name is never reused for something else.
- **New things are additions.** A token added to the engine follows the pack's own [`base` scales](#base) and inherits Afterglow's value otherwise, so an older pack keeps its proportions. A scene input added later is shadowed by a scene variable of the same name, so it never changes a scene written before it. A sound cue added later falls back to a cue the pack already ships.
- **`host` changes only for a break.** The theme format level is raised only when a pack written for the old level could load wrongly. Additions never raise it. A pack that needs a higher level still loads, and is never installed as an update.
- **`version` stays 2.** A manifest version outside 1–2 is refused, so a future format that cannot be read safely is not read at all.

One thing does move: a token a pack leaves out is Afterglow's, so when Afterglow's value changes, the pack's look changes with it. The contract lists Afterglow's values, so such a change shows in review. A pack that cares about a value sets it.

`Px` tokens are the Thor's panel pixels, as the host's own layout is. `Dp` tokens are density-independent. `sp` is type.

## Inheritance

Every pack inherits from Afterglow. A colour, `iconRadius`, `artScale`, style token, font, or sound a pack leaves out, or gets wrong, is Afterglow's. An icon slot it leaves out is Afterglow's too, slot by slot.

A pack can build on another installed pack instead with `"extends": "<id>"`. It inherits that pack's resolved look, its fonts, its sounds, its icons for slots it does not fill, and, when it names no scene of its own, its default background. That pack in turn inherits from Afterglow. A family of variants then ships one base pack and small packs that each change a few things. A pack whose parent is not installed, or whose chain leads back to itself, builds on Afterglow, and its report says why. Packs a pack builds on count as in use for its [trial](#releases-and-trials). A pack can therefore change one thing, say `{"name": "Square", "style": {"shape": {"round": 0}}}`, and look like Afterglow otherwise. A pack that wants a slot quiet or blank ships a silent clip or an empty icon in it. Scenes are not inherited; they are listed separately (see below). If Afterglow itself cannot load, packs fall back to the engine's built-in values, which the Built-in theme also uses.

## Compose providers

The Theme setting selects the base colors, font, styling, sounds, and music, each inherited from Afterglow where the pack has none. Icons can follow that theme or select another pack independently. Missing or unreadable override icons fall through to the base pack and then Afterglow. Built-in selects the native emergency icons explicitly.

Background lists are discovered from every installed pack. Selecting one pins it independently of the base theme. Each scene keeps its provider's shaders, textures, palette, variables, and timeline; unrelated icon packs cannot replace those internal assets. IDs are `pack-id:scene-id`. The host's Off choice means no scene. Old `ribbons`, `embers`, and `static` preferences migrate when the scene name is unambiguous. Unavailable theme/icon selections remain saved for when the provider returns.

## Manifest version 2

```json
{
  "version": 2,
  "id": "example",
  "name": "Example",
  "release": "1.0.0",
  "source": {"github": "someone/foldcade-example"},
  "author": "Someone",
  "description": "Soft light on true black.",
  "license": "CC-BY-4.0",
  "preview": "preview.png",
  "extends": "example-base",
  "fonts": {"regular": "fonts/Body.ttf", "bold": "fonts/Body-Bold.ttf", "display": "fonts/Display.otf"},
  "background": "#000000",
  "surface": "#1C1C20",
  "onBackground": "#F4F1EA",
  "muted": "#9A958C",
  "focus": "#C8FFE4",
  "iconRadius": 0.28,
  "artScale": 0.92,
  "backgroundMotion": "stars",
  "icons": {
    "mark.nintendo-3ds": {"file": "icons/3ds.webp", "accent": "#FF5C7A"},
    "button.a": {"file": "icons/a.xml"},
    "badge.cloud": {"file": "icons/cloud.xml", "tint": true}
  },
  "scenes": ["backgrounds/stars.json"],
  "style": {
    "focusRing": {"start": "#52B788", "end": "#3E8EC0", "stroke": 6},
    "type": {"heroTitle": 28, "hint": 14},
    "motion": {"durationFocus": 180, "scaleFocus": 1.05, "arrive": [0.05, 0.7, 0.1, 1]},
    "metrics": {"cardCornerDp": 28},
    "glow": {"focusOverflow": 64, "focusPeak": 1},
    "colors": {"scrim": "#0B0820"},
    "shape": {"round": 0.25, "highlightCornerDp": 12},
    "collections": {"gold": "#F2C14E", "glyphY": 38}
  }
}
```

`author`, `description`, `license` and `preview` (an image in the archive) describe the pack where packs are listed. None is inherited.

Category-only packs can omit colors, style, icons, or scenes. A name is required; give v2 packs a stable id using letters, numbers, dots, underscores or hyphens. The id `builtin` is reserved. Icons accept PNG, WebP, or self-contained Android vector XML with literal colors. XML supports paths, group transforms, clipping, strokes, and inline linear/radial/sweep gradients. XML may not refer to application resource IDs. The underlying vector grammar is documented by [Android](https://developer.android.com/reference/android/graphics/drawable/VectorDrawable). All asset paths are relative to the archive root. See Afterglow's manifest for the complete stock slot list and styling tokens. Platforms use `mark.<canonical-platform-id>` on the grid and `symbol.<canonical-platform-id>` in menus, folders use `mark.folder`, controller hints use `button.a`/`button.a.filled`, and status variants use slots such as `status.battery.100.charging`. Any platform's id works, not only the ones Afterglow draws: a pack that fills `mark.sega-cd` gives Sega CD its own tile, and a platform no pack draws wears `mark.console`. The host's own controls draw `control.check` and `control.dash` (a checkmark's tick, wiped in from the left), `control.shift` and `control.shift.on` (crossfaded as Shift lights), `control.erase`, `control.space` (2.4 times as wide as it is tall), and `control.search`; Find marks hidden results with `badge.hidden`. Mark these one-ink icons `"tint": true`, so they follow the theme's ink. An installed app wears `app.<package>`, for example `app.com.android.chrome`, on its tile, in the hero, and beside its notices, in place of its launcher icon; it is drawn like a mark, on the light in its accent. Afterglow fills one slot per package for each app it draws, so a pack can restyle one build of an app without the others.

An icon's accent is the light behind its tile and its hero glow. How Afterglow's icons are drawn is in [iconography](iconography.md). Badge icons use their own colors unless `tint` is true, which requests the control's semantic tint. Game cover art keeps its normal priority. An app's own launcher icon shows only when no pack fills its `app.` slot.

The resolved icon carries its accent, tint, and blending metadata together with its image; cache eviction does not change its appearance. Legacy `marks/<name>.png` files contribute `mark.<name>` slots and keep their original Screen blending. Explicit manifest slots take precedence over legacy files in the same pack.

A slot in a family the host names (`button.`, `status.`, `badge.`, `control.`) that Afterglow does not fill is a misspelling, and is reported. The `mark.`, `symbol.` and `app.` families take names of the pack's own.

### Fonts

`fonts` names up to three faces. `regular` is running text at `type.bodyWeight`. `bold` is strong text at `type.strongWeight`; without it the regular face is made bolder. A variable face, one with a `wght` axis, is set to both weights itself and needs no bold file. `display` is the hero's title, dialog titles, the clock and the save-conflict game; without it they wear the regular face. Each face a pack leaves out is its parent's, and a missing or unreadable face falls back the same way. A parent's bold belongs to its regular face, so it carries over only when the pack keeps that face too.

### Sounds

A pack ships `sounds/<cue>.ogg`. The first four cues have always played: `move`, `activate`, `back` and `notify`. The others refine them, and each one falls back to the cue that played at that moment before it existed, so a pack with only the first four sounds as it always did:

| Cue | Plays when | Falls back to |
| --- | --- | --- |
| `open` | A menu, dialog, page, Find or the connection form opens | `activate` |
| `close` | One of them closes | `back` |
| `launch` | A game or app starts | `activate` |
| `toggle` | A switch flips | `activate` |
| `type` | A key types on the on-screen keyboard | `activate` |
| `erase` | A key erases | `back` |
| `refuse` | The field turns a key down | nothing |
| `edge` | A move runs into an edge and changes nothing | nothing |

A cue plays the first clip of its chain the pack itself has, before looking in the pack it builds on and then in Afterglow.

V1 conventions remain accepted: `font.ttf`/`font.otf`, `marks/<name>.png`, `wallpaper-top.png`/`.webp`, `wallpaper-bottom.png`/`.webp`, and `sounds/{move,activate,back,notify}.ogg`. Font, wallpaper, and sound failures leave usable defaults. Wallpapers are letterboxed. Theme music follows the existing optional `music/theme.ogg` contract.

Styles are resolved once into typed values. An unknown token or group is reported, so a misspelling does not go unnoticed. Invalid tokens report their manifest path and use Afterglow's value: padding and dimensions must be nonnegative, type sizes/scales and durations positive, alpha/peak values in 0–1, rim values in 0–1 excluding 1, and glow steps whole nonnegative integers. Insets use 0–0.5 and art fractions use 0–1 excluding 0. Motion offsets and depth may be signed; easing y control points may overshoot. These domains prevent invalid native inputs; they do not cap rendering complexity. Duplicate pack IDs are ignored before fonts/audio are materialized, and cached files are keyed by their asset contents.

## Releases and trials

Three manifest fields describe a pack's versions. None is inherited from Afterglow, and getting one wrong is reported without stopping the pack loading.

- `release` is the pack's own version, a string such as `"1.2.0"`. `"1.2"` means `1.2.0`. Updates compare it.
- `host` is the theme format level the pack needs, a whole number from 1; left out, it is 1. This Foldcade reads level 1. A pack that needs a higher level still loads here, but is never installed as an update.
- `source` is where its updates come from: `{"github": "owner/repo"}`, the releases of that GitHub repository. Case does not matter.

An update installs only over a pack with the same `id` and the same `source`, and only with a higher `release`. The first install locks the source, so another repository cannot take over an id. A pack Foldcade ships cannot be replaced, a version 1 manifest has no stable id and never installs, and a pack with no `release` cannot be compared. Before an update replaces anything, it loads the way the next start will load it, over the installed pack it `extends` or else over Afterglow: every icon and scene it names must resolve and every shader must compile on the device. It is staged in `files/themes-staging`, written down in `files/theme-installs.json`, then renamed into `files/themes`. The archive it replaces is kept in `files/themes-previous`, unless that archive is itself still on trial: then the proven copy kept before it stays. A start that finds an update cut off part way finishes it. Installs take effect at the next start. **Add a theme** in Settings › Look › Themes installs this way, and the page offers to restart. Pressing X (or whichever button calibration left for the bottom screen) twice on an installed pack there takes it out, with the copy kept beside it, at the next start; its refused releases stay refused. A pack Foldcade ships cannot be taken out. Nothing downloads updates yet; `FoldcadeApp.installTheme` is where the updater will hand one over. An install is read the way the next start reads it: the archive is opened in place, not streamed, so what was checked is what loads.

An installed pack is read in place. At start Foldcade reads its manifest and scenes; its icons, wallpapers, sounds and music are read when something first draws or plays them. Installing many packs costs little more than their manifests until one is used.

Foldcade is the Home app, so a pack that crashes it could leave nothing to go back to. A pack that arrives in `files/themes`, or changes there, is on trial. Android's record of how Foldcade's process ended is read at the next start. Each crash or ANR counts against the packs on trial that were in use: the packs of the theme, icon theme, and background the shell had resolved, or every pack still loading when it stopped. A crash before a new copy loaded does not count against that copy. After two, the start refuses that release from then on, writes that down, and only then puts the copy kept in `themes-previous` back, or takes the pack out when there is none. R1 says which. A trial ends after three minutes of Home shown with the pack in use. Packs already in `files/themes` the first time Foldcade keeps the ledger are not on trial.

## Style tokens

Every token below is optional. A pack's value replaces Afterglow's; anything it leaves out is Afterglow's. `Dp` values are density-independent, `Px` values are panel pixels, and an `Alpha` is 0–1, usually a strength over `onBackground`, the focus ring's colours, or `colors.scrim`. Colours are `#RRGGBB`.

### `base`

Proportions a pack sets once for everything it inherits. Each multiplies the tokens of its kind the pack leaves out, including tokens added to Foldcade after the pack was made, so they come in at the pack's proportions rather than Afterglow's. A value the pack sets itself is never scaled. A scaled value stays inside its token's range. A pack that builds on another inherits that pack's scaled values, not its scales. Which scale a token follows is its `role` in the contract.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `base.cornerScale` | 1 | Every corner and roundness, the tiles' inherited `iconRadius` too. 0 squares the whole launcher |
| `base.strokeScale` | 1 | Every ring, rim, border and outline width. Above 0 |
| `base.glowScale` | 1 | Every glow's strength |
| `base.fillScale` | 1 | Every translucent fill, rim and track over the ink |
| `base.typeScale` | 1 | Every type size. Above 0 |
| `base.durationScale` | 1 | Every duration. Above 0; below 1 is quicker |

### `focusRing`

The focus ring every focusable thing shares. A pack that sets its own `focus` and leaves out `start` and `end` gets a ring in its `focus` colour; one that sets neither inherits Afterglow's gradient.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `focusRing.start` | #52B788 | Gradient colour at the top left |
| `focusRing.end` | #3E8EC0 | Gradient colour at the bottom right |
| `focusRing.stroke` | 6 | Stroke width, px |
| `focusRing.gap` | 2 | Space between the shape and the ring, px |
| `focusRing.glowSpread` | 10 | How far the glow reaches past the stroke, px |
| `focusRing.glowAlpha` | 0.1 | Glow strength, 0–1 |
| `focusRing.glowSteps` | 4 | Strokes the glow is built from, whole number |
| `focusRing.pronouncedStroke` | 10 | Stroke of the heavier ring for choices read from across the screen |
| `focusRing.pronouncedSpread` | 22 | Its glow reach |
| `focusRing.pronouncedAlpha` | 0.22 | Its glow strength |

### `type`

Text sizes in sp, and weights (CSS weights, 1–1000). One `font.ttf` is synthesised to the other weight.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `type.heroTitle` | 28 | The top screen's title |
| `type.conflictGame` | 40 | The game on a save-conflict card |
| `type.heroMeta` | 18 | Facts under the hero title |
| `type.availability` | 16 | Where a game is |
| `type.gridLabel` | 16 | Titles under tiles |
| `type.dialogTitle` | 20 | Dialog titles |
| `type.dialogBody` | 16 | Dialog text |
| `type.menuRow` | 18 | Menu and Settings rows |
| `type.sideRow` | 18 | Island rows and sliders |
| `type.hint` | 14 | Hint row, chips, small print |
| `type.clock` | 64 | The command center's clock |
| `type.bodyWeight` | 400 | Running text |
| `type.strongWeight` | 600 | Titles, headers, the typed name, Done |

### `glow`

The soft light behind marks and around focus.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `glow.focusOverflow` | 64 | Glow reach past a focused tile, px |
| `glow.restOverflow` | 18 | Glow reach past a resting tile, px |
| `glow.restTightness` | 2.4 | Falloff at rest (higher is tighter) |
| `glow.focusTightness` | 0.85 | Falloff when focused |
| `glow.restRim` | 0.42 | Where the falloff starts at rest, 0–1 |
| `glow.focusRim` | 0.66 | Where it starts when focused |
| `glow.restPeak` | 0 | Glow strength at rest |
| `glow.focusPeak` | 1 | Glow strength when focused |
| `glow.heroPeak` | 0.45 | The light behind a lone cover or mark in the hero |
| `glow.markLightTop` | 0.12 | Centre of the light inside a mark's tile, from its top, share of the tile |
| `glow.markLightReach` | 0.85 | Its reach, share of the tile's width |
| `glow.markLightPeak` | 0.34 | Its strength |
| `glow.markLightFade` | 0.72 | Where it has faded into the tile, 0–1 |

### `motion`

Durations in ms, scales, distances, and cubic-bézier curves `[x1, y1, x2, y2]`. Android's animator scale and the Motion setting still apply.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `motion.durationShort` | 100 | Small changes, and every move when animations are off |
| `motion.durationFocus` | 180 | Focus moving |
| `motion.durationTravel` | 200 | Hero art travelling with the cursor |
| `motion.durationIsland` | 320 | An island opening |
| `motion.durationPulse` | 720 | One breath of a busy badge |
| `motion.durationSyncMark` | 2400 | How long a finished sync's mark stays |
| `motion.durationScene` | 360 | Wide art crossfading |
| `motion.durationShowcase` | 1400 | Wide art settling |
| `motion.durationSelect` | 280 | A highlight landing, a switch flipping |
| `motion.durationBob` | 1800 | One rise and fall of a held tile |
| `motion.durationLetterHold` | 360 | How long the letter-jump letter stays |
| `motion.durationLetterFade` | 120 | Its fade |
| `motion.scaleRest` | 1 | A resting tile |
| `motion.scaleFocus` | 1.05 | A focused tile, key or result |
| `motion.heroScaleFrom` | 0.96 | Incoming hero art's starting scale |
| `motion.heroSlideUpPx` | 24 | How far hero art rises after a jump |
| `motion.heroTravelPx` | 64 | How far hero art travels with the cursor |
| `motion.sceneDepth` | 0.35 | Wide art's share of that travel |
| `motion.sceneOverscan` | 1.05 | How much larger wide art is drawn |
| `motion.sceneSettleFrom` | 1.1 | Wide art's starting scale |
| `motion.tiltPx` | 18 | How far the top screen's backdrop slides when the Thor tips, turns, or rolls |
| `motion.tiltDegrees` | 3 | The lean that takes nearly all of it |
| `motion.tiltArtShare` | 0.4 | Hero art's share of that slide |
| `motion.durationTiltRecenter` | 2400 | How long a held lean takes to become rest |
| `motion.scaleLift` | 1.1 | A held tile |
| `motion.scaleOver` | 0.74 | A held game over a group it can go into |
| `motion.scaleOpen` | 1.14 | That group, opening |
| `motion.bobPx` | 7 | How far a held tile bobs |
| `motion.liftGlowPx` | 30 | How far below a held tile its light falls |
| `motion.liftGlowPeak` | 0.7 | That light's strength |
| `motion.squash` | 0.08 | A tile's squash as it lands |
| `motion.keySquash` | 0.07 | A key's squash as it acts |
| `motion.riseFrom` | 0.96 | A page or card's starting scale as it rises in |
| `motion.riseDp` | 24 | How far it rises |
| `motion.sinkScale` | 0.06 | How much content shrinks as it gives way |
| `motion.sinkDp` | 16 | How far it sinks |
| `motion.cascadeDp` | 14 | How far each menu row rises as a menu opens |
| `motion.cascadeStep` | 0.05 | The delay between rows, share of the opening |
| `motion.cascadeSpan` | 0.35 | The most any row waits |
| `motion.popFrom` | 0.72 | A Find result's starting scale |
| `motion.shakePx` | 8 | How far a refused or cancelled row shakes each way |
| `motion.rewindFrom` | 0.94 | The board's starting scale as an undo or a tidy puts it back |
| `motion.folderPopFrom` | 0.92 | A folder's grid's starting scale as it opens |
| `motion.arrive` | [0.05, 0.7, 0.1, 1] | Things arriving |
| `motion.leave` | [0.3, 0, 0.8, 0.15] | Things leaving |
| `motion.pulse` | [0.4, 0, 0.6, 1] | Breathing |
| `motion.dissolve` | [0.2, 0, 0, 1] | Art crossing over art |
| `motion.playful` | [0.34, 1.36, 0.64, 1] | Landings that overshoot a little |

### `metrics`

Layout distances. Grid columns and rows are the host's.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `metrics.insetFraction` | 0.04 | Panel inset, share of the width (0–0.5) |
| `metrics.gapFraction` | 0.04 | Gap between tiles, share of the width |
| `metrics.focusStrokePx` | 3 | Room kept for a focused tile's stroke |
| `metrics.heroInsetPx` | 48 | Inset of the top screen's content |
| `metrics.dialogInsetPx` | 48 | Dialog inset and padding |
| `metrics.dialogBorderPx` | 2 | Dialog border width |
| `metrics.cardCornerDp` | 28 | Cards: islands, dialogs, Settings, the hero's name card, the typing field |
| `metrics.heroArtFraction` | 0.62 | Reach of the hero's glow and logo shade, share of the art |
| `metrics.chromeClearancePx` | 28 | Space between the hint row and a focus stroke |
| `metrics.glyphScale` | 1 | Controller glyphs beside rows, hints and chips, as a share of the size each place draws them |

### `colors`

Colours beyond the manifest's five, as `#RRGGBB`. Those whose Afterglow value is `null` are derived from the pack's other colours unless it sets them; a pack that inherits one it does not want writes `null` to derive it again.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `colors.scrim` | #000000 | What dims the screen under menus and dialogs, and shades art behind text |
| `colors.shade` | #000000 | The dark end of art's shading: a pressed glyph, a swatch's base |
| `colors.shine` | #FFFFFF | The light end: a pressed glyph's lip, a swatch's top and chosen rim |
| `colors.highlight` | null | The gliding highlights of menus, keys and a text selection. `null`: the focus ring's colours |
| `colors.on` | null | A switch, a check or a quick tile that is on. `null`: the focus ring's colours |
| `colors.progress` | null | What fills as something progresses: a download, a slider, the play-time bars. `null`: `focus` |

### `shape`

Corners. `round` is a share of a small control's shorter side: 0.5 makes pills and circles, 0 makes them square.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `shape.round` | 0.5 | Switches, sliders, chips, pills, dots, badges, the caret, the closed islands |
| `shape.highlightCornerDp` | 22 | The highlight gliding between menu and Settings rows, save cards, a delete hold |
| `shape.rowCornerPx` | 22 | A row highlight in the command center's lists |
| `shape.chipCornerPx` | 44 | The largest corner a text chip takes |
| `shape.keyCorner` | 0.2 | A keyboard key, share of its width (0–0.5) |
| `shape.badgeCornerPx` | 8 | An achievement badge |
| `shape.toastCornerDp` | 8 | The art-match toast |
| `shape.thumbCornerDp` | 12 | An art candidate in the art picker |

### `tile`

A home grid tile, beyond its corner (`iconRadius`) and glow.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `tile.restAlpha` | 0.82 | How bright art rests when not focused |
| `tile.socketAlpha` | 0.16 | The outline left where a held tile was |
| `tile.outlinePx` | 3 | That outline, and tiles while Edit home is open |
| `tile.editStrokePx` | 9 | The tile Edit home is on, and a carried gap |
| `tile.editRestAlpha` | 0.45 | Edit home's outline on other tiles |
| `tile.dotPx` | 10 | The favourite and on-Home dots |
| `tile.statusChipPx` | 36 | The status chip's height |
| `tile.statusIconPx` | 24 | Its badge |
| `tile.statusBackingAlpha` | 0.72 | Its backing over cover art |
| `tile.downloadBarPx` | 6 | The download bar's thickness |
| `tile.downloadTrackAlpha` | 0.22 | Its empty track |
| `tile.stripPx` | 6 | The bar under a wide grid that shows where the screen is |
| `tile.stripAlpha` | 0.45 | Its track, over `muted` |
| `tile.homeTargetStrokePx` | 3 | The Home pill's outline above a group's grid while a game is held |
| `tile.homeTargetAlpha` | 0.35 | That outline's strength |
| `tile.carriedRingAlpha` | 0.4 | The faintest a carried tile's ring gets before it is raised |

### `hero`

The top screen's selected item.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `hero.markScale` | 0.86 | A drawn mark's share of the hero box (legacy PNG marks use `artScale`) |
| `hero.cardAlpha` | 0.72 | The name card over game art |
| `hero.pillAlpha` | 0.94 | The selection count and letter-jump card |
| `hero.logoShadeAlpha` | 0.42 | The round shade behind a game's logo |
| `hero.logoShadowAlpha` | 0.85 | The soft shadow a logo casts in its own shape, in the scrim colour |
| `hero.logoShadowBlurDp` | 10 | How soft that shadow is; 0 is a hard copy |
| `hero.logoGlowBelow` | 0.3 | A logo darker than this (0 black, 1 white) casts that shadow in `colors.shine` instead, as a glow |
| `hero.sceneShadeTop` | 0.45 | Wide art's shade at the top |
| `hero.sceneShadeBottom` | 0.8 | And at the bottom |
| `hero.ambientBlurDp` | 48 | Blur of the box-art backdrop |
| `hero.ambientDim` | 0.55 | Its dim |

### `menu`

The L1, R1 and Y menus and their islands.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `menu.highlightStartAlpha` | 0.2 | The row highlight's fill where the ring starts |
| `menu.highlightEndAlpha` | 0.08 | And where it ends |
| `menu.labelRestAlpha` | 0.72 | Unfocused row labels and slider labels |
| `menu.artRestAlpha` | 0.78 | Unfocused row art |
| `menu.artGrow` | 0.12 | How much a focused row's art grows |
| `menu.scrimAlpha` | 0.32 | The dim behind an open menu, over the blur |
| `menu.scrimAlphaUnblurred` | 0.55 | The dim on Android 11 and older, which has no blur |
| `menu.blurPx` | 28 | The blur behind an open menu |
| `menu.islandLift` | 0.04 | How lit an open island's top is |
| `menu.islandBorderDp` | 1 | An island's rim |
| `menu.islandFocusBorderDp` | 2 | A focused closed island's rim |
| `menu.islandRimAlpha` | 0.08 | The rim at rest |
| `menu.islandRimLitAlpha` | 0.34 | How much it lights as the island opens |
| `menu.quickTileAlpha` | 0.08 | R1's round tiles at rest |
| `menu.quickTileLitAlpha` | 0.16 | And focused |
| `menu.quickTileOnAlpha` | 0.6 | The focus colours filling a tile whose radio (Wi-Fi, Bluetooth) is on |
| `menu.quickArtGrow` | 0.14 | How much their art grows |
| `menu.quickLabelRestAlpha` | 0.6 | A quick tile's word while it is not focused |
| `menu.noticeRestAlpha` | 0.86 | A notice's title in R1 while it is not focused |
| `menu.holdAlpha` | 0.3 | The fill that grows across a row while a delete is held |

### `controls`

Switches, checkmarks and sliders. The checkmark's tick is the `control.check` icon.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `controls.switchWidthDp` | 54 | Switch width |
| `controls.switchHeightDp` | 30 | Switch height |
| `controls.switchKnobInsetDp` | 4 | Space around the knob |
| `controls.trackAlpha` | 0.12 | An off track |
| `controls.trackFocusAlpha` | 0.2 | An off track, focused |
| `controls.checkSizeDp` | 30 | Checkmark size |
| `controls.checkStrokeDp` | 2 | Its ring |
| `controls.checkRingAlpha` | 0.26 | The ring at rest |
| `controls.checkRingFocusAlpha` | 0.42 | Focused |
| `controls.sliderTrackDp` | 6 | Slider track thickness |
| `controls.sliderTrackAlpha` | 0.28 | Its empty part |
| `controls.sliderKnobDp` | 7 | Knob radius |
| `controls.sliderGlow` | 2.2 | The knob's glow, as a multiple of its radius |
| `controls.sliderGlowAlpha` | 0.9 | That glow's strength at the knob |
| `controls.sliderGlowMidAlpha` | 0.34 | Its strength at `sliderGlowMid` |
| `controls.sliderGlowMid` | 0.42 | Where that middle strength sits, share of the glow (0–1, excluding both) |

### `keyboard`

The on-screen keyboard and its name chips. Shift, Erase and Space are `control.` icons.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `keyboard.highlightStartAlpha` | 0.26 | The key highlight's fill at the top |
| `keyboard.highlightEndAlpha` | 0.1 | At the bottom |
| `keyboard.inactiveHighlight` | 0.3 | How much highlight stays while the D-pad is elsewhere |
| `keyboard.keyAlpha` | 0.92 | A key's glass |
| `keyboard.glassAlpha` | 0.12 | How lit a letter key is |
| `keyboard.modifierGlassAlpha` | 0.05 | How lit Shift, Erase, Space and Done are |
| `keyboard.litGlassAlpha` | 0.06 | How much more a focused key lights |
| `keyboard.doneAlpha` | 0.55 | Done's colour |
| `keyboard.shiftAlpha` | 0.38 | Shift's colour while on |
| `keyboard.pressAlpha` | 0.22 | The flash as a key acts |
| `keyboard.sheenAlpha` | 0.1 | The sheen across a key's top |
| `keyboard.rimTopAlpha` | 0.22 | A key's rim at the top |
| `keyboard.rimBottomAlpha` | 0.04 | At the bottom |
| `keyboard.rimDp` | 1 | Rim width, keys and chips |
| `keyboard.glyphRestAlpha` | 0.62 | A button glyph on a key until it is held |
| `keyboard.chipAlpha` | 0.08 | A name chip |
| `keyboard.chipFlashAlpha` | 0.2 | Its flash as it acts |
| `keyboard.chipRimAlpha` | 0.12 | Its rim |
| `keyboard.chipLabelRestAlpha` | 0.82 | Its word while unfocused |
| `keyboard.lampHaloAlpha` | 0.3 | The halo of the lamp under Shift while it is on |

### `field`

The text field Find, rename and connect type into.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `field.ringAlpha` | 0.55 | The ring always on the field |
| `field.selectionStartAlpha` | 0.55 | A selection's start |
| `field.selectionEndAlpha` | 0.4 | Its end |
| `field.selectionLowAlpha` | 0.7 | A selection's faintest as it breathes, share of its strength |
| `field.selectionCornerDp` | 10 | A selection's corner |
| `field.caretDp` | 4 | Caret width |
| `field.caretGlowDp` | 6 | Its glow |
| `field.caretGlowAlpha` | 0.22 | The glow's strength |
| `field.caretLowAlpha` | 0.35 | The caret's faintest as it breathes |

### `dialog`

Dialogs, save cards, and the chips their buttons sit in.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `dialog.scrimAlpha` | 0.72 | The dim behind a dialog |
| `dialog.conflictScrimAlpha` | 0.82 | The dim behind the save-conflict cards |
| `dialog.borderAlpha` | 0.08 | A dialog's border |
| `dialog.chipAlpha` | 0.06 | A chip at rest |
| `dialog.chipFocusAlpha` | 0.1 | A focused chip |
| `dialog.chipStrongAlpha` | 0.16 | A focused save-card action |
| `dialog.rowAlpha` | 0.06 | A command-center row highlight |
| `dialog.toastAlpha` | 0.85 | The art-match toast |

### `settings`

The Settings page.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `settings.labelRestAlpha` | 0.62 | Unchosen category labels |
| `settings.artRestAlpha` | 0.7 | Unchosen category art |
| `settings.artGrow` | 0.14 | How much the chosen category's art grows |
| `settings.cardLift` | 0.035 | How lit the detail card's top is |
| `settings.borderAlpha` | 0.06 | The detail card's border |
| `settings.borderDp` | 1 | Its width |

### `find`

Find. The magnifier is `control.search` and the hidden mark is `badge.hidden`.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `find.ringRestAlpha` | 0.38 | The ring on the best result before the D-pad reaches it |
| `find.filterAlpha` | 0.07 | A filter pill |
| `find.chosenAlpha` | 0.3 | The chosen filter's fill |
| `find.chosenRimDp` | 1.5 | Its rim |
| `find.hiddenBackingAlpha` | 0.78 | The disc behind `badge.hidden` |

### `picker`

The art picker.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `picker.shimmerAlpha` | 0.18 | The shimmer across a candidate still loading, in `focus` over `surface` |

### `play`

Play activity: the week, the days and their sessions. The bars are `colors.progress`.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `play.backingAlpha` | 0.06 | A game's backing while its art loads, or when it has none |
| `play.trackAlpha` | 0.06 | A day's track |
| `play.hourAlpha` | 0.1 | The hour lines across it |
| `play.hourDp` | 1 | Their width |
| `play.emptyBarAlpha` | 0.12 | A day of the week with no play |
| `play.barCornerDp` | 3 | A day's bar's corner |

### `collections`

A collection's folder. Colours are keyed by the ids collections save, so a pack recolours them without renaming any. The glyph's place is in the 64-unit grid of `mark.collection-body`, so a pack that redraws the folder moves the glyph with it.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `collections.gold` | #FFC15A | The default colour |
| `collections.coral` | #FF7F5C | A collection colour |
| `collections.rose` | #FF5C8A | A collection colour |
| `collections.violet` | #B48CFF | A collection colour |
| `collections.indigo` | #7C8CFF | A collection colour |
| `collections.sky` | #4CC9FF | A collection colour |
| `collections.teal` | #3EE6C0 | Recently played |
| `collections.mint` | #8CF2A6 | A collection colour |
| `collections.lime` | #C6EE5A | A collection colour |
| `collections.slate` | #A3B1C6 | A collection colour |
| `collections.glyphX` | 32 | The glyph's centre across |
| `collections.glyphY` | 39.48 | Its centre down |
| `collections.glyphSize` | 29.3 | Its size |
| `collections.pressedDepth` | 0.62 | How far toward `colors.shade` the pressed glyph is |
| `collections.lipDrop` | 0.025 | How far below it its light lip sits, share of the glyph |
| `collections.lipAlpha` | 0.32 | The lip's strength |
| `collections.swatchShine` | 0.35 | How far an editor swatch's top lightens toward `colors.shine` |
| `collections.swatchShade` | 0.3 | How far its base darkens toward `colors.shade` |
| `collections.swatchRimAlpha` | 0.85 | The chosen swatch's rim |
| `collections.swatchRimPx` | 3 | Its width |
| `collections.swatchRimInsetPx` | 2.5 | How far inside the swatch's edge it sits |

### `sound`

UI sounds.

| Token | Afterglow | Controls |
| --- | --- | --- |
| `sound.volume` | 0.4 | Playback volume of `sounds/*.ogg`, 0–1 |

## Scene version 1

```json
{
  "version": 1,
  "id": "stars",
  "name": "Stars",
  "frameRate": 30,
  "renderScale": 1,
  "variables": {
    "pulse": {"keyframes": [[0, 0.2], [2, 1], [4, 0.2]], "duration": 4, "easing": "smooth"}
  },
  "layers": [{
    "name": "sky",
    "type": "shader",
    "file": "backgrounds/stars.agsl",
    "uniforms": {"size": ["width", "height"], "phase": "fract(time / 20)", "gain": "pulse"}
  }]
}
```

The shader's uniforms must agree with the names and component counts bound by the scene. Shader source is compiled when the scene is prepared, not during each frame. Shader brushes, textures, expression trees, and path sampling buffers are reused. `uniforms` accepts a scalar or an array, including arrays of vectors. Optional `inputs` maps shader-input names to image assets; these use pixel coordinates and clamp at their edges.

A scene can build on another scene in the same pack with `"extends": "backgrounds/base.json"`. Its own top-level keys replace the base's. Its `variables` replace same-named base variables in their original position, so later base variables see the override, and new names are appended. `layers` is inherited when omitted. When given, each entry is either a layer object or the name of a base layer to reuse unchanged. Bases may extend other bases, up to eight deep. A base scene does not need to be listed in `scenes`. Afterglow's Static extends Ribbons, slows its `pace`, turns off its `live` effects, dims its `breath`, and keeps only the shader layer.

Layers are drawn in order. Each supports a name, a positive integer `repeat` (default 1), and `blend`: `srcOver`, `lighten`, `plus`, `screen`, or `multiply`.

- **shader:** `file`, `uniforms`, optional `inputs`.
- **disc:** `x`, `y`, `radius`, RGBA `color` (0–1). `tightness` and `rim` control a smooth radial glow. Optional `path` contains `x`, `y`, and `samples` (default 48); `along` moves along its sampled arc length. Radius is the complete outer reach in render pixels.
- **line:** `x`, `y`, `endX`, `endY`, stroke `width`, and RGBA `color`.
- **image:** `file`, `x`, `y`, `width`, `height`, optional `alpha` (default 1) and `rotation` in degrees.

Numeric properties can be numbers, arithmetic-expression strings, or keyframe tracks. Expressions evaluate in double precision, so `time` stays exact through months of uptime. Values reach shaders as 32-bit floats, which lose a frame's worth of precision after about a day. Pass shaders a wrapped phase such as `fract(time / 20)` or `2*pi*fract(time / 20)`, not raw `time`. Available inputs are `time` in design seconds, `width`/`height` of the render target, `panel` (top 0, bottom 1), `seed`, `moving` (0/1), `clock`, repeated-layer `index`, and sampled-path `u` (0–1). The shell also tells a scene about the launcher: `accentR`, `accentG` and `accentB` are the focused item's accent (its app's or mark's light, a collection's colour, else `focus`), 0–1, eased as focus moves; `hour` is the local time of day, 0 to 24 with minutes as the fraction; `battery` is 0–1 and `charging` 0 or 1; `sinceMove` is real seconds since the cursor last moved, up to 3600. They are read each time the scene draws. A scene whose `frameRate` is 0 is asked again when the accent, the battery or a cursor move changes, and once a minute, so `if(charging,30,0)` starts when power is connected. A scene that never reads the accent or `sinceMove` is not redrawn for them. Every input added after scene version 1, `seed` included, is shadowed by a scene variable of the same name, so a scene written before an input existed keeps its own value. `seed` is a random 0–1 value each panel chooses when its scene starts and keeps until the scene changes. Two panels start a scene together, so without it both screens show the same moment. Afterglow adds `3600*seed` to its time. A scene that declares its own `seed` variable keeps it instead. `pi` is constant. Variables are evaluated in declaration order and can refer to preceding variables. Repeat-local variables are reevaluated for each instance.

Path samples reevaluate variables that depend directly or indirectly on `u`, including keyframe tracks, then restore the instance's values. Runtime errors in scene-wide variables or `frameRate` stop that scene and emit one diagnostic; layer errors disable only the failed layer.

Expressions support `+ - * / %`, comparisons, parentheses, and `sin`, `cos`, `abs`, `floor`, `ceil`, `sqrt`, `fract`, `smooth`, `min`, `max`, `pow`, `clamp`, `mix`, `if`, `hash`, and `window`. `if(condition, yes, no)` evaluates only the selected branch. `hash(integer, salt)` supplies deterministic 0–1 values. `window(time, start, duration, fade)` supplies a smooth fade at both edges. No expression can call app APIs.

Keyframes are increasing `[time, value]` pairs. They loop by default; `loop: false` holds the end value. `duration` defaults to the last keyframe. `time` can be an expression, for example `time + index * 0.2`. Easing is `linear` or `smooth`. Tracks use binary search to find their active segment.

`frameRate` may be an expression to request different cadences during a scene; zero stops the frame clock. Actual updates are synchronized to display frames. `renderScale` chooses the resolution of an offscreen surface when different from 1. Coordinates and fixed pixel sizes use that render target's pixels; the surface is scaled to fill the panel. Authors choose how their artwork responds to reduced resolution. `maxClock` optionally caps the scene's design-time rate. `frozenTime` defaults to zero. `animateWhenMotionOff` opts into slow movement even when the user-selected motion speed is Off; Afterglow's Static uses this to preserve its subtle drift. Android's remove-animations setting still stops the clock.

The host pauses hidden/inactive scenes and respects their panel lifecycle. Each panel has independent rendering state. Identical time and seed inputs give deterministic frames, which the native preview can freeze for comparison.

## Package and preview

```sh
./gradlew :app:packageAfterglowTheme
python3 tools/themes/pack.py /path/to/my-theme --output /tmp/my-theme.zip
```

App builds and language tests automatically generate Afterglow from `themes/afterglow/` into `app/build/generated/themeAssets/themes/afterglow.zip`. Both debug and release APKs include it as `assets/themes/afterglow.zip`. Keep the theme sources in Git; the generated archive stays in the ignored build directory.

The tool validates asset references and produces reproducible ZIPs using only Python's standard library. It also checks the manifest against [the contract](#compatibility): unknown or misspelt tokens and slots, values out of range, renamed and retired names, unknown font faces, and manifest fields this Foldcade does not read. It finds `themes/contract.json` beside itself in a Foldcade checkout; outside one, copy the contract next to `theme.json` or pass `--contract`. `--strict` fails on anything the engine would report. JSON/asset checks happen before packaging; the native loader validates expressions and the target device compiles shaders.

[`samples/themes`](../samples/themes) has two packs written the way a third-party pack is: Daylight, a light pack with its own colours, scales, an icon and a scene that reads `hour` and the accent, and Daylight Square, which extends it and squares every corner. Every build loads both and fails if Foldcade reports anything about them.

To try a pack on a release build, copy the zip to the device and add it from **Settings › Look › Themes › Add a theme**, then choose **Restart to see changes**. The page shows each pack's report: every diagnostic below, with its source. Archive reading retains the existing 8 MiB per-entry transport limit (4096 entries); these are separate from scene rendering choices.

Build a debug APK. `ThemePreviewActivity` is available only in debug builds and uses the production loader and renderer. With the debug package installed:

```sh
adb push /tmp/my-theme.zip /data/local/tmp/my-theme.zip
adb shell run-as app.foldcade.debug mkdir -p files/preview
adb shell run-as app.foldcade.debug cp /data/local/tmp/my-theme.zip files/preview/my-theme.zip
adb shell rm /data/local/tmp/my-theme.zip
adb shell am start -n app.foldcade.debug/app.foldcade.ThemePreviewActivity --es pack preview/my-theme.zip --es scene stars
```

Optional extras:

- `icons` and `backgrounds`: another ZIP path relative to private `files/`, to preview category composition.
- `time`: string design time, such as `--es time 3.5`, for deterministic snapshots.
- `chrome`: `--ez chrome false` for an unobstructed scene.
- `validateIcons`: `--ez validateIcons true` decodes every base-pack icon and writes `files/theme-preview-icons.txt`; Afterglow vectors also compare against the original Compose resource painters.
- `seed`: string seed in 0–1, such as `--es seed 0.4`. It defaults to 0 when `time` is given and to a random value otherwise.
- `panel`: `--ei panel 1` for bottom-panel scene inputs; use `am start --display <id>` to preview on that display.

The overlay switches scenes, reduced motion, and pause. It reports draw cadence, CPU submission time, layer count, and decoded texture memory. CPU submission is not GPU render time. Collect Android frame timing or profile on the target device for GPU cost:

```sh
adb logcat -s FoldcadeTheme FoldcadeThemePreview
adb shell dumpsys gfxinfo app.foldcade.debug reset
adb shell dumpsys gfxinfo app.foldcade.debug framestats
```

Errors include the asset path, layer name, expression column, or the shader compiler's source location. Authors are responsible for their effect complexity and quality presets; the engine provides control and measurements without silently changing their design.
