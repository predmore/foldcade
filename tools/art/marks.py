"""Afterglow's marks: every place, platform, and collection glyph, each drawn once as parts on the
64-unit grid (see objects.py). Platforms are the thing you hold, seen from the front: the controller
or the handheld. Original drawings of generic hardware shapes, no console logos.

A place or platform ships twice from one drawing: mark_<name> is the lit object on the home grid
and the hero, and symbol_<name> is the flat one-ink shape for menu rows and the collection picker.
A collection glyph ships only as symbol_<name>; on the grid it is pressed into the folder's face.
"""
import math
from art import *
from palette import *
from objects import *

# ---- accents. A mark's accent is its body colour and the light the grid draws behind it.
LIME = "#B6F25C"; GRAPE = "#C77DFF"; INDIGO = "#7C8CFF"; RED = "#FF4D5A"; LAVENDER = "#B39DFF"
GREEN = "#3EE68A"; SKY = "#8FDFFF"; JOY_L = "#2FC7FF"; JOY_R = "#FF4F5E"; SILVER = "#C3CEDF"
PSBLUE = "#4C8DFF"; CRIMSON = "#FF3B52"; SUNSET = "#FF8A3D"; DROID = "#3DDC84"; MOON = "#A9BCFF"
GOLD = "#FFC15A"; DECK = "#66C0F4"; EMU = "#B48CFF"; CUBE = "#9B7BFF"; PSP = "#6FC3FF"; SMS = "#FF5E3A"
CLOUD = "#40D6FF"; CART = "#3EE0C3"; DISC = "#BC9CFF"; HANDHELD = "#FFBA48"

rr = rrect
ci = circle

def stick(cx, cy, r, base):
    """A thumbstick: a well cut into the body and a rubber cap standing in it."""
    return [recess(ci(cx, cy, r + 1.3), base), control(ci(cx, cy, r))]

def bezel(x, y, w, h, r, base, glass=GLASS, inset=1.6):
    """A handheld's screen: a dark bezel cut into the body and the lit glass in it."""
    return [recess(rr(x, y, w, h, r), base), screen(rr(x + inset, y + inset, w - 2 * inset, h - 2 * inset, max(r - inset, 0.8)), glass, sym=0)]

def hinge(y, x0=12, x1=52):
    return recess(rr(x0, y, x1 - x0, 3.6, 1.8), RUBBER, sym=None)

# ---------------- places and libraries ----------------
PAD = ("M20,16 H44 C52,16 56,20 58.5,30 L61.5,43 C63,51 59,55 54,55 C50,55 47.5,52 45.5,48 L43.5,44 H20.5 "
       "L18.5,48 C16.5,52 14,55 10,55 C5,55 1,51 2.5,43 L5.5,30 C8,20 12,16 20,16 Z")

def console(c):
    """Any game: a plain controller."""
    return [shadow(32, 57, 28, 4), body(PAD, c), control(plus(18, 29, 6, 4)),
            control(ci(43, 26, 3.2), tone(c, 0.4)), control(ci(50, 32, 3.2), tone(c, 0.4)),
            control(rr(27.5, 33, 4, 2.6, 1.3)), control(rr(32.5, 33, 4, 2.6, 1.3))]

HOUSE = ("M32,7.5 Q33.4,7.5 34.5,8.4 L57,28 Q58.6,29.6 57,30.6 Q56.4,31 55.4,31 H52 V53 Q52,57 48,57 H16 "
         "Q12,57 12,53 V31 H8.6 Q7.6,31 7,30.6 Q5.4,29.6 7,28 L29.5,8.4 Q30.6,7.5 32,7.5 Z")

def home(c):
    """Home: a house, the same one printed on the Thor's Home key."""
    return [shadow(32, 59, 25, 3), body(HOUSE, c),
            recess("M27,57 V44 Q27,40 31,40 H33 Q37,40 37,44 V57 Z", c),
            screen(rr(17.5, 36, 6, 6, 1.2), "#FFE7BE", sym=0), screen(rr(40.5, 36, 6, 6, 1.2), "#FFE7BE", sym=0)]

def library(c):
    """Every game: a shelf of game cases, one leaning."""
    out = [shadow(32, 60, 28, 3)]
    shades = (tone(c, -0.15), c, tone(c, 0.15))
    for (x, y, h), s in zip(((9, 12, 40), (23, 16, 36)), shades):
        out += [body(rr(x, y, 11, h, 2.5), s), recess(rr(x + 2.5, y + 5, 6, 3, 1.5), s)]
    lean = lambda d: xf(d, deg=16, cx=44, cy=52)
    out += [body(lean(rr(38.5, 13, 11, 39, 2.5)), shades[2]), recess(lean(rr(41, 18, 6, 3, 1.5)), shades[2]),
            body(rr(6, 52, 52, 5.5, 2.4), tone(c, -0.3), sym=1)]
    return out

def android_apps(c):
    """Android apps: a grid of four app tiles."""
    shades = (tone(c, 0.12), tone(c, -0.1), tone(c, -0.18), c)
    return [shadow(32, 60, 26, 3)] + [body(rr(x, y, 22, 22, 7), s)
                                      for (x, y), s in zip(((8, 8), (34, 8), (8, 34), (34, 34)), shades)]

