"""Popular third-party apps, as lit objects: browsers, media, chat, stores, and tools. See apps.py."""
import math
from art import *
from palette import *
from objects import *
from marks import rr, ci, stick, bezel, PAD, PS, _crescent

# ---- shape helpers for this module
def rpoly(pts, r):
    """A polygon with its corners rounded by [r] (one radius, or one per corner)."""
    n = len(pts); rs = r if isinstance(r, (list, tuple)) else [r] * n; out = []
    for i in range(n):
        (x0, y0), (x1, y1), (x2, y2) = pts[i - 1], pts[i], pts[(i + 1) % n]
        la, lb = math.hypot(x0 - x1, y0 - y1), math.hypot(x2 - x1, y2 - y1)
        ta, tb = min(rs[i] / la, 0.5), min(rs[i] / lb, 0.5)
        a = (x1 + (x0 - x1) * ta, y1 + (y0 - y1) * ta); b = (x1 + (x2 - x1) * tb, y1 + (y2 - y1) * tb)
        out.append(f"{'M' if i == 0 else 'L'}{f(a[0])},{f(a[1])} Q{f(x1)},{f(y1)} {f(b[0])},{f(b[1])}")
    return " ".join(out) + " Z"

def rhomb(cx, cy, rx, ry, r=2.0):
    """A rhombus standing on its point, its corners softened."""
    return rpoly([(cx, cy - ry), (cx + rx, cy), (cx, cy + ry), (cx - rx, cy)], r)

def at(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)

# ---------------- browsers ----------------
def firefox(c):
    """Firefox: an orange fox curled around a violet globe."""
    globe = "#8A63FF"; gx, gy = 30, 32
    outer, inner = [], []
    for i in range(49):
        t = i / 48; a = -25 + 255 * t                      # from under the head, round the bottom, up the left
        w = 12.5 * math.sin(math.pi * (0.22 + 0.78 * t)) ** 0.75
        inner.append(at(gx, gy, 15.5 - 2.5 * t, a)); outer.append(at(gx, gy, 15.5 - 2.5 * t + w, a))
    tail = poly(outer + inner[::-1])
    head = rpoly([(35, 3.5), (44.5, 10), (57, 4.5), (57.5, 19), (46, 30), (35.5, 18.5)], [1.6, 2.5, 1.6, 5, 3, 5])
    muzzle = rpoly([(37.5, 17.5), (46.2, 21), (55.5, 17.5), (46.2, 30)], [3, 4, 3, 2.6])
    return [shadow(32, 60, 25, 3), body(ci(gx, gy, 17), globe, gloss=0.35), body(tail, c), body(head, tone(c, 0.05)),
            body(muzzle, "#FFE3C4", gloss=0.15), control(ci(46.2, 26.6, 1.8))]

# ---------------- media ----------------
def spotify(c):
    """Spotify: a green puck with three sound arcs cut into it, widest on top."""
    arcs = [(55, 33, 5.2, 236, 304), (60, 30, 4.4, 242, 298), (65, 27.5, 3.6, 247, 293)]
    return [shadow(32, 59, 22, 3), body(ci(32, 31, 26), c)] + \
           [recess(xf(arc_band(32, y, r, w, a0, a1), deg=-7, cx=32, cy=32), c) for y, r, w, a0, a1 in arcs]

def steam(c):
    """Steam: a pressure gauge on a pipe, its needle up in the red."""
    out = [shadow(32, 60, 22, 3), body(rr(26, 48, 12, 11, 2), tone(c, -0.35), gloss=0.1),
           body(ci(32, 29, 24), c), recess(ci(32, 29, 19), c), screen(ci(32, 29, 17), "#E8F4FF", sym=0)]
    for a in range(135, 406, 45):
        (x0, y0), (x1, y1) = at(32, 29, 14.5, a), at(32, 29, 11.5, a)
        out.append(control(capsule(x0, y0, x1, y1, 2.4)))
    nx, ny = at(32, 29, 12.5, -35)
    return out + [control(capsule(32, 29, nx, ny, 3), "#FF5A4E"), control(ci(32, 29, 3.4))]

def steam_link(c):
    """Steam Link: a pale puck with a navy controller set into it."""
    pad = xf(PAD, s=0.62, cx=32, cy=35.5, dy=-4.5)
    return [shadow(32, 59, 22, 3), body(ci(32, 31, 26), c), control(pad, "#22355E"),
            control(plus(24.2, 29.5, 2.8, 2), tone(c, 0.2)), control(ci(39, 28, 1.8), tone(c, 0.2)), control(ci(43.4, 31.6, 1.8), tone(c, 0.2))]

