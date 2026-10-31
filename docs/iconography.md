# Iconography

Every icon in Foldcade belongs to one family, drawn in two finishes:

- **Symbols** are flat shapes in one warm-white ink. They are used wherever the icon sits in the chrome: controller hints, the status islands, tile badges, menu rows, and the collection picker.
- **Objects** are the same shapes rendered as lit things. They are used on the home grid and in the top screen's hero, where they sit beside detailed cover art. Common Android apps and the emulators Foldcade launches get objects too, so their tiles match the platforms beside them.

A flat icon beside a 3D cover reads as a sticker, and a glossy icon in a menu row reads as noise. One drawing serves both: the art pipeline renders each place and platform once as an object and once as a symbol.

The source is Python in `tools/art`. It writes Android vector drawables to `themes/afterglow/icons/`, SVG copies to `art/svg/`, and the icon slots in `themes/afterglow/theme.json`. Never edit the XML by hand. Change the drawing and regenerate.

```sh
python3 tools/art/icons.py                          # write every icon and the manifest's icon slots
python3 tools/art/sheet.py /tmp/afterglow-icons.html  # a contact sheet of the whole set
python3 tools/art/preview.py /tmp/apps.png chrome dolphin game_boy  # grid tiles as a PNG, on macOS
```

The scripts use only the Python standard library. Open the sheet in any browser. The preview renders through macOS Quick Look, so you can check a drawing without one; elsewhere it writes the SVG beside the PNG.

## Where each icon is used

