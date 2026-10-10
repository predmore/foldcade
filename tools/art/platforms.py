"""Afterglow platform and library marks (64-unit art). Original hardware silhouettes, no console logos.

Every mark shares one material: a graphite body lit from above, a neon rim in the mark's
accent, glossy glass, and a soft halo that fades to nothing before the edge.
"""
import math
from art import *
from palette import *

LIME = "#B6F25C"; GRAPE = "#C77DFF"; INDIGO = "#7C8CFF"; RED = "#FF4D5A"; LAVENDER = "#B39DFF"
GREEN = "#3EE68A"; SKY = "#8FDFFF"; JOY_L = "#2FC7FF"; JOY_R = "#FF4F5E"; SILVER = "#C3CEDF"
PSBLUE = "#4C8DFF"; CRIMSON = "#FF3B52"; SUNSET = "#FF8A3D"; DROID = "#3DDC84"; MOON = "#A9BCFF"
GOLD = "#FFC15A"
SNES_X, SNES_Y, SNES_A, SNES_B = "#5BA8FF", "#4FE08A", "#FF5C6C", "#FFD24C"

def mix(a, b, t):
    a, b = a.lstrip("#"), b.lstrip("#")
    ca = [int(a[i:i + 2], 16) for i in (0, 2, 4)]; cb = [int(b[i:i + 2], 16) for i in (0, 2, 4)]
    return "#%02X%02X%02X" % tuple(round(x + (y - x) * t) for x, y in zip(ca, cb))

def hi(c): return mix(c, "#FFFFFF", 0.62)
def lo(c): return mix(c, "#000000", 0.42)

# ---- material
def glow(c, peak=0.32, cx=32, cy=32, r=32):
    return halo(cx, cy, r, c, peak, core=0.12)

def body(d, c, y0, y1, sw=2.0, top=CAP_TOP, bot=CAP_BOT, evenodd=False):
    return L(d, fill=Lin(0, y0, 0, y1, [(0, top, 1), (1, bot, 1)]),
             stroke=Lin(0, y0, 0, y1, [(0, "#FFFFFF", 0.9), (0.3, c, 1), (1, c, 0.5)]), sw=sw, evenodd=evenodd)

def sheen(d, y0, y1, a=0.16):
    """A top-lit sheen over a body: white that fades out by the middle."""
    return L(d, fill=Lin(0, y0, 0, y1, [(0, "#FFFFFF", a), (0.5, "#FFFFFF", 0)]))

def glass(x, y, w, h, r, c, bezel=True):
    out = []
    if bezel:
        out.append(L(rrect(x - 1.4, y - 1.4, w + 2.8, h + 2.8, r + 1.2), fill="#07080B", stroke="#000000", sw=0.6, salpha=0.6))
    out += [L(rrect(x, y, w, h, r), fill=Lin(x, y, x + w, y + h, [(0, hi(c), 1), (0.5, c, 1), (1, lo(c), 1)])),
            L(rrect(x + 0.7, y + 0.6, w - 1.4, h * 0.42, max(r - 0.5, 0.4)),
              fill=Lin(0, y, 0, y + h * 0.45, [(0, "#FFFFFF", 0.45), (1, "#FFFFFF", 0)]))]
    return out

def dpad(cx, cy, s, c=INK, a=0.92):
    t = s * 0.34; h = s / 2; k = t / 2
    d = (f"M{f(cx-k)},{f(cy-h)}H{f(cx+k)}V{f(cy-k)}H{f(cx+h)}V{f(cy+k)}H{f(cx+k)}V{f(cy+h)}"
         f"H{f(cx-k)}V{f(cy+k)}H{f(cx-h)}V{f(cy-k)}H{f(cx-k)}Z")
    return [L(d, fill=Lin(0, cy - h, 0, cy + h, [(0, "#FFFFFF", a), (1, c, a * 0.7)]), stroke="#000000", sw=0.5, salpha=0.5),
            L(circle(cx, cy, t * 0.28), fill="#000000", alpha=0.35)]

def btn(cx, cy, r, c, a=1.0):
    return [L(circle(cx, cy, r), fill=Rad(cx - r * 0.35, cy - r * 0.4, r * 1.5, [(0, hi(c), a), (0.55, c, a), (1, lo(c), a)]),
              stroke="#000000", sw=0.5, salpha=0.45),
            L(circle(cx - r * 0.3, cy - r * 0.35, r * 0.32), fill="#FFFFFF", alpha=0.55 * a)]

def stick(cx, cy, r, c=INK):
    return [L(circle(cx, cy, r), fill="#05060A", stroke=c, sw=0.9, salpha=0.55),
            L(circle(cx, cy, r * 0.62), fill=Rad(cx - r * 0.2, cy - r * 0.25, r, [(0, "#4A505C", 1), (1, "#16181E", 1)]),
              stroke="#FFFFFF", sw=0.5, salpha=0.35)]

def pill(cx, cy, w, h, c=MUTED, a=0.9):
    return L(rrect(cx - w / 2, cy - h / 2, w, h, h / 2), fill=c, alpha=a)

def diamond(cx, cy, off, r, colors):
    pts = [(cx, cy - off), (cx + off, cy), (cx, cy + off), (cx - off, cy)]
    out = []
    for (x, y), c in zip(pts, colors):
        out += btn(x, y, r, c)
    return out

def rot(points, cx, cy, deg):
    a = math.radians(deg); ca, sa = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in points]