def kodi(c):
    """Kodi: four blue tiles in a diamond."""
    shades = (tone(c, 0.15), c, tone(c, -0.18), tone(c, -0.05))
    spots = ((32, 16.5), (46.5, 31), (32, 45.5), (17.5, 31))
    return [shadow(32, 60, 22, 3)] + [body(rhomb(x, y, 12.6, 12.6, 2.6), s) for (x, y), s in zip(spots, shades)]

def vlc(c):
    """VLC: a traffic cone with two white bands, on its square foot."""
    k = 19 / 44
    band = lambda y0, y1: poly([(32 - (y0 - 6) * k, y0), (32 + (y0 - 6) * k, y0), (32 + (y1 - 6) * k, y1), (32 - (y1 - 6) * k, y1)])
    cone = "M29.6,9 Q32,4.5 34.4,9 L51,50 H13 Z"
    return [shadow(32, 59, 26, 3), body(rr(7, 48, 50, 9, 3), tone(c, -0.2)), body(cone, c),
            body(band(19, 26.5), "#F6F1EA", gloss=0.2), body(band(34, 41.5), "#F6F1EA", gloss=0.2)]

def plex(c):
    """Plex: a fat gold chevron."""
    return [shadow(32, 59, 18, 3), body(rpoly([(15, 7), (31, 7), (50, 31.5), (31, 56), (15, 56), (34, 31.5)], [3, 3, 4, 3, 3, 2]), c)]

def ribbon(pts, w0, w1):
    """A thick line through [pts], [w0] wide at the start tapering to [w1], with round ends."""
    left, right = [], []
    for i, (x, y) in enumerate(pts):
        (ax, ay), (bx, by) = pts[max(i - 1, 0)], pts[min(i + 1, len(pts) - 1)]
        n = math.hypot(bx - ax, by - ay); h = (w0 + (w1 - w0) * i / (len(pts) - 1)) / 2
        left.append((x - (by - ay) / n * h, y + (bx - ax) / n * h)); right.append((x + (by - ay) / n * h, y - (bx - ax) / n * h))
    (ex, ey), (sx, sy) = pts[-1], pts[0]
    return (poly(left, close=False) + f" A{f(w1 / 2)},{f(w1 / 2)} 0 0 0 {f(right[-1][0])},{f(right[-1][1])} "
            + poly(right[::-1], close=False).replace("M", "L", 1) + f" A{f(w0 / 2)},{f(w0 / 2)} 0 0 0 {f(left[0][0])},{f(left[0][1])} Z")

def jellyfin(c):
    """Jellyfin: a jellyfish, its violet bell lit by a cyan arch, its tentacles trailing."""
    cyan = "#4FD8F5"
    out = [shadow(32, 61, 20, 2.5)]
    for i, x in enumerate((17, 27, 37, 47)):
        ph = i * 1.3
        out.append(body(ribbon([(x + 3.2 * math.sin(ph + t * 2.6), 33 + t * 24 * (0.85 + 0.1 * (i % 2))) for t in (j / 12 for j in range(13))], 4.6, 2.6),
                        tone(cyan, -0.1 * (i % 2)), gloss=0.15))
    bell = "M7,33 C7,17 18,7 32,7 C46,7 57,17 57,33 Q57,37.5 53,37.5 H11 Q7,37.5 7,33 Z"
    return out + [body(bell, c), recess("M18.5,37.5 C18.5,26 24,18.5 32,18.5 C40,18.5 45.5,26 45.5,37.5 Z", c),
                  screen("M21.5,37.5 C21.5,28 25.5,21.5 32,21.5 C38.5,21.5 42.5,28 42.5,37.5 Z", cyan, sym=0)]

# ---------------- chat ----------------
def discord(c):
    """Discord: a blurple face shaped like a controller, two round eyes."""
    face = ("M17,12 Q24,9.5 32,10.5 Q40,9.5 47,12 Q56,22 59.5,44 Q54,51 46,53 L42.5,47.5 Q32,50 21.5,47.5 "
            "L18,53 Q10,51 4.5,44 Q8,22 17,12 Z")
    return [shadow(32, 57, 26, 3), body(face, c),
            screen(ellipse(23, 32.5, 5, 5.6), "#FFFFFF", sym=0), screen(ellipse(41, 32.5, 5, 5.6), "#FFFFFF", sym=0)]

