# Resource themes

Themes are ZIPs containing JSON, images, Android vector XML, fonts, audio, and AGSL shaders. They contain no Java, Kotlin, DEX, native libraries, or application scripts. The native renderer provides the machinery; packs own their design and performance. There is no automatic quality reduction or cap on scene layers, particles, shader complexity, or requested frame rate.

Afterglow is the reference pack in `themes/afterglow/`. Its icon artwork, focus styling, typography, motion settings, layout metrics, and backgrounds live there. The app generates its emergency drawable resources from those same icon files. Tests keep a CPU model of the Afterglow backgrounds. It is updated alongside the scenes and checks their uniforms, lit budget, and motion pacing.

The current version exposes the existing launcher's presentation tokens and components. It does not yet let a pack replace the controller's grid/navigation topology or author arbitrary screen layouts. Downloading/installing external packs through the UI is also future work; the loader already accepts resource packs from private `files/themes/*.zip` storage.

## Compose providers

The Theme setting selects the base colors, font, styling, sounds, and music. Icons can follow that theme or select another pack independently. Missing or unreadable override icons fall through to the base pack and then Afterglow. Built-in selects the native emergency icons explicitly.

Background lists are discovered from every installed pack. Selecting one pins it independently of the base theme. Each scene keeps its provider's shaders, textures, palette, variables, and timeline; unrelated icon packs cannot replace those internal assets. IDs are `pack-id:scene-id`. The host's Off choice means no scene. Old `ribbons`, `embers`, and `static` preferences migrate when the scene name is unambiguous. Unavailable theme/icon selections remain saved for when the provider returns.

## Manifest version 2

```json
{
  "version": 2,
  "id": "example",
  "name": "Example",
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
    "glow": {"focusOverflow": 64, "focusPeak": 1}
  }
}
```

