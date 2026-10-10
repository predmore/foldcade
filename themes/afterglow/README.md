# Afterglow

Foldcade’s original default theme. True black, rounded tiles, and a soft neon glow.

The art in this directory is licensed under [Creative Commons Attribution-ShareAlike 4.0 International](LICENSE):

- `preview.png`
- `sounds/` (`move`, `activate`, `back`, `notify`)

`font.ttf` is Nunito Regular, licensed under the [SIL Open Font License](OFL-Nunito.txt), not CC BY-SA.

`generate.py` is program source and stays under the GNU GPLv3 with the rest of Foldcade. The files it writes are the CC BY-SA 4.0 art.

These marks are Foldcade drawings. They are not console logos, and they are not taken from iiSU, Cocoon, Nintendo, Sony, or Disney.

`theme.json` is the v1 theme zip: colors, font (`font.ttf` or `font.otf`), the four sounds, `iconRadius`, and `artScale`, plus `backgroundMotion`. Optional wallpapers are `wallpaper-top` and `wallpaper-bottom`, each `.png` or `.webp`. The host letterboxes that image behind its panel and does not crop it or animate it. A missing image, a font the host cannot read, or a sound it cannot play is skipped. A zip that is not a theme leaves the built-in black theme, and the home still draws. Afterglow does not ship a wallpaper. `background` is `#000000`. `surface` is `#1C1C20`, the fill of islands, menus, and dialogs. Afterglow sets `backgroundMotion` to `ribbons` (thin teal and amber trails on black). A theme may set `embers`, `static`, or `off`. Static is a dimmer, almost still frame of those ribbons, shifted a few pixels every few minutes. A field the host cannot read falls back to the built-in theme, and a missing `backgroundMotion` stays off.

The grid draws original illustrated marks in the app, one per platform and library (`mark_*` in `app/src/main/res/drawable`, sources in `art/svg/marks`, drawn by `tools/art/platforms.py`). They are hardware silhouettes and simple pictures, not console logos, and they are CC BY-SA 4.0. The art-kit scripts and the focus-glow shader stay GPLv3.

A theme may replace any mark with `marks/<name>.png`. A platform's mark is named by its canonical id, such as `nintendo-3ds`, `game-boy-advance`, or `playstation-2`. The library marks are `android-games`, `android-apps`, `pc` (GameNative), `moonlight`, `folder` (a folder you made), `library` (All), and `console` (a platform with no mark of its own). Afterglow ships no `marks/` images and uses the drawn marks.