| Slot | File | Finish | Used for |
| --- | --- | --- | --- |
| `button.*` | `ic_btn_*.xml` | Symbol, 24 units | Controller hints. `.filled` is the held state. |
| `status.*` | `ic_status_*.xml` | Symbol, 24 units | The L1 and R1 islands, and Settings categories. |
| `badge.*` | `ic_badge_*.xml` | Symbol, 24 units, tinted | The chip in a tile's corner, and Find's hidden mark. |
| `control.*` | `ic_control_*.xml` | Symbol, 24 units, tinted | The host's own controls: a checkmark's tick, the keyboard's Shift, Erase and Space, Find's magnifier. |
| `symbol.*` | `symbol_*.xml` | Symbol, 64 units, tinted | Menu rows, the collection picker, and the glyph pressed into a collection. |
| `symbol.<verb>` | `symbol_<verb>.xml` | Symbol, 64 units, tinted | What Y's menu does to a tile, on the round tiles at its top and the rows under them, and Edit home's Sort and Tidy for the whole board. See [Verbs](#verbs). |
| `mark.*` | `mark_*.xml` | Object, 64 units | Grid tiles and the hero. Its `accent` is the light behind the tile. |
| `mark.collection-body`, `mark.collection-face` | `mark_collection_*.xml` | Object parts | A collection's folder. See [Collections](#collections). |
| `app.<package>` | `app_*.xml` | Object, 64 units | An installed app's tile, its hero, and its notices, in place of its launcher icon. See [Apps](#apps). |

A place or platform has both a `mark.` and a `symbol.` slot with the same name, for example `mark.game-boy` and `symbol.game-boy`. A platform's name is its canonical platform id. A collection glyph has only a `symbol.` slot, because on the grid the folder is the object.

Resource themes can override any slot (see [themes](themes.md)). A symbol is marked `"tint": true`, so the host may draw it in any colour.

## The rules

1. **One shape per idea.** Use the shape people already know. A house is Home, a shelf of game cases is the library, a cloud is the server, and a folder is a folder.
2. **Platforms are the thing you hold, seen from the front.** Draw the controller or the handheld, not a logo. When two platforms' controllers look alike, draw what differs: the Master System's d-pad is square, and Sega CD is the Genesis pad with a disc above it. When the difference is too small to read on a tile, draw the console instead. The three PlayStation controllers are nearly the same, so the PlayStations are their consoles: the first a grey box with a round lid, the PlayStation 2 lying down in two tiers, the PlayStation 3 a slab with a curved top. Saturn is the planet.
3. **Shape says what it is, colour says which one.** A device is a platform, a folder is a folder or a collection, and a house, a shelf, or a cloud is a place.
4. **Symbols are one ink.** No gradients, gloss, rims, or glows in the art. The host draws focus.
5. **Colour in a symbol means state.** Teal (`#3EE6C0`) means on or held. Rose (`#FF5C7A`) means it needs you. Warm amber (`style.colors.attention`, Afterglow `#FFC48A`) marks saves that need protection. Ink at 30% means off. Nothing else is coloured.
6. **Details are cut out of the shape, not drawn on it.** A screen, a button, or a window is a hole in the silhouette. Anything inside a hole is solid again.
7. **A button's cap is its only container.** Thor Start is a ▷ on the round cap, with no ring around it, the same as Select's square.
8. **Objects share one light and one material.** See [Objects](#objects). Every object follows the same recipe, so the grid looks like one set beside any cover art.

## Symbols

### The 24-unit grid: controller, status, and badges

Drawn in `tools/art/symbols.py` on a 24 × 24 viewport and shipped at 24dp.

- **Ink.** `INK` `#F4F1EA` for everything at rest. Strokes are 2 units with round ends and joins (`STROKE`). Fills are solid.
- **Caps.** A button is a dark cap (`CAP` `#30333B`) with its legend in ink. Held is a teal cap with a dark legend (`ON_TEAL`). Use `caps(pressed)`, which returns both colours. Round caps have radius 10.6 (`CAP_R`), and nothing else surrounds the legend.
- **Legends and counts** use the built-in monoline alphabet: `legend("L1", cx, cy, height, color, stroke)`. It has A B X Y L R, the digits, and +. Add a letter to `CHARS` the same way: 10 units tall, drawn with round-ended strokes.
- **State.** Off is ink at `OFF_ALPHA` (0.3). Disabled things are crossed out with `slashed()`, which cuts a black gap on either side of the line. Status icons sit on black islands, so a black gap reads as a cut.
- **Badges** are drawn in ink and marked `tint=True`. The host recolours them, so they must stay one colour.

`badge.unsynced` is a cloud with an exclamation cut out. It marks saves that are not syncing or are waiting to upload, in the attention colour.

### The 64-unit grid: places, platforms, and glyphs

Symbols for marks and glyphs come from the same parts as the objects (next section). In the symbol, every part's outline is filled in one ink with the even-odd rule:

- A recess or a control on a body becomes a hole.
- A part inside a hole is solid again. A screen in its bezel, or a stick in its well, shows as a solid shape inside a cut ring.
- Parts on different symbol layers (`sym=1`, `sym=2`) stack instead of cutting each other. Use a separate layer when two parts overlap but shouldn't cut, for example a note's stem and its head.
- Overlapping parts on the same layer cut each other where they overlap. Either keep parts on one layer apart, or move one to another layer.

Keep every hole and every gap at least 2.5 units wide. Menu rows show symbols at 32dp, where one unit is half a dp. Details smaller than that should be object-only (`sym=None`).

## Verbs

Drawn in `tools/art/verbs.py`, in the same style as the collection glyphs: one bold silhouette with its details cut out. A tile's menu leads with its own verbs (Move, Add to, Art, Saves, Rename, Icon, New collection) as round tiles, and every other action is a row with its symbol at 32dp. A verb is chrome, never a thing on the grid, so it has only a `symbol.` slot.

- **Draw the act, not the thing.** Move is four arrows out from the middle, Saves a floppy disk, Restart a ring that turns back, Undo an arrow that turns back on itself, Hide a shut eye.
- **Two verbs that are close share a shape and differ in one detail.** Delete is a bin and Remove download the same bin with a down arrow cut where its ribs would be. Show is an open eye and Hide a shut one.
- **No slashes.** A crossed-out symbol means off (rule 5's 30% ink), not an action.

A verb that reuses a place's symbol keeps it: Show on Home and New games on Home wear `symbol.home`, Move to Games or Apps wears the folder it goes to, and Recently played wears `symbol.clock`.

## Objects

Drawn in `tools/art/objects.py`. A mark is a list of parts on the 64-unit grid. `object_layers(parts)` renders the object and `symbol_layers(parts)` renders the symbol.

### The recipe

| Part | Object | Symbol |
| --- | --- | --- |
| `body(d, color)` | Soft-touch plastic in the mark's colour. Lighter at the top, darker at the base, a sheen on the upper half, and a thin light rim. | Filled |
| `recess(d, base)` | Cut into the body: near-black (`WELL`) at the top lip, with light catching its lower edge. | A hole |
| `screen(d, color)` | Lit glass in `GLASS` (`#BFE6FF`), or the hardware's own screen colour, with a diagonal sheen. | Left out unless `sym=0` |
| `control(d, color)` | A raised button, d-pad, or stick in `RUBBER` (`#3A3E48`), or a tone of the body. It casts a small drop below it. | A hole on a body, or solid in a recess |
| `shadow(cx, cy, rx, ry)` | A soft contact shadow under the object. | Left out |
| `paint(layers…)` | Raw layers for the object only, such as the disc's shine. | Left out |
| `outline(d)`, `line(d, w)` | Nothing | A fill or a stroke for the symbol only |

There is one light, and it comes from above. Gradients run top to bottom across each part, so a rotated part is still lit from above. Only gradients, clips, and alpha are used, because Android vector drawables have no blur or filters.

Helpers for common pieces live in `objects.py` and `marks.py`:

- `plus(cx, cy, arm, w)`: a d-pad
- `diamond(cx, cy, off, r)`: four face buttons
- `capsule(x1, y1, x2, y2, w)`: a bar with round ends
- `arc_band(...)`: a curved button
- `stick(cx, cy, r, base)`: a well and its rubber cap
- `bezel(x, y, w, h, r, base)`: a screen set in a dark bezel
- `hinge(y)`: a clamshell's hinge

`tone(c, t)` lightens a colour toward white (t > 0) or darkens it toward black (t < 0). Use it for shades of the body instead of new colours.

### Size and colour

- Draw at full size on the 64-unit grid, roughly 3 to 61 across, with the shadow below. `OBJECT_SCALE` (0.88) shrinks every object into the tile's live area, leaving room for the light behind it.
- The body is the mark's accent. The accent should be bright enough to read on black: no darker than the existing set, and no paler than `SILVER` `#C3CEDF`.
- Controls are rubber or a tone of the body. Keep hardware colours only where they identify the thing: the Switch's two Joy-Con colours, the GameCube's green A, and the Game Boy's pea-green screen.

### On the grid

The home grid draws every tile the same way:

- **One corner.** Every tile is a rounded square at the theme's `iconRadius` (0.28).
- **One border.** The focus ring is the only border drawn on the grid. It sits just outside the tile and follows the tile's corner. Nothing draws an edge inside a tile.
- **Light behind marks.** A mark's tile is the surface with a soft light in the mark's accent near its top. It replaces the old coloured edge.
- **Apps the theme draws are objects.** An app with an `app.<package>` slot sits on the light in its accent, like a mark.
- **Other apps fill their tiles.** An adaptive Android icon is drawn from its background and foreground layers, clipped to the tile's corner, like a cover. An icon that only ships a fixed shape stays on the surface.
- **One resting brightness.** Unfocused covers, apps, and marks all rest at 82% (`style.tile.restAlpha`).

## Collections

A collection is a folder in one of ten colours with a glyph pressed into its face. One set of files serves every colour. `Marks.kt` (`CollectionPainter`) draws it in three passes:

1. `mark_collection_body`: the folder in greys, multiplied by the tint. White becomes the tint itself and grey becomes a darker shade.
2. The glyph's `symbol.*`: a faint white lip just below it, then the glyph itself in a deep shade of the tint (62% toward black, `style.collections.pressedDepth`). Together they look pressed into the face.
3. `mark_collection_face`: the parts that keep their own colour, drawn last: the sheet inside, gloss, and rims.

Where the glyph sits is `FACE_GLYPH` in `marks.py`. `icons.py` prints the finished position after `OBJECT_SCALE`. Afterglow's `theme.json` keeps the same numbers in `style.collections.glyphX`, `glyphY`, and `glyphSize`; change both together. The ten colours are `style.collections` too, so another pack can redraw the folder, move the glyph, and recolour it without touching the app.

A collection can wear any glyph, any place, or any platform (`FOLDER_ICONS`). That is why every place and platform needs a symbol.

A theme pack from before symbols may draw a glyph only as `mark.<glyph>`. If no pack ahead of Afterglow draws `symbol.<glyph>`, that mark is drawn on the folder as it is (`collectionGlyphSlot`). A legacy black-backed `marks/<name>.png` is screened onto the folder.

## Apps

Afterglow draws an object for the apps people open on a handheld: the Thor's own apps (Chrome, Files, Settings, the Play Store, and the rest), common Android apps, and every emulator and front-end Foldcade launches. They live in three modules, one per group, gathered by `tools/art/apps.py`:

| Module | Draws |
| --- | --- |
| `apps_android.py` | Android's own apps and Google's |
| `apps_popular.py` | Popular third-party apps: browsers, media, chat, stores, and tools |
| `apps_emulators.py` | Emulators and game front-ends |

An app's object follows the same recipe as a platform's, with two more rules:

1. **Draw what the app does, or the one thing its icon is known by.** A browser can be the ring of colours people know Chrome by, and RetroArch is its invader. Keep the cue, change the drawing: echo the colour and the motif, never trace the logo, and never draw words. When the icon is one bold character and nothing else, as aPS3e's is a 3, build that character as a raised object in the icon's colours.
2. **An emulator is not its platform.** DuckStation is a duck, not a PlayStation controller. When an emulator's own icon is the console, draw what sets the emulator apart, so its tile never passes for the platform's.

Each entry in a module's `APPS` names the packages that wear it, and every package gets its own `app.<package>` slot pointing at the one file. List every package an app ships under: its store build, its F-Droid or nightly build, and its paid build. A theme can then restyle one build or all of them.

An app no pack draws keeps its own launcher icon. Android games always do: draw only apps, never a game.

## The app icon

Foldcade's own launcher icon is not one of the family. It is drawn in Afterglow's light, not as an object: one strand, the same teal-to-violet light as the backdrop's ribbons, draws the open clamshell. It comes in from the left, rings the top screen, folds down at the hinge, rings the bottom screen, and leaves on the right. The bottom screen holds a grid of covers in dim Afterglow colours; the one in the mint focus ring is lit, and the top screen shows it again, big, in its glow. That is the app in one picture: move on the bottom, and the top becomes the game.

- **One strand.** The two screens are one continuous line, never two boxes. Its tails fade out before the edge of what a launcher shows.
- **Light, not hardware.** No shell, bezel, buttons, or sticks. The coral of the lit cover is the only bright warm colour.
- **Covers, not keys.** The grid's covers are portrait and each a different colour, so the bottom screen reads as a library, not a keypad.
- **True black behind it.** The background layer is `#000000`, like Afterglow.
- **The monochrome layer** keeps the strand, the top screen's cover, and the grid, with only the lit cover solid. Its tails fade too.

`tools/art/appicon.py` writes the adaptive icon's three layers to `app/src/main/res/drawable/`, the local build's two to `app/src/local/res/drawable/`, their SVGs to `art/svg/app-icon/` and `art/svg/app-icon/local/`, and, on macOS, the 512 px store icon `app/src/main/ic_launcher-playstore.png`. minSdk is 33, so the adaptive icon is the only launcher icon; there are no PNG mipmaps.

```sh
python3 tools/art/appicon.py
```

### The local build's icon

Anything Gradle builds outside GitHub Actions is a local build, and it says so on the Thor. It keeps the drawing but the strand burns ember (amber to orange to rose) instead of Afterglow, and a warm-white disc with the status wrench cut through it sits on the bottom screen's corner. The label becomes "Foldcade (local)". The release, the rolling pre-release, and the unsigned dry-run all build on GitHub Actions, so they wear the published icon and label unchanged.

- **Only two layers change.** `app/src/local/res/` replaces `ic_launcher_foreground` and `ic_launcher_monochrome`. The background, the adaptive icon XML, and everything else come from `main`. `LocalBuildIconTest` holds it to that.
- **The badge is cut, not painted.** A themed icon keeps only a layer's alpha, so in the monochrome layer the gap round the disc is clipped out of the drawing and the wrench is an even-odd hole. Monochrome layers paint white only.
- **The switch.** `app/build.gradle.kts` treats a build as CI when `GITHUB_ACTIONS=true`, and `-PfoldcadeCiBuild=true` or `=false` overrides it. `printFoldcadeIdentity` writes `buildOrigin=ci` or `local`, and both release workflows fail unless it says `ci`.

## Adding an icon

### A platform or a place

1. **Draw it** in `tools/art/marks.py` as a function of its accent that returns parts. Start from the nearest existing drawing. For example:

   ```python
   def lynx(c):
       """Lynx-style: a wide handheld with the screen between two grips."""
       return [shadow(32, 52, 28, 3), body(rr(2, 17, 60, 32, 10), c), *bezel(18, 20, 28, 24, 2, c),
               control(plus(10, 33, 4, 2.9)), *[control(d) for d in diamond(54, 33, 3.4, 1.6)]]
   ```

2. **Register it** in `MARKS` with its accent and what it stands for: `"lynx": (lynx, "#FFB547", "Lynx-style landscape handheld")`. Use underscores. The slot replaces them with hyphens (`mark.lynx`), so the name must match the platform's canonical id.
3. **Regenerate** with `python3 tools/art/icons.py`. This writes `mark_lynx.xml`, `symbol_lynx.xml`, their SVGs, and both manifest slots.
4. **Wire it up.**
   - Its folder and tiles use the mark as soon as the slot exists; the app looks marks up by slot, so a pack can do the same. For a platform, add its id to `PLATFORM_MARKS` in `language/.../HomeBoard.kt`, so the collection picker offers it.
   - Give it a short picker name in `glyphName` in `CollectionLook.kt`.
5. **Check it** on the contact sheet beside its neighbours (see the checklist below), then on the device. Bump the icon count in `ThemeSceneTest` and run the tests.

### An app

1. **Draw it** in the module for its group, as a function of its accent that returns parts, the same as a platform.
2. **Register it** in that module's `APPS`: `"vlc": (vlc, "#FF8A1F", "VLC: a traffic cone", ["org.videolan.vlc"])`. The name is only the file's (`app_vlc.xml`). The slots are the packages.
3. **Check it** with `python3 tools/art/preview.py /tmp/vlc.png vlc nes chrome` beside a platform and another app, then regenerate with `python3 tools/art/icons.py`. Bump the icon count in `ThemeSceneTest`.

The app needs no code. Any tile or hero for a package with a slot draws the object.

### A collection glyph

1. Draw it in `marks.py` as a function with no arguments, returning `outline()` and `line()` parts, and add it to `GLYPHS`.
2. Regenerate. This writes `symbol_<name>.xml`.
3. Add it to `COLLECTION_GLYPHS` (in picker order) and to `glyphName`.

A glyph is a solid silhouette. It must read when it is pressed into the folder at about a third of the tile's width, so give it one strong outline and at most three cut-out details.

### A verb

1. Draw it in `verbs.py` as a function with no arguments, returning `outline()` and `line()` parts, and add it to `VERBS`.
2. Regenerate. This writes `symbol_<name>.xml` and the `symbol.<name>` slot.
3. Give its row the symbol in `rowIcon` in `MenuKit.kt`. A verb that leads a tile's menu as a tile goes in `verbRows` in `Picker.kt`, with its word in `tileLabel`.
4. Regenerate the theme contract (`./gradlew :language:testDebugUnitTest --tests '*ThemeContractTest*' -PwriteThemeContract=true`) and bump the icon count in `ThemeSceneTest`.

### A controller, status, or badge symbol

Draw it in `tools/art/symbols.py` on the 24-unit grid and add an `Asset` to `controller_assets()`, `status_assets()`, `badge_assets()`, or `control_assets()`. The file name sets the slot: `ic_status_cast_on` becomes `status.cast.on`. Regenerate. The app refers to it as `R.drawable.ic_status_cast_on`, and `themeIcon` resolves the slot.

### Before shipping

- [ ] It is recognisable at a glance, beside the rest of its row on the sheet, at 32 px for a symbol and on a tile for an object.
- [ ] A symbol is one ink, with cuts of at least 2.5 units, and colour only for state. Where it's drawn, the host passes its colour through `ThemeIconPainter.tint`.
- [ ] An object uses only parts from the recipe, its body in its accent, and no new light source.
- [ ] A platform is drawn as the thing you hold, or its console when its controller looks like a neighbour's, with no logo.
- [ ] An app echoes its own icon's colour and motif without tracing its logo or drawing words, and an emulator doesn't pass for its platform.
- [ ] Nothing new draws a border or edge inside a grid tile.
- [ ] The XML came from `icons.py`, not a hand edit, and the tests pass.
