"""Afterglow's flat symbols on the 24-unit grid: controller hints, the status islands, and tile badges.

Every symbol follows docs/iconography.md: one warm-white ink, 2-unit strokes with round ends, solid
shapes with their details cut out, and colour only for state. A button's cap is its only container.
Nothing here carries a gradient, a gloss, or a glow; the host draws focus.
"""
import math
from art import *
from palette import *

STROKE = 2.0                 # the one line weight on the 24 grid
CAP_R = 10.6                 # a round cap fills the grid with 1.4 units to spare

# ---- a monoline alphabet for legends and counts, 10 units tall, drawn with round ends
CHARS = {
    "A": (8.2, "M0,10 L4.1,0 L8.2,10 M1.6,6.5 H6.6"),
    "B": (6.7, "M0,0 V10 H4.1 A2.56,2.56 0 0 0 4.1,4.88 H0 M0,4.88 H3.7 A2.44,2.44 0 0 0 3.7,0 H0"),
    "X": (7.4, "M0,0 L7.4,10 M7.4,0 L0,10"),
    "Y": (7.4, "M0,0 L3.7,5 L7.4,0 M3.7,5 V10"),
    "L": (5.0, "M0,0 V10 H5"),
    "R": (6.4, "M0,10 V0 H3.6 A2.6,2.6 0 0 1 3.6,5.2 H0 M3.2,5.2 L6.4,10"),
    "1": (3.2, "M0,2.2 L3.2,0 V10"),
    "2": (6.0, "M0.2,2.6 A2.9,2.9 0 1 1 5.4,4.4 L0,10 H6"),
    "3": (5.8, "M0,0 H5.6 L2.4,4.1 A3,3 0 1 1 0,8.9"),
    "4": (6.2, "M4.6,10 V0 L0,6.8 H6.2"),
    "5": (5.8, "M5.4,0 H0.9 L0.4,4.3 A3.1,3.1 0 1 1 0,9"),
    "6": (6.0, "M5,0.6 Q4,0 3,0 Q0,0 0,6.9 A3,3 0 1 0 6,6.9 A3,3 0 1 0 0,6.9"),
    "7": (6.0, "M0,0 H6 L2.4,10"),
    "8": (6.0, "M3,4.6 A2.3,2.3 0 1 1 3,0 A2.3,2.3 0 1 1 3,4.6 Z M3,4.6 A2.7,2.7 0 1 1 3,10 A2.7,2.7 0 1 1 3,4.6 Z"),
    "9": (6.0, "M1,9.4 Q2,10 3,10 Q6,10 6,3.1 A3,3 0 1 0 0,3.1 A3,3 0 1 0 6,3.1"),
    "+": (5.4, "M0,5 H5.4 M2.7,2.3 V7.7"),
}

def legend(text, cx, cy, h, color, sw, track=1.6):
    """[text] centred on (cx, cy), [h] tall, as one round-ended stroke."""
    s = h / 10
    width = sum(CHARS[c][0] for c in text) * s + track * s * (len(text) - 1)
    x, parts = cx - width / 2, []
    for c in text:
        w, d = CHARS[c]
        parts.append(xf(d, s=s, cx=0, cy=0, dx=x, dy=cy - h / 2))
        x += (w + track) * s
    return L("".join(parts), stroke=color, sw=sw)

def ink(d, color=INK, sw=STROKE, alpha=1.0):
    return L(d, stroke=color, sw=sw, salpha=alpha)

def solid(d, color=INK, alpha=1.0, evenodd=False):
    return L(d, fill=color, alpha=alpha, evenodd=evenodd)

def gap(d, sw):
    """A cut where two marks cross. Status icons sit on black islands, so a cut is drawn in black."""
    return L(d, stroke=BLACK, sw=sw)

def caps(pressed):
    """The cap and legend colours: dark cap and ink at rest, teal cap and dark legend while pressed."""
    return (TEAL, ON_TEAL) if pressed else (CAP, INK)

# ---------------- controller ----------------
def face(letter, pressed):
    cap, ink_ = caps(pressed)
    return [solid(circle(12, 12, CAP_R), cap), legend(letter, 12, 12, 8.6, ink_, 2.1)]

