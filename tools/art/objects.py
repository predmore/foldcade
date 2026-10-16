"""One drawing, two finishes. A mark is a list of parts on the 64-unit grid. The same parts render
as a lit object for the home grid and the hero, and as a flat one-ink symbol for menus, the
collection picker, and the glyph pressed into a collection's face. See docs/iconography.md.

The object recipe: one light from above, a soft-touch body in the mark's colour, recesses cut
into it, dark rubber controls standing on it, screens that glow, and a soft shadow underneath.
It uses gradients, clips, and alpha only, so it ships as an Android vector drawable.

The symbol is every part's outline in one ink, filled even-odd: a recess or a control on a body
becomes a hole, and anything inside a hole is solid again. Parts on separate symbol layers
stack instead of cutting each other.
"""
import math
from art import *
from palette import *

OBJECT_SCALE = 0.88   # objects keep inside the tile's live area, leaving room for the light behind them
SYMBOL_INK = INK

def tone(c, t):
    """Lighter toward white for t > 0, darker toward black for t < 0."""
    c = c.lstrip("#"); rgb = [int(c[i:i + 2], 16) for i in (0, 2, 4)]
    if t >= 0: return "#%02X%02X%02X" % tuple(round(v + (255 - v) * t) for v in rgb)
    return "#%02X%02X%02X" % tuple(round(v * (1 + t)) for v in rgb)

def mix(a, b, t):
    a, b = a.lstrip("#"), b.lstrip("#")
    ca = [int(a[i:i + 2], 16) for i in (0, 2, 4)]; cb = [int(b[i:i + 2], 16) for i in (0, 2, 4)]
    return "#%02X%02X%02X" % tuple(round(x + (y - x) * t) for x, y in zip(ca, cb))

# ---- shapes
def capsule(x1, y1, x2, y2, w):
    """A bar from one point to another, [w] wide, with round ends."""
    dx, dy = x2 - x1, y2 - y1; n = math.hypot(dx, dy); r = w / 2
    ox, oy = -dy / n * r, dx / n * r
    return (f"M{f(x1 + ox)},{f(y1 + oy)} L{f(x2 + ox)},{f(y2 + oy)} A{f(r)},{f(r)} 0 0 0 {f(x2 - ox)},{f(y2 - oy)} "
            f"L{f(x1 - ox)},{f(y1 - oy)} A{f(r)},{f(r)} 0 0 0 {f(x1 + ox)},{f(y1 + oy)} Z")

def plus(cx, cy, arm, w):
    """A d-pad: a cross [arm] from the centre, [w] wide, softly rounded."""
    a, h, r = arm, w / 2, w * 0.28
    return (f"M{f(cx - h)},{f(cy - a + r)} Q{f(cx - h)},{f(cy - a)} {f(cx - h + r)},{f(cy - a)} H{f(cx + h - r)} "
            f"Q{f(cx + h)},{f(cy - a)} {f(cx + h)},{f(cy - a + r)} V{f(cy - h)} H{f(cx + a - r)} "
            f"Q{f(cx + a)},{f(cy - h)} {f(cx + a)},{f(cy - h + r)} V{f(cy + h - r)} Q{f(cx + a)},{f(cy + h)} {f(cx + a - r)},{f(cy + h)} "
            f"H{f(cx + h)} V{f(cy + a - r)} Q{f(cx + h)},{f(cy + a)} {f(cx + h - r)},{f(cy + a)} H{f(cx - h + r)} "
            f"Q{f(cx - h)},{f(cy + a)} {f(cx - h)},{f(cy + a - r)} V{f(cy + h)} H{f(cx - a + r)} "
            f"Q{f(cx - a)},{f(cy + h)} {f(cx - a)},{f(cy + h - r)} V{f(cy - h + r)} Q{f(cx - a)},{f(cy - h)} {f(cx - a + r)},{f(cy - h)} "
            f"H{f(cx - h)} Z")

