# Afterglow

Foldcade’s original default theme. True black, rounded tiles, and a soft neon glow.

The art in this directory is licensed under [Creative Commons Attribution-ShareAlike 4.0 International](LICENSE):

- `preview.png`
- `sounds/` (`move`, `activate`, `back`, `notify`)

`font.ttf` is Nunito Regular, licensed under the [SIL Open Font License](OFL-Nunito.txt), not CC BY-SA.

`generate.py` is program source and stays under the GNU GPLv3 with the rest of Foldcade. The files it writes are the CC BY-SA 4.0 art.

These marks are Foldcade drawings. They are not console logos, and they are not taken from iiSU, Cocoon, Nintendo, Sony, or Disney.

`theme.json` is the v2 resource-theme manifest. It owns the colors, font, sounds, typography, focus styling, motion, and layout metrics. `icons/` contains its platform marks, status icons, badges, and controller glyphs. The app generates emergency drawables from these same files; there is one source of the art. Optional wallpapers are still `wallpaper-top` and `wallpaper-bottom`, each `.png` or `.webp`, letterboxed by the host. A missing image, font, or sound is skipped and the launcher remains usable.

`backgrounds/` defines Ribbons, Embers, and Static entirely as scene data and shader assets. Their geometry, palette, timing, cadence, and glow treatments are authored here. Ribbons use one GPU shader for their strands and crossings plus declarative path-following beads and shimmers. Embers use repeated native line and disc layers. Static uses the same ribbon shader at a dimmer gain and an eight-second cadence. The generic renderer knows no Afterglow effect algorithms. These shader sources, like the art-kit scripts, are GPLv3 (see `LICENSE-shaders-GPLv3.txt`); the illustrated icon art stays CC BY-SA 4.0.

Packs may supply only icons or only scenes, and users can select them independently. V1 `marks/<name>.png` overrides remain accepted. See [the theme author guide](../../docs/themes.md) for the manifest, animation expressions and tracks, composition rules, packaging tool, and native preview. App builds and language tests automatically validate and package this directory. To generate just the ZIP, run `./gradlew :app:packageAfterglowTheme`; the archive is written to `app/build/generated/themeAssets/themes/afterglow.zip` and is not committed.
