"""Afterglow collection art (64-unit). A tinted folder card for the collections a player makes, and the
theme glyphs they can wear on its face. Original drawings, no logos.

A collection is drawn from three parts, so one set of files serves every color:
- mark_collection_body: the folder, halo and rims in greys. The host multiplies it by the tint
  (Modulate), so white becomes the tint and grey a darker shade of it.
- mark_collection_face: the parts that never take the tint: gloss, the dark well on the front.
- the glyph (mark_<name>, or any platform mark), drawn into the well at WELL.
"""
import math, re
from art import *
from palette import *
from platforms import glow, mix, hi, lo, rot, GOLD, LIME, GREEN, SKY, RED, CRIMSON, SILVER, SUNSET, LAVENDER, INDIGO

# ---- the ten tints. Each is the colour white on the body becomes; all sit well on true black.
TINTS = {
    "gold": "#FFC15A", "coral": "#FF7F5C", "rose": "#FF5C8A", "violet": "#B48CFF", "indigo": "#7C8CFF",
    "sky": "#4CC9FF", "teal": "#3EE6C0", "mint": "#8CF2A6", "lime": "#C6EE5A", "slate": "#A3B1C6",
}

# ---- path helpers (absolute M L H V C Q A Z only)
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

# ---- glyph material: a glossy enamel object with a lit rim and a soft drop shadow
def jewel(d, c, y0, y1, sw=1.3, rim=None, top=None, bot=None, shade=True, gloss=0.5, shadow=0.55):
    top = top or hi(c); bot = bot or lo(c); rim = rim or c
    out = []
    if shadow:
        out.append(L(xf(d, dx=0.7, dy=1.7), fill="#000000", stroke="#000000", sw=sw * 2, alpha=shadow, salpha=shadow))
    if sw:
        out.append(L(d, stroke=Lin(0, y0, 0, y1, [(0, "#FFFFFF", 0.95), (0.35, rim, 1), (1, lo(rim), 1)]), sw=sw * 2))
    out += [L(d, fill=Lin(0, y0, 0, y1, [(0, top, 1), (0.5, c, 1), (1, bot, 1)]) if shade else c)]
    if gloss:
        out.append(L(d, fill=Lin(0, y0, 0, y0 + (y1 - y0) * 0.55, [(0, "#FFFFFF", gloss), (1, "#FFFFFF", 0)])))
    return out

def ink(d, sw, c="#0B0C10", a=0.85):
    return L(d, stroke=c, sw=sw, salpha=a)

# ---- theme glyphs
def star():
    c = GOLD
    pts = []
    for i in range(10):
        r = 25 if i % 2 == 0 else 11
        a = math.radians(-90 + i * 36)
        pts.append((32 + r * math.cos(a), 34 + r * math.sin(a)))
    out = [glow(c, 0.34), *jewel(poly(pts), c, 9, 59, sw=1.5, gloss=0.35)]
    # bevelled arms: the half of each arm facing the light is brighter
    for i in range(0, 10, 2):
        tip, left, right = pts[i], pts[i - 1], pts[(i + 1) % 10]
        lit = math.cos(math.radians(-90 + i * 36) + math.radians(45)) < 0
        out += [L(poly([(32, 34), left, tip]), fill="#FFFFFF" if lit else "#7A3E00", alpha=0.2 if lit else 0.14),
                L(poly([(32, 34), tip, right]), fill="#7A3E00" if lit else "#FFFFFF", alpha=0.14 if lit else 0.2)]
    return out

def heart():
    c = "#FF4F6E"
    d = ("M32,54 C24,47 9,38 9,25 C9,16.8 15,11 22.2,11 C26.6,11 30,13.4 32,17 "
         "C34,13.4 37.4,11 41.8,11 C49,11 55,16.8 55,25 C55,38 40,47 32,54 Z")
    return [glow(c, 0.34), *jewel(d, c, 11, 54, sw=1.5),
            L("M15.6,22.4 C16.2,18.6 19,16.4 22.4,16.4", stroke="#FFFFFF", sw=2.2, salpha=0.8)]

def trophy():
    c = "#FFB547"
    cup = "M17,9 H47 V21 C47,32 40.4,38.6 32,38.6 C23.6,38.6 17,32 17,21 Z"
    handles = ("M18,14 H11.6 C9.6,14 8.6,15.6 9,18.4 C9.8,24.8 13.4,29.2 19.6,30.2 "
               "M46,14 H52.4 C54.4,14 55.4,15.6 55,18.4 C54.2,24.8 50.6,29.2 44.4,30.2")
    stem = "M28.6,38 H35.4 L36.6,46 H27.4 Z"
    base = rrect(19, 46, 26, 9, 2.4)
    return [glow(c, 0.32),
            L(xf(handles, dx=0.7, dy=1.6), stroke="#000000", sw=4.4, salpha=0.5),
            L(handles, stroke=Lin(0, 14, 0, 31, [(0, hi(c), 1), (1, lo(c), 1)]), sw=3.4),
            L(handles, stroke="#FFFFFF", sw=0.8, salpha=0.35),
            *jewel(stem, c, 38, 46, sw=1, gloss=0.3),
            *jewel(base, "#3A3F4A", 46, 55, sw=1.1, rim=c, top="#4A505C", bot="#14161B", gloss=0.25),
            L(rrect(25, 49.2, 14, 2.6, 1.3), fill=c, alpha=0.85),
            *jewel(cup, c, 9, 38.6, sw=1.4),
            L("M21.6,13 V21 C21.6,25.4 23,28.6 25.4,31", stroke="#FFFFFF", sw=2.2, salpha=0.6)]