def rrect_rot(x, y, w, h, r, deg, cx=None, cy=None):
    """A rounded rect turned [deg] about (cx, cy). Circular corners survive rotation."""
    cx = x + w / 2 if cx is None else cx; cy = y + h / 2 if cy is None else cy
    p = rot([(x + r, y), (x + w - r, y), (x + w, y + r), (x + w, y + h - r),
             (x + w - r, y + h), (x + r, y + h), (x, y + h - r), (x, y + r)], cx, cy, deg)
    a = f"A{f(r)},{f(r)} 0 0 1 "
    return (f"M{f(p[0][0])},{f(p[0][1])}L{f(p[1][0])},{f(p[1][1])}{a}{f(p[2][0])},{f(p[2][1])}"
            f"L{f(p[3][0])},{f(p[3][1])}{a}{f(p[4][0])},{f(p[4][1])}L{f(p[5][0])},{f(p[5][1])}"
            f"{a}{f(p[6][0])},{f(p[6][1])}L{f(p[7][0])},{f(p[7][1])}{a}{f(p[0][0])},{f(p[0][1])}Z")

def crescent(ax, ay, ar, bx, by, br):
    """Disc A minus disc B, as one closed outline."""
    d = math.hypot(bx - ax, by - ay)
    a = (ar * ar - br * br + d * d) / (2 * d); h = math.sqrt(ar * ar - a * a)
    mx, my = ax + a * (bx - ax) / d, ay + a * (by - ay) / d
    ux, uy = -(by - ay) / d, (bx - ax) / d
    p1 = (mx + h * ux, my + h * uy); p2 = (mx - h * ux, my - h * uy)
    return (f"M{f(p1[0])},{f(p1[1])}A{f(ar)},{f(ar)} 0 1 1 {f(p2[0])},{f(p2[1])}"
            f"A{f(br)},{f(br)} 0 0 0 {f(p1[0])},{f(p1[1])}Z")

# ---- Nintendo-family hardware shapes
def n3ds():
    c = ROSE
    top, bottom = rrect(9, 5, 46, 26, 5), rrect(8, 33, 48, 27, 5)
    return [glow(c),
            body(top, c, 5, 31), sheen(top, 5, 31), *glass(13, 9, 36, 18, 1.6, c),
            L(rrect(51.2, 11.4, 1.8, 9, 0.9), fill="#05060A"), L(rrect(51, 12.4, 2.2, 3, 1), fill=hi(c)),
            L(rrect(11, 30.4, 42, 3.4, 1.7), fill=Lin(0, 30, 0, 34, [(0, "#4A505E", 1), (1, "#14161B", 1)])),
            body(bottom, c, 33, 60), sheen(bottom, 33, 60), *glass(22, 37, 20, 15, 1.4, c),
            *stick(14.6, 40.6, 3.4), *dpad(14.6, 52, 6.4),
            *diamond(49.4, 44.5, 3.4, 1.5, [INK, INK, c, INK]),
            pill(29, 56.4, 3.2, 1.3), pill(35, 56.4, 3.2, 1.3), L(circle(49.4, 55.4, 1.1), fill=c, alpha=0.9)]

def nds():
    c = ORANGE
    top, bottom = rrect(8, 5, 48, 26, 5), rrect(8, 33, 48, 27, 5)
    out = [glow(c), body(top, c, 5, 31), sheen(top, 5, 31), *glass(20, 9, 24, 18, 1.4, c)]
    for sx in (13.5, 50.5):
        for dy in (0, 3.2):
            out += [L(circle(sx - 1.6, 16 + dy, 0.7), fill="#000000", alpha=0.85),
                    L(circle(sx + 1.6, 16 + dy, 0.7), fill="#000000", alpha=0.85)]
    out += [L(rrect(11, 30.4, 42, 3.4, 1.7), fill=Lin(0, 30, 0, 34, [(0, "#4A505E", 1), (1, "#14161B", 1)])),
            body(bottom, c, 33, 60), sheen(bottom, 33, 60), *glass(20, 37, 24, 18, 1.4, c),
            *dpad(13.6, 44, 7), *diamond(50.4, 44, 3.4, 1.5, [INK, INK, c, INK]),
            L(circle(50.4, 53.6, 0.9), fill=INK, alpha=0.7), L(circle(50.4, 56.4, 0.9), fill=INK, alpha=0.7)]
    return out

def game_boy():
    c = LIME
    shell = rrect(14, 3, 36, 58, (4, 4, 14, 4))
    out = [glow(c, 0.3), body(shell, c, 3, 61), sheen(shell, 3, 61),
           L("M17.5,7.5 H46.5", stroke="#000000", sw=0.7, salpha=0.6),
           L(rrect(18, 10, 28, 23, (2, 2, 6, 2)), fill=Lin(0, 10, 0, 33, [(0, "#3B404C", 1), (1, "#22252D", 1)]),
             stroke="#FFFFFF", sw=0.5, salpha=0.18),
           *glass(23.5, 13.2, 17, 15, 0.8, c, bezel=False),
           L(circle(20.6, 18.5, 0.95), fill=RED), L("M19.8,21.6 H21.4", stroke=MUTED, sw=0.6),
           *dpad(23, 41.5, 9.5), *btn(38.2, 44.2, 2.9, CRIMSON), *btn(44, 40.6, 2.9, CRIMSON)]
    out += [L(rrect_rot(25.2, 50.5, 4.4, 1.5, 0.75, -25), fill=MUTED, alpha=0.9),
            L(rrect_rot(31.4, 50.5, 4.4, 1.5, 0.75, -25), fill=MUTED, alpha=0.9)]
    for i in range(4):
        x = 36.4 + i * 2.5
        out.append(L(f"M{f(x)},{f(56.6)} L{f(x + 3.2)},{f(50.6)}", stroke="#000000", sw=1.1, salpha=0.75))
    return out

