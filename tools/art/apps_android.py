"""Android's own apps and Google's, as lit objects: what ships on the Thor and on most phones. See apps.py."""
import math
from art import *
from palette import *
from objects import *
from marks import rr, ci, stick, bezel

# The four colours Google's icons share, as lit plastic.
G_RED, G_YELLOW, G_GREEN, G_BLUE = "#FF5A4E", "#FFC93C", "#3DD47A", "#4C8DFF"

def chrome(c):
    """Chrome: a ball of three bright segments around a lens, the way its icon is a ring of three colours."""
    out = [shadow(32, 59, 21, 3)]
    for (a0, a1), shade in zip(((-150, -30), (-30, 90), (90, 210)), (G_RED, G_YELLOW, G_GREEN)):
        out.append(body(sector(32, 31, 26, 13.5, a0 + 3, a1 - 3), shade, gloss=0.22))
    return out + [recess(ci(32, 31, 11.6), c), screen(ci(32, 31, 8.6), c, sym=0)]

def files(c):
    """Files: a drawer pulled open, three folders standing in it."""
    out = [shadow(32, 59, 27, 3)]
    for x, y, shade in ((11, 13, tone(c, -0.25)), (22, 9, tone(c, -0.1)), (33, 15, tone(c, 0.12))):
        out += [body(rr(x, y, 20, 26, (3, 3, 0, 0)), shade, gloss=0.18, sym=None),
                paint(L(rr(x + 3.5, y + 4, 9, 2.6, 1.3), fill=INK, alpha=0.8))]
    return out + [body(rr(5, 29, 54, 27, 6), c), recess(capsule(24, 41, 40, 41, 4.2), c)]

def settings(c):
    """Settings: a cog with a hub."""
    return [shadow(32, 59, 22, 3), body(gear(32, 31, 26.5, 20.5, 8), c), recess(ci(32, 31, 10), c),
            control(ci(32, 31, 6), tone(c, 0.3))]

def play_store(c):
    """Play Store: a shopping bag with a play button on it, the store's first icon."""
    bag = "M13,19 H51 Q54,19 54.4,22 L57,52 Q57.3,56 53.3,56 H10.7 Q6.7,56 7,52 L9.6,22 Q10,19 13,19 Z"
    return [shadow(32, 59, 25, 3), body(arc_band(32, 20, 10, 3.4, 180, 360), tone(c, -0.3), gloss=0.1, sym=1),
            body(bag, c), recess(ci(22, 24.5, 1.9), c, sym=None), recess(ci(42, 24.5, 1.9), c, sym=None),
            control("M26,29 Q26,26.4 28.3,27.7 L42.2,36.2 Q44.4,37.5 42.2,38.8 L28.3,47.3 Q26,48.6 26,46 Z", "#FFFFFF")]

def calendar(c):
    """Calendar: a desk calendar's page on two binder rings, its header in the accent and today lit."""
    paper = tone(c, 0.86)
    out = [shadow(32, 59, 26, 3), body(rr(7, 20, 50, 37, (0, 0, 7, 7)), paper, gloss=0.15),
           body(rr(7, 10, 50, 13, (7, 7, 0, 0)), c, sym=1)]
    for x in (21, 43):
        out += [recess(ci(x, 16.5, 2.6), c, sym=None), control(capsule(x, 5.5, x, 16.5, 4), tone(c, -0.55), sym=2)]
    for row in range(3):
        for col in range(4):
            x, y = 12.5 + col * 10.2, 28 + row * 9
            if (row, col) == (1, 2):
                out.append(screen(rr(x, y, 8.6, 6.4, 1.6), c, sym=0))
            else:
                out.append(recess(rr(x, y, 8.6, 6.4, 1.6), paper))
    return out

def _bell(cx, cy, r, deg):
    """A half-dome with its flat side down, turned [deg] about its own centre."""
    return xf(f"M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 0 1 {f(cx + r)},{f(cy)} Z", deg=deg, cx=cx, cy=cy)

