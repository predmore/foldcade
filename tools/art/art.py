"""Tiny shared model: one description -> SVG source + Android VectorDrawable."""
import math, re
from xml.sax.saxutils import escape

def f(v):
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s

def hexa(color, alpha=1.0):
    c = color.lstrip("#")
    return "#%02X%s" % (round(max(0, min(1, alpha)) * 255), c.upper())

class Lin:
    def __init__(s, x1, y1, x2, y2, stops):
        s.x1, s.y1, s.x2, s.y2, s.stops = x1, y1, x2, y2, stops
class Rad:
    def __init__(s, cx, cy, r, stops):
        s.cx, s.cy, s.r, s.stops = cx, cy, r, stops

def glow_stops(color, peak, n=10, core=0.0):
    """Gaussian-like falloff that reaches exactly 0 at the rim (no ring)."""
    out = []
    for i in range(n + 1):
        t = i / n
        if t <= core:
            a = peak
        else:
            u = (t - core) / (1 - core)
            a = peak * math.exp(-4.2 * u * u) * (1 - u) ** 1.5
        out.append((t, color, a))
    out[-1] = (1.0, color, 0.0)
    return out

class L:
    """A layer: path + paint. scale=(sx, sy, px, py) wraps it in a scaling group."""
    def __init__(s, d, fill=None, stroke=None, sw=0, alpha=1.0, cap="round", join="round",
                 evenodd=False, scale=None, salpha=1.0):
        s.d, s.fill, s.stroke, s.sw, s.alpha, s.cap, s.join = d, fill, stroke, sw, alpha, cap, join
        s.evenodd, s.scale, s.salpha = evenodd, scale, salpha

# ---- geometry
def rrect(x, y, w, h, r):
    if isinstance(r, (int, float)):
        r = (r, r, r, r)
    tl, tr, br, bl = [min(v, w / 2, h / 2) for v in r]
    p = [f"M{f(x+tl)},{f(y)}", f"H{f(x+w-tr)}"]
    if tr: p.append(f"A{f(tr)},{f(tr)} 0 0 1 {f(x+w)},{f(y+tr)}")
    p.append(f"V{f(y+h-br)}")
    if br: p.append(f"A{f(br)},{f(br)} 0 0 1 {f(x+w-br)},{f(y+h)}")
    p.append(f"H{f(x+bl)}")
    if bl: p.append(f"A{f(bl)},{f(bl)} 0 0 1 {f(x)},{f(y+h-bl)}")
    p.append(f"V{f(y+tl)}")
    if tl: p.append(f"A{f(tl)},{f(tl)} 0 0 1 {f(x+tl)},{f(y)}")
    p.append("Z")
    return "".join(p)

def circle(cx, cy, r):
    return (f"M{f(cx-r)},{f(cy)}A{f(r)},{f(r)} 0 1 1 {f(cx+r)},{f(cy)}"
            f"A{f(r)},{f(r)} 0 1 1 {f(cx-r)},{f(cy)}Z")

def circle_ccw(cx, cy, r):
    return (f"M{f(cx-r)},{f(cy)}A{f(r)},{f(r)} 0 1 0 {f(cx+r)},{f(cy)}"
            f"A{f(r)},{f(r)} 0 1 0 {f(cx-r)},{f(cy)}Z")

def poly(pts, close=True):
    s = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)
    return s + ("Z" if close else "")

def rot(points, cx, cy, deg):
    a = math.radians(deg); ca, sa = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in points]

# ---- path transforms (absolute M L H V C Q A Z only)
_TOK = re.compile(r"[MLHVCQAZ]|-?\d*\.?\d+(?:e-?\d+)?")

def xf(d, s=1.0, deg=0.0, dx=0.0, dy=0.0, cx=32.0, cy=32.0):
    """Scale by [s] and turn by [deg] about (cx, cy), then shift. Uniform, so arcs stay arcs."""
    a = math.radians(deg); ca, sa = math.cos(a) * s, math.sin(a) * s
    def p(x, y):
        x, y = x - cx, y - cy
        return f"{f(cx + x * ca - y * sa + dx)},{f(cy + x * sa + y * ca + dy)}"
    t = _TOK.findall(d); out = []; i = 0; cur = (0.0, 0.0); cmd = None
    def num():
        nonlocal i
        v = float(t[i]); i += 1; return v
    while i < len(t):
        if t[i].isalpha():
            cmd = t[i]; i += 1
            if cmd == "Z":
                out.append("Z"); continue
        if cmd in "ML":
            x, y = num(), num(); out.append(f"{cmd}{p(x, y)}"); cur = (x, y)
        elif cmd == "H":
            x = num(); cur = (x, cur[1]); out.append(f"L{p(*cur)}")
        elif cmd == "V":
            y = num(); cur = (cur[0], y); out.append(f"L{p(*cur)}")
        elif cmd == "C":
            v = [num() for _ in range(6)]; out.append(f"C{p(v[0], v[1])} {p(v[2], v[3])} {p(v[4], v[5])}"); cur = (v[4], v[5])
        elif cmd == "Q":
            v = [num() for _ in range(4)]; out.append(f"Q{p(v[0], v[1])} {p(v[2], v[3])}"); cur = (v[2], v[3])
        elif cmd == "A":
            rx, ry, r, la, sw, x, y = [num() for _ in range(7)]
            out.append(f"A{f(rx * s)},{f(ry * s)} {f(r + deg)} {int(la)} {int(sw)} {p(x, y)}"); cur = (x, y)
    return "".join(out)

