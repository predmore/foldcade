#!/usr/bin/env python3
"""Write every Afterglow icon: themes/afterglow/icons/*.xml, their SVG sources in art/svg/, and the
icon slots in themes/afterglow/theme.json. Standard library only.

    python3 tools/art/icons.py

The script owns every ic_btn_, ic_status_, ic_badge_, ic_control_, mark_, symbol_ and app_ file in those folders, so an
icon it no longer draws is removed. See docs/iconography.md before adding one.
"""
import json, os, re
from art import Asset, to_svg, to_vd
from palette import INK
from objects import object_layers, symbol_layers
import apps, marks, symbols, verbs

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ICONS = f"{REPO}/themes/afterglow/icons"
SVG = f"{REPO}/art/svg"
OWNED = ("ic_btn_", "ic_status_", "ic_badge_", "ic_control_", "mark_", "symbol_", "app_")

def slot(name):
    """The manifest slot an icon file fills: ic_btn_a_filled -> button.a.filled, mark_game_boy -> mark.game-boy."""
    for prefix, kind, sep in (("ic_btn_", "button", "."), ("ic_status_", "status", "."), ("ic_badge_", "badge", "."), ("ic_control_", "control", "."),
                              ("mark_", "mark", "-"), ("symbol_", "symbol", "-")):
        if name.startswith(prefix):
            return f"{kind}.{name[len(prefix):].replace('_', sep)}"
    raise ValueError(name)

def slots(asset):
    """Every manifest slot an icon fills. An app's object fills one `app.<package>` slot per package that wears it."""
    if asset.name.startswith("app_"):
        return [f"app.{p}" for p in apps.APPS[asset.name[len("app_"):]][3]]
    return [slot(asset.name)]

def one_ink_status(asset):
    """A status icon drawn only in INK follows the theme's ink, so it shows on a light pack too.
    Afterglow's ink is INK, so tinting it changes nothing there. One with state colours keeps them."""
    if not asset.name.startswith("ic_status_"):
        return False
    colours = set(re.findall(r'android:(?:fillColor|strokeColor|color)="#([0-9A-Fa-f]{8})"', to_vd(asset)))
    return colours == {"FF" + INK.lstrip("#").upper()}

def assets():
    """(asset, svg folder, accent or None) for every icon."""
    out = [(a, "controller", None) for a in symbols.controller_assets()]
    out += [(a, "status", None) for a in symbols.status_assets()]
    out += [(a, "badges", None) for a in symbols.badge_assets()]
    out += [(a, "controls", None) for a in symbols.control_assets()]
    for name, (draw, accent, about) in marks.MARKS.items():
        parts = draw(accent)
        out.append((Asset(f"mark_{name}", 64, 64, object_layers(parts), dp=(48, 48), note=f"Afterglow mark: {about}"),
                    "marks", accent))
        out.append((Asset(f"symbol_{name}", 64, 64, symbol_layers(parts), dp=(48, 48), tint=True,
                          note=f"Afterglow symbol: {about}"), "symbols", None))
    for name, (draw, about) in marks.GLYPHS.items():
        out.append((Asset(f"symbol_{name}", 64, 64, symbol_layers(draw()), dp=(48, 48), tint=True,
                          note=f"Afterglow collection glyph: {about}"), "symbols", None))
    for name, (draw, about) in verbs.VERBS.items():
        out.append((Asset(f"symbol_{name}", 64, 64, symbol_layers(draw()), dp=(48, 48), tint=True,
                          note=f"Afterglow verb: {about}"), "verbs", None))
    for name, (draw, accent, about, _) in apps.APPS.items():
        out.append((Asset(f"app_{name}", 64, 64, object_layers(draw(accent)), dp=(48, 48), note=f"Afterglow app object: {about}"),
                    "apps", accent))
    out += [(Asset("mark_collection_body", 64, 64, marks.collection_body(), dp=(48, 48),
                   note="Afterglow collection folder in greys; the host multiplies it by the collection's tint"), "collections", None),
            (Asset("mark_collection_face", 64, 64, marks.collection_face(), dp=(48, 48),
                   note="Afterglow collection gloss, rims, and the sheet inside; drawn over the glyph, never tinted"), "collections", None)]
    return out

def main():
    made = assets()
    names = [a.name for a, _, _ in made]
    assert len(names) == len(set(names)), "two icons share a name"
    filled = [s for a, _, _ in made for s in slots(a)]
    assert len(filled) == len(set(filled)), "two icons fill one slot"
    os.makedirs(ICONS, exist_ok=True); os.makedirs(SVG, exist_ok=True)
    for folder in (ICONS, *(f"{SVG}/{d}" for d in os.listdir(SVG))):
        for file in os.listdir(folder):
            if file.startswith(OWNED) and os.path.splitext(file)[0] not in names:
                os.remove(f"{folder}/{file}")
    for a, folder, _ in made:
        os.makedirs(f"{SVG}/{folder}", exist_ok=True)
        open(f"{ICONS}/{a.name}.xml", "w").write(to_vd(a))
        open(f"{SVG}/{folder}/{a.name}.svg", "w").write(to_svg(a))
    for folder in os.listdir(SVG):
        if not os.listdir(f"{SVG}/{folder}"): os.rmdir(f"{SVG}/{folder}")
    path = f"{REPO}/themes/afterglow/theme.json"
    theme = json.loads(open(path).read())
    kept = {k: v for k, v in theme["icons"].items() if not v["file"].startswith(tuple(f"icons/{p}" for p in OWNED))}
    for a, _, accent in made:
        entry = {"file": f"icons/{a.name}.xml", "tint": a.tint or one_ink_status(a)}
        if accent: entry["accent"] = accent.upper()
        for s in slots(a): kept[s] = dict(entry)
    theme["icons"] = dict(sorted(kept.items()))
    open(path, "w").write(json.dumps(theme, indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {len(made)} icons; the collection glyph sits at {tuple(round(v, 3) for v in marks.face_glyph())}")

if __name__ == "__main__":
    main()