def clock(c):
    """Clock: a twin-bell alarm clock, its face lit and its hands at ten past ten."""
    out = [shadow(32, 59, 22, 3),
           body(capsule(19, 50, 14.5, 57, 3.6), tone(c, -0.35), gloss=0, sym=1), body(capsule(45, 50, 49.5, 57, 3.6), tone(c, -0.35), gloss=0, sym=1),
           control(capsule(32, 7, 32, 14, 3), tone(c, -0.5), sym=None),
           body(_bell(17, 15.5, 9, -40), tone(c, 0.25), sym=1), body(_bell(47, 15.5, 9, 40), tone(c, 0.25), sym=1),
           body(ci(32, 34, 21), c), recess(ci(32, 34, 17), c), screen(ci(32, 34, 15.4), "#FFF4E2", sym=0)]
    for a in (0, 90, 180, 270):
        x, y = 32 + 12 * math.cos(math.radians(a)), 34 + 12 * math.sin(math.radians(a))
        out.append(control(ci(x, y, 1.3), tone(c, -0.3), sym=None))
    return out + [control(capsule(32, 34, 25, 27.5, 2.8), sym=1), control(capsule(32, 34, 40.5, 28.5, 2.8), sym=1),
                  control(ci(32, 34, 2.6), c, sym=None)]

def gallery(c):
    """Gallery: a framed photo of mountains under a sun."""
    return [shadow(32, 58, 27, 3), body(rr(4, 9, 56, 45, 5), c), recess(rr(9.5, 14.5, 45, 34, 2), c),
            screen(rr(11, 16, 42, 31, 1.2), "#9FD9FF", sym=0),
            screen(ci(42.5, 24.5, 4.6), "#FFE68A", sym=None),
            body("M11.4,47 L23.5,29.5 L33,41 L38.5,34.5 L52.6,47 Z", "#4FC77A", gloss=0.2, sym=None),
            body("M23.5,29.5 L27.2,34.9 L25.2,33.8 L23.5,36 L21.6,33.6 L19.9,34.7 Z", "#F4F7FB", gloss=0, sym=None)]

def photos(c):
    """Photos: a toy pinwheel of four lit vanes on a stick, after the pinwheel Google Photos is known by."""
    cx, cy, R = 32, 27, 23
    out = [shadow(32, 60, 12, 2.4), body(capsule(32, 30, 32, 58, 4.6), tone(c, -0.45), gloss=0.1, sym=1)]
    for k, shade in enumerate((G_RED, G_YELLOW, G_GREEN, G_BLUE)):
        a = math.radians(-90 + 90 * k + 12)
        ux, uy = math.cos(a), math.sin(a)
        gx, gy = math.cos(a + math.pi / 4) * 0.9, math.sin(a + math.pi / 4) * 0.9
        x0, y0 = cx + gx, cy + gy
        x1, y1 = x0 + R * ux, y0 + R * uy
        out.append(body(f"M{f(x0)},{f(y0)} L{f(x1)},{f(y1)} A{f(R / 2)},{f(R / 2)} 0 0 1 {f(x0)},{f(y0)} Z", shade, gloss=0.22))
    return out + [control(ci(cx, cy, 3.4), tone(c, 0.7), sym=2)]

def music(c):
    """Music: a pair of headphones."""
    return [shadow(32, 59, 26, 3), body(arc_band(32, 33, 22, 5.6, 180, 360), tone(c, -0.15), sym=1),
            control(arc_band(32, 33, 18, 2.6, 205, 335), tone(c, -0.55), sym=None),
            control(rr(15.5, 31, 6, 23, 3)), control(rr(42.5, 31, 6, 23, 3)),
            body(rr(5, 30, 13, 26, 6.5), c), body(rr(46, 30, 13, 26, 6.5), c),
            recess(capsule(11.5, 37, 11.5, 49, 3), c), recess(capsule(52.5, 37, 52.5, 49, 3), c)]

def recorder(c):
    """Recorder: a studio microphone in its cradle, a red light on while it records."""
    return [shadow(32, 59, 17, 2.6), body(arc_band(32, 28, 16, 3.4, 0, 180), tone(c, -0.35), gloss=0.1, sym=1),
            body(rr(29.6, 44, 4.8, 9, 0), tone(c, -0.45), gloss=0, sym=1), body(rr(18, 51.5, 28, 5.5, 2.75), tone(c, -0.2)),
            body(rr(20, 4, 24, 37, 12), c),
            *[recess(capsule(25, y, 39, y, 2.6), c) for y in (11.5, 17, 22.5)],
            screen(ci(32, 32, 3.2), "#FF4D5A", sym=0)]