def shoulder(name, pressed):
    cap, ink_ = caps(pressed)
    left, trigger = name[0] == "L", name[1] == "2"
    if trigger:   # a trigger: taller, domed toward the player
        body = "M3,10 Q3,4.5 9,4.5 H15 Q21,4.5 21,10 V17.5 Q21,19.5 19,19.5 H5 Q3,19.5 3,17.5 Z"
        cy = 12.6
    else:         # a bumper: wide, its outer top corner rounder
        body = rrect(2, 6.5, 20, 11, (6, 3, 3, 3) if left else (3, 6, 3, 3))
        cy = 12
    return [solid(body, cap), legend(name, 12, cy, 6.6, ink_, 1.9, track=2.4)]

THOR_LEGENDS = {
    "square": "M8.3,8.3 H15.7 V15.7 H8.3 Z",                        # Select: a square, as printed on the Thor
    "triangle": "M10.2,8.1 L15.9,12 L10.2,15.9 Z",                  # Start: a play triangle, nothing around it
    "home": "M7.4,12.2 L12,8 L16.6,12.2 M9.1,10.9 V16.2 H14.9 V10.9",
    "back": "M8.2,9.4 A5.2,5.2 0 1 1 7.6,14.4 M8.2,6.2 V9.6 H11.6",
}

def round_system(kind, pressed):
    """The Thor's four round system keys. The cap is the button; the legend is what's printed on it."""
    cap, ink_ = caps(pressed)
    return [solid(circle(12, 12, CAP_R), cap), ink(THOR_LEGENDS[kind], ink_)]

def pill_system(kind, pressed):
    """Start and Select on other controllers: a pill cap with the same legends as the Thor's."""
    cap, ink_ = caps(pressed)
    legend_ = {"start": "M10.5,9.1 L14.8,12 L10.5,14.9 Z", "select": "M9.3,9.3 H14.7 V14.7 H9.3 Z"}[kind]
    return [solid(rrect(2.5, 6.5, 19, 11, 5.5), cap), ink(legend_, ink_, 1.8)]

DPAD = ("M9.4,2.2 H14.6 Q15.4,2.2 15.4,3 V8.6 H21 Q21.8,8.6 21.8,9.4 V14.6 Q21.8,15.4 21,15.4 H15.4 V21 "
        "Q15.4,21.8 14.6,21.8 H9.4 Q8.6,21.8 8.6,21 V15.4 H3 Q2.2,15.4 2.2,14.6 V9.4 Q2.2,8.6 3,8.6 H8.6 V3 "
        "Q8.6,2.2 9.4,2.2 Z")
DPAD_ARROWS = {"up": "M12,4.6 L13.8,7.1 H10.2 Z", "down": "M12,19.4 L13.8,16.9 H10.2 Z",
               "left": "M4.6,12 L7.1,10.2 V13.8 Z", "right": "M19.4,12 L16.9,10.2 V13.8 Z"}
DPAD_ARMS = {"up": (8.6, 2.2, 6.8, 7.2), "down": (8.6, 14.6, 6.8, 7.2),
             "left": (2.2, 8.6, 7.2, 6.8), "right": (14.6, 8.6, 7.2, 6.8)}

def dpad(active=None, pressed=False):
    """The cross with an arrow on each arm. One arm lit in ink names a direction; teal is the whole pad held."""
    cap, ink_ = caps(pressed)
    out = [solid(DPAD, cap)]
    for way, arrow in DPAD_ARROWS.items():
        if way == active:
            x, y, w, h = DPAD_ARMS[way]
            out += [G([solid(rrect(x, y, w, h, 0), INK)], clip=DPAD), solid(arrow, CAP)]
        else:
            out.append(solid(arrow, ink_))
    return out

def stick(side, pressed=False, clicked=False):
    """A stick's round cap with its four directions marked. Clicking it in says L3 or R3."""
    cap, ink_ = caps(pressed)
    out = [solid(circle(12, 12, CAP_R), cap)]
    out += [solid(circle(x, y, 1.05), ink_) for x, y in ((12, 3.9), (12, 20.1), (3.9, 12), (20.1, 12))]
    out.append(legend(side + "3", 12, 12, 5.8, ink_, 1.8, track=1.2) if clicked else legend(side, 12, 12, 7, ink_, 2.0))
    return out