def arc_band(cx, cy, r, w, a0, a1):
    """A curved button: the band [w] wide along a circle of radius [r], from angle [a0] to [a1]
    degrees (clockwise from east), with round ends."""
    ro, ri, h = r + w / 2, r - w / 2, w / 2
    pt = lambda rad, a: (cx + rad * math.cos(math.radians(a)), cy + rad * math.sin(math.radians(a)))
    large = 1 if (a1 - a0) % 360 > 180 else 0
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = pt(ro, a0), pt(ro, a1), pt(ri, a1), pt(ri, a0)
    return (f"M{f(x0)},{f(y0)} A{f(ro)},{f(ro)} 0 {large} 1 {f(x1)},{f(y1)} A{f(h)},{f(h)} 0 0 1 {f(x2)},{f(y2)} "
            f"A{f(ri)},{f(ri)} 0 {large} 0 {f(x3)},{f(y3)} A{f(h)},{f(h)} 0 0 1 {f(x0)},{f(y0)} Z")

def diamond(cx, cy, off, r):
    """Four round buttons in a diamond, as one path per button."""
    return [circle(cx + dx, cy + dy, r) for dx, dy in ((0, -off), (off, 0), (0, off), (-off, 0))]

def points(d):
    """Every on-curve and control point of a path, after H and V are spelled out."""
    t = re.findall(r"[MLCQAZ]|-?\d*\.?\d+(?:e-?\d+)?", xf(d)); out = []; i = 0; cmd = None
    while i < len(t):
        if t[i].isalpha():
            cmd = t[i]; i += 1
            if cmd == "Z": continue
        n = {"M": 2, "L": 2, "C": 6, "Q": 4, "A": 7}[cmd]
        v = [float(x) for x in t[i:i + n]]; i += n
        out += _arc(out[-1], *v) if cmd == "A" else list(zip(v[0::2], v[1::2]))
    return out

def _arc(start, rx, ry, rot_, large, sweep, x, y, steps=16):
    """Points along an SVG arc, from its endpoint form (SVG spec, appendix F.6)."""
    x1, y1 = start; phi = math.radians(rot_); c, s_ = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x) / 2, (y1 - y) / 2
    xp, yp = c * dx + s_ * dy, -s_ * dx + c * dy
    lam = (xp / rx) ** 2 + (yp / ry) ** 2
    if lam > 1: rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
    k = math.sqrt(max(0.0, num / (rx * rx * yp * yp + ry * ry * xp * xp))) * (-1 if large == sweep else 1)
    cxp, cyp = k * rx * yp / ry, -k * ry * xp / rx
    cx, cy = c * cxp - s_ * cyp + (x1 + x) / 2, s_ * cxp + c * cyp + (y1 + y) / 2
    ang = lambda ux, uy: math.atan2(uy, ux)
    t0 = ang((xp - cxp) / rx, (yp - cyp) / ry)
    dt = ang((-xp - cxp) / rx, (-yp - cyp) / ry) - t0
    if sweep and dt < 0: dt += 2 * math.pi
    if not sweep and dt > 0: dt -= 2 * math.pi
    pts = []
    for i in range(1, steps + 1):
        t = t0 + dt * i / steps
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        pts.append((c * ex - s_ * ey + cx, s_ * ex + c * ey + cy))
    return pts

def bounds(d):
    """The top and bottom of a path, for lighting a part from one to the other."""
    ys = [y for _, y in points(d)]
    return (min(ys), max(ys))

# ---- parts
class Part:
    """kind: body, recess, screen, control, shadow, paint (object-only layers), or line (a symbol-only stroke).
    sym: the symbol layer it belongs to, or None to leave it out of the symbol. obj: False to leave it
    out of the object. round: a symbol fill's corner rounding, as a stroke in its own ink."""
    def __init__(s, kind, d, color=None, sym=0, obj=True, gloss=0.3, y=None, round=0.0):
        s.kind, s.d, s.color, s.sym, s.obj, s.gloss, s.round = kind, d, color, sym, obj, gloss, round
        s.y = y or (bounds(d) if isinstance(d, str) else (0, 64))