def android_games(c):
    """Android games: a phone held in a controller."""
    grips = ("M12,17 H52 Q61,17 61,26 V44 Q61,53 55,53 Q50,53 49,47 L48,45 H16 L15,47 Q14,53 9,53 "
             "Q3,53 3,44 V26 Q3,17 12,17 Z")
    return [shadow(32, 56, 28, 3.5), body(grips, c), *bezel(15, 19.5, 34, 23.5, 3, c),
            control(plus(9, 30, 3.6, 2.6)), control(ci(55, 27, 2.3), tone(c, 0.4)), control(ci(55, 34.5, 2.3), tone(c, 0.4))]

FOLDER_BACK = "M6,15 Q6,10 11,10 H23.5 Q26,10 27.6,12 L30.6,16 H53 Q58,16 58,21 V48 H6 Z"
FOLDER_FRONT = "M5.2,27 Q5.4,23 9.4,23 H54.6 Q58.6,23 58.8,27 L57.6,52 Q57.4,56 53.4,56 H10.6 Q6.6,56 6.4,52 Z"
FOLDER_TAB = "M6,15 Q6,10 11,10 H23.5 Q26,10 27.6,12 L30.6,16 H53 Q58,16 58,21 V20 H6 Z"   # the back, above a cut
PAPER = rr(10, 17, 44, 6, (2, 2, 0, 0))

def folder(c):
    """A folder: a back, a sheet inside, and the front."""
    return [shadow(32, 59, 27, 3.4), body(FOLDER_BACK, tone(c, -0.3), gloss=0.12, sym=None),
            paint(L(PAPER, fill=INK, alpha=0.85)), body(FOLDER_FRONT, c, gloss=0.26), outline(FOLDER_TAB)]

def cloud(c):
    """RomM: a cloud with a download arrow."""
    return [shadow(32, 55, 24, 3),
            body("M17,50 A10.5,10.5 0 0 1 16.4,29 A16,16 0 0 1 46.5,24.5 A12.8,12.8 0 0 1 47,50 Z", c),
            recess("M29.6,26 H34.4 V35.5 H39 L32,43.5 L25,35.5 H29.6 Z", c)]

def _crescent(ax, ay, ar, bx, by, br):
    """Disc A minus disc B, as one closed outline."""
    d = math.hypot(bx - ax, by - ay)
    a = (ar * ar - br * br + d * d) / (2 * d); h = math.sqrt(ar * ar - a * a)
    mx, my = ax + a * (bx - ax) / d, ay + a * (by - ay) / d
    ux, uy = -(by - ay) / d, (bx - ax) / d
    p1 = (mx + h * ux, my + h * uy); p2 = (mx - h * ux, my - h * uy)
    return (f"M{f(p1[0])},{f(p1[1])} A{f(ar)},{f(ar)} 0 1 1 {f(p2[0])},{f(p2[1])} "
            f"A{f(br)},{f(br)} 0 0 0 {f(p1[0])},{f(p1[1])} Z")

def moonlight(c):
    """Moonlight: a crescent moon and a star."""
    return [shadow(30, 59, 20, 3), body(_crescent(29, 35, 21, 39, 26, 17.5), c),
            body("M50,6 Q51,13 57,14 Q51,15 50,22 Q49,15 43,14 Q49,13 50,6 Z", tone(c, 0.4), gloss=0.1)]

def pc(c):
    """GameNative: a monitor showing a controller."""
    return [shadow(32, 58.5, 22, 2.5), body(rr(5, 8, 54, 37, 4), c),
            recess(rr(9, 12, 46, 29, 2), c), screen(rr(10.5, 13.5, 43, 26, 1.2)),
            control(xf(PAD, s=0.42, cx=32, cy=35.5, dy=-9), tone(c, -0.55)),
            body(rr(28, 45, 8, 6.5, 0), tone(c, -0.25), gloss=0), body(rr(18, 51.5, 28, 5, 2.5), tone(c, -0.1))]

def droiddeck(c):
    """DroidDeck: a wide handheld with two sticks."""
    shell = "M14,17 H50 Q62,17 62,29 V40 Q62,51 54,51 Q49,51 47,46 H17 Q15,51 10,51 Q2,51 2,40 V29 Q2,17 14,17 Z"
    return [shadow(32, 54, 28, 3), body(shell, c), *bezel(17, 20, 30, 22.5, 2, c),
            *stick(9.5, 27, 3, c), control(plus(9.5, 39, 3.4, 2.4)),
            *[control(d) for d in diamond(54.5, 27.5, 3.3, 1.5)], *stick(54.5, 39, 3, c)]

def emulators(c):
    """Emulators: a cartridge going into a handheld."""
    return [shadow(32, 59, 27, 3), body("M21,6 H40 L44,10 V25 H20 V7 Q20,6 21,6 Z", tone(c, -0.2)),
            recess(rr(23.5, 11, 17, 10.5, 1.5), c),
            body(rr(4, 27, 56, 29, 10), c), *bezel(19, 30, 26, 22.5, 2, c),
            control(plus(11.5, 41.5, 3.6, 2.6)), control(ci(53, 38.5, 2.5), tone(c, 0.35)), control(ci(47.5, 44.5, 2.5), tone(c, 0.35))]

