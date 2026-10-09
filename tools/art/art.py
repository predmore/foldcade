"""Tiny shared model: one description -> SVG source + Android VectorDrawable."""
import math
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

def halo(cx, cy, r, color, peak, sx=1.0, sy=1.0, core=0.0):
    return L(circle(cx, cy, r), fill=Rad(cx, cy, r, glow_stops(color, peak, core=core)),
             scale=(sx, sy, cx, cy) if (sx != 1 or sy != 1) else None)

# ---- emitters
class Asset:
    def __init__(s, name, w, h, layers, dp=None, kind="glyph", note=""):
        s.name, s.w, s.h, s.layers = name, w, h, layers
        s.dp = dp or (w, h)
        s.kind, s.note = kind, note

def _svg_paint(p, defs, key, ctr):
    if p is None:
        return "none"
    if isinstance(p, str):
        return p
    gid = f"g{ctr[0]}"; ctr[0] += 1
    stops = "".join(f'<stop offset="{f(o)}" stop-color="{c}" stop-opacity="{f(a)}"/>' for o, c, a in p.stops)
    if isinstance(p, Lin):
        defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(p.x1)}" y1="{f(p.y1)}" x2="{f(p.x2)}" y2="{f(p.y2)}">{stops}</linearGradient>')
    else:
        defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(p.cx)}" cy="{f(p.cy)}" r="{f(p.r)}">{stops}</radialGradient>')
    return f"url(#{gid})"

def to_svg(a, px=None, bg=None):
    defs, body, ctr = [], [], [0]
    for l in a.layers:
        fill = _svg_paint(l.fill, defs, "f", ctr)
        stroke = _svg_paint(l.stroke, defs, "s", ctr)
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
    W, H = (px or a.w), (px or a.h) * a.h / a.w if px else a.h
    bgr = f'<rect width="{f(a.w)}" height="{f(a.h)}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{f(W)}" height="{f(H)}" viewBox="0 0 {f(a.w)} {f(a.h)}">'
            f'<!-- Foldcade original art, CC BY-SA 4.0. {escape(a.note)} -->'
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

def to_vd(a):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- Foldcade original art, CC BY-SA 4.0 (see LICENSE). {escape(a.note)} -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           f'    android:width="{f(a.dp[0])}dp"', f'    android:height="{f(a.dp[1])}dp"',
           f'    android:viewportWidth="{f(a.w)}"', f'    android:viewportHeight="{f(a.h)}">']
    for l in a.layers:
        ind = "    "
        pre = post = ""
        if l.scale:
            sx, sy, cx, cy = l.scale
            pre = (f'    <group android:pivotX="{f(cx)}" android:pivotY="{f(cy)}" '
                   f'android:scaleX="{f(sx)}" android:scaleY="{f(sy)}">\n')
            post = "    </group>\n"
            ind = "        "
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
            el = f"{ind}<path\n{ind}    {a_s}>\n{grads}{ind}</path>\n"
        else:
            el = f"{ind}<path\n{ind}    {a_s} />\n"
        out.append((pre + el + post).rstrip("\n"))
    out.append("</vector>\n")
    return "\n".join(out)
