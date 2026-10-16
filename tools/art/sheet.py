#!/usr/bin/env python3
"""A contact sheet of every Afterglow icon, as one self-contained HTML page. Standard library only.

    python3 tools/art/sheet.py /tmp/afterglow-icons.html

Open it in any browser. Grid objects sit on tiles drawn the way the home grid draws them: the
surface, the light in the mark's colour, the tile's corner, and the resting brightness. Symbols
show at menu size and large. Collections show every colour. Check a new icon here, beside the
rest of its family, before shipping it (docs/iconography.md).
"""
import sys
from art import Asset, to_svg
from objects import object_layers, symbol_layers, tone
from palette import INK
import marks, symbols

SURFACE = "#1C1C20"
TINTS = {"gold": "#FFC15A", "coral": "#FF7F5C", "rose": "#FF5C8A", "violet": "#B48CFF", "indigo": "#7C8CFF",
         "sky": "#4CC9FF", "teal": "#3EE6C0", "mint": "#8CF2A6", "lime": "#C6EE5A", "slate": "#A3B1C6"}

def svg(layers, w=64, ids=""):
    return to_svg(Asset("x", w, w, layers), ids=ids)

def tile(art, accent, label, rest=False):
    light = f"radial-gradient(circle at 50% 12%, color-mix(in srgb, {accent} 34%, {SURFACE}) 0%, {SURFACE} 61%)"
    return (f'<figure><div class="tile{" rest" if rest else ""}" style="background:{light}"><div class="art">{art}</div></div>'
            f'<figcaption>{label}</figcaption></figure>')

def collection(glyph, tint, ids):
    """The folder as Marks.kt's CollectionPainter draws it: the body times the tint, the glyph pressed
    in over a faint lip, then the face. Each is its own layer, stacked as the host stacks them."""
    r, g, b = (int(tint[i:i + 2], 16) / 255 for i in (1, 3, 5))
    x, y, size = marks.face_glyph()
    parts = marks.GLYPHS[glyph][0]() if glyph in marks.GLYPHS else marks.MARKS[glyph][0](marks.MARKS[glyph][1])
    box = lambda dy: (f"left:{(x - size / 2) / 64 * 100}%;top:{(y - size / 2 + dy) / 64 * 100}%;"
                      f"width:{size / 64 * 100}%;height:{size / 64 * 100}%")
    multiply = (f'<svg width="0" height="0" style="position:absolute"><filter id="{ids}"><feColorMatrix type="matrix" '
                f'values="{r} 0 0 0 0 0 {g} 0 0 0 0 0 {b} 0 0 0 0 0 1 0"/></filter></svg>')
    return (f'<div class="stack">{multiply}<div style="filter:url(#{ids})">{svg(marks.collection_body(), ids=ids + "b")}</div>'
            f'<div style="{box(size * 0.025)};opacity:0.32">{svg(symbol_layers(parts, "#FFFFFF"))}</div>'
            f'<div style="{box(0)}">{svg(symbol_layers(parts, tone(tint, -0.62)))}</div>'
            f'<div>{svg(marks.collection_face(), ids=ids + "f")}</div></div>')

def main(out):
    sections = []
    tiles = []
    for name, (draw, accent, about) in marks.MARKS.items():
        tiles.append(tile(svg(object_layers(draw(accent)), ids=name), accent, name.replace("_", "-")))
    sections.append(("Grid objects", "On a tile, focused (full brightness).", f'<div class="tiles">{"".join(tiles)}</div>'))
    rest = [tile(svg(object_layers(marks.MARKS[n][0](marks.MARKS[n][1])), ids="r" + n), marks.MARKS[n][1], n, rest=True)
            for n in ("nintendo_3ds", "game_boy", "snes", "library", "android_apps", "cloud")]
    sections.append(("At rest", "Unfocused tiles rest at 82%, the same as covers.", f'<div class="tiles">{"".join(rest)}</div>'))
    flat = []
    for name, (draw, accent, about) in marks.MARKS.items():
        s = svg(symbol_layers(draw(accent)))
        flat.append(f'<figure class="sym"><div>{s}{s}</div><figcaption>{name.replace("_", "-")}</figcaption></figure>')
    for name, (draw, about) in marks.GLYPHS.items():
        s = svg(symbol_layers(draw()))
        flat.append(f'<figure class="sym"><div>{s}{s}</div><figcaption>{name.replace("_", "-")}</figcaption></figure>')
    sections.append(("Symbols", "Places, platforms, and collection glyphs at menu-row size (32) and large.", f'<div class="syms">{"".join(flat)}</div>'))
    folders = [tile(collection(g, t, f"c{i}"), t, f"{g.replace('_', '-')} · {n}")
               for i, ((n, t), g) in enumerate(zip(TINTS.items(), ("star", "heart", "skull", "ghost", "sword", "music",
                                                                    "clock", "leaf", "mushroom", "nintendo_3ds")))]
    sections.append(("Collections", "The folder in each colour, its glyph pressed into the face.", f'<div class="tiles">{"".join(folders)}</div>'))
    chrome = []
    seen = set()
    for a in symbols.controller_assets() + symbols.status_assets() + symbols.badge_assets():
        key = to_svg(a).split("-->", 1)[1]
        if key in seen: continue
        seen.add(key)
        chrome.append(f'<figure class="ui"><div>{to_svg(a, px=24)}{to_svg(a, px=48)}</div><figcaption>{a.name}</figcaption></figure>')
    sections.append(("Controller, status, and badges", "24-unit symbols at 24 and 48 px. Identical drawings that only differ by name show once.",
                     f'<div class="syms">{"".join(chrome)}</div>'))
    body = "".join(f"<section><h2>{t}</h2><p>{p}</p>{h}</section>" for t, p, h in sections)
    open(out, "w").write(f"""<!doctype html><html><head><meta charset="utf-8"><title>Afterglow icons</title><style>
body {{ background:#000; color:{INK}; font:14px system-ui, sans-serif; margin:32px; }}
h2 {{ margin:40px 0 4px; }} p {{ color:#9A958C; margin:0 0 16px; }}
figure {{ margin:0; text-align:center; }} figcaption {{ color:#9A958C; font-size:12px; margin-top:6px; word-break:break-all; }}
.tiles {{ display:grid; grid-template-columns:repeat(auto-fill, 132px); gap:20px; }}
.tile {{ width:132px; height:132px; border-radius:28%; overflow:hidden; display:grid; place-items:center; }}
.tile .art {{ width:92%; height:92%; }} .tile svg {{ width:100%; height:100%; display:block; }}
.tile.rest .art {{ opacity:0.82; }}
.stack {{ position:relative; width:100%; height:100%; }} .stack > div {{ position:absolute; inset:0; }}
.stack > div[style*='left'] {{ inset:auto; }}
.syms {{ display:grid; grid-template-columns:repeat(auto-fill, 150px); gap:12px; }}
.syms figure {{ background:{SURFACE}; border-radius:14px; padding:12px 8px 8px; }}
.syms div {{ display:flex; gap:12px; align-items:center; justify-content:center; }}
.sym svg:first-child {{ width:32px; height:32px; }} .sym svg:last-child {{ width:72px; height:72px; }}
</style></head><body><h1>Afterglow icons</h1>{body}</body></html>
""")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "afterglow-icons.html")