def twitch(c):
    """Twitch: a purple speech bubble with two eye slits."""
    bubble = "M18,8 H48 Q56,8 56,16 V36 Q56,44 48,44 H31 L20.5,54.5 Q18.5,56.5 18.5,53.5 V44 H16 Q8,44 8,36 V16 Q8,8 18,8 Z"
    return [shadow(32, 59, 24, 3), body(bubble, c), recess(capsule(27, 18.5, 27, 30.5, 5), c), recess(capsule(39, 18.5, 39, 30.5, 5), c)]

def telegram(c):
    """Telegram: a paper plane in flight, its wing lit and its keel in shade."""
    return [shadow(34, 59, 22, 2.6), body(rpoly([(24, 37), (57, 9), (43, 51)], [1.5, 2, 2.5]), tone(c, -0.12)),
            body(rpoly([(26, 38), (28.5, 52), (35.5, 44)], [1, 1.6, 1]), tone(c, -0.38), gloss=0.1),
            body(rpoly([(5, 29.5), (57, 9), (24, 37)], [2.5, 2, 1.5]), c)]

def whatsapp(c):
    """WhatsApp: a round green speech bubble with a white handset in it."""
    (x0, y0), (x1, y1) = at(33, 29, 25, 146), at(33, 29, 25, 112)
    bubble = f"M{f(x0)},{f(y0)} A25,25 0 1 1 {f(x1)},{f(y1)} L7,57 Z"
    hx, hy, ro, ri, d = 40, 21, 18.5, 13.5, 9          # the handle curls round (hx, hy); each pad reaches d in from it
    arc = lambda r, a0, a1: [at(hx, hy, r, a0 + (a1 - a0) * i / 10) for i in range(11)]
    def pad(a, half):
        """A pad's two inner corners, its sides parallel to the radius through [a]."""
        ux, uy = math.cos(math.radians(a)), math.sin(math.radians(a))
        return [(x - ux * d, y - uy * d) for x, y in (at(hx, hy, ro, a - half), at(hx, hy, ro, a + half))]
    (m1, m2), (e1, e2) = pad(92, 13), pad(176, 13)
    handset = rpoly(arc(ro, 189, 79) + [m1, m2] + arc(ri, 110, 158) + [e1, e2],
                    [2.4] + [9] * 9 + [2.4] * 4 + [9] * 9 + [2.4] * 3)
    return [shadow(32, 59, 24, 3), body(bubble, c), control(handset, "#FFFFFF")]

def reddit(c):
    """Reddit: a friendly orange alien head with one antenna."""
    return [shadow(32, 60, 24, 3), control(capsule(33, 26, 38.5, 9, 3.2), tone(c, -0.35)), body(ci(39.5, 8.5, 4.6), tone(c, 0.15)),
            body(ellipse(32, 39.5, 25.5, 18.5), c), control(ellipse(22.5, 36.5, 4.4, 5.2)), control(ellipse(41.5, 36.5, 4.4, 5.2)),
            recess(arc_band(32, 41.5, 8.5, 3, 35, 145), c)]

# ---------------- stores and tools ----------------
def fdroid(c):
    """F-Droid: a blue robot head with two antennae and round white eyes."""
    return [shadow(32, 59, 25, 3), control(capsule(19, 20, 12.5, 7.5, 3.2), tone(c, -0.3)), control(capsule(45, 20, 51.5, 7.5, 3.2), tone(c, -0.3)),
            body(rr(6, 17, 52, 38, 11), c), screen(ci(21.5, 34, 7), "#FFFFFF", sym=0), screen(ci(42.5, 34, 7), "#FFFFFF", sym=0),
            control(ci(22.5, 35, 3)), control(ci(43.5, 35, 3)), recess(capsule(26, 47.5, 38, 47.5, 3), c)]

def termux(c):
    """Termux: a terminal window with a lit prompt, a chevron and a cursor."""
    glow = "#DFFFEA"
    return [shadow(32, 59, 27, 3), body(rr(4, 8, 56, 47, 7), c),
            *[control(ci(x, 13.5, 1.8), tone(c, -0.45)) for x in (11, 17, 23)],
            recess(rr(8.5, 19, 47, 31.5, 3.5), c),
            screen(rpoly([(14.5, 25), (19.5, 25), (27, 32.5), (19.5, 40), (14.5, 40), (22, 32.5)], 1.2), glow, sym=0),
            screen(rr(30, 37, 13, 3.6, 1.2), glow, sym=0)]