# ---------------- platforms ----------------
def nintendo_3ds(c):
    """Nintendo 3DS-style: a clamshell with a wide top screen and a circle pad."""
    return [shadow(32, 60, 25, 3.5), body(rr(10, 4, 44, 27, 4.5), c), *bezel(13.5, 7.5, 37, 20, 2, c),
            hinge(30.2), body(rr(10, 33, 44, 25, 5), c), *bezel(22.5, 36.5, 19, 14.5, 2, c),
            *stick(16.3, 40.5, 2.6, c), control(plus(16.3, 51.4, 3.2, 2.3)),
            *[control(d) for d in diamond(47.6, 44, 3.2, 1.45)]]

def nintendo_ds(c):
    """Nintendo DS-style: a clamshell, two equal screens, speakers either side of the top one."""
    out = [shadow(32, 60, 25, 3.5), body(rr(9, 4, 46, 27, 4.5), c), *bezel(20, 7.5, 24, 20, 2, c)]
    out += [recess(ci(x, y, 1.2), c) for x in (14.5, 49.5) for y in (12.5, 17.5, 22.5)]
    out += [hinge(30.2), body(rr(9, 33, 46, 25, 5), c), *bezel(20, 36.5, 24, 18, 2, c),
            control(plus(14.5, 44, 3.6, 2.6)), *[control(d) for d in diamond(49.5, 44, 3.3, 1.5)]]
    return out

def game_boy(c):
    """Game Boy-style: a portrait handheld, its bottom corner rounded."""
    shell = "M15,4 H49 Q54,4 54,9 V48 Q54,60 42,60 H15 Q10,60 10,55 V9 Q10,4 15,4 Z"
    return [shadow(32, 61, 23, 3), body(shell, c), recess(rr(15.5, 9.5, 33, 24, (2.5, 2.5, 8, 2.5)), c),
            screen(rr(21.5, 13.5, 21, 16, 1.2), "#D6F7A0", sym=0),
            control(plus(21.5, 45, 5.6, 3.9)), control(ci(43.5, 41.5, 3.4), tone(c, -0.55)), control(ci(36.5, 45.5, 3.4), tone(c, -0.55)),
            control(capsule(25.5, 54, 28.5, 52.5, 1.9)), control(capsule(31, 54, 34, 52.5, 1.9)),
            recess(capsule(41, 57, 44.5, 52.5, 1.6), c), recess(capsule(45.5, 57, 49, 52.5, 1.6), c)]

def game_boy_color(c):
    """Game Boy Color-style: a smaller portrait handheld with a colour screen."""
    shell = "M15,5 H49 Q53,5 53,9 V50 Q53,60 43,60 H21 Q11,60 11,50 V9 Q11,5 15,5 Z"
    return [shadow(32, 61, 22, 3), body(shell, c), recess(rr(15.5, 9.5, 33, 22, 3), c),
            screen(rr(21, 12.5, 22, 16, 1.2), "#FFE9B0", sym=0),
            control(plus(21, 42, 5.2, 3.6)), control(ci(43.5, 39.5, 3.2), tone(c, -0.5)), control(ci(37, 43.5, 3.2), tone(c, -0.5)),
            control(capsule(26, 52.5, 29, 52.5, 2)), control(capsule(35, 52.5, 38, 52.5, 2))]

def game_boy_advance(c):
    """Game Boy Advance-style: a landscape handheld with rounded wings."""
    shell = "M14,17 H50 Q62,17 62,32 Q62,48 50,48 H14 Q2,48 2,32 Q2,17 14,17 Z"
    return [shadow(32, 51, 28, 3.2), body(shell, c), *bezel(19, 20.5, 26, 23, 2, c),
            control(plus(11, 30, 4.2, 3)), control(ci(54.5, 29, 2.8), tone(c, 0.35)), control(ci(49, 34.5, 2.8), tone(c, 0.35)),
            control(ci(9.5, 40.5, 1.2), sym=None), control(ci(13.5, 42, 1.2), sym=None)]

def nes(c):
    """NES-style: a rectangular controller with a dark face."""
    return [shadow(32, 49, 28, 3.6), body(rr(3, 18, 58, 28, 4), c), recess(rr(7.5, 22.5, 49, 19, 2), c),
            control(plus(17, 32, 5.6, 3.8)), control(rr(26.5, 33, 5, 2.6, 1.3), "#5A5F6B"), control(rr(33, 33, 5, 2.6, 1.3), "#5A5F6B"),
            control(ci(44.5, 34, 3.6), tone(c, 0.15)), control(ci(51.5, 34, 3.6), tone(c, 0.15))]

def snes(c):
    """Super NES-style: a rounded controller with four buttons in a diamond."""
    pills = [xf(rr(26.5, 34.7, 5, 2.6, 1.3), deg=-28, cx=29, cy=36), xf(rr(32.5, 34.7, 5, 2.6, 1.3), deg=-28, cx=35, cy=36)]
    return [shadow(32, 50, 28, 4), body(rr(3, 17, 58, 30, 15), c), recess(rr(9.5, 24, 15, 16, 7.5), c),
            control(plus(17, 32, 5.8, 4)), *[control(p) for p in pills],
            *[control(d, tone(c, 0.35)) for d in diamond(47, 32, 5.4, 3.1)]]

N64 = ("M12,12 H52 Q60,12 60,20 V44 Q60,51 54,51 Q48,51 47.5,44 L46.6,34 H40 L39.6,50 Q39.4,56 32,56 "
       "Q24.6,56 24.4,50 L24,34 H17.4 L16.5,44 Q16,51 10,51 Q4,51 4,44 V20 Q4,12 12,12 Z")