def camera(c):
    """Camera: a camera body with one big lens."""
    return [shadow(32, 59, 27, 3), body(rr(19, 9, 26, 12, (4, 4, 0, 0)), tone(c, -0.2), gloss=0.15, sym=1),
            control(rr(47, 11.5, 8, 6, (2, 2, 0, 0)), tone(c, 0.3), sym=None),
            body(rr(4, 17, 56, 39, 8), c), recess(ci(32, 37, 14.5), c), control(ci(32, 37, 12.6)),
            screen(ci(32, 37, 8.6), "#8FB8FF", sym=0), screen(rr(47, 22, 8, 5, 1.6), "#FFF4D6", sym=0)]

def contacts(c):
    """Contacts: an address book with a person on its cover and tabs down its edge."""
    out = [shadow(32, 59, 23, 3)]
    for y, shade in ((14, G_YELLOW), (25, G_GREEN), (36, G_RED)):
        out.append(body(rr(46, y, 10, 8, 2.5), shade, gloss=0.15, sym=1))
    return out + [body(rr(9, 5, 42, 52, (3, 7, 7, 3)), c), body(rr(9, 5, 6, 52, (3, 0, 0, 3)), tone(c, -0.3), gloss=0.1, sym=None),
                  control(ci(33, 23, 7), tone(c, 0.72)),
                  control("M20.5,46 Q20.5,33.5 33,33.5 Q45.5,33.5 45.5,46 Q45.5,48 43.5,48 H22.5 Q20.5,48 20.5,46 Z", tone(c, 0.72))]

def phone(c):
    """Phone: a desk telephone, its handset resting on the cradle."""
    base = "M16,29 H48 Q51,29 52,32 L57,51 Q58,56 53,56 H11 Q6,56 7,51 L12,32 Q13,29 16,29 Z"
    handset = ("M9,22 Q8,13 18,10.5 Q32,7.5 46,10.5 Q56,13 55,22 Q55,25 52,25 H45 Q42,25 42,22 V19.5 "
               "Q32,17.5 22,19.5 V22 Q22,25 19,25 H12 Q9,25 9,22 Z")
    out = [shadow(32, 59, 27, 3), body(base, c), body(handset, tone(c, 0.2), sym=1)]
    for row in range(3):
        for col in range(3):
            out.append(control(rr(22.5 + col * 7, 34.5 + row * 6, 5, 3.6, 1.4), tone(c, -0.5)))
    return out

def messages(c):
    """Messages: a speech bubble with three dots, someone typing."""
    bubble = ("M18,8 H46 Q59,8 59,21 V32 Q59,45 46,45 H25 L14.5,54.5 Q12,56.5 12.4,53.3 L13.6,44 "
              "Q5,40.5 5,32 V21 Q5,8 18,8 Z")
    return [shadow(30, 59, 24, 2.8), body(bubble, c), *[control(ci(x, 26.5, 3.8), tone(c, 0.7)) for x in (19.5, 32, 44.5)]]

def calculator(c):
    """Calculator: a desk calculator with a lit display and a column of operator keys."""
    out = [shadow(32, 60, 23, 3), body(rr(10, 3, 44, 56, 7), c), *bezel(14.5, 7.5, 35, 14, 2.5, c, "#D8FFF4")]
    for row in range(4):
        for col in range(4):
            x, y = 14.5 + col * 9.1, 25.5 + row * 7.8
            out.append(control(rr(x, y, 7.1, 5.6, 1.8), "#FFB547" if col == 3 else RUBBER))
    return out

