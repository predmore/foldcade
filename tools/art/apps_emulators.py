"""Emulators and game front-ends, as lit objects. Each is inspired by the app's own icon, never a copy of it. See apps.py."""
import math
from art import *
from palette import *
from objects import *
from marks import rr, ci, stick, bezel, hinge, _ring_half
from marks import moonlight, pc, droiddeck, MOON, DECK

# ---- shape helpers for this module
def rpoly(pts, r):
    """A polygon with every corner rounded by [r], or by r[i] at corner i."""
    n = len(pts); rs = r if isinstance(r, (list, tuple)) else [r] * n
    def toward(a, b, dist):
        dx, dy = b[0] - a[0], b[1] - a[1]; t = min(dist, math.hypot(dx, dy) / 2) / math.hypot(dx, dy)
        return (a[0] + dx * t, a[1] + dy * t)
    d = ""
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        a, b = toward(p1, p0, rs[i]), toward(p1, p2, rs[i])
        d += ("M" if i == 0 else "L") + f"{f(a[0])},{f(a[1])} Q{f(p1[0])},{f(p1[1])} {f(b[0])},{f(b[1])} "
    return d + "Z"

def squash(pts, sy, cy):
    """Points pressed flat toward the line y = [cy], for a disc seen at an angle."""
    return [(x, cy + (y - cy) * sy) for x, y in pts]

def arc_pts(cx, cy, r, a0, a1, n=12):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n)))
            for i in range(n + 1)]

def band(cx, cy, r, y0, y1):
    """The part of a disc between two heights."""
    x0, x1 = math.sqrt(max(r * r - (y0 - cy) ** 2, 0)), math.sqrt(max(r * r - (y1 - cy) ** 2, 0))
    return (f"M{f(cx - x0)},{f(y0)} H{f(cx + x0)} A{f(r)},{f(r)} 0 0 1 {f(cx + x1)},{f(y1)} "
            f"H{f(cx - x1)} A{f(r)},{f(r)} 0 0 1 {f(cx - x0)},{f(y0)} Z")