def sword():
    blade_c, guard_c, grip_c = SILVER, GOLD, "#9B7BFF"
    blade = "M32,3.5 L36.2,10 V40 H27.8 V10 Z"
    T = lambda d: xf(d, s=1.02, deg=45)
    return [glow("#9FC2FF", 0.32),
            *jewel(T(rrect(29.2, 44, 5.6, 11, 2)), grip_c, 26, 58, sw=1, gloss=0.3),
            L(T("M29.6,47.2 H34.4 M29.6,50.4 H34.4"), stroke="#1A1030", sw=0.9, salpha=0.6),
            *jewel(T(circle(32, 57.6, 3.6)), guard_c, 40, 62, sw=1),
            *jewel(T(blade), blade_c, 0, 42, sw=1.3, top="#FFFFFF", bot="#7E8A9E"),
            L(T("M32,8 V38"), stroke="#FFFFFF", sw=1.1, salpha=0.75),
            L(T("M32,8 V38"), stroke="#5A6576", sw=0.5, salpha=0.5),
            *jewel(T(rrect(19.5, 39.4, 25, 5.6, 2.8)), guard_c, 30, 46, sw=1.1),
            L(T(circle(32, 42.2, 1.5)), fill="#FF4D5A")]

def shield():
    c = INDIGO
    d = "M32,6 L53,12.6 V30 C53,43.4 44,51.6 32,57.6 C20,51.6 11,43.4 11,30 V12.6 Z"
    inner = "M32,11.4 L48.2,16.4 V30 C48.2,40.6 41.4,47.6 32,52.4 C22.6,47.6 15.8,40.6 15.8,30 V16.4 Z"
    return [glow(c, 0.34), *jewel(d, SILVER, 6, 57.6, sw=1.4, rim=c, top="#FFFFFF", bot="#6E7A8E"),
            L(inner, fill=Lin(0, 11, 0, 52, [(0, hi(c), 1), (0.45, c, 1), (1, lo(c), 1)])),
            L(inner, fill=Lin(0, 11, 0, 32, [(0, "#FFFFFF", 0.4), (1, "#FFFFFF", 0)])),
            L("M20.4,31 L32,23.4 L43.6,31", stroke="#000000", sw=5.2, salpha=0.35),
            L("M20.4,30 L32,22.4 L43.6,30", stroke=Lin(0, 22, 0, 31, [(0, "#FFFFFF", 1), (1, "#D6DCFF", 1)]), sw=3.6),
            L("M20.4,40 L32,32.4 L43.6,40", stroke="#000000", sw=5.2, salpha=0.35),
            L("M20.4,39 L32,31.4 L43.6,39", stroke=Lin(0, 31, 0, 40, [(0, "#FFFFFF", 1), (1, "#D6DCFF", 1)]), sw=3.6)]

def racing_flag():
    c = "#E8ECF2"
    def wave(x):  # vertical offset of the cloth at x
        return 2.6 * math.sin((x - 16) / 38 * 2 * math.pi)
    x0, x1, y0, y1 = 17, 55, 10, 36
    def edge(y, xs): return [(x, y + wave(x)) for x in xs]
    xs = [x0 + (x1 - x0) * i / 24 for i in range(25)]
    cloth = poly(edge(y0, xs) + edge(y1, xs[::-1]))
    out = [glow("#C9F7FF", 0.28),
           L("M14.6,9 V59", stroke="#000000", sw=5, salpha=0.5),
           L("M14,8 V58", stroke=Lin(12, 0, 16, 0, [(0, "#FFFFFF", 1), (1, "#6E7A8E", 1)]), sw=3.4),
           L(circle(14, 7.4, 2.6), fill=Rad(13, 6.4, 3.4, [(0, "#FFFFFF", 1), (1, "#8A96AA", 1)])),
           *jewel(cloth, c, 8, 39, sw=1.2, top="#FFFFFF", bot="#B7BFCC", gloss=0)]
    cols, rows = 6, 4
    cw, rh = (x1 - x0) / cols, (y1 - y0) / rows
    for r in range(rows):
        for k in range(cols):
            if (r + k) % 2: continue
            xa, xb = x0 + k * cw, x0 + (k + 1) * cw
            ya, yb = y0 + r * rh, y0 + (r + 1) * rh
            sx = [xa + (xb - xa) * i / 4 for i in range(5)]
            sq = poly([(x, ya + wave(x)) for x in sx] + [(x, yb + wave(x)) for x in sx[::-1]])
            out.append(L(sq, fill=Lin(0, y0, 0, y1, [(0, "#2A2E38", 1), (1, "#0B0C10", 1)])))
    out.append(L(cloth, fill=Lin(0, 6, 0, 24, [(0, "#FFFFFF", 0.35), (1, "#FFFFFF", 0)])))
    return out

