# Afterglow

Foldcade’s original default theme. True black, rounded tiles, and a soft neon glow.

The original work in this directory is licensed under [MIT](LICENSE):

- `preview.png`
- `sounds/` (`move`, `activate`, `back`, `notify`)
- `icons/`, scene manifests, shader sources, and `generate.py`

`font.ttf` is Nunito Regular, licensed separately under the [SIL Open Font License](OFL-Nunito.txt).

These marks are Foldcade drawings. They are not console logos, and they are not taken from iiSU, Cocoon, Nintendo, Sony, or Disney.

`theme.json` is the v2 resource-theme manifest. It owns the colors, font, sounds, typography, focus styling, motion, and layout metrics. `icons/` contains its platform marks, status icons, badges, and controller glyphs. The app generates emergency drawables from these same files; there is one source of the art. Optional wallpapers are still `wallpaper-top` and `wallpaper-bottom`, each `.png` or `.webp`, letterboxed by the host. A missing image, font, or sound is skipped and the launcher remains usable.

`backgrounds/` defines Ribbons, Embers, and Static entirely as scene data and shader assets. Their geometry, palette, timing, cadence, and glow treatments are authored here. Ribbons use one GPU shader for their strands and crossings plus declarative path-following beads and shimmers. Embers use repeated native line and disc layers. Static uses the same ribbon shader at a dimmer gain and an eight-second cadence. The generic renderer knows no Afterglow effect algorithms. These shader sources and the illustrated icon art share the MIT license.

Packs may supply only icons or only scenes, and users can select them independently. V1 `marks/<name>.png` overrides remain accepted. See [the theme author guide](../../docs/themes.md) for the manifest, animation expressions and tracks, composition rules, packaging tool, and native preview. App builds and language tests automatically validate and package this directory. To generate just the ZIP, run `./gradlew :app:packageAfterglowTheme`; the archive is written to `app/build/generated/themeAssets/themes/afterglow.zip` and is not committed.