def youtube(c):
    """YouTube: a rounded red television with a lit play button, its rabbit ears up."""
    return [shadow(32, 59, 25, 3), control(capsule(32, 17, 22, 5, 2.8), tone(c, -0.45), sym=1), control(capsule(32, 17, 42, 5, 2.8), tone(c, -0.45), sym=1),
            body(rr(15, 50, 7, 7, 2), tone(c, -0.4), gloss=0, sym=1), body(rr(42, 50, 7, 7, 2), tone(c, -0.4), gloss=0, sym=1),
            body(rr(3, 15, 58, 39, 13), c),
            screen("M26,25 Q26,22 28.6,23.5 L43,32.5 Q45.4,34 43,35.5 L28.6,44.5 Q26,46 26,43 Z", "#FFFFFF", sym=0)]

def youtube_music(c):
    """YouTube Music: a red disc with a raised ring around a lit play button."""
    ring = ci(32, 31, 16) + circle_ccw(32, 31, 12.6)
    return [shadow(32, 60, 22, 2.6), body(ci(32, 31, 27), c), control(ring, tone(c, 0.75)),
            screen("M28,24.5 Q28,22.4 29.9,23.5 L38.4,29.3 Q40.2,30.5 38.4,31.7 L29.9,37.5 Q28,38.6 28,36.5 Z", "#FFFFFF", sym=0)]

def gmail(c):
    """Gmail: an envelope whose flap folds in red, with the other colours in its corners."""
    paper = "#E9EDF4"
    env = rr(4, 13, 56, 40, 5)
    return [shadow(32, 57, 27, 3), body(env, paper, gloss=0.2),
            body("M4,18 V48 Q4,53 9,53 H13 V25.5 Z", G_BLUE, gloss=0.15, sym=None),
            body("M60,18 V48 Q60,53 55,53 H51 V25.5 Z", G_GREEN, gloss=0.15, sym=None),
            body("M5.5,14.5 Q7,13 9,13 L32,30 L55,13 Q57,13 58.5,14.5 L32,36 Z", c, gloss=0.25, sym=1)]

def maps(c):
    """Maps: a pin standing on a folded map."""
    panels = (("M5,30 L21,26 V52 L5,56 Z", tone(c, 0.3)), ("M21,26 L37,30 V56 L21,52 Z", c), ("M37,30 L59,26 V52 L37,56 Z", tone(c, 0.45)))
    out = [shadow(32, 58, 28, 3)] + [body(d, s, gloss=0.15, sym=None) for d, s in panels]
    out += [body(capsule(8, 47, 57, 34, 3.4), G_YELLOW, gloss=0.2, sym=None), outline("M5,30 L21,26 L37,30 L59,26 V52 L37,56 L21,52 L5,56 Z"),
            paint(L(ellipse(32, 47, 6, 1.8), fill=BLACK, alpha=0.35)),
            body("M32,4 C41.5,4 48,10.5 48,19 C48,30 32,46 32,46 C32,46 16,30 16,19 C16,10.5 22.5,4 32,4 Z", G_RED, sym=1),
            recess(ci(32, 19, 5.6), G_RED, sym=1)]
    return out

def drive(c):
    """Drive: a triangle of three lit bars in green, yellow, and blue."""
    a, l, r = (32, 9), (9, 49), (55, 49)
    w = 12
    def bar(p, q, t0, t1):
        return capsule(p[0] + (q[0] - p[0]) * t0, p[1] + (q[1] - p[1]) * t0, p[0] + (q[0] - p[0]) * t1, p[1] + (q[1] - p[1]) * t1, w)
    return [shadow(32, 58, 26, 3), body(bar(r, l, 0.0, 1.0), G_BLUE), body(bar(l, a, 0.0, 1.0), G_GREEN, sym=1),
            body(bar(a, r, 0.0, 1.0), G_YELLOW, sym=2)]

def keep(c):
    """Keep: a sticky note with a corner turned up and a lit bulb on it."""
    note = "M12,5 H52 Q56,5 56,9 V45 L45,57 H12 Q8,57 8,53 V9 Q8,5 12,5 Z"
    return [shadow(32, 59, 24, 3), body(note, c), body("M56,45 H48.5 Q45,45 45,48.5 V57 Z", tone(c, -0.3), gloss=0.1, sym=1),
            screen("M32,13 A10.5,10.5 0 0 1 38.2,32 Q37,33 37,35 V36.5 H27 V35 Q27,33 25.8,32 A10.5,10.5 0 0 1 32,13 Z", "#FFFBEA", sym=0),
            control(rr(27, 38.5, 10, 6.5, 2.2), tone(c, -0.5))]