def game_boy_color():
    c = GRAPE
    shell = ("M18,4 H46 Q49,4 49,7 V44 C49,53 42,60 32,60 C22,60 15,53 15,44 V7 Q15,4 18,4 Z")
    rainbow = Lin(23, 12, 41, 28, [(0, "#7DF0FF", 1), (0.35, "#B98CFF", 1), (0.7, "#FF7AB6", 1), (1, "#FFC15A", 1)])
    out = [glow(c, 0.32), body(shell, c, 4, 60), sheen(shell, 4, 60),
           L("M18.5,9 Q32,6.6 45.5,9 V30.5 Q32,33 18.5,30.5 Z", fill=Lin(0, 8, 0, 33, [(0, "#3B404C", 1), (1, "#1E2128", 1)]),
             stroke="#FFFFFF", sw=0.5, salpha=0.18),
           L(rrect(23, 12.4, 18, 15, 0.8), fill=rainbow),
           L(rrect(23.7, 13, 16.6, 6, 0.6), fill=Lin(0, 12, 0, 19, [(0, "#FFFFFF", 0.45), (1, "#FFFFFF", 0)])),
           L(circle(20.6, 17.5, 0.9), fill=RED),
           *dpad(23.5, 41.5, 9), *btn(37.6, 43.4, 2.7, c), *btn(43, 40, 2.7, c),
           pill(28.4, 50.8, 4, 1.4), pill(35.6, 50.8, 4, 1.4)]
    for i, (x, y) in enumerate([(38, 55), (40.6, 53.6), (43, 51.8), (40.4, 56.4), (42.9, 54.8)]):
        out.append(L(circle(x, y, 0.75), fill="#000000", alpha=0.8))
    return out

def gba():
    c = INDIGO
    shell = ("M14,15 H50 C57,15 61,21 61,30 C61,40 57,48 50,48 H14 C7,48 3,40 3,30 C3,21 7,15 14,15 Z")
    out = [glow(c), L(rrect(5, 13, 13, 6, 3), fill="#2A2E38", stroke=c, sw=1, salpha=0.6),
           L(rrect(46, 13, 13, 6, 3), fill="#2A2E38", stroke=c, sw=1, salpha=0.6),
           body(shell, c, 15, 48), sheen(shell, 15, 48), *glass(20.5, 20.5, 23, 17, 1.2, c),
           *dpad(11.6, 30, 8.4), *btn(49.6, 33.4, 2.7, c), *btn(55, 29.4, 2.7, c),
           pill(13, 41.8, 2.6, 1.2), pill(13, 44.6, 2.6, 1.2), L(circle(48, 42.4, 0.7), fill=GREEN)]
    for i in range(3):
        out.append(L(circle(52 + i * 2, 43.6 - i * 0.6, 0.6), fill="#000000", alpha=0.8))
    return out

def nes():
    c = RED
    shell = rrect(3, 19, 58, 27, 3)
    out = [glow(c, 0.28), body(shell, c, 19, 46, top="#3A3E48", bot="#1A1C22"), sheen(shell, 19, 46, 0.2),
           L(rrect(7, 23, 50, 19, 1.6), fill="#08090C", stroke="#FFFFFF", sw=0.4, salpha=0.15),
           *dpad(15.4, 32.5, 10.5)]
    for i, y in enumerate((26.2, 29.6, 33, 36.4)):
        out.append(L(rrect(24.6, y, 14.8, 2.2, 0.6), fill="#5A606C" if i else "#6A707C", alpha=0.85))
    out += [L(rrect(25, 38.6, 14, 2.6, 1.3), fill="#2A2D35"),
            pill(28.6, 39.9, 3.8, 1.4, "#0A0B0E"), pill(35.4, 39.9, 3.8, 1.4, "#0A0B0E"),
            L(rrect(42.6, 29, 12, 9.4, 1.6), fill="#5A606C", alpha=0.35),
            *btn(45.6, 34.4, 2.9, c), *btn(51.8, 34.4, 2.9, c)]
    return out

def snes():
    c = LAVENDER
    shell = ("M18,19 C25,19 27,21.2 32,21.2 C37,21.2 39,19 46,19 A13,13 0 0 1 46,45 "
             "C39,45 37,42.8 32,42.8 C27,42.8 25,45 18,45 A13,13 0 0 1 18,19 Z")
    return [glow(c), body(shell, c, 19, 45, top="#3A3D49", bot="#15161C"), sheen(shell, 19, 45, 0.2),
            L(circle(18, 32, 8.6), fill="#000000", alpha=0.35), *dpad(18, 32, 11),
            L(circle(46, 32, 9), fill="#000000", alpha=0.35),
            *btn(46, 27.3, 2.5, SNES_X), *btn(41.3, 32, 2.5, SNES_Y), *btn(50.7, 32, 2.5, SNES_A), *btn(46, 36.7, 2.5, SNES_B),
            L(rrect_rot(26.2, 34.4, 4.6, 1.6, 0.8, -32), fill=MUTED), L(rrect_rot(33.2, 34.4, 4.6, 1.6, 0.8, -32), fill=MUTED)]