def inset_blocks(rows, x, y, cell, gap, sub=16):
    """Pixel art whose shape is pulled in from every edge by [gap] / 2, so pieces laid side by side show a seam."""
    on = {(i, j) for j, row in enumerate(rows) for i, ch in enumerate(row) if ch == "X"}
    m = max(1, round(gap / 2 / (cell / sub)))
    fine = [["X" if all(((si + di) // sub, (sj + dj) // sub) in on for di in range(-m, m + 1) for dj in range(-m, m + 1)) else "."
             for si in range(len(rows[0]) * sub)] for sj in range(len(rows) * sub)]
    return pixels(["".join(r) for r in fine], x, y, cell / sub)

def turned(parts, deg, cx=32, cy=32):
    """The same parts, turned about a point. Each part still lights from above."""
    out = []
    for p in parts:
        if p.kind == "shadow" or not isinstance(p.d, str): out.append(p); continue
        out.append(Part(p.kind, xf(p.d, deg=deg, cx=cx, cy=cy), p.color, p.sym, p.obj, p.gloss))
    return out

# ---------------- RetroArch ----------------
INVADER = ("..X.....X..", "...X...X...", "..XXXXXXX..", ".XX.XXX.XX.", "XXXXXXXXXXX", "X.XXXXXXX.X", "X.X.....X.X", "...XX.XX...")

def retroarch(c):
    """RetroArch: a lit space invader with glowing eyes, after the invader on its icon."""
    cell = 5.2; x = 32 - 11 * cell / 2; y = 9
    eye = lambda col: screen(rr(x + col * cell + 0.5, y + 3 * cell + 0.5, cell - 1, cell - 1, 1), "#9EF0FF", sym=0)
    return [shadow(32, 58, 25, 3), body(pixels(INVADER, x, y, cell), c), eye(3), eye(7)]

# ---------------- Nintendo handheld emulators ----------------
def azahar(c):
    """Azahar: a six-petal blossom, cream petals around a green-gold heart."""
    petal = "M32,31 C25,26 20.5,15 24,9.5 C26.5,5.5 29.5,5 32,7.5 C34.5,5 37.5,5.5 40,9.5 C43.5,15 39,26 32,31 Z"
    out = [shadow(32, 59, 22, 3)]
    for k, shade in ((1, tone(c, 0.2)), (3, tone(c, 0.2)), (5, tone(c, 0.2)), (0, tone(c, 0.55)), (2, tone(c, 0.55)), (4, tone(c, 0.55))):
        out.append(body(xf(petal, deg=60 * k, cx=32, cy=31), shade, gloss=0.2))
    return out + [body(ci(32, 31, 7.5), "#B9D84A"), control(ci(32, 31, 3.6), "#E6F28A")]

def citra(c):
    """Citra: half an orange, its cut face tilted up to show the segments."""
    cx, cy, R = 32, 27, 26
    out = [shadow(32, 58, 24, 3), body(f"M{cx - R},{cy} A{R},{R + 2} 0 0 0 {cx + R},{cy} Z", c),
           body(ellipse(cx, cy, R, 11.5), tone(c, 0.15), gloss=0.1),
           body(ellipse(cx, cy + 0.6, 23.5, 10), "#FFF0D2", gloss=0.1)]
    for i in range(8):
        a0, a1 = i * 45 + 5, i * 45 + 40
        pts = [(cx + 2.4 * math.cos(math.radians((a0 + a1) / 2)), cy + 2.4 * math.sin(math.radians((a0 + a1) / 2)))] + arc_pts(cx, cy, 21, a0, a1, 6)
        out.append(screen(rpoly(squash(pts, 9 / 21, cy + 0.6), [0.6] + [2] + [0] * 5 + [2]), "#FFB43A"))
    return out

def melonds(c):
    """melonDS: a watermelon split open like a clamshell, a screen in each half's flesh."""
    top = lambda rx, ry: f"M{32 - rx},29 A{rx},{ry} 0 0 1 {32 + rx},29 Z"
    bot = lambda rx, ry: f"M{32 - rx},33.5 A{rx},{ry} 0 0 0 {32 + rx},33.5 Z"
    out = [shadow(32, 60, 26, 3)]
    for half in (top, bot):
        out += [body(half(28.5, 25), "#3FC468", gloss=0.2), body(half(25.5, 22), "#F3F0C6", gloss=0.1), body(half(23, 19.5), c)]
    seed = lambda x, y, deg: control(ellipse(x, y, 1.5, 2.4, deg), "#2A1F24")
    return out + [hinge(29.4, 14, 50), *bezel(20, 13, 24, 14, 2, c), *bezel(20, 35.5, 24, 14, 2, c),
                  seed(13.5, 25, -20), seed(50.5, 25, 20), seed(14.5, 38, 20), seed(49.5, 38, -20)]

PAPER_WHITE = "#E3E8F4"

def my_boy(c):
    """My Boy!: a white landscape handheld, tilted, its screen lit in the app's blue."""
    shell = "M14,18 H50 Q61,18 61,32 Q61,47 50,47 H14 Q3,47 3,32 Q3,18 14,18 Z"
    parts = [body(shell, PAPER_WHITE), *bezel(19.5, 21.5, 25, 22, 2, PAPER_WHITE, tone(c, 0.45)),
             control(plus(11, 30.5, 4, 2.9)), control(ci(54, 29.5, 2.8), c), control(ci(48.8, 35, 2.8), c)]
    return [shadow(32, 55, 27, 3)] + turned(parts, -14)

def my_oldboy(c):
    """My OldBoy!: the white portrait handheld to My Boy!'s landscape one, tilted the other way."""
    shell = "M17,4 H47 Q51,4 51,8 V49 Q51,59 41,59 H23 Q13,59 13,49 V8 Q13,4 17,4 Z"
    parts = [body(shell, PAPER_WHITE), *bezel(17.5, 8.5, 29, 22, 2.5, PAPER_WHITE, tone(c, 0.45)),
             control(plus(22, 41, 4.6, 3.2)), control(ci(42.5, 38.5, 3), c), control(ci(36.5, 42.5, 3), c),
             control(capsule(27, 52, 30, 52, 2)), control(capsule(34, 52, 37, 52, 2))]
    return [shadow(32, 61, 21, 3)] + turned(parts, 12)

CHEESE = "#FFC23E"

def pizza_boy(c):
    """Pizza Boy: a slice of pepperoni pizza. One drawing for its three builds; the light says which."""
    return [shadow(32, 60, 16, 2.5),
            body("M9,17 Q32,11 55,17 L34.4,56.5 Q32,61 29.6,56.5 Z", CHEESE),
            body("M7,12 Q32,2 57,12 Q61,13.6 59.6,17.6 L59,19.5 Q32,10.5 5,19.5 L4.4,17.6 Q3,13.6 7,12 Z", "#E8A050", gloss=0.25),
            *[control(ci(x, y, r), "#E2483E") for x, y, r in ((21, 25, 4.3), (41.5, 25.5, 4.3), (31.5, 38.5, 4))]]

# ---- the EX family: one chunky chip, each carrying a cue of its system in a window
def _chip(c, cue):
    pin = lambda x, y, w, h: rr(x, y, w, h, 1.8)
    rows = (19, 30, 41)
    return [shadow(32, 59, 25, 3),
            body("".join(pin(4, i - 2.4, 10, 4.8) + pin(50, i - 2.4, 10, 4.8) for i in rows), tone(c, -0.3), gloss=0.1, sym=1),
            body("".join(pin(i - 2.4, 4, 4.8, 10) for i in rows), tone(c, -0.3), gloss=0.1, sym=1),
            body("".join(pin(i - 2.4, 50, 4.8, 10) for i in rows), tone(c, -0.3), gloss=0.1, sym=1),
            body(rr(10.5, 10.5, 43, 43, 7), c), recess(rr(17, 17, 30, 30, 5), c)] + cue

GBA_SHELL = "M14,17 H50 Q62,17 62,32 Q62,48 50,48 H14 Q2,48 2,32 Q2,17 14,17 Z"
GBC_SHELL = "M15,5 H49 Q53,5 53,9 V50 Q53,60 43,60 H21 Q11,60 11,50 V9 Q11,5 15,5 Z"

def gba_emu(c):
    """GBA.emu: the EX chip, a little landscape handheld in its window."""
    return _chip(c, [body(xf(GBA_SHELL, s=0.46, cx=32, cy=32.5), tone(c, 0.35), gloss=0.2),
                     screen(rr(26.6, 27.6, 10.8, 9.6, 1), GLASS, sym=0), control(plus(22.2, 31.6, 2.7, 2)),
                     control(ci(42.3, 31, 1.5), tone(c, -0.3)), control(ci(39.6, 34, 1.5), tone(c, -0.3))])

def gbc_emu(c):
    """GBC.emu: the EX chip, a little portrait handheld in its window."""
    return _chip(c, [body(xf(GBC_SHELL, s=0.44, cx=32, cy=32.5), tone(c, 0.35), gloss=0.2),
                     screen(rr(27.2, 22.2, 9.6, 8, 1), "#FFE9B0", sym=0), control(plus(27.6, 37.4, 2.6, 1.9)),
                     control(ci(37.6, 36.2, 1.5), tone(c, -0.3)), control(ci(34.8, 38.4, 1.5), tone(c, -0.3))])

def nes_emu(c):
    """NES.emu: the EX chip, a d-pad and two red buttons in its window."""
    return _chip(c, [control(plus(25, 32, 4.6, 3.2), tone(c, 0.65)),
                     control(ci(35.5, 34, 2.8), "#FF4D5A"), control(ci(41.7, 34, 2.8), "#FF4D5A")])

def snes9x_ex(c):
    """Snes9x EX+: the EX chip, four round buttons in blue, red, yellow, and green."""
    return _chip(c, [control(d, col) for d, col in zip(diamond(32, 32, 6.4, 3.9), ("#4C8DFF", "#FF4D5A", "#FFC93C", "#3EE68A"))])

def md_emu(c):
    """MD.emu: the EX chip, three buttons climbing in a row."""
    return _chip(c, [control(ci(24, 32, 4.6)), recess(plus(24, 32, 3.2, 1.6), RUBBER),
                     *[control(ci(x, y, 2.5), tone(c, 0.5)) for x, y in ((33.5, 36), (38.5, 32), (43.3, 28))]])

def saturn_emu(c):
    """Saturn.emu: the EX chip, a ringed planet in its window."""
    back, front = _ring_half(False, 12.5, 3.8, 9.4, 2, cx=32, cy=32), _ring_half(True, 12.5, 3.8, 9.4, 2, cx=32, cy=32)
    return _chip(c, [body(back, tone(c, -0.2), gloss=0.1), body(ci(32, 32, 6.8), tone(c, 0.2)), body(front, tone(c, 0.55), gloss=0.2)])

# ---------------- Nintendo 64 ----------------
def m64plus_fz(c):
    """Mupen64Plus FZ: a cube seen from a corner, lime on top, green and cyan on its sides."""
    t, l, r_, ctr, b = (32, 5), (9, 18), (55, 18), (32, 31), (32, 58)
    return [shadow(32, 59, 23, 3),
            body(rpoly([l, ctr, b, (9, 45)], 2.5), "#36C66A", gloss=0.15),
            body(rpoly([ctr, r_, (55, 45), b], 2.5), "#2EC2E6", gloss=0.15),
            body(rpoly([t, r_, ctr, l], 2.5), c, gloss=0.3)]

def mupen64plus_ae(c):
    """Mupen64Plus AE: four chunky blocks fitted together into a square, in lime, cyan, yellow, and red."""
    cell, x, y = 10.5, 10, 7
    pieces = ((("XXX.", "X...", "....", "...."), c), (("...X", "...X", "...X", "...X"), "#2EC2E6"),
              (("....", ".XX.", ".XX.", "...."), "#FFD23C"), (("....", "....", "X...", "XXX."), "#FF5A4E"))
    tops = [(inset_blocks(rows, x, y, cell, 2.2), col) for rows, col in pieces]
    return ([shadow(32, 59, 26, 3)] + [body(xf(d, dy=4.2), tone(col, -0.45), gloss=0, sym=None) for d, col in tops]
            + [body(d, col, gloss=0.25) for d, col in tops])

# ---------------- GameCube and Wii ----------------
DOLPHIN = ("M61,31.5 Q59,29 55.2,28.2 C54.8,21.5 51,17.2 46,15.6 C42.5,14.3 39,13.5 35.5,13.4 C32,9.5 27.5,6 22.5,5 "
           "C24.6,8.6 25.4,12.4 25,16.4 C18,21 13,30 11.2,40 C10.2,43.6 6.5,45.6 1.5,46 Q6.5,49.6 11,51 Q13,55 17,58.5 "
           "C17.8,52.5 16.6,47 17,42 C19,33 24.5,25.5 32.5,23.4 C40,21.6 46,24 50.4,28.6 Q55,32.6 61,31.5 Z")
FLIPPER = "M40,23.4 Q39.5,30.5 33,34.5 Q34.5,29 33.5,23.6 Z"

def dolphin(c):
    """Dolphin: a glossy dolphin mid-leap."""
    return [shadow(32, 60, 22, 2.6), body(DOLPHIN, c, gloss=0.42), body(FLIPPER, tone(c, -0.12), gloss=0.15),
            control(ci(49.5, 21.5, 1.7))]

def dolphin_mmjr(c):
    """Dolphin MMJR: the dolphin leaping through a neon ring."""
    neon = "#FF8AF0"
    hoop = lambda front: _ring_half(front, 14.5, 6.6, 10.6, 3.4, deg=33, cx=19.5, cy=30.5)
    return [shadow(32, 60, 22, 2.6), screen(hoop(False), tone(neon, -0.25), sym=1),
            body(DOLPHIN, c, gloss=0.42), body(FLIPPER, tone(c, -0.12), gloss=0.15), control(ci(49.5, 21.5, 1.7)),
            screen(hoop(True), neon, sym=1)]

# ---------------- Switch ----------------
def frond(px, py, heading, length, droop, w=3.8, n=12):
    """A palm leaf from (px, py), setting off at [heading] degrees and sagging [droop] by its tip, tapering to a point."""
    hx, hy = math.cos(math.radians(heading)), math.sin(math.radians(heading))
    c1 = (px + hx * length * 0.6, py + hy * length * 0.6 - droop * 0.35)
    end = (px + hx * length, py + hy * length + droop)
    mid = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * px + 2 * (1 - t) * t * c1[0] + t * t * end[0]
        y = (1 - t) ** 2 * py + 2 * (1 - t) * t * c1[1] + t * t * end[1]
        dx = 2 * (1 - t) * (c1[0] - px) + 2 * t * (end[0] - c1[0]); dy = 2 * (1 - t) * (c1[1] - py) + 2 * t * (end[1] - c1[1])
        k = math.hypot(dx, dy); half = w / 2 * math.sin(math.pi * min(1, 0.15 + t * 0.95)) ** 0.7 if t < 1 else 0
        mid.append((x, y, -dy / k * half, dx / k * half))
    return poly([(x + nx, y + ny) for x, y, nx, ny in mid] + [(x - nx, y - ny) for x, y, nx, ny in mid[::-1]])

def eden(c):
    """Eden: a striped sunset sun, pink to violet to blue, with a palm in front of it."""
    cx, cy, R = 30, 31, 25
    stops = ((6, 26, "#FF5FA2"), (28, 32.5, "#E05AD8"), (35, 39.5, "#B45CFF"), (42.5, 46, "#8466FF"), (49.5, 52.5, "#5B7BFF"))
    out = [shadow(32, 59, 24, 3)] + [body(band(cx, cy, R, y0, y1), col, gloss=0.2) for y0, y1, col in stops]
    trunk = "M43.6,55.5 Q41.6,40 44.6,21 L47.6,21 Q45.4,40 48.4,55.5 Z"
    px, py = 46, 21.5
    fronds = [frond(px, py, 195, 17, 9), frond(px, py, 222, 14, 4), frond(px, py, 172, 13, 9, 3.4),
              frond(px, py, -15, 15, 9), frond(px, py, -45, 12, 4), frond(px, py, 10, 11.5, 8, 3.4), frond(px, py, -100, 8, 2, 3.2)]
    dark = "#2B2236"
    return out + [control(trunk, dark)] + [control(d, dark) for d in fronds]

# ---------------- PlayStation ----------------
DUCK = ("....XXXX....", "...XXXXXX...", "...XXXXXX...", "...XXXXXX...", "....XXXX....", "X...XXXX....",
        "XX.XXXXXXXX.", "XXXXXXXXXXXX", ".XXXXXXXXXX.", "..XXXXXXXX..")
BEAK = ("............", "............", ".........XX.", ".........XXX")

def duckstation(c):
    """DuckStation: a voxel duck, yellow with an orange beak."""
    cell, x, y = 4.6, 32 - 6 * 4.6, 7.5
    d, b = pixels(DUCK, x, y, cell), pixels(BEAK, x, y, cell)
    return [shadow(32, 59, 24, 3), body(xf(d, dx=1.6, dy=2.6), tone(c, -0.4), gloss=0, sym=None),
            body(xf(b, dx=1.6, dy=2.6), tone("#FF8A2A", -0.4), gloss=0, sym=None),
            body(d, c, gloss=0.25), body(b, "#FF8A2A", gloss=0.25),
            control(rr(x + 6 * cell + 0.6, y + 2 * cell + 0.6, cell - 1.2, cell - 1.2, 1))]

def nethersx2(c):
    """NetherSX2: a glossy red cube, seen face-on from a little above."""
    front, top, side = [(7, 22), (43, 22), (43, 58), (7, 58)], [(7, 22), (20, 8), (57, 8), (43, 22)], [(43, 22), (57, 8), (57, 44), (43, 58)]
    return [shadow(32, 59.5, 25, 3), body(rpoly(side, 3), tone(c, -0.3), gloss=0.12), body(rpoly(top, 3), tone(c, 0.3), gloss=0.3),
            body(rpoly(front, 3.5), c, gloss=0.35)]

def armsx2(c):
    """ARMSX2: a tall glowing crystal pillar."""
    top = [(19, 10), (26, 6.5), (38, 6.5), (45, 10), (38, 13.5), (26, 13.5)]
    left, front, right = [(19, 10), (26, 13.5), (26, 52), (19, 49)], [(26, 13.5), (38, 13.5), (38, 52), (26, 52)], [(38, 13.5), (45, 10), (45, 49), (38, 52)]
    return [shadow(32, 59.5, 24, 3), body(rr(9, 48, 46, 9, 4), c), recess(rr(15, 46.5, 34, 5, 2.5), c),
            screen(rpoly(left, 1), tone(c, -0.15), sym=0), screen(rpoly(right, 1), tone(c, -0.3), sym=0),
            screen(rpoly(front, 1), tone(c, 0.35), sym=0), screen(rr(30, 18, 4, 30, 2), tone(c, 0.8), sym=1),
            screen(rpoly(top, 1.2), tone(c, 0.7), sym=0)]

def ppsspp(c):
    """PPSSPP: four chamfered arrow keys in a cross, like a handheld's d-pad."""
    arm = rpoly([(25, 6), (39, 6), (39, 21), (32, 28), (25, 21)], [3, 3, 1.5, 1.5, 1.5])
    out = [shadow(32, 60, 22, 3)]
    for deg in (0, 90, 180, 270):
        out += [body(xf(arm, deg=deg), c), recess(xf(rpoly([(32, 11), (36, 16.5), (28, 16.5)], 1), deg=deg), c)]
    return out

# ---------------- Sega ----------------
def yaba_sanshiro(c):
    """Yaba Sanshiro: a Saturn-style pad, a round d-pad and six face buttons in two arcs."""
    shell = "M12,19 H52 Q61,19 61,30 V37 Q61,49 51,49 Q45,49 42,44 H22 Q19,49 13,49 Q3,49 3,37 V30 Q3,19 12,19 Z"
    return [shadow(32, 53, 28, 3), body(rr(7, 14.5, 15, 8, 3.5), tone(c, -0.3), gloss=0.1, sym=1), body(rr(42, 14.5, 15, 8, 3.5), tone(c, -0.3), gloss=0.1, sym=1),
            body(shell, c), control(ci(15, 32.5, 7)), recess(plus(15, 32.5, 4.9, 2.3), RUBBER),
            *[control(ci(x, y, 3)) for x, y in ((40, 37.5), (46.6, 36), (53.2, 33.5))],
            *[control(ci(x, y, 2.1), tone(c, -0.45)) for x, y in ((40, 29.3), (46.4, 27.8), (52.8, 25.4))],
            control(rr(28.5, 37, 7, 2.8, 1.4))]

def flycast(c):
    """Flycast: a thick spiral, raised off the tile."""
    turns, r0, r1 = 1.7, 3.5, 23.5
    n = 64; outer, inner = [], []
    for i in range(n + 1):
        t = i / n; th = t * turns * 2 * math.pi + math.pi * 0.25
        r = r0 + (r1 - r0) * t; w = 3 + 4.6 * t
        outer.append((32 + (r + w / 2) * math.cos(th), 31 + (r + w / 2) * math.sin(th)))
        inner.append((32 + (r - w / 2) * math.cos(th), 31 + (r - w / 2) * math.sin(th)))
    def cap(t, a_from, flip):
        th = t * turns * 2 * math.pi + math.pi * 0.25; r = r0 + (r1 - r0) * t; w = 3 + 4.6 * t
        mx, my = 32 + r * math.cos(th), 31 + r * math.sin(th)
        return [(mx + w / 2 * math.cos(th + flip * math.pi * k / 8), my + w / 2 * math.sin(th + flip * math.pi * k / 8)) for k in range(1, 8)]
    d = poly(outer + cap(1, 0, 1) + inner[::-1] + cap(0, 0, 1)[::-1][::-1])
    return [shadow(32, 59, 24, 3), body(xf(d, dy=4), tone(c, -0.45), gloss=0, sym=None), body(d, c, gloss=0.3)]

def redream(c):
    """Redream: two lightning bolts chasing each other round in a vortex."""
    shape = [(8, 0), (19, 0), (13.5, 9.5), (21, 9.5), (5, 27), (10.5, 15), (2, 15)]
    bolt = xf(rpoly([((x - 11.5) * 1.35 + 32, (y - 13.5) * 1.35 + 32) for x, y in shape], 1.2), deg=25, dx=-8, dy=-4.5)
    return [shadow(32, 59, 23, 3), body(bolt, tone(c, 0.3), gloss=0.3), body(xf(bolt, deg=180, cy=31.5), c, gloss=0.3)]

# name -> (drawing, accent, what it stands for, packages that wear it)
APPS = {
    # The streaming and PC front-ends Foldcade already draws as places wear the same objects as apps.
    "moonlight": (moonlight, MOON, "Moonlight: a crescent moon and a star", ["com.limelight"]),
    "gamenative": (pc, BLUE, "GameNative: a monitor showing a controller", ["app.gamenative", "app.gamenative.gold"]),
    "droiddeck": (droiddeck, DECK, "DroidDeck: a wide handheld with two sticks", ["com.droiddeck.launcher"]),
    "retroarch": (retroarch, "#B39DFF", "RetroArch: a space invader", ["com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32"]),
    "azahar": (azahar, "#F2BE86", "Azahar: a six-petal blossom", ["org.azahar_emu.azahar", "io.github.lime3ds.android"]),
    "citra": (citra, "#FF9A2E", "Citra: half an orange", ["org.citra.citra_emu"]),
    "melonds": (melonds, "#FF5468", "melonDS: a watermelon split open like a clamshell", ["me.magnum.melonds", "me.magnum.melonds.nightly"]),
    "my_boy": (my_boy, "#5C7CFF", "My Boy!: a white landscape handheld, tilted", ["com.fastemulator.gba"]),
    "my_oldboy": (my_oldboy, "#A070FF", "My OldBoy!: a white portrait handheld, tilted", ["com.fastemulator.gbc"]),
    "pizza_boy_gba": (pizza_boy, "#7C8CFF", "Pizza Boy GBA: a slice of pepperoni pizza", ["it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboygbapro"]),
    "pizza_boy_gbc": (pizza_boy, "#FFB547", "Pizza Boy GBC: a slice of pepperoni pizza", ["it.dbtecno.pizzaboy", "it.dbtecno.pizzaboypro"]),
    "pizza_boy_sc": (pizza_boy, "#FF4D5A", "Pizza Boy SC: a slice of pepperoni pizza", ["it.dbtecno.pizzaboyscbasic", "it.dbtecno.pizzaboyscpro"]),
    "gba_emu": (gba_emu, "#7C8CFF", "GBA.emu: a chip holding a little landscape handheld", ["com.explusalpha.GbaEmu"]),
    "gbc_emu": (gbc_emu, "#C77DFF", "GBC.emu: a chip holding a little portrait handheld", ["com.explusalpha.GbcEmu"]),
    "nes_emu": (nes_emu, "#FF4D5A", "NES.emu: a chip holding a d-pad and two buttons", ["com.explusalpha.NesEmu"]),
    "snes9x_ex": (snes9x_ex, "#AEB6CC", "Snes9x EX+: a chip holding four coloured buttons", ["com.explusalpha.Snes9xPlus"]),
    "md_emu": (md_emu, "#4C7DFF", "MD.emu: a chip holding three buttons in a row", ["com.explusalpha.MdEmu"]),
    "saturn_emu": (saturn_emu, "#FFC15A", "Saturn.emu: a chip holding a ringed planet", ["com.explusalpha.SaturnEmu"]),
    "m64plus_fz": (m64plus_fz, "#B4E43C", "Mupen64Plus FZ: a three-colour cube", ["org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita.amazon"]),
    "mupen64plus_ae": (mupen64plus_ae, "#8EE04A", "Mupen64Plus AE: interlocking blocks", ["org.mupen64plusae.v3.alpha"]),
    "dolphin": (dolphin, "#3CB4F2", "Dolphin: a leaping dolphin", ["org.dolphinemu.dolphinemu"]),
    "dolphin_mmjr": (dolphin_mmjr, "#C25CFF", "Dolphin MMJR: a dolphin through a neon ring", ["org.dolphinemu.mmjr", "org.mm.jr"]),
    "eden": (eden, "#FF5FA8", "Eden: a striped sunset sun and a palm", ["dev.eden.eden_emulator", "dev.legacy.eden_emulator"]),
    "duckstation": (duckstation, "#FFD23C", "DuckStation: a voxel duck", ["com.github.stenzek.duckstation"]),
    "nethersx2": (nethersx2, "#FF4848", "NetherSX2: a glossy red cube", ["xyz.aethersx2.android", "xyz.aethersx2.tturnip", "xyz.aethersx2.cturnip"]),
    "armsx2": (armsx2, "#6FA0FF", "ARMSX2: a glowing crystal pillar", ["com.armsx2", "com.armsx2.nightly"]),
    "ppsspp": (ppsspp, "#5D9BE0", "PPSSPP: four arrow keys in a cross", ["org.ppsspp.ppsspp"]),
    "ppsspp_gold": (ppsspp, "#F2B51E", "PPSSPP Gold: four gold arrow keys in a cross", ["org.ppsspp.ppssppgold"]),
    "yaba_sanshiro": (yaba_sanshiro, "#7F94C6", "Yaba Sanshiro: a Saturn-style pad with six buttons", ["org.devmiyax.yabasanshioro2", "org.devmiyax.yabasanshioro2.pro"]),
    "flycast": (flycast, "#8C7CFF", "Flycast: a thick spiral", ["com.flycast.emulator"]),
    "redream": (redream, "#3F8CFF", "Redream: two lightning bolts in a vortex", ["io.recompiled.redream"]),
}
