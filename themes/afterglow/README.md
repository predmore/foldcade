# Afterglow

Foldcade’s original default theme. True black, rounded tiles, a soft neon glow, and four original marks.

The art in this directory is licensed under [Creative Commons Attribution-ShareAlike 4.0 International](LICENSE):

- `marks/` (`dual`, `pocket`, `desk`, `beam`)
- `preview.png`
- `sounds/` (`move`, `activate`, `back`, `notify`)

`font.ttf` is Nunito Regular, licensed under the [SIL Open Font License](OFL-Nunito.txt), not CC BY-SA.

`generate.py` is program source and stays under the GNU GPLv3 with the rest of Foldcade. The files it writes are the CC BY-SA 4.0 art.

These marks are Foldcade drawings. They are not console logos, and they are not taken from iiSU, Cocoon, Nintendo, Sony, or Disney.

`theme.json` is the v1 theme zip: colors, and the host also reads the font, sounds, `iconRadius`, `artScale`, and `backgroundMotion`. Optional wallpaper files are still accepted for another theme. Afterglow does not ship one. `background` and `surface` are `#000000`. Afterglow sets `backgroundMotion` to `ribbons` (thin teal and amber trails on black). A theme may set `embers`, `static`, or `off`. Static is a dimmer, almost still frame of those ribbons, shifted a few pixels every few minutes. A field the host cannot read falls back to the built-in theme, and a missing `backgroundMotion` stays off.

The grid draws a separate set of original line glyphs in the app: clamshell, slim dual-screen, landscape handheld, cartridge, disc, and cloud. Those vectors are program source under the GNU GPLv3. The `marks/` images stay in this zip as the painted marks and stay CC BY-SA 4.0. Neither set is a console logo.

| Mark | Color | What it stands for |
| --- | --- | --- |
| `dual` | rose | a dual-screen handheld library |
| `pocket` | cyan | a pocket handheld library |
| `desk` | amber | a desk library |
| `beam` | violet | a stream library |