def nintendo_64(c):
    """Nintendo 64-style: the three-handled controller, its stick on the middle handle."""
    return [shadow(32, 58, 28, 3), body(N64, c), control(plus(12.5, 23, 4.4, 3.2)), *stick(32, 40, 3.4, c),
            control(ci(46, 28.5, 2.8), tone(c, 0.35)), control(ci(41.5, 22.5, 2.6), tone(c, 0.35)),
            *[control(d, sym=None) for d in diamond(53.5, 22.5, 3.1, 1.5)], control(ci(32, 22, 1.9), sym=None)]

GC = ("M18,15 H46 C53,15 58,20 59.5,28 L61.5,42 C62.5,50 58.5,55 53,55 C48,55 45.5,51 44,47 L42.5,44 H21.5 "
      "L20,47 C18.5,51 16,55 11,55 C5.5,55 1.5,50 2.5,42 L4.5,28 C6,20 11,15 18,15 Z")

def gamecube(c):
    """GameCube-style: a controller with one big A button and two kidney buttons around it."""
    return [shadow(32, 57, 28, 4), body(GC, c), *stick(15.5, 27, 3.8, c),
            control(ci(45.5, 28.5, 4.6), "#4FD97F"), control(ci(38.5, 34, 2.2), "#F0606A"),
            control(arc_band(45.5, 28.5, 8.4, 3.2, 212, 272), tone(c, 0.4)), control(arc_band(45.5, 28.5, 8.4, 3.2, 322, 392), tone(c, 0.4)),
            recess(ci(40.5, 39.5, 3.4), c), control(ci(40.5, 39.5, 2.3), "#FFD24C"), control(plus(23.5, 39.5, 2.9, 2.1))]

def wii(c):
    """Wii-style: the remote, upright."""
    return [shadow(32, 61.5, 12, 2.2), body(rr(22, 3, 20, 58, 7), c), recess(rr(25.5, 5, 13, 3, 1.5), c),
            control(plus(32, 15, 4.6, 3.2)), control(ci(32, 27, 3.6), tone(c, 0.35)),
            control(ci(26.5, 35, 1.3), sym=None), control(ci(32, 35, 1.6)), control(ci(37.5, 35, 1.3), sym=None),
            control(ci(32, 45, 2.3)), control(ci(32, 52, 2.3))]

def nintendo_switch(c):
    """Switch-style: a tablet with a controller on either side, blue and red."""
    left = "M14,18 V46 H11 Q3,46 3,38 V26 Q3,18 11,18 Z"
    right = "M50,18 H53 Q61,18 61,26 V38 Q61,46 53,46 H50 Z"
    return [shadow(32, 49, 29, 3), body(left, JOY_L), body(right, c), body(rr(15.5, 18, 33, 28, 1.5), "#4A4F5C"),
            recess(rr(17.5, 20, 29, 24, 1), "#4A4F5C"), screen(rr(18.5, 21, 27, 22, 0.6), sym=0),
            *stick(8.6, 24.5, 2.4, JOY_L), *[control(d, sym=None) for d in diamond(8.6, 38, 2.5, 1.1)],
            *[control(d, sym=None) for d in diamond(55.4, 25.5, 2.5, 1.1)], *stick(55.4, 38.5, 2.4, c)]

PS = ("M17,17 H47 C53,17 56.5,21 58,27 L61.6,46 C62.6,52 59.4,56 55,56 C51,56 49,53 47.6,49 L46.6,46 H17.4 "
      "L16.4,49 C15,53 13,56 9,56 C4.6,56 1.4,52 2.4,46 L6,27 C7.5,21 11,17 17,17 Z")

def playstation(c):
    """PlayStation-style: the first controller, before it had sticks."""
    return [shadow(32, 58, 28, 3.4), body(PS, c), control(plus(17, 29, 5.2, 3.6)),
            *[control(d, tone(c, -0.45)) for d in diamond(47, 29, 4.6, 2.4)],
            control(rr(26.5, 30.5, 4.8, 2.4, 1.2)), control(rr(32.7, 30.5, 4.8, 2.4, 1.2))]

def playstation_2(c):
    """PlayStation 2-style: the dual-stick controller."""
    return [shadow(32, 58, 28, 3.4), body(PS, c), control(plus(16.5, 27, 4.8, 3.4)),
            *[control(d, tone(c, -0.45)) for d in diamond(47.5, 27, 4.4, 2.3)],
            control(rr(27, 25.8, 4.2, 2.2, 1.1)), control(rr(32.8, 25.8, 4.2, 2.2, 1.1)),
            *stick(24.5, 37.5, 3.6, c), *stick(39.5, 37.5, 3.6, c)]

def psp(c):
    """PSP-style: a long handheld with a wide screen."""
    return [shadow(32, 47, 28, 2.6), body(rr(2, 20, 60, 24, 12), c), *bezel(15.5, 22.5, 33, 19, 1.5, c),
            control(plus(8.8, 29.5, 3.4, 2.4)), control(ci(8.8, 38.6, 1.9)),
            *[control(d) for d in diamond(55.2, 30.5, 2.9, 1.5)]]