def obtainium(c):
    """Obtainium: a cut violet gem with a down arrow notched into it."""
    crown = [[(18, 9), (26.5, 9), (22, 24), (5, 24)], [(26.5, 9), (37.5, 9), (42, 24), (22, 24)], [(37.5, 9), (46, 9), (59, 24), (42, 24)]]
    out = [shadow(32, 60, 22, 3)]
    out += [body(rpoly(p, 1.5), s, gloss=0.25) for p, s in zip(crown, (tone(c, 0.1), tone(c, 0.35), tone(c, -0.05)))]
    out += [body(rpoly([(5, 27), (59, 27), (32, 58)], [2, 2, 3]), c),
            recess(rpoly([(28.5, 30), (35.5, 30), (35.5, 38), (41, 38), (32, 48.5), (23, 38), (28.5, 38)], 1.2), c)]
    return out

def xbox(c):
    """Xbox: a glowing green orb, set like the guide button in a dark ring."""
    return [shadow(32, 59, 22, 3), body(ci(32, 31, 26), tone(c, -0.5), gloss=0.2), recess(ci(32, 31, 21.5), c),
            body(ci(32, 31, 19.5), c, gloss=0.45), screen(ellipse(32, 23, 11, 6.5), tone(c, 0.35))]

def ps_remote_play(c):
    """PS Remote Play: a pale dual-stick controller with three signal dots rising over it."""
    pad = xf(PS, s=0.86, cx=32, cy=36.5, dy=4.5)
    lit = tone(c, 0.7)
    return [shadow(32, 59, 26, 3), body(pad, lit), control(plus(18.3, 33.6, 4, 2.9)),
            *[control(d, tone(c, -0.1)) for d in diamond(45.7, 33.6, 3.7, 1.9)],
            *stick(26, 42.5, 2.9, lit), *stick(38, 42.5, 2.9, lit),
            *[body(ci(x, y, 3), c, gloss=0.2) for x, y in ((22, 12), (32, 8), (42, 12))]]

def chiaki(c):
    """Chiaki: an acid-lime cat head wearing a navy visor with pink pixel eyes."""
    head = "M10,6.5 L22.5,16.5 Q32,14 41.5,16.5 L54,6.5 Q58,4 58,9 V35 Q58,57 32,57 Q6,57 6,35 V9 Q6,4 10,6.5 Z"
    return [shadow(32, 60, 24, 3), body(head, c), recess(rr(10, 26, 44, 16, 8), "#1C2A55"),
            screen(rr(18.5, 30, 6.5, 6.5, 1), "#FF6FB8", sym=0), screen(rr(39, 30, 6.5, 6.5, 1), "#FF6FB8", sym=0)]

def winlator(c):
    """Winlator: a window of four lit panes, its top corner folded down like a page."""
    frame = rpoly([(6, 8), (45, 8), (58, 21), (58, 56), (6, 56)], [6, 1.5, 1.5, 6, 6])
    panes = [rr(11.5, 14, 18.5, 17, 2), rpoly([(34, 14), (39.5, 14), (39.5, 25.5), (52.5, 25.5), (52.5, 31), (34, 31)], 2),
             rr(11.5, 35, 18.5, 15.5, 2), rr(34, 35, 18.5, 15.5, 2)]
    return [shadow(32, 59, 27, 3), body(frame, c), *[screen(p, "#CDEBFF", sym=0) for p in panes],
            body(rpoly([(45, 8), (45, 21), (58, 21)], [1.5, 2, 1.5]), tone(c, 0.45), gloss=0.15)]

def gamehub(c):
    """GameHub: a pale pad with a big d-pad on it, held between two bracket-shaped grooves."""
    left = [(19, 13), (19, 16), (14, 16), (14, 48), (19, 48), (19, 51), (11, 51), (11, 13)]
    right = [(64 - x, y) for x, y in reversed(left)]
    return [shadow(32, 59, 26, 3), body(rr(5, 7, 54, 50, 14), c), recess(rpoly(left, 1), c), recess(rpoly(right, 1), c),
            control(plus(32, 32, 14, 10), tone(c, -0.6)), recess(ci(32, 32, 3), tone(c, -0.6))]