def ellipse(cx, cy, rx, ry, deg=0.0):
    k = 0.5523
    pts = [(rx, 0), (rx, ry * k), (rx * k, ry), (0, ry), (-rx * k, ry), (-rx, ry * k), (-rx, 0), (-rx, -ry * k),
           (-rx * k, -ry), (0, -ry), (rx * k, -ry), (rx, -ry * k)]
    a = math.radians(deg); ca, sa = math.cos(a), math.sin(a)
    q = [f"{f(cx + x * ca - y * sa)},{f(cy + x * sa + y * ca)}" for x, y in pts]
    return f"M{q[0]}C{q[1]} {q[2]} {q[3]}C{q[4]} {q[5]} {q[6]}C{q[7]} {q[8]} {q[9]}C{q[10]} {q[11]} {q[0]}Z"

class G:
    """A group: its layers drawn inside an optional clip path, then scaled, turned, and shifted together
    as Android does: about the pivot, scale first, then rotate, then translate."""
    def __init__(s, layers, clip=None, scale=(1.0, 1.0), rotate=0.0, pivot=(0.0, 0.0), shift=(0.0, 0.0)):
        s.layers, s.clip, s.scale, s.rotate, s.pivot, s.shift = layers, clip, scale, rotate, pivot, shift


def halo(cx, cy, r, color, peak, sx=1.0, sy=1.0, core=0.0):
    return L(circle(cx, cy, r), fill=Rad(cx, cy, r, glow_stops(color, peak, core=core)),
             scale=(sx, sy, cx, cy) if (sx != 1 or sy != 1) else None)

# ---- emitters
class Asset:
    def __init__(s, name, w, h, layers, dp=None, kind="glyph", note="", tint=False):
        s.name, s.w, s.h, s.layers = name, w, h, layers
        s.dp = dp or (w, h)
        s.kind, s.note, s.tint = kind, note, tint

def _svg_paint(p, defs, ctr):
    if p is None:
        return "none"
    if isinstance(p, str):
        return p
    gid = f"{ctr[1]}g{ctr[0]}"; ctr[0] += 1
    stops = "".join(f'<stop offset="{f(o)}" stop-color="{c}" stop-opacity="{f(a)}"/>' for o, c, a in p.stops)
    if isinstance(p, Lin):
        defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(p.x1)}" y1="{f(p.y1)}" x2="{f(p.x2)}" y2="{f(p.y2)}">{stops}</linearGradient>')
    else:
        defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(p.cx)}" cy="{f(p.cy)}" r="{f(p.r)}">{stops}</radialGradient>')
    return f"url(#{gid})"

def _group_transform(g):
    (sx, sy), (px, py), (tx, ty) = g.scale, g.pivot, g.shift
    parts = [f"translate({f(tx + px)} {f(ty + py)})"]
    if g.rotate: parts.append(f"rotate({f(g.rotate)})")
    if (sx, sy) != (1, 1): parts.append(f"scale({f(sx)} {f(sy)})")
    parts.append(f"translate({f(-px)} {f(-py)})")
    return " ".join(parts)

def _svg_layers(layers, defs, ctr):
    body = []
    for l in layers:
        if isinstance(l, G):
            attrs = f' transform="{_group_transform(l)}"'
            inner = "".join(_svg_layers(l.layers, defs, ctr))
            if l.clip:
                cid = f"{ctr[1]}c{ctr[0]}"; ctr[0] += 1
                defs.append(f'<clipPath id="{cid}"><path d="{l.clip}"/></clipPath>')
                inner = f'<g clip-path="url(#{cid})">{inner}</g>'
            body.append(f"<g{attrs}>{inner}</g>")
            continue
        fill = _svg_paint(l.fill, defs, ctr)
        stroke = _svg_paint(l.stroke, defs, ctr)
        attrs = f'd="{l.d}" fill="{fill}"'
        if l.alpha != 1: attrs += f' fill-opacity="{f(l.alpha)}"'
        if l.evenodd: attrs += ' fill-rule="evenodd"'
        if l.stroke is not None:
            attrs += f' stroke="{stroke}" stroke-width="{f(l.sw)}" stroke-linecap="{l.cap}" stroke-linejoin="{l.join}"'
            if l.salpha != 1: attrs += f' stroke-opacity="{f(l.salpha)}"'
        el = f"<path {attrs}/>"
        if l.scale:
            sx, sy, cx, cy = l.scale
            el = f'<g transform="translate({f(cx)} {f(cy)}) scale({f(sx)} {f(sy)}) translate({f(-cx)} {f(-cy)})">{el}</g>'
        body.append(el)
    return body