def odin_launcher(c):
    """Odin Launcher: a winged Viking helmet, for the Thor's own game launcher."""
    gold = "#FFC15A"
    wing = ("M22,24 C17,21 11,15 7.5,6.5 Q5.5,3 4,6.5 C2.5,11.5 2.5,16.5 4,20.5 Q7.5,20.5 9,23 "
            "Q7,26.5 8.5,30 Q11.5,29.5 13.5,31.5 Q12,35 14,38 Q17,37.5 19,39.5 L22,41 Z")
    return [shadow(32, 59, 22, 3), body(wing, gold, gloss=0.25, sym=1), body(xf_mirror(wing), gold, gloss=0.25, sym=1),
            body("M14,43 Q14,14 32,14 Q50,14 50,43 Z", c), control(capsule(32, 17, 32, 39, 5), tone(c, 0.35)),
            body("M28,44 H36 L35,54.5 Q32,58 29,54.5 Z", tone(c, -0.15), sym=1),
            body(rr(11, 38.5, 42, 8.5, 4.25), tone(c, -0.12)),
            *[control(ci(x, 42.75, 1.5), tone(c, 0.4), sym=None) for x in (17, 24.5, 39.5, 47)]]

def feather(x1, y1, x2, y2, w1, w2):
    """A bar from a narrow root to a wide round tip."""
    dx, dy = x2 - x1, y2 - y1; n = math.hypot(dx, dy); r0, r1 = w1 / 2, w2 / 2
    ux, uy = -dy / n, dx / n
    return (f"M{f(x1 + ux * r0)},{f(y1 + uy * r0)} L{f(x2 + ux * r1)},{f(y2 + uy * r1)} A{f(r1)},{f(r1)} 0 0 0 {f(x2 - ux * r1)},{f(y2 - uy * r1)} "
            f"L{f(x1 - ux * r0)},{f(y1 - uy * r0)} A{f(r0)},{f(r0)} 0 0 0 {f(x1 + ux * r0)},{f(y1 + uy * r0)} Z")

def xf_mirror(d):
    """Mirror a path left to right about x = 32."""
    t = re.findall(r"[MLHVCQAZ]|-?\d*\.?\d+(?:e-?\d+)?", d); out = []; i = 0; cmd = None
    while i < len(t):
        if t[i].isalpha():
            cmd = t[i]; i += 1
            if cmd == "Z": out.append("Z"); continue
        n = {"M": 2, "L": 2, "C": 6, "Q": 4, "H": 1, "V": 1, "A": 7}[cmd]
        v = [float(x) for x in t[i:i + n]]; i += n
        if cmd == "H": v[0] = 64 - v[0]
        elif cmd == "A": v[4] = 1 - v[4]; v[5] = 64 - v[5]
        elif cmd != "V": v = [64 - x if k % 2 == 0 else x for k, x in enumerate(v)]
        out.append(cmd + " ".join(f(x) for x in v))
    return "".join(out)

def _star4(cx, cy, r, waist):
    """A four-point star: points [r] out on the axes, its sides pulled in to [waist] on the diagonals."""
    pts = [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]
    w = waist / math.sqrt(2)
    mids = [(cx + w, cy - w), (cx + w, cy + w), (cx - w, cy + w), (cx - w, cy - w)]
    d = f"M{f(pts[0][0])},{f(pts[0][1])}"
    for i in range(4):
        (mx, my), (nx, ny) = mids[i], pts[(i + 1) % 4]
        d += f" Q{f(mx)},{f(my)} {f(nx)},{f(ny)}"
    return d + " Z"