def n64():
    c = GREEN
    shell = ("M14,10 C21,10 25,12.5 32,12.5 C39,12.5 43,10 50,10 C57,10 61,15 61,22 C61,30 59,38 56.5,46 "
             "C55.2,50 51.6,51.6 48.8,49.4 C46,47.2 46,40 43.6,34.4 C42.4,32.2 40.2,32.2 39.4,34.4 "
             "C38.2,42 37.6,50 35.6,54.6 C34.4,57.6 29.6,57.6 28.4,54.6 C26.4,50 25.8,42 24.6,34.4 "
             "C23.8,32.2 21.6,32.2 20.4,34.4 C18,40 18,47.2 15.2,49.4 C12.4,51.6 8.8,50 7.5,46 "
             "C5,38 3,30 3,22 C3,15 7,10 14,10 Z")
    return [glow(c, 0.3), body(shell, c, 10, 57), sheen(shell, 10, 40, 0.18),
            *dpad(13.6, 21.6, 9), *stick(32, 30, 4.6), *btn(32, 19.4, 1.7, RED),
            *btn(44.6, 25.4, 2.6, PSBLUE), *btn(41, 20.8, 2.4, GREEN),
            *diamond(53, 20.4, 2.9, 1.35, [GOLD, GOLD, GOLD, GOLD])]

def gamecube():
    c = "#9B7BFF"
    top = poly([(32, 11), (53, 22), (32, 33), (11, 22)])
    left = poly([(11, 22), (32, 33), (32, 57), (11, 46)])
    right = poly([(32, 33), (53, 22), (53, 46), (32, 57)])
    rim = Lin(0, 11, 0, 57, [(0, "#FFFFFF", 0.9), (0.3, c, 1), (1, c, 0.5)])
    return [glow(c, 0.34),
            L("M36,13.4 V6.6 L49.6,13.8 V20.6", stroke="#000000", sw=4.6, salpha=0.6),
            L("M36,13.4 V6.6 L49.6,13.8 V20.6", stroke=Lin(36, 6, 50, 20, [(0, hi(c), 1), (1, c, 0.85)]), sw=3),
            L(top, fill=Lin(11, 11, 53, 33, [(0, "#4A4466", 1), (1, "#2A2740", 1)]), stroke=rim, sw=1.6),
            L(left, fill=Lin(0, 22, 0, 57, [(0, "#2C2A3C", 1), (1, "#121118", 1)]), stroke=rim, sw=1.6),
            L(right, fill=Lin(0, 22, 0, 57, [(0, "#1E1C2A", 1), (1, "#09090D", 1)]), stroke=rim, sw=1.6),
            L(circle(32, 22, 9.4), fill=Rad(29, 19, 12, [(0, "#6E64A0", 1), (1, "#2A2740", 1)]),
              stroke=hi(c), sw=0.8, salpha=0.6, scale=(1, 0.52, 32, 22)),
            L(circle(32, 22, 2.4), fill=c, alpha=0.8, scale=(1, 0.52, 32, 22)),
            L(poly([(14.5, 41.5), (18.5, 43.6), (18.5, 47.2), (14.5, 45.1)]), fill="#000000", stroke=c, sw=0.5, salpha=0.6),
            L(poly([(20.5, 44.6), (24.5, 46.7), (24.5, 50.3), (20.5, 48.2)]), fill="#000000", stroke=c, sw=0.5, salpha=0.6),
            L(poly([(43, 32), (49, 28.9)], close=False), stroke=c, sw=1.2, salpha=0.7)]

def wii():
    c = SKY
    shell = rrect(22, 3, 20, 58, 9)
    out = [glow(c, 0.34), body(shell, c, 3, 61, top="#3A3F4A", bot="#14161C"), sheen(shell, 3, 61, 0.22),
           L(circle(32, 9.4, 1.6), fill="#000000", stroke=RED, sw=0.6, salpha=0.8),
           *dpad(32, 19, 9.4), L(rrect(28.3, 27.4, 7.4, 7.4, 2.4), fill=Lin(0, 27, 0, 35, [(0, "#FFFFFF", 1), (1, "#B9C2CF", 1)]),
                                    stroke="#000000", sw=0.5, salpha=0.4),
           pill(27.4, 40.6, 2.4, 1.4, INK), L(circle(32, 40.6, 1.4), fill=c), pill(36.6, 40.6, 2.4, 1.4, INK),
           *btn(32, 47.4, 1.9, "#E8ECF2"), *btn(32, 52.4, 1.9, "#E8ECF2")]
    for i in range(4):
        out.append(L(circle(28.1 + i * 2.6, 57, 0.6), fill=c if i == 0 else "#2A3A48"))
    return out

def switch():
    left = rrect(3, 13, 14, 38, (7, 0, 0, 7)); right = rrect(47, 13, 14, 38, (0, 7, 7, 0))
    mid = rrect(17, 13, 30, 38, 1.4)
    return [glow(JOY_L, 0.26, cx=16), glow(JOY_R, 0.26, cx=48),
            L(left, fill=Lin(0, 13, 0, 51, [(0, hi(JOY_L), 1), (0.4, JOY_L, 1), (1, lo(JOY_L), 1)]), stroke="#FFFFFF", sw=0.8, salpha=0.5),
            L(right, fill=Lin(0, 13, 0, 51, [(0, hi(JOY_R), 1), (0.4, JOY_R, 1), (1, lo(JOY_R), 1)]), stroke="#FFFFFF", sw=0.8, salpha=0.5),
            body(mid, "#C8D2E0", 13, 51, sw=1.4), *glass(19.6, 15.6, 24.8, 32.8, 0.8, "#5E7BFF", bezel=False),
            L(rrect(19.6, 15.6, 24.8, 32.8, 0.8), fill=Lin(19, 15, 45, 48, [(0, JOY_L, 0.25), (1, JOY_R, 0.25)])),
            *stick(10, 22, 3.2), *diamond(10, 35, 3, 1.3, ["#0B1E2A"] * 4), pill(10, 16.6, 2.6, 1, "#0B1E2A"),
            *diamond(54, 22, 3, 1.3, ["#2A0B10"] * 4), *stick(54, 35, 3.2),
            L(rrect(52.5, 15.5, 3, 1, 0.5), fill="#2A0B10"), L(rrect(53.5, 14.5, 1, 3, 0.5), fill="#2A0B10"),
            L(circle(53.4, 45.6, 1.2), fill="#2A0B10"),
            L(rrect(8.6, 44.8, 2.6, 2.6, 0.5), fill="#0B1E2A")]