def genesis(c):
    """Genesis-style: a rounded controller with a round d-pad and three buttons in a row."""
    shell = ("M14,15 Q23,13 32,18.5 Q41,13 50,15 Q61,18 61,31 Q61,46 50,48 Q41,49.5 32,44 Q23,49.5 14,48 "
             "Q3,46 3,31 Q3,18 14,15 Z")
    return [shadow(32, 51, 28, 3), body(shell, c), control(ci(16, 31, 7.5)), recess(plus(16, 31, 5.2, 2.2), RUBBER),
            *[control(ci(x, y, 3.3)) for x, y in ((40.5, 37), (47.2, 33), (54, 29))],
            control(rr(29.5, 25.5, 5, 2.4, 1.2))]

def master_system(c):
    """Master System-style: a controller with a square d-pad and two buttons."""
    return [shadow(32, 49, 28, 3), body(rr(4, 19, 56, 27, 6), c), control(rr(10, 23.5, 18, 18, 3.5)),
            recess(plus(19, 32.5, 6, 3), RUBBER), control(ci(43.5, 32.5, 3.9), tone(c, -0.5)), control(ci(53, 32.5, 3.9), tone(c, -0.5))]

def game_gear(c):
    """Game Gear-style: a wide handheld with a big screen."""
    return [shadow(32, 52, 28, 3), body(rr(2, 16, 60, 33, 11), c), *bezel(17.5, 19.5, 29, 26, 2.5, c),
            control(plus(9.6, 32.5, 4, 2.9)), control(ci(51.5, 35.5, 2.8), tone(c, -0.5)), control(ci(56.6, 30, 2.8), tone(c, -0.5)),
            control(ci(55, 21.8, 1.3), sym=None)]

def _ring_half(front, rx, ry, rxi, ryi, deg=-18, cx=32, cy=32):
    """The front or back half of a tilted ring, as one closed outline."""
    if front:
        d = f"M{-rx},0 A{rx},{ry} 0 0 0 {rx},0 L{rxi},0 A{rxi},{ryi} 0 0 1 {-rxi},0 Z"
    else:
        d = f"M{-rx},0 A{rx},{ry} 0 0 1 {rx},0 L{rxi},0 A{rxi},{ryi} 0 0 0 {-rxi},0 Z"
    return xf(d, deg=deg, cx=0, cy=0, dx=cx, dy=cy)

def saturn(c):
    """Saturn: the ringed planet."""
    back, front = _ring_half(False, 29, 8.5, 21.5, 4.6), _ring_half(True, 29, 8.5, 21.5, 4.6)
    return [shadow(32, 58, 20, 2.6), body(back, tone(c, -0.35), gloss=0.1, sym=1), body(ci(32, 32, 15.5), c),
            paint(L(xf(front, dy=1.2), fill=BLACK, alpha=0.35)), body(front, tone(c, 0.5), gloss=0.2)]

def dreamcast(c):
    """Dreamcast-style: a controller with a memory-card screen in its middle."""
    shell = ("M14,14 H50 Q60,14 60,26 L58.5,44 Q57.5,54 48.5,54 Q42.5,54 40,47 H24 Q21.5,54 15.5,54 "
             "Q6.5,54 5.5,44 L4,26 Q4,14 14,14 Z")
    return [shadow(32, 57, 26, 3), body(shell, c), *bezel(23, 16, 18, 14, 2, c, "#CFE9FF"),
            *stick(14.5, 25, 3.4, c), control(plus(14.5, 38, 4.2, 3)),
            *[control(d) for d in diamond(49.5, 30, 4.4, 2.3)], control(ci(32, 37, 1.8))]

# ---------------- generic marks ----------------
def clamshell(c):
    """Any dual-screen clamshell."""
    return [shadow(32, 60, 25, 3.5), body(rr(13, 4, 38, 27, 4.5), c), *bezel(16.5, 7.5, 31, 20, 2.5, c),
            hinge(30.2, 14, 50), body(rr(11, 33, 42, 25, 5), c), *bezel(21, 36.5, 22, 15, 2, c),
            control(plus(16.3, 44, 3.6, 2.8)), *[control(d) for d in diamond(47.7, 44, 3.2, 1.5)]]

def slim(c):
    """Two screens side by side, folded flat."""
    out = [shadow(32, 55, 28, 3), body(rr(4, 12, 25, 40, (6, 1.5, 1.5, 6)), c), body(rr(35, 12, 25, 40, (1.5, 6, 6, 1.5)), c),
           recess(rr(29.6, 14, 4.8, 36, 1.5), RUBBER, sym=None)]
    out += [*bezel(7.5, 15.5, 18, 25, 2, c), *bezel(38.5, 15.5, 18, 25, 2, c),
            control(plus(13, 46.5, 3.2, 2.3)), *[control(d) for d in diamond(51, 46.5, 2.8, 1.3)]]
    return out

def handheld(c):
    """Any landscape handheld."""
    return [shadow(32, 51, 28, 3), body(rr(3, 16, 58, 32, 11), c), *bezel(18, 19.5, 28, 25, 2, c),
            control(plus(10.5, 32, 4, 2.9)), control(ci(53.5, 29, 2.7), tone(c, 0.35)), control(ci(48.5, 35, 2.7), tone(c, 0.35))]

def cartridge(c):
    """A game cartridge with its label and grip."""
    shell = "M17,5 H42 L51,14 V55 Q51,59 47,59 H17 Q13,59 13,55 V9 Q13,5 17,5 Z"
    return [shadow(32, 61, 20, 2.5), body(shell, c), *bezel(18, 11, 28, 25, 2, c, tone(c, 0.65)),
            *[recess(capsule(x, 44, x, 52.5, 2.4), c) for x in (21, 26.5, 32, 37.5, 43)]]