def puzzle():
    c = LIME
    d = ("M12,18 H25.03 A6,6 0 1 1 32.97,18 H46 V31.03 A6,6 0 1 1 46,38.97 V52 H32.97 "
         "A6,6 0 1 0 25.03,52 H12 Z")
    d = xf(d, s=1.06, dx=-2.2, dy=1.4)
    return [glow(c, 0.32), *jewel(d, c, 6, 56, sw=1.5)]

def ghost():
    c = LAVENDER
    d = ("M13,30 C13,17.6 21.4,9 32,9 C42.6,9 51,17.6 51,30 V52 C51,55 48.4,56 46.2,54 L42.6,50.6 "
         "C41.2,49.4 39.6,49.4 38.4,50.6 L35,54 C33.4,55.6 30.6,55.6 29,54 L25.6,50.6 C24.4,49.4 22.8,49.4 21.4,50.6 "
         "L17.8,54 C15.6,56 13,55 13,52 Z")
    return [glow(c, 0.36), *jewel(d, c, 9, 56, sw=1.4, top="#FFFFFF", bot=mix(c, "#3A2A70", 0.35)),
            L(ellipse(25, 28.6, 3.6, 5), fill="#12091F"), L(ellipse(39, 28.6, 3.6, 5), fill="#12091F"),
            L(circle(26.2, 26.6, 1.3), fill="#FFFFFF"), L(circle(40.2, 26.6, 1.3), fill="#FFFFFF"),
            L(ellipse(32, 38.4, 2.6, 1.8), fill="#12091F", alpha=0.8)]

def crown():
    c = "#FFD24C"
    body = "M11,44 L8.6,19 L21.4,30.4 L32,12.4 L42.6,30.4 L55.4,19 L53,44 Z"
    band = rrect(10, 42, 44, 10.4, 2.6)
    return [glow(c, 0.32), *jewel(body, c, 12, 44, sw=1.4),
            *jewel(circle(8.6, 18.2, 3), c, 15, 21, sw=0.8), *jewel(circle(32, 11.2, 3.2), c, 8, 14.4, sw=0.8),
            *jewel(circle(55.4, 18.2, 3), c, 15, 21, sw=0.8),
            *jewel(band, c, 42, 52.4, sw=1.4),
            *jewel(ellipse(32, 47.2, 3.6, 3), "#FF4D5A", 44, 50, sw=0.6, shadow=0.3),
            *jewel(circle(20.6, 47.2, 2.4), "#4CC9FF", 45, 49.6, sw=0.6, shadow=0.3),
            *jewel(circle(43.4, 47.2, 2.4), "#3EE68A", 45, 49.6, sw=0.6, shadow=0.3)]

def rocket():
    c = SUNSET
    T = lambda d: xf(d, s=0.98, deg=40, dx=1, dy=-1)
    body = "M32,4 C41,10.6 43,24 41.6,41 H22.4 C21,24 23,10.6 32,4 Z"
    fin_l = "M23,28.6 L13.6,40.6 V49 L22.6,43.4 Z"; fin_r = "M41,28.6 L50.4,40.6 V49 L41.4,43.4 Z"
    flame = "M25.4,44.6 C24.6,50.8 28,55.6 32,61.6 C36,55.6 39.4,50.8 38.6,44.6 Z"
    core = "M28.6,45 C29.2,49.6 30.6,52.4 32,55.6 C33.4,52.4 34.8,49.6 35.4,45 Z"
    fx, fy = rot([(32, 50)], 32, 32, 40)[0]
    return [glow(c, 0.32, cx=30, cy=34), halo(fx + 1, fy - 1, 12, "#FF8A3D", 0.55),
            L(T(flame), fill=Lin(40, 40, 18, 58, [(0, "#FFE08A", 1), (0.5, "#FF8A3D", 1), (1, "#FF3B2E", 0.6)])),
            L(T(core), fill="#FFF4C8", alpha=0.9),
            *jewel(T(fin_l), "#FF4D5A", 14, 50, sw=1), *jewel(T(fin_r), "#FF4D5A", 14, 50, sw=1),
            *jewel(T(body), "#E8ECF2", 4, 41, sw=1.3, rim=c, top="#FFFFFF", bot="#8A96AA"),
            *jewel(T(circle(32, 21.4, 5.4)), "#4C8DFF", 16, 27, sw=1.1, rim="#3A3F4A", top="#C6EEFF", bot="#1E3C8A", shadow=0),
            L(T(rrect(25.6, 40.4, 12.8, 4.6, 1.6)), fill="#3A3F4A", stroke="#FFFFFF", sw=0.5, salpha=0.3)]

