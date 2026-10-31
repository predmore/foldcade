#!/usr/bin/env python3
"""Write Foldcade's launcher icon: the adaptive icon's three layers in app/src/main/res/drawable/,
their SVG sources in art/svg/app-icon/, and the 512px store icon. Standard library only.

    python3 tools/art/appicon.py

One Afterglow strand draws the open clamshell. It comes in from the left, rings the lid, folds down
at the hinge, rings the base, and leaves on the right. The cover lit in the base's grid is shown again, big, in the lid:
move on the bottom screen, and the top screen becomes the game.

A local build (anything Gradle builds outside GitHub Actions) wears app/src/local/res/ on top: the same
drawing with the strand burning ember instead of Afterglow, and a wrench badge, so a bench build on the
Thor is never mistaken for a release. Only the foreground and monochrome layers are replaced.

The store icon is rendered through macOS Quick Look, like preview.py. Elsewhere it is left as it was.
"""
import os, shutil, subprocess, tempfile
from art import *
from palette import *
import symbols

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = f"{ROOT}/app/src/main/res/drawable"
SVG = f"{ROOT}/art/svg/app-icon"
LOCAL_RES = f"{ROOT}/app/src/local/res/drawable"
LOCAL_SVG = f"{SVG}/local"
STORE = f"{ROOT}/app/src/main/ic_launcher-playstore.png"

WHITE = "#FFFFFF"
SCALE = 0.76         # the drawing below spans the 108-unit layer; this keeps the panes in the 66-unit safe zone
STRAND = 3.0         # the strand's width before SCALE
R = 7                # the panes' corner radius

LID = (26, 20, 82, 53)       # x1, y1, x2, y2
BASE = (33, 53, 75, 90)      # the base's top edge is the lid's bottom edge: the strand folds there
HERO = (45.5, 25.5, 17, 22, 3)  # the lit tile again, big: the game fills the top screen
TILES = [(39 + c * 11, 59.5 + r * 13.5, 8, 10.5, 2) for r in range(2) for c in range(3)]  # portrait, like covers
LIT = 1                      # the tile in the focus ring: top row, middle
COVERS = [TEAL, None, VIOLET, BLUE, ROSE, AMBER]  # the rest of the library, dimmed

AFTERGLOW = [(0.23, TEAL, 1), (0.48, BLUE, 1), (0.77, VIOLET, 1)]
EMBER = [(0.23, AMBER, 1), (0.48, ORANGE, 1), (0.77, ROSE, 1)]  # a local build's strand
BADGE = (71, 71, 9.5)  # the local badge, in layer units outside SCALE: it reaches 35.5 from the centre, inside a circle mask
BADGE_GAP = 2.2        # black between the badge and the drawing it sits on

def strand_path():
    (lx1, ly1, lx2, ly2), (bx1, _, bx2, by2) = LID, BASE
    return (
        # in from the left, rising into the lid's left side
        f"M-4,74 C8,74 16,60 22,55 Q{lx1},51.5 {lx1},47 "
        # the lid, clockwise, then back along its bottom edge
        f"L{lx1},{ly1 + R} Q{lx1},{ly1} {lx1 + R},{ly1} L{lx2 - R},{ly1} Q{lx2},{ly1} {lx2},{ly1 + R} "
        f"L{lx2},{ly2 - R} Q{lx2},{ly2} {lx2 - R},{ly2} L{bx1 + R},{ly2} "
        # the fold: down the base's left side, round the base
        f"Q{bx1},{ly2} {bx1},{ly2 + 3.5} L{bx1},{by2 - R} Q{bx1},{by2} {bx1 + R},{by2} L{bx2 - R},{by2} "
        f"Q{bx2},{by2} {bx2},{by2 - R} L{bx2},66 "
        # and out to the right
        f"Q{bx2},62 79,59 C86,53 96,36 112,36")

def fading(stops):
    """A strand paint across the layer whose tails are gone by the time they cross a circle mask."""
    return Lin(0, 0, 108, 0, [(0, stops[0][1], 0), (0.1, stops[0][1], 0)] + stops + [(0.9, stops[-1][1], 0), (1, stops[-1][1], 0)])

def strand(d, light=AFTERGLOW):
    paint = fading(light)
    core = fading([(0.23, WHITE, 0.4), (0.77, WHITE, 0.4)])
    # Vector drawables have no blur, so the glow is many faint widening strokes, packed near the core
    glow = [L(d, stroke=paint, sw=STRAND * (1 + 4.5 * (i / 16) ** 1.6), salpha=0.02) for i in range(16, 0, -1)]
    return glow + [L(d, stroke=paint, sw=STRAND), L(d, stroke=core, sw=STRAND * 0.32)]