FACE_SEATS = {"top": (12, 4.6), "left": (4.6, 12), "right": (19.4, 12), "bottom": (12, 19.4)}

def face_diamond(lit=None, pressed=False):
    """Four seats, no letters: the diamond says where the button is, and the cap beside it says which.
    Letters at this size can't be read, and Nintendo and Xbox layouts swap them."""
    out = []
    for seat, (x, y) in FACE_SEATS.items():
        if pressed and (lit is None or seat == lit): color = TEAL
        elif seat == lit: color = INK
        else: color = CAP
        out.append(solid(circle(x, y, 3.7), color))
    return out

def controller_assets():
    A = []
    for suffix, pressed in (("", False), ("_filled", True)):
        held = " (held)" if pressed else ""
        for k in "ABXY":
            A.append(Asset(f"ic_btn_{k.lower()}{suffix}", 24, 24, face(k, pressed), note=f"Face button {k}{held}"))
        for name in ("L1", "R1", "L2", "R2"):
            A.append(Asset(f"ic_btn_{name.lower()}{suffix}", 24, 24, shoulder(name, pressed), note=f"Shoulder {name}{held}"))
        A.append(Asset(f"ic_btn_start{suffix}", 24, 24, pill_system("start", pressed), note=f"Start on other controllers{held}"))
        A.append(Asset(f"ic_btn_select{suffix}", 24, 24, pill_system("select", pressed), note=f"Select on other controllers{held}"))
        A.append(Asset(f"ic_btn_square{suffix}", 24, 24, round_system("square", pressed), note=f"Thor Select: a square on a round cap{held}"))
        A.append(Asset(f"ic_btn_triangle{suffix}", 24, 24, round_system("triangle", pressed), note=f"Thor Start: a play triangle on a round cap{held}"))
        A.append(Asset(f"ic_btn_home{suffix}", 24, 24, round_system("home", pressed), note=f"Thor Home: a house on a round cap{held}"))
        A.append(Asset(f"ic_btn_back{suffix}", 24, 24, round_system("back", pressed), note=f"Thor Back: a return arrow on a round cap{held}"))
        A.append(Asset(f"ic_btn_dpad{suffix}", 24, 24, dpad(None, pressed), note=f"D-pad{held}"))
        A.append(Asset(f"ic_btn_lstick{suffix}", 24, 24, stick("L", pressed), note=f"Left stick{held}"))
        A.append(Asset(f"ic_btn_rstick{suffix}", 24, 24, stick("R", pressed), note=f"Right stick{held}"))
        # The diamond shows position only, so the Nintendo (_n), Xbox (_x) and unlabeled names draw alike.
        # The bare names are the Nintendo set's older aliases.
        for layout in ("n", "x", "blank"):
            names = [f"ic_btn_face_diamond_{layout}{suffix}"]
            if layout == "n": names.append(f"ic_btn_face_diamond{suffix}")
            for n in names:
                A.append(Asset(n, 24, 24, face_diamond(None, pressed), note=f"Face-button diamond{held}"))
        letters = {"n": {"top": "x", "left": "y", "right": "a", "bottom": "b"},
                   "x": {"top": "y", "left": "x", "right": "b", "bottom": "a"}}
        for layout, seats in letters.items():
            for seat, letter in seats.items():
                names = [f"ic_btn_face_diamond_{layout}_{letter}{suffix}"]
                # ic_btn_face_diamond_x is the Xbox base, so only y, a and b keep a bare Nintendo alias.
                if layout == "n" and letter != "x": names.append(f"ic_btn_face_diamond_{letter}{suffix}")
                for n in names:
                    A.append(Asset(n, 24, 24, face_diamond(seat, pressed), note=f"Face-button diamond, {seat} seat{held}"))
        for seat in FACE_SEATS:
            A.append(Asset(f"ic_btn_face_diamond_blank_{seat}{suffix}", 24, 24, face_diamond(seat, pressed),
                           note=f"Face-button diamond, {seat} seat{held}"))
    for way in DPAD_ARROWS:
        A.append(Asset(f"ic_btn_dpad_{way}", 24, 24, dpad(way), note=f"D-pad {way}"))
    A.append(Asset("ic_btn_lstick_press", 24, 24, stick("L", clicked=True), note="Left stick click (L3)"))
    A.append(Asset("ic_btn_rstick_press", 24, 24, stick("R", clicked=True), note="Right stick click (R3)"))
    return A