def mushroom():
    c = "#FF4D5A"
    cap = "M7,35 C7,19 18,8 32,8 C46,8 57,19 57,35 C57,38.4 55,39.6 51.6,39.6 H12.4 C9,39.6 7,38.4 7,35 Z"
    stem = "M21.6,39 H42.4 C43.6,47 42.4,53.6 38.4,56 H25.6 C21.6,53.6 20.4,47 21.6,39 Z"
    spots = [(32, 16.6, 5.4), (17.6, 27.4, 4.4), (46.4, 27.4, 4.4), (32, 31, 2.6)]
    out = [glow(c, 0.32), *jewel(stem, "#F4E9D4", 39, 56, sw=1.2, rim="#FFB59E", top="#FFFFFF", bot="#C9B79A"),
           L(ellipse(27.6, 46.4, 1.6, 2.6), fill="#2A1A10"), L(ellipse(36.4, 46.4, 1.6, 2.6), fill="#2A1A10"),
           *jewel(cap, c, 8, 40, sw=1.5)]
    for x, y, r in spots:
        out.append(L(circle(x, y, r), fill=Lin(0, y - r, 0, y + r, [(0, "#FFFFFF", 1), (1, "#F2D6D6", 1)])))
    return out

def music():
    c = "#FF6FD8"
    beam = poly([(24.6, 13.8), (51.6, 6.6), (51.6, 14), (24.6, 21.2)])
    d = (ellipse(19, 47, 7.2, 5.4, -22) + ellipse(45.8, 40, 7.2, 5.4, -22) + beam +
         rrect(23.2, 15.4, 3.8, 32, 1.4) + rrect(50, 8.4, 3.8, 32, 1.4))
    return [glow(c, 0.34), *jewel(d, c, 6, 53, sw=1.4)]

def two_players():
    a, b = "#FF7F5C", "#4CC9FF"
    def bust(cx, cy, s):
        head = circle(cx, cy - 12 * s, 8 * s)
        body = (f"M{f(cx - 15 * s)},{f(cy + 17 * s)} C{f(cx - 15 * s)},{f(cy + 3 * s)} {f(cx - 8.6 * s)},{f(cy - 1.6 * s)} {f(cx)},{f(cy - 1.6 * s)} "
                f"C{f(cx + 8.6 * s)},{f(cy - 1.6 * s)} {f(cx + 15 * s)},{f(cy + 3 * s)} {f(cx + 15 * s)},{f(cy + 17 * s)} "
                f"Q{f(cx + 15 * s)},{f(cy + 19 * s)} {f(cx + 13 * s)},{f(cy + 19 * s)} H{f(cx - 13 * s)} "
                f"Q{f(cx - 15 * s)},{f(cy + 19 * s)} {f(cx - 15 * s)},{f(cy + 17 * s)} Z")
        return head, body
    h1, b1 = bust(22.6, 31, 0.92); h2, b2 = bust(39.6, 36.4, 1.0)
    return [glow(a, 0.22, cx=22, cy=30), glow(b, 0.26, cx=40, cy=36),
            *jewel(b1, a, 28, 49, sw=1.2), *jewel(h1, a, 16, 31, sw=1.2),
            L(b2, stroke="#000000", sw=6), L(h2, stroke="#000000", sw=6),
            *jewel(b2, b, 34, 56, sw=1.3, shadow=0), *jewel(h2, b, 16, 33, sw=1.3, shadow=0)]

def hourglass():
    c = "#FFB547"; frame = "#7A5A3A"
    glass = ("M20,11.6 H44 C44,22 36.6,27 34.2,32 C36.6,37 44,42 44,52.4 H20 C20,42 27.4,37 29.8,32 "
             "C27.4,27 20,22 20,11.6 Z")
    sand_top = "M23.4,18 H40.6 C39.4,23 34.6,26.6 32,30.6 C29.4,26.6 24.6,23 23.4,18 Z"
    sand_bot = "M21.6,51.4 C23.2,44.2 28.6,40.6 32,40 C35.4,40.6 40.8,44.2 42.4,51.4 Z"
    return [glow(c, 0.32),
            L(glass, fill=Lin(0, 12, 0, 52, [(0, "#CDEBFF", 0.22), (1, "#7FA8C8", 0.14)]), stroke="#000000", sw=3, salpha=0.5),
            *jewel(sand_top, c, 18, 31, sw=0, gloss=0.25, shadow=0), *jewel(sand_bot, c, 40, 52, sw=0, gloss=0.25, shadow=0),
            L("M32,30 V41", stroke=c, sw=1.2),
            L(glass, stroke=Lin(0, 12, 0, 52, [(0, "#FFFFFF", 0.9), (1, "#B9D6EE", 0.5)]), sw=1.2),
            L("M23.4,14.6 C23.8,19.6 26.6,23 28.8,25.4", stroke="#FFFFFF", sw=1.6, salpha=0.55),
            *jewel("M16.4,12.6 V51.4", frame, 12, 52, sw=1.6, rim=c, gloss=0),
            *jewel("M47.6,12.6 V51.4", frame, 12, 52, sw=1.6, rim=c, gloss=0),
            *jewel(rrect(12, 6.4, 40, 6.4, 3.2), "#3A3F4A", 6, 13, sw=1.1, rim=c, top="#5A606C", bot="#16181E", gloss=0.25),
            *jewel(rrect(12, 51.2, 40, 6.4, 3.2), "#3A3F4A", 51, 58, sw=1.1, rim=c, top="#5A606C", bot="#16181E", gloss=0.25)]