def disc(c):
    """A game disc."""
    hole = circle_ccw(32, 32, 3.8)
    shine = Lin(10, 10, 54, 54, [(0, "#FFFFFF", 0), (0.35, "#FFFFFF", 0), (0.45, "#8FF3FF", 0.38), (0.55, "#FFE08A", 0.32),
                                 (0.62, "#FF9AD0", 0.28), (0.72, "#FFFFFF", 0), (1, "#FFFFFF", 0)])
    return [shadow(32, 60.5, 22, 2.4), body(ci(32, 32, 26) + hole, c, gloss=0.2), paint(L(ci(32, 32, 26) + hole, fill=shine)),
            recess(ci(32, 32, 10.5) + hole, c), control(ci(32, 32, 7.8) + hole, tone(c, 0.3))]

# name -> (drawing, accent, what it stands for)
MARKS = {
    "console": (console, AMBER, "A game controller, for any game"),
    "home": (home, AMBER, "A house, for Home"),
    "library": (library, TEAL, "A shelf of game cases"),
    "android_apps": (android_apps, DROID, "A grid of apps"),
    "android_games": (android_games, DROID, "A phone held in a controller"),
    "folder": (folder, AMBER, "A folder"),
    "cloud": (cloud, CLOUD, "A cloud with a download arrow, for RomM"),
    "moonlight": (moonlight, MOON, "A crescent moon and a star"),
    "pc": (pc, BLUE, "A monitor showing a controller"),
    "droiddeck": (droiddeck, DECK, "A wide handheld with two sticks"),
    "emulators": (emulators, EMU, "A cartridge going into a handheld"),
    "nintendo_3ds": (nintendo_3ds, ROSE, "Nintendo 3DS-style clamshell"),
    "nintendo_ds": (nintendo_ds, ORANGE, "Nintendo DS-style clamshell"),
    "game_boy": (game_boy, LIME, "Game Boy-style portrait handheld"),
    "game_boy_color": (game_boy_color, GRAPE, "Game Boy Color-style handheld"),
    "game_boy_advance": (game_boy_advance, INDIGO, "Game Boy Advance-style landscape handheld"),
    "nes": (nes, RED, "NES-style controller"),
    "snes": (snes, LAVENDER, "Super NES-style controller"),
    "nintendo_64": (nintendo_64, GREEN, "Nintendo 64-style three-handled controller"),
    "gamecube": (gamecube, CUBE, "GameCube-style controller"),
    "wii": (wii, SKY, "Wii-style remote"),
    "nintendo_switch": (nintendo_switch, JOY_R, "Switch-style tablet between two controllers"),
    "playstation": (playstation, SILVER, "PlayStation-style controller"),
    "playstation_2": (playstation_2, PSBLUE, "PlayStation 2-style dual-stick controller"),
    "psp": (psp, PSP, "PSP-style widescreen handheld"),
    "genesis": (genesis, CRIMSON, "Genesis-style three-button controller"),
    "master_system": (master_system, SMS, "Master System-style controller"),
    "game_gear": (game_gear, TEAL, "Game Gear-style landscape handheld"),
    "saturn": (saturn, GOLD, "A ringed planet for Saturn"),
    "dreamcast": (dreamcast, SUNSET, "Dreamcast-style controller with a memory-card screen"),
    "clamshell": (clamshell, ROSE, "A dual-screen clamshell"),
    "slim": (slim, ORANGE, "Two screens side by side"),
    "handheld": (handheld, HANDHELD, "A landscape handheld"),
    "cartridge": (cartridge, CART, "A game cartridge"),
    "disc": (disc, DISC, "A game disc"),
}

# ---------------- collection glyphs ----------------
# Flat shapes only: in the picker they are symbols, and on the grid they are pressed into the folder.
def star():
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 24 if i % 2 == 0 else 10.8
        pts.append((32 + r * math.cos(a), 34.5 + r * math.sin(a)))
    return [outline(poly(pts), round=4)]

def heart():
    return [outline("M32,54 C24,47 9,38 9,25 C9,16.8 15,11 22.2,11 C26.6,11 30,13.4 32,17 "
                    "C34,13.4 37.4,11 41.8,11 C49,11 55,16.8 55,25 C55,38 40,47 32,54 Z")]

def trophy():
    return [outline("M18,9 H46 V24 Q46,40 32,40 Q18,40 18,24 Z"), outline(rr(28.5, 38, 7, 9, 1)), outline(rr(19, 47, 26, 8, 3)),
            line("M19,15 H13.5 Q10,15 10,19 Q10,28.5 20.5,30.5 M45,15 H50.5 Q54,15 54,19 Q54,28.5 43.5,30.5", 4)]

def crown():
    return [outline("M9,45 L12,21 L23.5,33.5 L32,14 L40.5,33.5 L52,21 L55,45 Z", round=2.4), outline(rr(9, 48.5, 46, 7, 2.5)),
            outline(ci(12, 17.5, 3.6), sym=1), outline(ci(32, 10.5, 3.6), sym=1), outline(ci(52, 17.5, 3.6), sym=1)]

def gem():
    return [outline("M19.5,10 H44.5 L56.5,24 H7.5 Z", round=1.6), outline("M7.5,28 H56.5 L32,56 Z", round=1.6),
            outline("M26,12 H30 L28,22.5 H23.8 Z"), outline("M34,12 H38 L40.2,22.5 H36 Z")]