# ---------------- status islands ----------------
SLASH = "M4.5,4.5 L19.5,19.5"

def slashed(layers):
    """Off: the icon at off ink, crossed out with a cut either side of the line."""
    return layers + [gap(SLASH, 4.6), ink(SLASH, INK, 2.2)]

def wifi(bars, off=False):
    """A dot and three arcs. The dot is the first bar."""
    cx, cy = 12, 18.6
    lit = lambda n: not off and bars >= n
    out = [solid(circle(cx, cy, 1.7), INK, 1 if lit(1) else OFF_ALPHA)]
    for i, r in enumerate((5.2, 9.2, 13.2)):
        a0, a1 = math.radians(-135), math.radians(-45)
        d = (f"M{f(cx + r * math.cos(a0))},{f(cy + r * math.sin(a0))} "
             f"A{f(r)},{f(r)} 0 0 1 {f(cx + r * math.cos(a1))},{f(cy + r * math.sin(a1))}")
        out.append(ink(d, INK, 2.2, 1 if lit(i + 2) else OFF_ALPHA))
    return slashed(out) if off else out

BOLT = "M12.5,7.4 L8.9,12.5 H11.3 L10.7,16.6 L14.5,11.3 H12 Z"

def battery(level, charging=False):
    """The cell fills with charge. Low is rose; charging is teal, with a bolt."""
    color = ROSE if level <= 15 and not charging else (TEAL if charging else INK)
    out = [L(rrect(2.2, 7.4, 17.6, 9.2, 2.6), stroke=INK, sw=STROKE), solid(rrect(20.6, 10.3, 1.9, 3.4, (0, 0.9, 0.9, 0)))]
    w = 13.2 * level / 100
    if w > 0:
        out.append(solid(rrect(4.4, 9.6, max(w, 1.6), 4.8, 1.1), color))
    if charging:
        out += [L(BOLT, stroke=BLACK, sw=2.2), solid(BOLT)]
    return out

BELL = ("M12,3.4 C8.7,3.4 6.6,5.9 6.6,9.3 V12.8 L5.1,15.3 Q4.6,16.4 5.9,16.4 H18.1 Q19.4,16.4 18.9,15.3 "
        "L17.4,12.8 V9.3 C17.4,5.9 15.3,3.4 12,3.4 Z")

def bell(count=None):
    """The bell, and a rose count when something is waiting. A dot when the count isn't known."""
    out = [solid(BELL), ink("M10,19.2 Q12,20.8 14,19.2")]
    if count == "dot":
        out += [solid(circle(18, 5.6, 4.6), BLACK), solid(circle(18, 5.6, 3.4), ROSE)]
    elif count:
        x0 = 11.2 if len(count) > 1 else 13.2
        pill = rrect(x0, 1.6, 22.8 - x0, 8.6, 4.3)
        out += [L(pill, fill=ROSE, stroke=BLACK, sw=2), legend(count, (x0 + 22.8) / 2, 5.9, 4.4, INK, 1.4, track=1.2)]
    return out

def bluetooth(on=True):
    out = [ink("M7.2,8 L16.8,16 L12,20.2 V3.8 L16.8,8 L7.2,16", INK, STROKE, 1 if on else OFF_ALPHA)]
    return out if on else slashed(out)

SPEAKER = ("M3.4,10 Q3.4,8.8 4.6,8.8 H7.4 L11.2,5.5 Q12.6,4.4 12.6,6.2 V17.8 Q12.6,19.6 11.2,18.5 L7.4,15.2 "
           "H4.6 Q3.4,15.2 3.4,14 Z")

def volume(level):
    """A speaker and up to three waves. Muted shows a cross where the waves were."""
    out = [solid(SPEAKER)]
    if level == 0:
        return out + [ink("M15.6,9.4 L20.8,14.6 M20.8,9.4 L15.6,14.6")]
    for i, r in enumerate((3.4, 6.4, 9.4)):
        a = math.radians(46)
        x, y = 12.8 + r * math.cos(a), r * math.sin(a)
        out.append(ink(f"M{f(x)},{f(12 - y)} A{f(r)},{f(r)} 0 0 1 {f(x)},{f(12 + y)}", INK, STROKE, 1 if level > i else OFF_ALPHA))
    return out