def rosette(cx, cy, r_in, r_out, n):
    p = []
    for i in range(n):
        a0 = math.radians(-90 + i * 360 / n); am = math.radians(-90 + (i + 0.5) * 360 / n)
        a1 = math.radians(-90 + (i + 1) * 360 / n)
        if not p:
            p.append(f"M{f(cx + r_in * math.cos(a0))},{f(cy + r_in * math.sin(a0))}")
        p.append(f"Q{f(cx + r_out * math.cos(am))},{f(cy + r_out * math.sin(am))} {f(cx + r_in * math.cos(a1))},{f(cy + r_in * math.sin(a1))}")
    return "".join(p) + "Z"

def check_badge():
    c = "#3EE68A"
    return [glow(c, 0.34), *jewel(rosette(32, 32, 22.4, 27.4, 12), c, 6, 58, sw=1.3),
            L(circle(32, 32, 16.6), fill=Lin(0, 15, 0, 49, [(0, lo(c), 1), (1, mix(c, "#000000", 0.62), 1)]),
              stroke="#FFFFFF", sw=0.8, salpha=0.35),
            L("M23.4,33.4 L29.6,39.6 L41.6,26.4", stroke="#000000", sw=6.4, salpha=0.4),
            L("M23,32.4 L29.2,38.6 L41.2,25.4", stroke=Lin(0, 25, 0, 39, [(0, "#FFFFFF", 1), (1, "#D8FFE6", 1)]), sw=5)]

def play_badge():
    c = "#4CC9FF"
    tri = "M27,21.6 Q27,19.2 29.2,20.4 L44,29.6 Q46,31 44,32.4 L29.2,41.6 Q27,42.8 27,40.4 Z"
    tri = xf(tri, dy=1)
    return [glow(c, 0.36), *jewel(circle(32, 32, 24), c, 8, 56, sw=1.4),
            L(circle(32, 32, 18.6), fill=Lin(0, 13, 0, 51, [(0, lo(c), 1), (1, mix(c, "#000000", 0.66), 1)]),
              stroke="#FFFFFF", sw=0.8, salpha=0.35),
            L(xf(tri, dx=0.6, dy=1.4), fill="#000000", alpha=0.4),
            L(tri, fill=Lin(0, 20, 0, 43, [(0, "#FFFFFF", 1), (1, "#CFF1FF", 1)]))]

def clock():
    c = "#3EE6C0"
    out = [glow(c, 0.34),
           *jewel(circle(32, 32, 24.4), "#3A3F4A", 7.6, 56.4, sw=1.4, rim=c, top="#5A606C", bot="#121418", gloss=0.25),
           L(circle(32, 32, 19.6), fill=Lin(0, 12, 0, 52, [(0, "#FFFFFF", 1), (1, "#C9D2DC", 1)])),
           L(f"M32,32 L32,12.4 A19.6,19.6 0 0 1 {f(32 + 19.6 * math.cos(math.radians(30)))},{f(32 + 19.6 * math.sin(math.radians(30)))} Z",
             fill=c, alpha=0.35)]
    for i in range(12):
        a = math.radians(i * 30)
        r0 = 15.4 if i % 3 else 13.8
        out.append(L(f"M{f(32 + r0 * math.sin(a))},{f(32 - r0 * math.cos(a))} L{f(32 + 17.4 * math.sin(a))},{f(32 - 17.4 * math.cos(a))}",
                     stroke="#1A1D24", sw=1.6 if i % 3 == 0 else 1, salpha=0.85))
    out += [L("M32,32 L32,17.4", stroke="#1A1D24", sw=2.6), L("M32,32 L41.2,37.4", stroke="#1A1D24", sw=3.2),
            L(circle(32, 32, 2.6), fill=c, stroke="#1A1D24", sw=0.8)]
    return out