def psp():
    c = "#6FC3FF"
    shell = rrect(2, 20, 60, 25, 12.5)
    return [glow(c, 0.3), body(shell, c, 20, 45), sheen(shell, 20, 45, 0.2),
            *glass(15.6, 23.6, 32.8, 17.8, 1, c),
            *dpad(8.6, 29, 6.4), L(rrect(5.2, 35.4, 6.4, 5.4, 2.6), fill="#05060A", stroke=INK, sw=0.6, salpha=0.5),
            L(circle(8.4, 38.1, 1.6), fill="#3A3F4A"),
            *diamond(55.4, 31.6, 2.8, 1.25, [INK, INK, INK, INK]),
            pill(52, 41, 2.4, 1), pill(56, 41, 2.4, 1), L("M24,43.6 H40", stroke=c, sw=0.6, salpha=0.6)]

def playstation():
    c = SILVER
    shell = rrect(5, 12, 54, 40, 4)
    return [glow("#9FB2CC", 0.28), body(shell, c, 12, 52, top="#4A505C", bot="#22252C"), sheen(shell, 12, 52, 0.2),
            L(circle(24, 31, 15.4), fill="#000000", alpha=0.45),
            L(circle(24, 31, 14), fill=Rad(19, 25, 20, [(0, "#5A606C", 1), (1, "#2C3038", 1)]), stroke="#FFFFFF", sw=0.7, salpha=0.35),
            L(circle(24, 31, 9.8), stroke="#000000", sw=0.6, salpha=0.4),
            L(circle(24, 31, 3.2), fill="#1A1C22", stroke=c, sw=0.5, salpha=0.5),
            L("M14.6,23.2 A13,13 0 0 1 21.2,18.4", stroke="#FFFFFF", sw=1.4, salpha=0.4),
            L(rrect(44, 17.6, 9, 4.4, 2.2), fill="#2C3038", stroke=c, sw=0.5, salpha=0.6),
            L(rrect(44, 25, 9, 4.4, 2.2), fill="#2C3038", stroke=c, sw=0.5, salpha=0.6),
            *btn(48.5, 39.4, 3.8, "#6A707C"), L(circle(41.2, 21.6, 0.7), fill=GREEN),
            L("M9,49 H17 M47,49 H55", stroke="#000000", sw=1.6, salpha=0.7)]

def playstation_2():
    c = PSBLUE
    shell = rrect(19, 3, 24, 54, 1.6)
    out = [glow(c, 0.34), body(shell, c, 3, 57, top="#2A2E38", bot="#07080B"), sheen(shell, 3, 57, 0.14),
           L("M28,5 V55", stroke="#000000", sw=1, salpha=0.8)]
    for i in range(10):
        y = 7 + i * 4.6
        out.append(L(f"M20.6,{f(y)} H26.6", stroke="#4A505C", sw=1.4, salpha=0.9))
    out += [L("M35.6,7 V44", stroke="#000000", sw=1.6), L("M36.6,7 V44", stroke="#4A505C", sw=0.6, salpha=0.7),
            L(rrect(32.4, 47.4, 7, 2, 1), fill=c), L(rrect(32.4, 51.2, 7, 2, 1), fill="#2A2E38", stroke=c, sw=0.4, salpha=0.6),
            L(rrect(14, 57, 34, 3.6, 1.6), fill=Lin(0, 57, 0, 61, [(0, "#3A3F4A", 1), (1, "#101216", 1)]), stroke=c, sw=0.8, salpha=0.7),
            halo(36, 48.5, 6, c, 0.7)]
    return out

# ---- Sega and friends
def genesis():
    c = CRIMSON
    shell = ("M8,27 C8,18.5 16,16.5 32,18.6 C48,16.5 56,18.5 56,27 C56,37 50,47.5 42,45.4 "
             "C38,44.4 36,41 32,41 C28,41 26,44.4 22,45.4 C14,47.5 8,37 8,27 Z")
    return [glow(c, 0.3), body(shell, c, 17, 46), sheen(shell, 17, 46, 0.18),
            L(circle(19.4, 29, 7.6), fill=Rad(17, 26, 9, [(0, "#3A3E48", 1), (1, "#101216", 1)]), stroke="#000000", sw=0.6),
            *dpad(19.4, 29, 10.6), *btn(39.2, 33, 2.6, INK), *btn(44.6, 30.2, 2.6, INK), *btn(50, 27.4, 2.6, INK),
            L(rrect(29.4, 24.2, 5.2, 2, 1), fill=c, alpha=0.9)]

def master_system():
    c = "#FF5E3A"
    shell = poly([(3, 31), (13, 18), (51, 18), (61, 31), (61, 47), (3, 47)])
    return [glow(c, 0.28), body(shell, c, 18, 47), sheen(shell, 18, 40, 0.16),
            L(poly([(15, 21), (49, 21), (54, 28), (10, 28)]), fill="#07080B", stroke="#FFFFFF", sw=0.4, salpha=0.15),
            L(rrect(20, 23, 24, 2.6, 1.3), fill="#000000", stroke=c, sw=0.5, salpha=0.6),
            L("M4,35 H60", stroke=c, sw=2.6), L("M4,35 H60", stroke=hi(c), sw=0.8, salpha=0.7),
            L(rrect(8, 39.6, 7, 3.6, 1), fill="#2A2E38", stroke=INK, sw=0.5, salpha=0.4),
            L(rrect(17, 39.6, 7, 3.6, 1), fill="#2A2E38", stroke=INK, sw=0.5, salpha=0.4),
            L(rrect(36, 40.4, 20, 2.2, 1.1), fill="#000000")]