def argosy(c):
    """Argosy: a ship's wheel, eight spokes and their handles."""
    out = [shadow(32, 60, 24, 3)]
    for i in range(8):
        a = i * 45 - 90
        (x0, y0), (x1, y1) = at(32, 31, 4, a), at(32, 31, 27, a)
        out.append(body(capsule(x0, y0, x1, y1, 4.4), tone(c, -0.12), gloss=0.2))
    rim = ci(32, 31, 20) + circle_ccw(32, 31, 14.5)
    return out + [body(rim, c), body(ci(32, 31, 7), tone(c, 0.2)), recess(ci(32, 31, 2.8), c)]

def daijisho(c):
    """Daijishō: a violet rhombus split into two triangles."""
    top, right, bottom, left = (33, 4), (59, 30.5), (31, 58), (5, 33.5)
    return [shadow(32, 60, 22, 3), body(rpoly([top, (31.6, 57.2 - 2), left], [2, 2, 3]), tone(c, 0.18)),
            body(rpoly([(34.4, 7.2), right, bottom], [2, 3, 2]), tone(c, -0.12))]

def es_de(c):
    """ES-DE: a carousel of three tilted game cards, the front one lit, over its three page dots."""
    card = rr(19, 7, 26, 37, 4.5)
    fan = lambda deg, dx: xf(card, s=0.86, deg=deg, cx=32, cy=26, dx=dx, dy=3)
    side = lambda deg, dx: [body(fan(deg, dx), tone(c, -0.3), gloss=0.15), screen(xf(rr(22, 10, 20, 31, 2.5), s=0.86, deg=deg, cx=32, cy=26, dx=dx, dy=3), tone(c, -0.05))]
    return [shadow(32, 54, 26, 2.6), *side(-16, -14), *side(16, 14),
            body(card, c), recess(rr(21.5, 9.5, 21, 32, 3), c), screen(rr(23, 11, 18, 29, 2), "#FFC9D1", sym=0),
            control(ci(23, 56, 2), tone(c, -0.45)), control(ci(32, 56, 2.4), tone(c, 0.5)), control(ci(41, 56, 2), tone(c, -0.45))]

def pegasus(c):
    """Pegasus: one bold red wing on a short arrowhead, a curved covert over three stacked flight feathers."""
    feathers = [((47, 37), (12, 20), 8.5), ((47, 43), (18, 32), 8), ((47, 48.5), (26, 42.5), 7.5)]
    out = [shadow(34, 59, 24, 3)]
    for i, ((x0, y0), (x1, y1), w) in enumerate(feathers):
        out.append(body(capsule(x0, y0, x1, y1, w), tone(c, 0.18 - 0.13 * i), gloss=0.22))
    covert = "M51,47 C56,38 55,27 47,20 C38,12 24,8 9,8.5 Q5,8.8 7.5,11.5 C20,17 33,24 39,33 C42,38 45,43 51,47 Z"
    return out + [body(covert, tone(c, 0.32), gloss=0.25),
                  body(rpoly([(62, 49), (41, 38), (45.5, 49), (41, 60)], [2, 2.5, 2, 2.5]), c)]

def beacon(c):
    """Beacon: a pink lighthouse, its lamp lit, a sparkle either side."""
    star = lambda x, y, r: f"M{f(x)},{f(y - r)} Q{f(x + r * 0.18)},{f(y - r * 0.18)} {f(x + r)},{f(y)} Q{f(x + r * 0.18)},{f(y + r * 0.18)} {f(x)},{f(y + r)} Q{f(x - r * 0.18)},{f(y + r * 0.18)} {f(x - r)},{f(y)} Q{f(x - r * 0.18)},{f(y - r * 0.18)} {f(x)},{f(y - r)} Z"
    return [shadow(32, 59, 22, 3), body(rpoly([(25, 25), (39, 25), (45, 56), (19, 56)], [1, 1, 2, 2]), c),
            body(rr(22, 21, 20, 5, 2), tone(c, -0.2)), recess(rr(25, 11, 14, 11, 2), c), screen(rr(27, 13, 10, 9, 1), "#FFF1B8", sym=0),
            body("M23,12 Q24,4 32,3 Q40,4 41,12 Z", tone(c, -0.15)), recess(rr(29, 44, 6, 12, (3, 3, 0, 0)), c),
            body(star(10.5, 18, 6), tone(c, 0.6), gloss=0.1), body(star(53.5, 30, 5), tone(c, 0.6), gloss=0.1)]

