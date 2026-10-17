#!/usr/bin/env python3
"""A quick look at grid objects as PNG, without a browser. Standard library only, plus macOS Quick Look.

    python3 tools/art/preview.py /tmp/apps.png                 # every app object
    python3 tools/art/preview.py /tmp/some.png chrome retroarch nintendo_ds

Names are app or mark names (tools/art/apps.py, tools/art/marks.py). Each sits on a tile the way the
home grid draws it: the surface, a light in its accent near the top, and the tile's corner. The last
row repeats the first few at the size a tile has on the Thor's bottom screen, to check they still read.
Off macOS, open the .svg it writes beside the PNG in any browser.
"""
import os, shutil, subprocess, sys, tempfile
from art import Asset, to_svg
from objects import object_layers
import apps, marks

SURFACE = "#1C1C20"
TILE, GAP, COLUMNS, SMALL = 160, 28, 8, 64

def drawing(name):
    if name in apps.APPS:
        draw, accent, about, _ = apps.APPS[name]
    else:
        draw, accent, about = marks.MARKS[name]
    return object_layers(draw(accent)), accent

def tile(name, x, y, size, n, caption=True):
    layers, accent = drawing(name)
    art = to_svg(Asset(name, 64, 64, layers), ids=f"t{n}_").replace('<svg xmlns="http://www.w3.org/2000/svg" ', "<svg ", 1)
    art = art.replace('width="64" height="64"', f'x="{x + size * 0.04}" y="{y + size * 0.04}" width="{size * 0.92}" height="{size * 0.92}"', 1)
    light = f"light{n}"
    out = (f'<radialGradient id="{light}" gradientUnits="userSpaceOnUse" cx="{x + size / 2}" cy="{y + size * 0.12}" r="{size * 0.61}">'
           f'<stop offset="0" stop-color="{accent}" stop-opacity="0.34"/><stop offset="1" stop-color="{accent}" stop-opacity="0"/></radialGradient>'
           f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="{size * 0.28}" fill="{SURFACE}"/>'
           f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="{size * 0.28}" fill="url(#{light})"/>{art}')
    if caption:
        out += (f'<text x="{x + size / 2}" y="{y + size + 18}" fill="#9A958C" font-family="Helvetica" font-size="13" '
                f'text-anchor="middle">{name}</text>')
    return out

def sheet(names):
    rows = (len(names) + COLUMNS - 1) // COLUMNS
    small = names[:COLUMNS * 2]
    w = GAP + COLUMNS * (TILE + GAP)
    h = GAP + rows * (TILE + GAP + 22) + (SMALL + GAP)
    body = [tile(n, GAP + (i % COLUMNS) * (TILE + GAP), GAP + (i // COLUMNS) * (TILE + GAP + 22), TILE, i)
            for i, n in enumerate(names)]
    top = GAP + rows * (TILE + GAP + 22)
    body += [tile(n, GAP + i * (SMALL + 12), top, SMALL, 1000 + i, caption=False) for i, n in enumerate(small)]
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="{w}" height="{h}" fill="#000"/>{"".join(body)}</svg>'

def main(out, names):
    names = names or list(apps.APPS)
    unknown = [n for n in names if n not in apps.APPS and n not in marks.MARKS]
    if unknown: sys.exit(f"unknown: {', '.join(unknown)}")
    svg = sheet(names)
    base = os.path.splitext(out)[0]
    open(base + ".svg", "w").write(svg)
    if not shutil.which("qlmanage"):
        print(f"wrote {base}.svg (no Quick Look here; open it in a browser)")
        return
    w, h = (int(svg.split(f'{k}="', 1)[1].split('"', 1)[0]) for k in ("width", "height"))
    with tempfile.TemporaryDirectory() as tmp:
        # Quick Look draws at twice an SVG's size, into a square as big as asked, from the top left.
        # So halve the size it is told, then cut the square down to the sheet. sips reads an offset of
        # 0 as "centre it", so 1, 1 is the top left.
        half = os.path.join(tmp, "sheet.svg")
        open(half, "w").write(svg.replace(f'width="{w}" height="{h}"', f'width="{w / 2}" height="{h / 2}"', 1))
        subprocess.run(["qlmanage", "-t", "-s", str(max(w, h)), "-o", tmp, half], check=True, capture_output=True)
        subprocess.run(["sips", "-c", str(h), str(w), "--cropOffset", "1", "1", f"{half}.png", "--out", out], check=True, capture_output=True)
    print(f"wrote {out}")

if __name__ == "__main__":
    if len(sys.argv) < 2: sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2:])