def body(d, color, gloss=0.3, sym=0, obj=True): return Part("body", d, color, sym, obj, gloss)
def recess(d, base, sym=0, obj=True): return Part("recess", d, base, sym, obj)
def screen(d, color=GLASS, sym=None, obj=True): return Part("screen", d, color, sym, obj)
def control(d, color=RUBBER, sym=0, obj=True): return Part("control", d, color, sym, obj)
def shadow(cx, cy, rx, ry, a=0.55): return Part("shadow", (cx, cy, rx, ry, a), sym=None, y=(cy - ry, cy + ry))
def paint(*layers): return Part("paint", list(layers), sym=None)
def outline(d, sym=0, round=0.0): return Part("body", d, None, sym, obj=False, round=round)
def line(d, w, sym=1): return Part("line", d, None, sym, obj=False, round=w)

def _lin(y0, y1, stops):
    return Lin(0, y0, 0, y1 if y1 > y0 else y0 + 0.01, stops)

def object_layers(parts, scale=OBJECT_SCALE):
    out = []
    for p in parts:
        if not p.obj: continue
        y0, y1 = p.y
        if p.kind == "shadow":
            cx, cy, rx, ry, a = p.d
            out.append(L(circle(cx, cy, rx), fill=Rad(cx, cy, rx, [(0, BLACK, a), (0.6, BLACK, a * 0.45), (1, BLACK, 0)]),
                         scale=(1, ry / rx, cx, cy)))
        elif p.kind == "paint":
            out += p.d
        elif p.kind == "body":
            c, h = p.color, y1 - y0
            out += [L(p.d, fill=_lin(y0, y1, [(0, tone(c, 0.28), 1), (0.45, c, 1), (1, tone(c, -0.38), 1)]))]
            if p.gloss: out.append(L(p.d, fill=_lin(y0, y0 + h * 0.5, [(0, "#FFFFFF", p.gloss), (1, "#FFFFFF", 0)])))
            out.append(L(p.d, stroke=_lin(y0, y0 + h * 0.6, [(0, "#FFFFFF", 0.65), (1, "#FFFFFF", 0)]), sw=0.9))
        elif p.kind == "recess":
            out += [L(p.d, fill=_lin(y0, y1, [(0, WELL, 1), (1, tone(p.color, -0.72), 1)])),
                    L(p.d, stroke=_lin(y0, y1, [(0, "#FFFFFF", 0), (0.7, "#FFFFFF", 0), (1, "#FFFFFF", 0.35)]), sw=0.8)]
        elif p.kind == "screen":
            h = y1 - y0
            sheen = f"M0,{f(y0)} H64 V{f(y0 + h * 0.18)} L0,{f(y0 + h * 0.62)} Z"
            out += [L(p.d, fill=_lin(y0, y1, [(0, tone(p.color, 0.45), 1), (1, p.color, 1)])),
                    G([L(sheen, fill="#FFFFFF", alpha=0.22)], clip=p.d)]
        elif p.kind == "control":
            out += [L(xf(p.d, dy=1.1), fill=BLACK, alpha=0.38),
                    L(p.d, fill=_lin(y0, y1, [(0, tone(p.color, 0.35), 1), (1, tone(p.color, -0.25), 1)]))]
    return [G(out, scale=(scale, scale), pivot=(32, 32))] if scale != 1 else out

def symbol_layers(parts, color=SYMBOL_INK):
    """Each symbol layer is one even-odd fill, so a part inside another cuts it, plus any lines on it."""
    out = []
    for layer in sorted({p.sym for p in parts if p.sym is not None and p.kind != "shadow"}):
        mine = [p for p in parts if p.sym == layer]
        fills = [p for p in mine if p.kind not in ("line", "shadow", "paint")]
        if fills:
            r = max(p.round for p in fills)
            out.append(L("".join(p.d for p in fills), fill=color, evenodd=True,
                         stroke=color if r else None, sw=r))
        for p in mine:
            if p.kind == "line": out.append(L(p.d, stroke=color, sw=p.round))
    return out