def gem():
    c = "#5CE8FF"
    outline = poly([(18.6, 12), (45.4, 12), (57, 25.6), (32, 56), (7, 25.6)])
    facets = [([(18.6, 12), (26, 12), (21.4, 25.6), (7, 25.6)], "#B6F6FF"),
              ([(26, 12), (38, 12), (42.6, 25.6), (21.4, 25.6)], "#E6FCFF"),
              ([(38, 12), (45.4, 12), (57, 25.6), (42.6, 25.6)], "#7FDDF5"),
              ([(7, 25.6), (21.4, 25.6), (32, 56)], "#3FB4E0"),
              ([(21.4, 25.6), (42.6, 25.6), (32, 56)], "#5CE8FF"),
              ([(42.6, 25.6), (57, 25.6), (32, 56)], "#2A7FC8")]
    out = [glow(c, 0.36), *jewel(outline, c, 12, 56, sw=1.4, gloss=0)]
    for pts, col in facets:
        out.append(L(poly(pts), fill=col, stroke="#FFFFFF", sw=0.5, salpha=0.5))
    out += [L(poly([(26, 12), (38, 12), (42.6, 25.6), (21.4, 25.6)]), fill=Lin(0, 12, 0, 26, [(0, "#FFFFFF", 0.6), (1, "#FFFFFF", 0)])),
            L(outline, stroke=Lin(0, 12, 0, 56, [(0, "#FFFFFF", 0.95), (1, c, 0.8)]), sw=1)]
    return out

def flame():
    c = "#FF7A2E"
    outer = ("M32,5 C36,15 48.6,21.6 48.6,37 C48.6,48.4 41,57 32,57 C23,57 15.4,48.4 15.4,37 "
             "C15.4,29.6 18.6,24.4 23,20.4 C22.8,26.6 24.8,30.6 28,32 C26.6,21.8 28.8,12.6 32,5 Z")
    inner = "M32,29 C35.6,35.6 41,38.6 41,45.4 C41,51.6 37,55.4 32,55.4 C27,55.4 23,51.6 23,45.4 C23,40.2 27.6,36.6 32,29 Z"
    return [glow(c, 0.38), *jewel(outer, c, 5, 57, sw=1.4, top="#FFD24C", bot="#E8282E", rim="#FF9A3D"),
            L(inner, fill=Lin(0, 29, 0, 56, [(0, "#FFE9A0", 1), (1, "#FFF8E0", 1)])),
            halo(32, 47, 9, "#FFFFFF", 0.4)]

def leaf():
    c = "#3EE68A"
    d = "M13,51 C10,30.6 22.6,11 52,9.4 C54,34.6 39.4,52.6 13,51 Z"
    return [glow(c, 0.32), L("M14.6,49.4 L8,56.4", stroke="#000000", sw=4, salpha=0.4),
            L("M14,49 L7.6,55.6", stroke=Lin(0, 49, 0, 56, [(0, hi(c), 1), (1, lo(c), 1)]), sw=2.8),
            *jewel(d, c, 9, 52, sw=1.4),
            L("M15.4,48.6 C24.6,39.4 35.6,27.4 47.4,14.4", stroke=lo(c), sw=1.8, salpha=0.85),
            L("M22.6,41.4 C22.4,35.6 23,31.6 24.6,27.6 M29.4,34.2 C29.6,29.6 30.6,25.2 32.6,21.4 "
              "M22.6,41.4 C27.6,42 32.4,41.6 37,40 M30.2,33.4 C35.2,33.8 39.6,33 43.4,31", stroke=lo(c), sw=1.2, salpha=0.6)]

def skull():
    c = CRIMSON; bone = "#F4EEDF"
    d = ("M32,7 C44.8,7 53.4,15.6 53.4,27.4 C53.4,34.6 50,39.4 45.4,42 V48.4 C45.4,50.6 43.8,52.2 41.6,52.2 "
         "H22.4 C20.2,52.2 18.6,50.6 18.6,48.4 V42 C14,39.4 10.6,34.6 10.6,27.4 C10.6,15.6 19.2,7 32,7 Z")
    out = [glow(c, 0.34), *jewel(d, bone, 7, 52.2, sw=1.4, rim="#FF9AA4", top="#FFFFFF", bot="#BFB59E"),
           L(ellipse(23.4, 28.6, 6, 6.6, 8), fill="#160A0C"), L(ellipse(40.6, 28.6, 6, 6.6, -8), fill="#160A0C"),
           halo(23.4, 29.4, 4, c, 0.9), halo(40.6, 29.4, 4, c, 0.9),
           L(circle(23.4, 29.4, 1.5), fill="#FF8A94"), L(circle(40.6, 29.4, 1.5), fill="#FF8A94"),
           L("M32,35.6 L29.2,41 Q32,42.4 34.8,41 Z", fill="#160A0C")]
    for x in (26.6, 32, 37.4):
        out.append(L(f"M{f(x)},45.2 V52.2", stroke="#3A2A20", sw=1.4, salpha=0.7))
    return out