def game_gear():
    c = TEAL
    shell = rrect(2, 15, 60, 34, 10)
    out = [glow(c, 0.3), body(shell, c, 15, 49), sheen(shell, 15, 49, 0.18),
           L(rrect(17, 18, 30, 28, 3), fill="#07080B", stroke="#FFFFFF", sw=0.4, salpha=0.15),
           *glass(21, 21.4, 22, 18.6, 1, c, bezel=False), L(circle(19.4, 43.6, 0.7), fill=RED),
           *dpad(10, 32, 9), *btn(50.2, 35, 2.8, "#3A3F4A"), *btn(55.8, 30.4, 2.8, "#3A3F4A"),
           L(rrect(48.6, 22.8, 4, 1.6, 0.8), fill=PSBLUE)]
    for i in range(3):
        for j in range(3):
            out.append(L(circle(49 + i * 2.4, 41 + j * 2.2, 0.55), fill="#000000", alpha=0.85))
    return out

def saturn():
    c = GOLD
    tilt, rx, ry = -16, 28.5, 8.2
    ca, sa = math.cos(math.radians(tilt)), math.sin(math.radians(tilt))
    lx, ly = 32 - rx * ca, 32 - rx * sa; rx_, ry_ = 32 + rx * ca, 32 + rx * sa
    back = f"M{f(lx)},{f(ly)} A{f(rx)},{f(ry)} {f(tilt)} 0 1 {f(rx_)},{f(ry_)}"
    front = f"M{f(rx_)},{f(ry_)} A{f(rx)},{f(ry)} {f(tilt)} 0 1 {f(lx)},{f(ly)}"
    ring = Lin(4, 0, 60, 0, [(0, c, 0.35), (0.5, hi(c), 1), (1, c, 0.35)])
    return [glow(c, 0.3),
            L(back, stroke=ring, sw=3.4, salpha=0.75), L(back, stroke="#000000", sw=0.8, salpha=0.4),
            L(circle(32, 32, 15.4), fill=Rad(26, 25, 24, [(0, "#FFE8B8", 1), (0.35, c, 1), (0.8, "#B8641C", 1), (1, "#5A2A0C", 1)]),
              stroke=hi(c), sw=0.8, salpha=0.6),
            L("M17.6,27.6 C26,25.6 38,24.8 46.6,26.4", stroke="#7A3A10", sw=1.6, salpha=0.5),
            L("M17,34.2 C26,32.6 38,32 47.2,33.6", stroke="#7A3A10", sw=1.2, salpha=0.4),
            L("M20.6,22.6 A13,13 0 0 1 28.6,17.6", stroke="#FFFFFF", sw=1.6, salpha=0.55),
            L(front, stroke=ring, sw=3.4), L(front, stroke="#FFFFFF", sw=0.7, salpha=0.6)]

def dreamcast():
    c = SUNSET
    shell = ("M13,15 H51 C57,15 60.4,19.6 59.4,25.4 L56.6,42.6 C55.6,49.2 50,51.6 46,47.6 L42,43.6 H22 L18,47.6 "
             "C14,51.6 8.4,49.2 7.4,42.6 L4.6,25.4 C3.6,19.6 7,15 13,15 Z")
    return [glow(c, 0.3), body(shell, c, 15, 50), sheen(shell, 15, 50, 0.2),
            L(rrect(24.4, 18, 15.2, 21.4, 2.4), fill="#05060A", stroke=c, sw=0.8, salpha=0.7),
            *glass(26.6, 20.6, 10.8, 9, 0.8, c, bezel=False),
            *btn(29.4, 34.8, 1.3, INK), *btn(34.6, 34.8, 1.3, INK),
            *stick(14.4, 25, 4.2), *dpad(15.4, 37.6, 7.4),
            *btn(49.6, 25.8, 1.8, SNES_X), *btn(46, 29.4, 1.8, SNES_B), *btn(53.2, 29.4, 1.8, SNES_A), *btn(49.6, 33, 1.8, SNES_Y)]

# ---- libraries
def android_games():
    c = DROID
    grip_l = rrect(3, 18, 16, 30, (9, 2, 2, 11)); grip_r = rrect(45, 18, 16, 30, (2, 9, 11, 2))
    phone = rrect(17, 19, 30, 26, 3)
    return [glow(c, 0.32),
            body(grip_l, c, 18, 48), body(grip_r, c, 18, 48), sheen(grip_l, 18, 48), sheen(grip_r, 18, 48),
            L(phone, fill="#05060A", stroke="#FFFFFF", sw=0.9, salpha=0.5), *glass(19.4, 21.4, 25.2, 21.2, 1.6, c, bezel=False),
            L(poly([(28.6, 26.6), (37.6, 32), (28.6, 37.4)]), fill="#06220F", alpha=0.8),
            *stick(10.6, 26.4, 3.4), *dpad(11, 38.6, 6.6),
            *diamond(53, 26.4, 3, 1.35, [INK, INK, c, INK]), *stick(53.4, 38.6, 3.4)]