def jelly(c):
    """Browser (LineageOS Jelly): a pocket compass, its white four-point star standing on a lit face."""
    out = [shadow(32, 60, 23, 3),
           body(ci(32, 8, 4) + circle_ccw(32, 8, 1.9), tone(c, -0.3), gloss=0.15, sym=1),
           body(rr(28.8, 10.8, 6.4, 4, 1.3), tone(c, -0.2), gloss=0.15, sym=1),
           body(ci(32, 35, 24), c), recess(ci(32, 35, 20.5), c), screen(ci(32, 35, 18), "#3466E0", sym=0)]
    for a in (45, 135, 225, 315):
        (x0, y0), (x1, y1) = [(32 + r * math.cos(math.radians(a)), 35 + r * math.sin(math.radians(a))) for r in (12.5, 15.5)]
        out.append(control(capsule(x0, y0, x1, y1, 2), tone(c, 0.45), sym=None))
    return out + [control(_star4(32, 35, 16, 6.2), "#FFFFFF"), control(rpoly([(32, 30), (37, 35), (32, 40), (27, 35)], 1), "#1E3266")]

def audiofx(c):
    """AudioFX: an effects unit, a big white knob in its dark collar beside two faders."""
    knob = (24, 34)
    out = [shadow(32, 59, 28, 3), body(rr(3, 11, 58, 45, 8), c)]
    for i in range(7):
        a = 150 + i * 40
        x, y = knob[0] + 16 * math.cos(math.radians(a)), knob[1] + 16 * math.sin(math.radians(a))
        out.append(screen(ci(x, y, 1.3), tone(c, 0.75) if i < 5 else tone(c, -0.45), sym=None))
    out += [recess(ci(*knob, 13), c), control(ci(*knob, 11.8), "#2C3434"), control(ci(*knob, 9), "#F4F4F6"),
            control(capsule(knob[0] + 2.4, knob[1] - 2.4, knob[0] + 5.6, knob[1] - 5.6, 2.3), tone(c, -0.2), sym=1)]
    for x, y in ((46.5, 38), (55, 25)):
        out += [recess(capsule(x, 18, x, 48, 3), c), control(rr(x - 3.5, y - 2.6, 7, 5.2, 1.6), "#F4F4F6", sym=1)]
    return out

# ---- the Thor's tool apps: thor-features' root tools and Foldcade's device plugin
# Foldcade's own colours, the teal-to-violet of Afterglow's light, mark Foldcade's plugins.
FOLD_TEAL, FOLD_VIOLET = "#3EE6C0", "#B98CFF"

def _fan_blades(cx, cy, r, color, n=3, twist=34):
    """Swept blades round a hub: the status fan's blade (symbols.fan) grown to radius [r], each point turned
    further the farther out it lies, so the blades sweep with the turn."""
    k = r / 10.0
    blade = [(10.2, 9.8), (7.4, 7.6), (8.0, 2.4), (12.4, 2.2), (16.8, 2.0), (17.2, 6.8), (14.0, 9.6)]
    out = []
    for i in range(n):
        pts = []
        for x, y in blade:
            dx, dy = (x - 12) * k, (y - 12) * k
            a = 2 * math.pi * i / n + math.radians(twist) * math.hypot(dx, dy) / r
            pts.append((cx + dx * math.cos(a) - dy * math.sin(a), cy + dx * math.sin(a) + dy * math.cos(a)))
        p = pts
        d = (f"M{f(p[0][0])},{f(p[0][1])} C{f(p[1][0])},{f(p[1][1])} {f(p[2][0])},{f(p[2][1])} {f(p[3][0])},{f(p[3][1])} "
             f"C{f(p[4][0])},{f(p[4][1])} {f(p[5][0])},{f(p[5][1])} {f(p[6][0])},{f(p[6][1])} Z")
        out.append(control(d, color))
    return out

def fan_profiles(c):
    """Fan profiles: a case fan in its square frame, the status bar's swept blades turning in the shroud."""
    out = [shadow(32, 59, 23, 3), body(rr(8, 8, 48, 48, 10), c), recess(ci(32, 32, 21), c)]
    out += [recess(ci(x, y, 1.9), c, sym=None) for x in (13.5, 50.5) for y in (13.5, 50.5)]
    return out + _fan_blades(32, 32, 19, tone(c, 0.6)) + [control(ci(32, 32, 4.8), tone(c, -0.35), sym=1),
                                                          control(ci(32, 32, 2.2), tone(c, 0.45), sym=None)]