def sword():
    t = lambda d: xf(d, s=0.95, deg=45)
    return [outline(t("M32,3 L37,9 V38 H27 V9 Z")), outline(t(capsule(32, 13, 32, 33, 2.2))),
            outline(t(rr(18, 38, 28, 6, 3))), outline(t(rr(29, 44, 6, 11, 2))), outline(t(ci(32, 58.6, 3.4)))]

def shield():
    return [outline("M32,6 L53,12.6 V30 C53,43.4 44,51.6 32,57.6 C20,51.6 11,43.4 11,30 V12.6 Z"),
            outline("M19.5,32 L32,23.5 L44.5,32 V38 L32,29.5 L19.5,38 Z")]

def skull():
    return [outline("M32,7 C46,7 54,16 54,28.5 C54,35.5 51,39.5 47,41.5 V48 Q47,51 44,51 H20 Q17,51 17,48 V41.5 "
                    "C13,39.5 10,35.5 10,28.5 C10,16 18,7 32,7 Z"),
            outline(ci(23, 29, 6)), outline(ci(41, 29, 6)), outline("M32,35 L35.4,41.5 H28.6 Z"),
            outline(rr(26.2, 45, 2.6, 6.5, 0)), outline(rr(35.2, 45, 2.6, 6.5, 0))]

def flame():
    return [outline("M32,5 C35,15 49,22 49,38 A17,17 0 0 1 15,38 C15,29 20,24 23,17 C27,23 27.5,26 30,28.5 "
                    "C33,22 31.5,13 32,5 Z"),
            outline("M32,32 C34,38 40,40 40,46 A8,8 0 0 1 24,46 C24,41 29,38 32,32 Z")]

def rocket():
    t = lambda d: xf(d, s=0.9, deg=45)
    return [outline(t("M32,3 C40,9 43,19 43,31 V44 H21 V31 C21,19 24,9 32,3 Z")), outline(t(ci(32, 22.5, 5))),
            outline(t("M21,33 L12,43 V52 L21,47 Z")), outline(t("M43,33 L52,43 V52 L43,47 Z")),
            outline(t("M25.5,47.5 H38.5 L35,55 L32,61 L29,55 Z"))]

def racing_flag():
    flag = "M18,10 C26,6.5 32,13.5 40,10 C46,7.5 50,8.5 54,10 V34 C48,32.5 46,32 40,34.5 C32,38 26,30.5 18,34 Z"
    out = [outline(capsule(13, 8, 13, 58, 4)), outline(flag)]
    for row, y in enumerate((13.5, 19.5, 25.5)):
        for col in range(6):
            if (row + col) % 2 == 0:
                out.append(outline(rr(18 + col * 6, y, 6, 6, 0)))
    return out

def puzzle():
    d = ("M10,18 H24 A6,6 0 1 1 36,18 H48 V30 A6,6 0 1 1 48,42 V54 H36 A6,6 0 1 0 24,54 H10 V42 "
         "A6,6 0 1 0 10,30 Z")
    return [outline(xf(d, dx=0, dy=1), round=2)]

def ghost():
    return [outline("M13,31 Q13,9 32,9 Q51,9 51,31 V53.5 Q51,56 48.6,54.6 L44.5,51.5 L38.3,55.2 L32,51.5 L25.7,55.2 "
                    "L19.5,51.5 L15.4,54.6 Q13,56 13,53.5 Z"),
            outline(rr(22.5, 24, 6, 9, 3)), outline(rr(35.5, 24, 6, 9, 3))]

def mushroom():
    return [outline("M7,33 Q7,9 32,9 Q57,9 57,33 Q57,37 53,37 H11 Q7,37 7,33 Z"),
            outline(ci(32, 20, 6)), outline(ci(15.5, 28, 3.8)), outline(ci(48.5, 28, 3.8)),
            outline("M21,40 H43 V49 Q43,56 36,56 H28 Q21,56 21,49 Z"), outline(rr(27, 43, 3, 6, 1.5)), outline(rr(34, 43, 3, 6, 1.5))]

def music():
    return [outline(ellipse(19.5, 48, 7.2, 5.4, -20)), outline(ellipse(45.5, 42, 7.2, 5.4, -20)),
            outline(rr(23.6, 16, 3.8, 32, 0), sym=1), outline(rr(49.6, 10, 3.8, 32, 0), sym=1),
            outline("M23.6,14 L53.4,8 V15.5 L23.6,21.5 Z", sym=2)]

def two_players():
    return [outline(ci(18, 23.5, 7)), outline("M5,53 V48.5 Q5,36 18,36 Q31,36 31,48.5 V53 Z"),
            outline(ci(45.5, 20.5, 8.5)), outline("M33,55 V49 Q33,34 45.5,34 Q58,34 58,49 V55 Z")]

def hourglass():
    glass = "M17,13.5 H47 Q47,25 36,32 Q47,39 47,50.5 H17 Q17,39 28,32 Q17,25 17,13.5 Z"
    inside = "M21.4,16.5 H42.6 Q42,25 33.2,32 Q42,39 42.6,47.5 H21.4 Q22,39 30.8,32 Q22,25 21.4,16.5 Z"
    return [outline(rr(13, 5.5, 38, 6, 3)), outline(rr(13, 52.5, 38, 6, 3)), outline(glass), outline(inside),
            outline("M24,47.5 H40 Q38.5,41 32,39 Q25.5,41 24,47.5 Z"), outline("M26.5,20 H37.5 Q36,24.5 32,27 Q28,24.5 26.5,20 Z")]