def paw():
    c = "#FF9ACB"
    pad = "M32,32.6 C38.6,32.6 45.6,39.4 45.6,46 C45.6,51 41.4,53.4 37.4,52.4 C35.4,51.9 34,51 32,51 C30,51 28.6,51.9 26.6,52.4 C22.6,53.4 18.4,51 18.4,46 C18.4,39.4 25.4,32.6 32,32.6 Z"
    toes = [ellipse(13.6, 30.6, 5, 6.6, -28), ellipse(24.4, 18.4, 5.4, 7.2, -10),
            ellipse(39.6, 18.4, 5.4, 7.2, 10), ellipse(50.4, 30.6, 5, 6.6, 28)]
    out = [glow(c, 0.34), *jewel(pad, c, 32, 53, sw=1.4)]
    for t in toes:
        out += jewel(t, c, 11, 38, sw=1.2)
    return out

# name -> (drawing, accent, what it stands for). The mark name is the key with - for _.
GLYPHS = {
    "star": (star, GOLD, "A star, for Favorites"),
    "heart": (heart, "#FF4F6E", "A heart"),
    "trophy": (trophy, "#FFB547", "A trophy"),
    "sword": (sword, "#9FC2FF", "A sword"),
    "shield": (shield, INDIGO, "A shield with two chevrons"),
    "racing_flag": (racing_flag, "#C9F7FF", "A chequered racing flag"),
    "puzzle": (puzzle, LIME, "A puzzle piece"),
    "ghost": (ghost, LAVENDER, "A friendly ghost"),
    "crown": (crown, "#FFD24C", "A jewelled crown"),
    "rocket": (rocket, SUNSET, "A rocket"),
    "mushroom": (mushroom, "#FF4D5A", "A spotted mushroom"),
    "music": (music, "#FF6FD8", "Two beamed notes"),
    "two_players": (two_players, "#4CC9FF", "Two players"),
    "hourglass": (hourglass, "#FFB547", "An hourglass, for a backlog"),
    "check_badge": (check_badge, "#3EE68A", "A rosette with a check, for finished games"),
    "play_badge": (play_badge, "#4CC9FF", "A play button, for games in progress"),
    "clock": (clock, "#3EE6C0", "A clock, for Recently played"),
    "gem": (gem, "#5CE8FF", "A cut gem"),
    "flame": (flame, "#FF7A2E", "A flame"),
    "leaf": (leaf, "#3EE68A", "A leaf, for cozy games"),
    "skull": (skull, CRIMSON, "A skull, for hard games"),
    "paw": (paw, "#FF9ACB", "A paw print"),
}

# ---- the collection silhouette
WELL = (11.2, 25.0, 41.6, 29.2, 7.4)          # x, y, w, h, r of the dark well on the front
GLYPH_BOX = 36.0                               # a 64-unit glyph is drawn this big, centred in the well
BACK = ("M6,15 Q6,9.6 11.4,9.6 H23.4 Q25.6,9.6 27,11.4 L29.4,14.4 H52.6 Q58,14.4 58,19.8 "
        "V51.6 Q58,57 52.6,57 H11.4 Q6,57 6,51.6 Z")
FRONT = ("M4.6,25.4 Q5,20.4 10,20.4 H54 Q59.4,20.4 59.4,25.4 L57.6,52.6 Q57.2,58 51.8,58 "
         "H12.2 Q6.8,58 6.4,52.6 Z")

def body():
    """Greys only: the host multiplies these by the tint, so white becomes the tint itself."""
    return [halo(32, 34, 32, "#FFFFFF", 0.3, core=0.12),
            L(BACK, fill=Lin(0, 9.6, 0, 57, [(0, "#8A8A8A", 1), (1, "#3A3A3A", 1)]),
              stroke=Lin(0, 9.6, 0, 57, [(0, "#FFFFFF", 0.9), (1, "#FFFFFF", 0.45)]), sw=1.3),
            L(xf(FRONT, dy=1.4), fill="#000000", alpha=0.45),
            L(FRONT, fill=Lin(6, 20, 58, 58, [(0, "#FFFFFF", 1), (0.5, "#E8E8E8", 1), (1, "#8C8C8C", 1)]),
              stroke=Lin(0, 20, 0, 58, [(0, "#FFFFFF", 1), (1, "#FFFFFF", 0.55)]), sw=1.4)]