def screen_panel(c):
    """Screen Panel: a panel of two brightness faders, one per screen. Each row starts with its screen, the top
    one wide and bright, the bottom one small and dimmed, and its groove glows up to the fader's cap."""
    out = [shadow(32, 58, 27, 3), body(rr(3, 9, 58, 46, 9), c)]
    for y, (sw, sh), glass, level in ((21, (18, 11.5), GLASS, 0.8), (43, (13, 10.5), tone(GLASS, -0.4), 0.38)):
        x0, x1 = 29, 55
        knob = x0 + 3 + (x1 - x0 - 6) * level
        out += [*bezel(17 - sw / 2, y - sh / 2, sw, sh, 2, c, glass, inset=1.2),
                recess(capsule(x0, y, x1, y, 4.4), c),
                screen(capsule(x0, y, knob, y, 2.6), "#F2FFE8", sym=0),
                control(rr(knob - 3.2, y - 7, 6.4, 14, 2.6), tone(c, 0.75), sym=1)]
    return out

def oled_wash(c):
    """OLED Wash: a squeegee pushing a lit band of wash up a dark screen, leaving it black behind."""
    return [shadow(32, 58, 27, 3), body(rr(3, 9, 58, 40, 6), c), recess(rr(7, 13, 50, 32, 3), c),
            screen(rr(8.5, 18, 47, 10, 1), tone(c, 0.2), sym=0),
            body(capsule(32, 34, 37.5, 49, 4.4), tone(c, 0.15), sym=2),
            control(capsule(37, 47, 39.5, 55, 6.6), tone(c, -0.3), sym=2),
            control(capsule(10, 29.6, 54, 29.6, 2.8), sym=1),
            body(rr(14, 31, 36, 5.2, 2.2), "#C3CEDF", gloss=0.35, sym=1)]

def stick_cal(c):
    """Stick Cal: a stick's range as a blue ring with a crosshair and its calibrated range dashed inside it, the
    amber stick cap pushed out to the edge on a line from the centre."""
    amber = "#FFC15A"
    a = math.radians(-135); ex, ey = 32 + 22.5 * math.cos(a), 32 + 22.5 * math.sin(a)
    out = [shadow(32, 60, 23, 3), body(ci(32, 32, 26.5), c), recess(ci(32, 32, 19.5), c)]
    out += [control(arc_band(32, 32, 11.5, 1.8, t + 11, t + 34), tone(c, -0.05), sym=None) for t in range(0, 360, 45)]
    for t in (0, 90, 180, 270):
        u, v = math.cos(math.radians(t)), math.sin(math.radians(t))
        out.append(control(capsule(32 + 14.5 * u, 32 + 14.5 * v, 32 + 17.5 * u, 32 + 17.5 * v, 2), tone(c, 0.1), sym=None))
    return out + [control(ci(32, 32, 3.6), tone(c, 0.3), sym=1), control(capsule(32, 32, ex, ey, 3.4), tone(amber, -0.15), sym=1),
                  control(ci(ex, ey, 7.5), amber, sym=2)]

def foldcade_thor(c):
    """Foldcade Thor: a jigsaw piece, the plugin that fits the Thor into Foldcade, carrying the Thor's two screens,
    big over small, lit in Foldcade's teal and violet."""
    piece = ("M10,15 H23 A5,5 0 1 1 29,15 H42 Q47,15 47,20 V35 A5,5 0 1 1 47,41 V52 Q47,57 42,57 H29 "
             "A5,5 0 1 0 23,57 H10 Q5,57 5,52 V41 A5,5 0 1 0 5,35 V20 Q5,15 10,15 Z")
    dx = 2
    return [shadow(32, 60, 24, 2.8), body(xf(piece, dx=dx), c),
            *bezel(9.5 + dx, 19, 33, 12, 2.5, c, "#2FE0B8", inset=1.5), *bezel(16.5 + dx, 35.5, 19.5, 10.5, 2.2, c, "#A57BFF", inset=1.5)]