def android_apps():
    tiles = [(8, 8, DROID), (35, 8, "#4CC9FF"), (8, 35, AMBER), (35, 35, ROSE)]
    out = [glow(DROID, 0.3)]
    for x, y, c in tiles:
        out += [L(rrect(x + 0.8, y + 1.6, 21, 21, 6.4), fill="#000000", alpha=0.6),
                L(rrect(x, y, 21, 21, 6.4), fill=Lin(x, y, x + 21, y + 21, [(0, hi(c), 1), (0.45, c, 1), (1, lo(c), 1)]),
                  stroke="#FFFFFF", sw=0.8, salpha=0.45),
                L(rrect(x + 1, y + 0.9, 19, 8.6, 5.6), fill=Lin(0, y, 0, y + 10, [(0, "#FFFFFF", 0.5), (1, "#FFFFFF", 0)]))]
    out += [L("M13.4,14.4 H23.6 Q25,14.4 25,15.8 V20.6 Q25,22 23.6,22 H17.6 L14.6,24.6 V22 H13.4 Q12,22 12,20.6 V15.8 Q12,14.4 13.4,14.4 Z",
              fill="#06220F", alpha=0.55),
            L(poly([(43, 13.4), (51, 18.5), (43, 23.6)]), fill="#06202C", alpha=0.55),
            L("M17,41.6 V47.6 M17,41.6 L22.4,40.4 V46.4", stroke="#2A1A04", sw=1.4, salpha=0.6),
            L(circle(15.6, 47.8, 1.8), fill="#2A1A04", alpha=0.6), L(circle(21, 46.6, 1.8), fill="#2A1A04", alpha=0.6),
            L("M45.5,40.6 L46.9,43.6 L50.2,44 L47.7,46.2 L48.4,49.4 L45.5,47.8 L42.6,49.4 L43.3,46.2 L40.8,44 L44.1,43.6 Z",
              fill="#2C0A12", alpha=0.55)]
    return out

def pc():
    c = BLUE
    mon = rrect(4, 7, 56, 37, 3.6)
    pad = ("M25.6,20.6 H38.4 C41.8,20.6 43.6,23.4 44.4,27 C45.2,30.6 44.6,33.2 42.4,33.6 C40.6,34 39.4,32.2 38,30.4 "
           "H26 C24.6,32.2 23.4,34 21.6,33.6 C19.4,33.2 18.8,30.6 19.6,27 C20.4,23.4 22.2,20.6 25.6,20.6 Z")
    return [glow(c, 0.3),
            L("M27,44 L25.4,52 H38.6 L37,44 Z", fill=Lin(0, 44, 0, 52, [(0, "#3A3F4A", 1), (1, "#15171C", 1)]), stroke=c, sw=0.7, salpha=0.5),
            L(rrect(16, 51.4, 32, 4, 2), fill=Lin(0, 51, 0, 56, [(0, "#3A3F4A", 1), (1, "#101216", 1)]), stroke=c, sw=1, salpha=0.7),
            body(mon, c, 7, 44), *glass(8, 11, 48, 29, 1.4, c, bezel=False),
            L(pad, fill="#04121C", alpha=0.75),
            L("M24.8,25.6 V28.6 M23.3,27.1 H26.3", stroke=hi(c), sw=1),
            L(circle(38, 25.8, 1), fill=hi(c)), L(circle(40.6, 28.2, 1), fill=hi(c)),
            L(circle(32, 42, 0.7), fill=c)]

def moonlight():
    c = MOON
    moon = crescent(27, 34, 18, 36, 27, 15)
    out = [glow(c, 0.34, cx=28, cy=34),
           L(moon, fill=Lin(10, 18, 40, 52, [(0, "#FFFFFF", 1), (0.35, hi(c), 1), (0.75, c, 1), (1, "#4A5AB8", 1)]),
             stroke="#FFFFFF", sw=0.8, salpha=0.6),
           L(circle(16.6, 40.6, 2.2), fill="#5A6AC8", alpha=0.45), L(circle(21.4, 47.4, 1.4), fill="#5A6AC8", alpha=0.4),
           L(circle(13.6, 30.6, 1.2), fill="#5A6AC8", alpha=0.4)]
    for i, r in enumerate((7, 12.5, 18)):
        out.append(L(f"M{f(40 + r * math.cos(math.radians(-80)))},{f(26 + r * math.sin(math.radians(-80)))} "
                     f"A{f(r)},{f(r)} 0 0 1 {f(40 + r * math.cos(math.radians(-10)))},{f(26 + r * math.sin(math.radians(-10)))}",
                     stroke=Lin(40, 8, 58, 26, [(0, hi(c), 1), (1, c, 0.7)]), sw=2.6, salpha=1 - i * 0.22))
    out += [L(circle(40, 26, 1.8), fill=hi(c)), halo(40, 26, 5, c, 0.6),
            L("M51,44 L52,46.4 L54.4,47.4 L52,48.4 L51,50.8 L50,48.4 L47.6,47.4 L50,46.4 Z", fill="#FFFFFF", alpha=0.85)]
    return out