def to_svg(a, px=None, bg=None, ids=""):
    """[ids] prefixes gradient and clip ids, so many drawings can share one HTML page."""
    defs, ctr = [], [0, ids]
    body = _svg_layers(a.layers, defs, ctr)
    W, H = (px or a.w), (px or a.h) * a.h / a.w if px else a.h
    bgr = f'<rect width="{f(a.w)}" height="{f(a.h)}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{f(W)}" height="{f(H)}" viewBox="0 0 {f(a.w)} {f(a.h)}">'
            f'<!-- Foldcade original art, MIT licensed. {escape(a.note)} -->'
            f'<defs>{"".join(defs)}</defs>{bgr}{"".join(body)}</svg>\n')

def _vd_grad(attr, p, ind):
    items = "\n".join(f'{ind}        <item android:offset="{f(o)}" android:color="{hexa(c, a)}" />' for o, c, a in p.stops)
    if isinstance(p, Lin):
        g = (f'<gradient android:type="linear" android:startX="{f(p.x1)}" android:startY="{f(p.y1)}" '
             f'android:endX="{f(p.x2)}" android:endY="{f(p.y2)}">')
    else:
        g = (f'<gradient android:type="radial" android:centerX="{f(p.cx)}" android:centerY="{f(p.cy)}" '
             f'android:gradientRadius="{f(p.r)}">')
    return (f'{ind}    <aapt:attr name="android:{attr}">\n{ind}      {g}\n{items}\n'
            f'{ind}      </gradient>\n{ind}    </aapt:attr>\n')

def _vd_path(l, ind):
    attrs = [f'android:pathData="{l.d}"']
    if isinstance(l.fill, str): attrs.append(f'android:fillColor="{hexa(l.fill)}"')
    if l.alpha != 1: attrs.append(f'android:fillAlpha="{f(l.alpha)}"')
    if l.evenodd: attrs.append('android:fillType="evenOdd"')
    if l.stroke is not None:
        if isinstance(l.stroke, str): attrs.append(f'android:strokeColor="{hexa(l.stroke)}"')
        attrs += [f'android:strokeWidth="{f(l.sw)}"', f'android:strokeLineCap="{l.cap}"',
                  f'android:strokeLineJoin="{l.join}"']
        if l.salpha != 1: attrs.append(f'android:strokeAlpha="{f(l.salpha)}"')
    grads = ""
    if l.fill is not None and not isinstance(l.fill, str): grads += _vd_grad("fillColor", l.fill, ind)
    if l.stroke is not None and not isinstance(l.stroke, str): grads += _vd_grad("strokeColor", l.stroke, ind)
    a_s = ("\n" + ind + "    ").join(attrs)
    if grads:
        return f"{ind}<path\n{ind}    {a_s}>\n{grads}{ind}</path>\n"
    return f"{ind}<path\n{ind}    {a_s} />\n"

def _vd_layers(layers, ind):
    out = []
    for l in layers:
        if isinstance(l, G):
            (sx, sy), (px, py), (tx, ty) = l.scale, l.pivot, l.shift
            attrs = []
            if (px, py) != (0, 0): attrs += [f'android:pivotX="{f(px)}"', f'android:pivotY="{f(py)}"']
            if sx != 1: attrs.append(f'android:scaleX="{f(sx)}"')
            if sy != 1: attrs.append(f'android:scaleY="{f(sy)}"')
            if l.rotate: attrs.append(f'android:rotation="{f(l.rotate)}"')
            if tx: attrs.append(f'android:translateX="{f(tx)}"')
            if ty: attrs.append(f'android:translateY="{f(ty)}"')
            head = f"{ind}<group" + ("".join(f"\n{ind}    {a}" for a in attrs)) + ">\n"
            clip = f'{ind}    <clip-path android:pathData="{l.clip}" />\n' if l.clip else ""
            out.append(head + clip + "".join(_vd_layers(l.layers, ind + "    ")) + f"{ind}</group>\n")
            continue
        if l.scale:
            sx, sy, cx, cy = l.scale
            out.append(f'{ind}<group android:pivotX="{f(cx)}" android:pivotY="{f(cy)}" '
                       f'android:scaleX="{f(sx)}" android:scaleY="{f(sy)}">\n' + _vd_path(l, ind + "    ") + f"{ind}</group>\n")
        else:
            out.append(_vd_path(l, ind))
    return out

def to_vd(a):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- Foldcade original art, MIT licensed (see LICENSE). {escape(a.note)} -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           f'    android:width="{f(a.dp[0])}dp"', f'    android:height="{f(a.dp[1])}dp"',
           f'    android:viewportWidth="{f(a.w)}"', f'    android:viewportHeight="{f(a.h)}">']
    out.append("".join(_vd_layers(a.layers, "    ")).rstrip("\n"))
    out.append("</vector>\n")
    return "\n".join(out)