# name -> (drawing, accent, what it stands for, packages that wear it)
APPS = {
    "chrome": (chrome, "#4C8DFF", "Chrome: three segments around a lens", ["com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary"]),
    "files": (files, "#5AA9FF", "Files: a drawer of folders", ["com.android.documentsui", "com.google.android.documentsui", "com.google.android.apps.nbu.files"]),
    "settings": (settings, "#A7B6CC", "Settings: a cog", ["com.android.settings"]),
    "play_store": (play_store, "#3DDC84", "Play Store: a shopping bag with a play button", ["com.android.vending"]),
    "calendar": (calendar, "#4C8DFF", "Calendar: a desk calendar page", ["com.android.calendar", "com.google.android.calendar", "ws.xsoh.etar", "org.lineageos.etar"]),
    "clock": (clock, "#FF5C7A", "Clock: a twin-bell alarm clock", ["com.android.deskclock", "com.google.android.deskclock"]),
    "gallery": (gallery, "#FFB547", "Gallery: a framed photo of mountains", ["com.android.gallery3d", "org.fossify.gallery", "com.simplemobiletools.gallery.pro", "org.lineageos.glimpse"]),
    "photos": (photos, "#FFC93C", "Photos: a four-colour pinwheel", ["com.google.android.apps.photos", "com.google.android.apps.photosgo"]),
    "music": (music, "#FF8A3D", "Music: headphones", ["com.android.music", "org.lineageos.twelve"]),
    "recorder": (recorder, "#C3CEDF", "Recorder: a microphone with a red light", ["com.android.soundrecorder", "com.google.android.apps.recorder", "org.lineageos.recorder"]),
    "camera": (camera, "#7C8CFF", "Camera: a camera with a big lens", ["com.android.camera2", "com.android.camera", "com.google.android.GoogleCamera", "org.lineageos.aperture"]),
    "contacts": (contacts, "#4CC9FF", "Contacts: an address book with a person on it", ["com.android.contacts", "com.google.android.contacts"]),
    "phone": (phone, "#3EE68A", "Phone: a desk telephone", ["com.android.dialer", "com.google.android.dialer"]),
    "messages": (messages, "#8FB4FF", "Messages: a speech bubble", ["com.android.messaging", "com.android.mms", "com.google.android.apps.messaging"]),
    "calculator": (calculator, "#3EE6C0", "Calculator: a desk calculator", ["com.android.calculator2", "com.google.android.calculator"]),
    "youtube": (youtube, "#FF3B3B", "YouTube: a red screen with a play button", ["com.google.android.youtube"]),
    "youtube_music": (youtube_music, "#FF2E63", "YouTube Music: a red disc with a ringed play button", ["com.google.android.apps.youtube.music"]),
    "gmail": (gmail, "#FF5A4E", "Gmail: an envelope with a red flap", ["com.google.android.gm", "com.google.android.gm.lite"]),
    "maps": (maps, "#6FD487", "Maps: a pin on a folded map", ["com.google.android.apps.maps"]),
    "drive": (drive, "#3DD47A", "Drive: a triangle of three bars", ["com.google.android.apps.docs"]),
    "keep": (keep, "#FFD23F", "Keep: a sticky note with a lightbulb", ["com.google.android.keep"]),
    "jelly": (jelly, "#6B95FF", "Browser (LineageOS Jelly): a compass with a four-point star", ["org.lineageos.jelly"]),
    "audiofx": (audiofx, "#FF4D97", "AudioFX: a knob beside two faders", ["org.lineageos.audiofx"]),
    "odin_launcher": (odin_launcher, "#9FB8FF", "Odin Launcher: a winged helmet", ["com.odin.odinlauncher"]),
    "fan_profiles": (fan_profiles, "#6FD3FF", "Fan profiles: a case fan in its frame", ["com.thorfeatures.appprofiles"]),
    "screen_panel": (screen_panel, "#A9DB8E", "Thor Screen Panel: a brightness fader for each screen", ["com.thorfeatures.screenpanel"]),
    "oled_wash": (oled_wash, "#74DCCB", "Thor OLED Wash: a squeegee leaving a lit band across a dark screen", ["com.thorfeatures.oledwash"]),
    "stick_cal": (stick_cal, "#4A78FF", "Thor Stick Cal: a stick cap pushed to the edge of its range, over a crosshair", ["com.thorfeatures.stickcal"]),
    "foldcade_thor": (foldcade_thor, "#C3CEDF", "Foldcade Thor: a jigsaw piece carrying two lit screens", ["app.foldcade.thor"]),
}