def gear():
    n, ro, ri = 8, 10.2, 7.6
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n - math.pi / 2
        for da, r in ((-0.30, ri), (-0.17, ro), (0.17, ro), (0.30, ri)):
            pts.append((12 + r * math.cos(a + da), 12 + r * math.sin(a + da)))
    return [L(poly(pts) + circle_ccw(12, 12, 3.6), fill=INK, stroke=INK, sw=1.2)]

def wrench():
    d = ("M14.9,3.4 A5.2,5.2 0 0 0 10.3,10.4 L3.9,16.8 A2.3,2.3 0 0 0 7.2,20.1 L13.6,13.7 "
         "A5.2,5.2 0 0 0 20.6,9.1 L17.6,12 L14.1,11.4 L13.5,7.9 L16.4,4.9 A5.2,5.2 0 0 0 14.9,3.4 Z")
    return [solid(d)]

def sliders():
    out = [ink("M4,7 H20 M4,12 H20 M4,17 H20")]
    for x, y in ((9, 7), (15, 12), (7.5, 17)):
        out += [solid(circle(x, y, 3.4), BLACK), solid(circle(x, y, 2.6))]
    return out

def power():
    return [ink("M7.4,6.9 A7.6,7.6 0 1 0 16.6,6.9 M12,3.4 V11.4", INK, 2.2)]

def clock():
    return [ink(circle(12, 12, 8.6) + "M12,7.6 V12 L15,13.8")]

def info():
    return [ink(circle(12, 12, 8.6) + "M12,11 V16.2"), solid(circle(12, 7.8, 1.3))]

def display():
    """Personalization: the brightness sun."""
    rays = "".join(f"M{f(12 + 6.6 * math.cos(a))},{f(12 + 6.6 * math.sin(a))} L{f(12 + 9 * math.cos(a))},{f(12 + 9 * math.sin(a))}"
                   for a in (math.pi / 4 * i for i in range(8)))
    return [solid(circle(12, 12, 4.2)), ink(rays)]

def status_assets():
    A = [Asset("ic_status_bell", 24, 24, bell(), note="Notifications"),
         Asset("ic_status_bell_dot", 24, 24, bell("dot"), note="Notifications, something unread")]
    for n in list(range(1, 10)) + ["9+"]:
        A.append(Asset(f"ic_status_bell_{'9plus' if n == '9+' else n}", 24, 24, bell(str(n)), note=f"Notifications, {n} unread"))
    for b in range(5):
        A.append(Asset(f"ic_status_wifi_{b}", 24, 24, wifi(b), note=f"Wi-Fi, {b} bars"))
    A.append(Asset("ic_status_wifi_off", 24, 24, wifi(0, off=True), note="Wi-Fi off"))
    for lv in range(0, 101, 10):
        A.append(Asset(f"ic_status_battery_{lv}", 24, 24, battery(lv), note=f"Battery {lv}%"))
        A.append(Asset(f"ic_status_battery_{lv}_charging", 24, 24, battery(lv, True), note=f"Battery {lv}%, charging"))
    A += [Asset("ic_status_bluetooth", 24, 24, bluetooth(True), note="Bluetooth on"),
          Asset("ic_status_bluetooth_off", 24, 24, bluetooth(False), note="Bluetooth off")]
    for v in range(4):
        A.append(Asset(f"ic_status_volume_{v}" if v else "ic_status_volume_mute", 24, 24, volume(v), note=f"Volume level {v}"))
    A += [Asset("ic_status_settings", 24, 24, gear(), note="Settings"),
          Asset("ic_status_wrench", 24, 24, wrench(), note="Wrench"),
          Asset("ic_status_tools", 24, 24, sliders(), note="Tools: three sliders"),
          Asset("ic_status_power", 24, 24, power(), note="Power"),
          Asset("ic_status_playtime", 24, 24, clock(), note="Play time today"),
          Asset("ic_status_info", 24, 24, info(), note="About"),
          Asset("ic_status_display", 24, 24, display(), note="Personalization: brightness")]
    return A

