# Afterglow

Foldcade’s original default theme. True black, rounded tiles, a soft neon glow, and four original marks.

The art in this directory is licensed under [Creative Commons Attribution-ShareAlike 4.0 International](LICENSE):

- `wallpaper-top.png` and `wallpaper-bottom.png`
- `marks/` (`dual`, `pocket`, `desk`, `beam`)
- `preview.png`
- `font.ttf` (Foldcade Afterglow)
- `sounds/` (`move`, `activate`, `back`, `notify`)

`generate.py` is program source and stays under the GNU GPLv3 with the rest of Foldcade. The files it writes are the CC BY-SA 4.0 art.

These marks are Foldcade drawings. They are not console logos, and they are not taken from iiSU, Cocoon, Nintendo, Sony, or Disney.

`theme.json` is the v1 theme zip: colors, and the host also reads the font, wallpapers, sounds, `iconRadius`, and `artScale`. `background` and `surface` are `#000000`. The `marks/` images are this reference theme’s platform glyphs. A field the host cannot read falls back to the built-in theme.

| Mark | Color | What it stands for |
| --- | --- | --- |
| `dual` | rose | a dual-screen handheld library |
| `pocket` | cyan | a pocket handheld library |
| `desk` | amber | a desk library |
| `beam` | violet | a stream library |