Category-only packs can omit colors, style, icons, or scenes. A name is required; give v2 packs a stable id using letters, numbers, dots, underscores or hyphens. The id `builtin` is reserved. Icons accept PNG, WebP, or self-contained Android vector XML with literal colors. XML supports paths, group transforms, clipping, strokes, and inline linear/radial/sweep gradients. XML may not refer to application resource IDs. The underlying vector grammar is documented by [Android](https://developer.android.com/reference/android/graphics/drawable/VectorDrawable). All asset paths are relative to the archive root. See Afterglow's manifest for the complete stock slot list and styling tokens. Platforms use `mark.<canonical-platform-id>`, folders use `mark.folder`, controller hints use `button.a`/`button.a.filled`, and status variants use slots such as `status.battery.100.charging`.

An icon's accent is its tile/hero glow color. Badge icons use their own colors unless `tint` is true, which requests the control's semantic tint. Game cover art and installed app artwork retain their normal content priority.

The resolved icon carries its accent, tint, and blending metadata together with its image; cache eviction does not change its appearance. Legacy `marks/<name>.png` files contribute `mark.<name>` slots and keep their original Screen blending. Explicit manifest slots take precedence over legacy files in the same pack.

V1 conventions remain accepted: `font.ttf`/`font.otf`, `marks/<name>.png`, `wallpaper-top.png`/`.webp`, `wallpaper-bottom.png`/`.webp`, and `sounds/{move,activate,back,notify}.ogg`. Font, wallpaper, and sound failures leave usable defaults. Wallpapers are letterboxed. Theme music follows the existing optional `music/theme.ogg` contract.

Styles are resolved once into typed values. Invalid tokens report their manifest path and use their defaults: padding and dimensions must be nonnegative, type sizes/scales and durations positive, alpha/peak values in 0–1, rim values in 0–1 excluding 1, and glow steps whole nonnegative integers. Insets use 0–0.5 and art fractions use 0–1 excluding 0. Motion offsets and depth may be signed; easing y control points may overshoot. These domains prevent invalid native inputs; they do not cap rendering complexity. Duplicate pack IDs are ignored before fonts/audio are materialized, and cached files are keyed by their asset contents.

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

Numeric properties can be numbers, arithmetic-expression strings, or keyframe tracks. Expressions evaluate in double precision, so `time` stays exact through months of uptime. Values reach shaders as 32-bit floats, which lose a frame's worth of precision after about a day. Pass shaders a wrapped phase such as `fract(time / 20)` or `2*pi*fract(time / 20)`, not raw `time`. Available inputs are `time` in design seconds, `width`/`height` of the render target, `panel` (top 0, bottom 1), `moving` (0/1), `clock`, repeated-layer `index`, and sampled-path `u` (0–1). `pi` is constant. Variables are evaluated in declaration order and can refer to preceding variables. Repeat-local variables are reevaluated for each instance.

Path samples reevaluate variables that depend directly or indirectly on `u`, including keyframe tracks, then restore the instance's values. Runtime errors in scene-wide variables or `frameRate` stop that scene and emit one diagnostic; layer errors disable only the failed layer.

Expressions support `+ - * / %`, comparisons, parentheses, and `sin`, `cos`, `abs`, `floor`, `ceil`, `sqrt`, `fract`, `smooth`, `min`, `max`, `pow`, `clamp`, `mix`, `if`, `hash`, and `window`. `if(condition, yes, no)` evaluates only the selected branch. `hash(integer, salt)` supplies deterministic 0–1 values. `window(time, start, duration, fade)` supplies a smooth fade at both edges. No expression can call app APIs.

Keyframes are increasing `[time, value]` pairs. They loop by default; `loop: false` holds the end value. `duration` defaults to the last keyframe. `time` can be an expression, for example `time + index * 0.2`. Easing is `linear` or `smooth`. Tracks use binary search to find their active segment.

`frameRate` may be an expression to request different cadences during a scene; zero stops the frame clock. Actual updates are synchronized to display frames. `renderScale` chooses the resolution of an offscreen surface when different from 1. Coordinates and fixed pixel sizes use that render target's pixels; the surface is scaled to fill the panel. Authors choose how their artwork responds to reduced resolution. `maxClock` optionally caps the scene's design-time rate. `frozenTime` defaults to zero. `animateWhenMotionOff` opts into slow movement even when the user-selected motion speed is Off; Afterglow's Static uses this to preserve its subtle drift. Android's remove-animations setting still stops the clock.

The host pauses hidden/inactive scenes and respects their panel lifecycle. Each panel has independent rendering state. Identical time inputs give deterministic frames, which the native preview can freeze for comparison.

## Package and preview

```sh
./gradlew :app:packageAfterglowTheme
python3 tools/themes/pack.py /path/to/my-theme --output /tmp/my-theme.zip
```

App builds and language tests automatically generate Afterglow from `themes/afterglow/` into `app/build/generated/themeAssets/themes/afterglow.zip`. Both debug and release APKs include it as `assets/themes/afterglow.zip`. Keep the theme sources in Git; the generated archive stays in the ignored build directory.

The tool validates asset references and produces reproducible ZIPs using only Python's standard library. JSON/asset checks happen before packaging; the native loader validates expressions and the target device compiles shaders. Archive reading retains the existing 8 MiB per-entry transport limit (4096 entries); these are separate from scene rendering choices.

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
- `panel`: `--ei panel 1` for bottom-panel scene inputs; use `am start --display <id>` to preview on that display.

The overlay switches scenes, reduced motion, and pause. It reports draw cadence, CPU submission time, layer count, and decoded texture memory. CPU submission is not GPU render time. Collect Android frame timing or profile on the target device for GPU cost:

```sh
adb logcat -s FoldcadeTheme FoldcadeThemePreview
adb shell dumpsys gfxinfo app.foldcade.debug reset
adb shell dumpsys gfxinfo app.foldcade.debug framestats
```

Errors include the asset path, layer name, expression column, or the shader compiler's source location. Authors are responsible for their effect complexity and quality presets; the engine provides control and measurements without silently changing their design.