def play_badge():
    return [outline(ci(32, 32, 24)), outline("M26.5,20.5 L45,32 L26.5,43.5 Z", round=0)]

def check_badge():
    pts = [(32 + (24 + 2.2 * math.cos(12 * t)) * math.cos(t), 32 + (24 + 2.2 * math.cos(12 * t)) * math.sin(t))
           for t in (2 * math.pi * i / 96 for i in range(96))]
    return [outline(poly(pts)), outline("M20.5,32.5 L24.8,28.2 L29.5,32.9 L39.6,22.8 L43.9,27.1 L29.5,41.5 Z")]

def clock():
    return [outline(ci(32, 32, 25)), outline(ci(32, 32, 20)),
            outline(capsule(32, 32, 32, 18.5, 4.4), sym=1), outline(capsule(32, 32, 41.5, 37.5, 4.4), sym=2)]

def leaf():
    blade = "M13,51 C11,29 23,11 53,9 C55,37 39,53 13,51 Z"
    return [outline(blade), outline(capsule(19, 45, 45.5, 16, 2.6)), outline(capsule(8, 57, 16.5, 48, 3.4), sym=1)]

def paw():
    pad = ("M32,34 C40,34 48,42 48,49 C48,55 43,57 38,56 C35,55.5 34,54 32,54 C30,54 29,55.5 26,56 "
           "C21,57 16,55 16,49 C16,42 24,34 32,34 Z")
    return [outline(pad), outline(ellipse(13.5, 30, 5, 6.6, -20)), outline(ellipse(24.5, 17.5, 5.4, 7, -8)),
            outline(ellipse(39.5, 17.5, 5.4, 7, 8)), outline(ellipse(50.5, 30, 5, 6.6, 20))]

GLYPHS = {
    "star": (star, "A star, for Favorites"),
    "heart": (heart, "A heart"),
    "trophy": (trophy, "A trophy"),
    "crown": (crown, "A crown"),
    "gem": (gem, "A cut gem"),
    "sword": (sword, "A sword"),
    "shield": (shield, "A shield"),
    "skull": (skull, "A skull, for hard games"),
    "flame": (flame, "A flame"),
    "rocket": (rocket, "A rocket"),
    "racing_flag": (racing_flag, "A checkered flag"),
    "puzzle": (puzzle, "A puzzle piece"),
    "ghost": (ghost, "A ghost"),
    "mushroom": (mushroom, "A spotted mushroom"),
    "music": (music, "Two beamed notes"),
    "two_players": (two_players, "Two players"),
    "hourglass": (hourglass, "An hourglass, for a backlog"),
    "play_badge": (play_badge, "A play button, for games in progress"),
    "check_badge": (check_badge, "A rosette with a check, for finished games"),
    "clock": (clock, "A clock, for Recently played"),
    "leaf": (leaf, "A leaf, for cozy games"),
    "paw": (paw, "A paw print"),
}

# ---------------- the collection folder ----------------
# A collection is drawn from three parts, so one set of files serves every colour:
# - mark_collection_body: the folder in greys. The host multiplies it by the tint (Modulate),
#   so white becomes the tint itself and grey a darker shade of it.
# - the glyph's symbol, pressed into the front: the host draws it in a dark shade of the tint,
#   over a faint light lip just below it, the way a pressed shape catches the light on its lower edge.
# - mark_collection_face: what never takes the tint, drawn last: the sheet inside, gloss, and rims.
FACE_GLYPH = (32.0, 40.5, 33.3)     # centre x, centre y, and size of the glyph's 64-unit box, before OBJECT_SCALE

def face_glyph():
    """Where the host draws the glyph, in the finished 64-unit art (Marks.kt mirrors these)."""
    x, y, size = FACE_GLYPH
    return (32 + (x - 32) * OBJECT_SCALE, 32 + (y - 32) * OBJECT_SCALE, size * OBJECT_SCALE)

def collection_body():
    return object_layers([shadow(32, 59, 27, 3.4), paint(
        L(FOLDER_BACK, fill=Lin(0, 10, 0, 48, [(0, "#A8A8A8", 1), (1, "#555555", 1)])),
        L(FOLDER_FRONT, fill=Lin(0, 23, 0, 56, [(0, "#F4F4F4", 1), (0.5, "#DADADA", 1), (1, "#8A8A8A", 1)])))])

def collection_face():
    above_front = rr(0, 0, 64, 23, 0)
    back_light = [L(FOLDER_BACK, fill=Lin(0, 10, 0, 29, [(0, "#FFFFFF", 0.12), (1, "#FFFFFF", 0)])),
                  L(FOLDER_BACK, stroke=Lin(0, 10, 0, 33, [(0, "#FFFFFF", 0.65), (1, "#FFFFFF", 0)]), sw=0.9)]
    return object_layers([paint(G(back_light, clip=above_front), L(PAPER, fill=INK, alpha=0.85),
                                L(FOLDER_FRONT, fill=Lin(0, 23, 0, 39.5, [(0, "#FFFFFF", 0.16), (1, "#FFFFFF", 0)])),
                                L(FOLDER_FRONT, stroke=Lin(0, 23, 0, 43, [(0, "#FFFFFF", 0.65), (1, "#FFFFFF", 0)]), sw=0.9))])