def cocoonshell(c):
    """CocoonShell: a silk cocoon hanging from a twig, wound with two bands."""
    pod = "M32,13 C42,13 48,20 47.5,31 C47,43 39,52 33,59.5 Q32,61 31,59.5 C25,52 17,43 16.5,31 C16,20 22,13 32,13 Z"
    silk = tone(c, 0.55)
    return [shadow(32, 61, 14, 2), body(capsule(6, 9.5, 58, 6, 5), "#8C6A4E", gloss=0.15),
            control(capsule(32, 9, 32, 15, 3), tone(c, -0.3)), body(pod, c),
            body(capsule(19, 27, 45.5, 21.5, 3.4), silk, gloss=0.15), body(capsule(19.5, 37.5, 45, 32, 3.4), silk, gloss=0.15),
            body(capsule(24, 47, 40.5, 43, 3.4), silk, gloss=0.15)]

# name -> (drawing, accent, what it stands for, packages that wear it)
APPS = {
    "firefox": (firefox, "#FF7A2E", "Firefox: a fox curled around a globe",
                ["org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.fennec_fdroid"]),
    "spotify": (spotify, "#3FE06E", "Spotify: a green puck with three sound arcs", ["com.spotify.music"]),
    "discord": (discord, "#7584FF", "Discord: a blurple controller-shaped face", ["com.discord"]),
    "twitch": (twitch, "#A970FF", "Twitch: a speech bubble with two eyes", ["tv.twitch.android.app"]),
    "steam": (steam, "#47AEFF", "Steam: a pressure gauge", ["com.valvesoftware.android.steam.community"]),
    "steam_link": (steam_link, "#A9CBEF", "Steam Link: a controller set into a pale puck", ["com.valvesoftware.steamlink"]),
    "kodi": (kodi, "#34BEF0", "Kodi: four tiles in a diamond", ["org.xbmc.kodi"]),
    "vlc": (vlc, "#FF8A1F", "VLC: a traffic cone", ["org.videolan.vlc"]),
    "plex": (plex, "#FFB61F", "Plex: a gold chevron", ["com.plexapp.android"]),
    "jellyfin": (jellyfin, "#BC6CF0", "Jellyfin: a jellyfish lit by an arch", ["org.jellyfin.mobile", "org.jellyfin.androidtv"]),
    "telegram": (telegram, "#3FA9F5", "Telegram: a paper plane",
                 ["org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"]),
    "whatsapp": (whatsapp, "#33DB76", "WhatsApp: a speech bubble with a handset", ["com.whatsapp", "com.whatsapp.w4b"]),
    "reddit": (reddit, "#FF5A1F", "Reddit: an alien head with an antenna", ["com.reddit.frontpage"]),
    "fdroid": (fdroid, "#4D96F0", "F-Droid: a robot head with antennae", ["org.fdroid.fdroid", "org.fdroid.basic"]),
    "termux": (termux, "#9EABC2", "Termux: a terminal window with a prompt", ["com.termux"]),
    "obtainium": (obtainium, "#C47BFF", "Obtainium: a cut gem with a down arrow", ["dev.imranr.obtainium"]),
    "xbox": (xbox, "#6BDB3A", "Xbox: a lit green orb", ["com.microsoft.xboxone.smartglass", "com.gamepass"]),
    "ps_remote_play": (ps_remote_play, "#3F8CFF", "PS Remote Play: a controller with signal dots", ["com.playstation.remoteplay"]),
    "chiaki": (chiaki, "#C8EE3C", "Chiaki: a cat head in a visor", ["com.metallic.chiaki"]),
    "winlator": (winlator, "#4F8EFF", "Winlator: a window of four panes with a folded corner", ["com.winlator"]),
    "gamehub": (gamehub, "#B4BFCF", "GameHub: a d-pad between two brackets", ["com.xiaoji.egggame"]),
    "argosy": (argosy, "#8C8AFF", "Argosy: a ship's wheel", ["com.nendo.argosy"]),
    "daijisho": (daijisho, "#A57CFF", "Daijishō: a rhombus split into two triangles", ["com.magneticchen.daijishou"]),
    "es_de": (es_de, "#FF3D55", "ES-DE: a chamfered crimson emblem", ["org.es_de.frontend"]),
    "pegasus": (pegasus, "#FF4B3E", "Pegasus: a winged arrowhead", ["org.pegasus_frontend.android"]),
    "beacon": (beacon, "#FF6FAE", "Beacon: a lighthouse with its lamp lit", ["com.radikal.gamelauncher"]),
    "cocoonshell": (cocoonshell, "#6EDDB0", "CocoonShell: a silk cocoon on a twig", ["rip.moth.cocoonshell"]),
}