def lit(x, y, w, h, r, ring=False):
    out = [halo(x + w / 2, y + h / 2, max(w, h) * 1.15, AMBER, 0.45)]
    if ring:   # the home grid's focus ring
        out.append(L(rrect(x - 1.6, y - 1.6, w + 3.2, h + 3.2, r + 1.4), stroke=MINT, sw=0.9))
    return out + [
        L(rrect(x, y, w, h, r), fill=Lin(x, y, x + w, y + h, [(0, "#FFD98A", 1), (0.5, ORANGE, 1), (1, ROSE, 1)])),
        L(rrect(x, y, w, h, r), fill=Lin(x, y, x + w * 0.6, y + h, [(0, WHITE, 0.18), (1, WHITE, 0)]))]

def fitted(layers):
    return [G(layers, scale=(SCALE, SCALE), pivot=(54, 54))]

def foreground(light=AFTERGLOW):
    # the game's light spreads across the top screen
    out = strand(strand_path(), light) + [halo(54, 36.5, 30, AMBER, 0.22, sx=1.3, sy=0.55)] + lit(*HERO)
    for i, t in enumerate(TILES):
        out += lit(*t, ring=True) if i == LIT else [L(rrect(*t), fill=Lin(0, t[1], 0, t[1] + t[3], [(0, COVERS[i], 0.5), (1, COVERS[i], 0.28)]))]
    return fitted(out)

def monochrome():
    # the tails fade here too, so a themed icon's strand doesn't read as a cord
    out = [L(strand_path(), stroke=fading([(0.23, WHITE, 1), (0.77, WHITE, 1)]), sw=STRAND * 1.47), L(rrect(*HERO), fill=WHITE)]
    return fitted(out + [L(rrect(*t), fill=WHITE, alpha=1 if i == LIT else 0.4) for i, t in enumerate(TILES)])

def badge(color):
    """A warm-white disc with the status wrench cut through it, as one even-odd path, so a themed icon keeps the hole."""
    x, y, r = BADGE
    k = r * 1.25 / 12   # the 24-unit wrench spans about 20 units; this leaves it a ring of disc
    wrench = xf(symbols.wrench()[0].d, s=k, dx=x - 12, dy=y - 12, cx=12, cy=12)
    return L(circle(x, y, r) + wrench, fill=color, evenodd=True)

def local_foreground():
    x, y, r = BADGE
    return foreground(EMBER) + [L(circle(x, y, r + BADGE_GAP), fill=BLACK), badge(INK)]

def local_monochrome():
    # a monochrome layer is alpha only, so the gap round the badge is cut out of the drawing, not painted black
    x, y, r = BADGE
    return [G(monochrome(), clip="M0,0H108V108H0Z" + circle_ccw(x, y, r + BADGE_GAP)), badge(WHITE)]

def local_assets():
    return [Asset("ic_launcher_foreground", 108, 108, local_foreground(), note="Local build adaptive icon foreground: the strand burns ember and wears a wrench"),
            Asset("ic_launcher_monochrome", 108, 108, local_monochrome(), note="Local build adaptive icon monochrome layer (themed icons): the wrench badge cut out")]

def assets():
    return [Asset("ic_launcher_foreground", 108, 108, foreground(), note="Adaptive icon foreground: one Afterglow strand draws the open clamshell"),
            Asset("ic_launcher_background", 108, 108, [L("M0,0H108V108H0Z", fill=BLACK)], note="Adaptive icon background: true black"),
            Asset("ic_launcher_monochrome", 108, 108, monochrome(), note="Adaptive icon monochrome layer (themed icons)")]

def store_icon(fg, bg):
    """The 512px store icon: the 72 units a launcher shows, square, on Quick Look. None off macOS."""
    if not shutil.which("qlmanage"):
        return None
    layers = [G(bg.layers + fg.layers, scale=(1.5, 1.5), pivot=(54, 54))]
    # Quick Look draws a square SVG as big as asked for at 1:1 only when the two sizes match
    svg = to_svg(Asset("store", 108, 108, layers), px=512)
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "store.svg"); open(src, "w").write(svg)
        subprocess.run(["qlmanage", "-t", "-s", "512", "-o", tmp, src], check=True, capture_output=True)
        shutil.copyfile(f"{src}.png", STORE)
    return STORE

def write(made, res, svg):
    os.makedirs(res, exist_ok=True)
    os.makedirs(svg, exist_ok=True)
    for a in made:
        open(f"{res}/{a.name}.xml", "w").write(to_vd(a))
        open(f"{svg}/{a.name}.svg", "w").write(to_svg(a))

def main():
    made = assets()
    write(made, RES, SVG)
    write(local_assets(), LOCAL_RES, LOCAL_SVG)
    store = store_icon(made[0], made[1])
    print(f"wrote {len(made)} layers, 2 local layers" + (f" and {os.path.relpath(store, ROOT)}" if store else "; no Quick Look here, so the store icon is unchanged"))

if __name__ == "__main__":
    main()