def card(x, deg, fill, rim, stripe):
    """A game card tucked into the folder. Cut level at the front's top edge, so only its top shows."""
    from platforms import rot
    w, top, cut, r = 15.4, 11.6, 20.0, 2.4
    cx = x + w / 2
    def outline(dx=0.0, dy=0.0):
        p = rot([(x, 30), (x, top + r), (x + r, top), (x + w - r, top), (x + w, top + r), (x + w, 30)], cx, cut, deg)
        p = [(px + dx, py + dy) for px, py in p]
        def at_cut(a, b):  # where a straight side meets the cut
            t = (cut - a[1]) / (b[1] - a[1]); return (a[0] + (b[0] - a[0]) * t, cut)
        p0, p5 = at_cut(p[1], p[0]), at_cut(p[4], p[5])
        arc = f"A{f(r)},{f(r)} 0 0 1 "
        return (f"M{f(p0[0])},{f(p0[1])}L{f(p[1][0])},{f(p[1][1])}{arc}{f(p[2][0])},{f(p[2][1])}"
                f"L{f(p[3][0])},{f(p[3][1])}{arc}{f(p[4][0])},{f(p[4][1])}L{f(p5[0])},{f(p5[1])}Z")
    s = rot([(x + 3, top + 3.4), (x + w - 3, top + 3.4)], cx, cut, deg)
    return [L(outline(0.4, 0.8), fill="#000000", alpha=0.5),
            L(outline(), fill=fill),
            L(outline(), stroke=rim, sw=0.9, salpha=0.8),
            L(f"M{f(s[0][0])},{f(s[0][1])}L{f(s[1][0])},{f(s[1][1])}", stroke=stripe, sw=1.6, salpha=0.9)]

def face(well=True):
    """Never tinted: the cards peeking out, gloss on the front, and the dark well the glyph sits in."""
    x, y, w, h, r = WELL
    out = [*card(14.6, -8, Lin(0, 11, 0, 22, [(0, "#FFFFFF", 1), (1, "#CFC8BA", 1)]), "#FFFFFF", "#9A958C"),
           *card(33.6, 6, Lin(0, 11, 0, 22, [(0, "#4A505C", 1), (1, "#1A1C22", 1)]), "#C3CEDF", "#6E7A8E"),
           L("M8.6,22.4 Q9,21.8 10.6,21.8 H53.4 Q57,21.8 57.6,24", stroke="#FFFFFF", sw=1.2, salpha=0.7),
           L(FRONT, fill=Lin(0, 20, 0, 40, [(0, "#FFFFFF", 0.38), (1, "#FFFFFF", 0)])),
           L(rrect(8.8, 12.4, 12, 1.6, 0.8), fill="#FFFFFF", alpha=0.35)]
    if well:
        out += [L(rrect(x - 0.6, y - 0.4, w + 1.2, h + 1.6, r + 0.6), fill="#FFFFFF", alpha=0.35),
                L(rrect(x, y, w, h, r), fill=Lin(0, y, 0, y + h, [(0, "#020203", 1), (1, "#16171C", 1)])),
                L(rrect(x + 0.6, y + 0.6, w - 1.2, h - 1.2, r - 0.6), stroke="#000000", sw=1.6, salpha=0.6)]
    return out

def collection_assets():
    out = [Asset("mark_collection_body", 64, 64, body(), dp=(48, 48),
                 note="Afterglow collection folder in greys; the host multiplies it by the collection's tint"),
           Asset("mark_collection_face", 64, 64, face(), dp=(48, 48),
                 note="Afterglow collection gloss and glyph well; drawn over the tinted body, never tinted")]
    out += [Asset(f"mark_{n}", 64, 64, fn(), dp=(48, 48), note=f"Afterglow collection glyph: {about}")
            for n, (fn, _, about) in GLYPHS.items()]
    return out

def manifest_slots():
    """Afterglow manifest entries for these assets: slot -> entry. A glyph's accent paints its tile glow."""
    out = {"mark.collection-body": {"file": "icons/mark_collection_body.xml", "tint": False},
           "mark.collection-face": {"file": "icons/mark_collection_face.xml", "tint": False}}
    for n, (_, accent, _) in GLYPHS.items():
        out["mark." + n.replace("_", "-")] = {"file": f"icons/mark_{n}.xml", "tint": False, "accent": accent.upper()}
    return out

if __name__ == "__main__":
    import json, os
    repo = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    os.makedirs(f"{repo}/art/svg/collections", exist_ok=True)
    for a in collection_assets():
        open(f"{repo}/themes/afterglow/icons/{a.name}.xml", "w").write(to_vd(a))
        open(f"{repo}/art/svg/collections/{a.name}.svg", "w").write(to_svg(a))
    # Keep the manifest's marks (the last block) sorted, with these slots among them.
    path = f"{repo}/themes/afterglow/theme.json"
    theme = json.loads(open(path).read())
    icons = {**theme["icons"], **manifest_slots()}
    marks = sorted(k for k in icons if k.startswith("mark."))
    theme["icons"] = {**{k: v for k, v in icons.items() if not k.startswith("mark.")}, **{k: icons[k] for k in marks}}
    open(path, "w").write(json.dumps(theme, indent=2, ensure_ascii=False) + "\n")
    print("wrote", len(GLYPHS), "glyphs and the collection body and face")
