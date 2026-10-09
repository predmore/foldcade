"""Turn Nunito ExtraBold glyphs into SVG path data (outlines only)."""
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
import functools

SRC = "/usr/share/fonts/truetype/sand-box/google/Nunito/Nunito-VariableFont_wght.ttf"

@functools.lru_cache(None)
def font(wght=850):
    f = TTFont(SRC)
    return instancer.instantiateVariableFont(f, {"wght": wght})

def text_path(s, cx, cy, cap_h, wght=850, track=0.0):
    """Path for string s, centred at (cx, cy) with cap height cap_h (viewport units)."""
    f = font(wght)
    gs = f.getGlyphSet()
    cmap = f.getBestCmap()
    capH = f["OS/2"].sCapHeight or 700
    sc = cap_h / capH
    # measure
    names = [cmap[ord(c)] for c in s]
    adv = [gs[n].width for n in names]
    total = sum(adv) + track / sc * (len(s) - 1)
    # bounds of ink for horizontal centring
    bp = BoundsPen(gs)
    x = 0
    xmin, xmax = 1e9, -1e9
    for n, a in zip(names, adv):
        b = BoundsPen(gs); gs[n].draw(b)
        if b.bounds:
            xmin = min(xmin, x + b.bounds[0]); xmax = max(xmax, x + b.bounds[2])
        x += a + track / sc
    ink_w = xmax - xmin
    ox = cx - (xmin + ink_w / 2) * sc
    oy = cy + cap_h / 2
    out = []
    x = 0
    for n, a in zip(names, adv):
        p = SVGPathPen(gs, ntos=lambda v: ("%.3f" % v).rstrip("0").rstrip("."))
        tp = TransformPen(p, (sc, 0, 0, -sc, ox + x * sc, oy))
        gs[n].draw(tp)
        out.append(p.getCommands())
        x += a + track / sc
    return " ".join(out)