# ---------------- tile badges (tinted by the host) ----------------
# ---------------- controls ----------------
def control_assets():
    """Glyphs the host's own controls draw: a checkmark's tick, the keyboard's keys, and Find's field.
    They are drawn at the weights the host drew them with before they were theme slots, so a 24-unit
    stroke here is 9% of the key the glyph sits on (2.16), or a tenth of the checkmark (2.4)."""
    key = 2.16
    tick = poly([(6.96, 12.48), (10.56, 16.08), (17.28, 8.88)], close=False)
    arrow = poly([(12, 1.92), (22.56, 12.48), (16.8, 12.48), (16.8, 18.24), (7.2, 18.24), (7.2, 12.48), (1.44, 12.48)])
    tag = poly([(0.96, 12), (7.68, 4.32), (23.04, 4.32), (23.04, 19.68), (7.68, 19.68)])
    cross = "M11.28,8.4 L18.48,15.6 M11.28,15.6 L18.48,8.4"
    space = "M2.304,8.64 V15.84 H55.296 V8.64"
    glass = circle(10.08, 10.08, 7.2)
    handle = "M15.264,15.264 L21.6,21.6"
    note = "Tinted at draw time."
    return [Asset("ic_control_check", 24, 24, [ink(tick, sw=2.4)], note=f"A checkmark's tick, revealed left to right as it turns on. {note}", tint=True),
            Asset("ic_control_dash", 24, 24, [ink("M6.72,12 H17.28", sw=2.4)], note=f"A checkmark for a choice that is partly on. {note}", tint=True),
            Asset("ic_control_shift", 24, 24, [L(arrow, stroke=INK, sw=key, cap="butt")], note=f"Keyboard Shift while it is off. {note}", tint=True),
            Asset("ic_control_shift_on", 24, 24, [L(arrow, fill=INK, stroke=INK, sw=key, cap="butt")], note=f"Keyboard Shift while it is on; it fades in over the outline. {note}", tint=True),
            Asset("ic_control_erase", 24, 24, [L(tag, stroke=INK, sw=key), ink(cross, sw=key)], note=f"Keyboard Erase. {note}", tint=True),
            Asset("ic_control_space", 57.6, 24, [ink(space, sw=key)], dp=(57.6, 24), note=f"Keyboard Space, 2.4 keys' glyphs wide. {note}", tint=True),
            Asset("ic_control_search", 24, 24, [ink(glass, sw=2.64), ink(handle, sw=2.64)], note=f"Find's field before anything is typed. {note}", tint=True)]

def badge_assets():
    cloud = "M7,19 H17.5 A4.5,4.5 0 0 0 18.1,10.04 A6,6 0 0 0 6.6,9.1 A5,5 0 0 0 7,19 Z"
    check = circle(12, 12, 10) + "M6.4,12.4 L8,10.8 L10.5,13.3 L16,7.8 L17.6,9.4 L10.5,16.5 Z"
    eye = "M4.32,12 Q12,3.84 19.68,12 Q12,20.16 4.32,12 Z"
    arrows = [ink("M5,12 A7,7 0 0 1 18.58,9.61", INK, 2.4), solid("M20.84,8.79 L19.47,12.05 L16.32,10.43 Z"),
              ink("M19,12 A7,7 0 0 1 5.42,14.39", INK, 2.4), solid("M3.16,15.21 L4.53,11.95 L7.68,13.57 Z")]
    return [Asset("ic_badge_cloud", 24, 24, [solid(cloud)], note="Tile badge: the game is on its server, not this device. Tinted at draw time.", tint=True),
            Asset("ic_badge_check", 24, 24, [solid(check, evenodd=True)], note="Tile badge: the sync worked. Tinted at draw time.", tint=True),
            Asset("ic_badge_sync", 24, 24, arrows, note="Tile badge: saves are syncing. Tinted at draw time.", tint=True),
            Asset("ic_badge_hidden", 24, 24, [ink(eye, sw=1.92), solid(circle(12, 12, 2.16)), ink("M5.76,18.72 L18.24,5.28", sw=2.304)],
                  note="Find badge: a hidden result. The host draws the disc behind it. Tinted at draw time.", tint=True)]