def folder():
    c = AMBER
    back = "M7,17 Q7,13 11,13 H23.6 Q25.4,13 26.6,14.4 L29.4,17.6 H53 Q57,17.6 57,21.6 V48 Q57,52 53,52 H11 Q7,52 7,48 Z"
    front = "M7.6,27.4 Q8,24 11.4,24 H52.8 Q57,24 56.4,28 L54.4,48.4 Q54,52 50.4,52 H11.4 Q7.6,52 7.6,48 Z"
    return [glow(c, 0.3),
            L(back, fill=Lin(0, 13, 0, 52, [(0, "#3A3426", 1), (1, "#16130C", 1)]), stroke=c, sw=1.2, salpha=0.6),
            L(rrect(12, 19.6, 40, 8, 1.4), fill="#F4F1EA", alpha=0.18),
            L(front, fill=Lin(7, 24, 57, 52, [(0, hi(c), 1), (0.45, c, 1), (1, lo(c), 1)]),
              stroke=Lin(0, 24, 0, 52, [(0, "#FFFFFF", 0.9), (1, c, 0.6)]), sw=1.4),
            L("M11.6,26.6 H52.4", stroke="#FFFFFF", sw=1.2, salpha=0.55),
            L("M24,40 H40", stroke=lo(c), sw=2, salpha=0.6)]

def library():
    cards = [(-16, VIOLET, 22, 36), (14, AMBER, 42, 36), (0, TEAL, 32, 34)]
    out = [glow(TEAL, 0.3)]
    for deg, c, cx, cy in cards:
        x, y, w, h = cx - 11, cy - 15, 22, 30
        out += [L(rrect_rot(x + 0.8, y + 1.6, w, h, 3.6, deg), fill="#000000", alpha=0.6),
                L(rrect_rot(x, y, w, h, 3.6, deg), fill=Lin(0, y, 0, y + h, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
                  stroke=Lin(0, y, 0, y + h, [(0, "#FFFFFF", 0.9), (0.3, c, 1), (1, c, 0.5)]), sw=1.6),
                L(rrect_rot(x + 3.4, y + 3.6, w - 6.8, h * 0.5, 1.6, deg, cx, cy),
                  fill=Lin(x, y, x + w, y + h * 0.6, [(0, hi(c), 1), (0.55, c, 1), (1, lo(c), 1)])),
                L(rrect_rot(x + 5, y + h * 0.66, w - 10, 1.8, 0.9, deg, cx, cy), fill=c, alpha=0.7),
                L(rrect_rot(x + 5, y + h * 0.66 + 3.6, w - 14, 1.8, 0.9, deg, cx, cy), fill=c, alpha=0.45)]
    return out

def console():
    c = AMBER
    shell = ("M19,18 H45 C52,18 56.4,23.6 58.6,32 C60.8,40.4 60,47.4 54.6,48.6 C50.4,49.6 47.6,45.6 44.6,41.6 "
             "H19.4 C16.4,45.6 13.6,49.6 9.4,48.6 C4,47.4 3.2,40.4 5.4,32 C7.6,23.6 12,18 19,18 Z")
    return [glow(c, 0.3), body(shell, c, 18, 49), sheen(shell, 18, 49, 0.18),
            *dpad(18.6, 28.6, 8.6), *diamond(45.4, 28.6, 3.4, 1.6, [SNES_X, SNES_A, SNES_B, SNES_Y]),
            *stick(25.4, 38, 3.8), *stick(38.6, 38, 3.8), pill(28.4, 26, 3, 1.2), pill(35.6, 26, 3, 1.2)]

# name -> (drawing, accent, what it stands for)
MARKS = {
    "nintendo_3ds": (n3ds, ROSE, "Nintendo 3DS-style clamshell"),
    "nintendo_ds": (nds, ORANGE, "Nintendo DS-style clamshell"),
    "game_boy": (game_boy, LIME, "Game Boy-style portrait handheld"),
    "game_boy_color": (game_boy_color, GRAPE, "Game Boy Color-style handheld"),
    "game_boy_advance": (gba, INDIGO, "Game Boy Advance-style landscape handheld"),
    "nes": (nes, RED, "NES-style controller"),
    "snes": (snes, LAVENDER, "Super NES-style controller"),
    "nintendo_64": (n64, GREEN, "Nintendo 64-style three-handled controller"),
    "gamecube": (gamecube, "#9B7BFF", "GameCube-style cube console"),
    "wii": (wii, SKY, "Wii-style remote"),
    "nintendo_switch": (switch, JOY_R, "Switch-style tablet with detachable controllers"),
    "playstation": (playstation, SILVER, "PlayStation-style top-loading console"),
    "playstation_2": (playstation_2, PSBLUE, "PlayStation 2-style tower console"),
    "psp": (psp, "#6FC3FF", "PSP-style widescreen handheld"),
    "genesis": (genesis, CRIMSON, "Genesis-style three-button controller"),
    "master_system": (master_system, "#FF5E3A", "Master System-style console"),
    "game_gear": (game_gear, TEAL, "Game Gear-style landscape handheld"),
    "saturn": (saturn, GOLD, "A ringed planet for Saturn"),
    "dreamcast": (dreamcast, SUNSET, "Dreamcast-style controller with a memory-card screen"),
    "android_games": (android_games, DROID, "A phone held in a controller"),
    "android_apps": (android_apps, DROID, "A grid of apps"),
    "pc": (pc, BLUE, "A monitor showing a controller"),
    "moonlight": (moonlight, MOON, "A crescent moon sending a stream"),
    "folder": (folder, AMBER, "A folder"),
    "library": (library, TEAL, "A fanned stack of games"),
    "console": (console, AMBER, "A game controller"),
}

def platform_assets():
    return [Asset(f"mark_{name}", 64, 64, fn(), dp=(48, 48), note=f"Afterglow mark: {about}")
            for name, (fn, _, about) in MARKS.items()]

if __name__ == "__main__":
    import os
    repo = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    for a in platform_assets():
        open(f"{repo}/app/src/main/res/drawable/{a.name}.xml", "w").write(to_vd(a))
        open(f"{repo}/art/svg/marks/{a.name}.svg", "w").write(to_svg(a))
    print("wrote", len(MARKS), "marks")
