"""Refined, illustrative platform marks (48dp). Generic device silhouettes, no console logos."""
from art import *
from palette import *

def dev_body(shape, col, y0, y1):
    return L(shape, fill=Lin(0, y0, 0, y1, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
             stroke=Lin(0, y0, 0, y1, [(0, "#FFFFFF", 0.85), (0.35, col, 1), (1, col, 0.45)]), sw=1.6)

def screen(x, y, w, h, r, col, hi, lo):
    return [L(rrect(x, y, w, h, r), fill=Lin(x, y, x + w, y + h, [(0, hi, 1), (0.55, col, 1), (1, lo, 1)])),
            L(rrect(x + 0.8, y + 0.7, w - 1.6, h * 0.36, max(r - 0.6, 0.5)), fill=Lin(0, y, 0, y + h * 0.4, [(0, "#FFFFFF", 0.4), (1, "#FFFFFF", 0)]))]

def clamshell():
    c, hi, lo = ROSE, "#FFB3C1", "#B02A4E"
    return [halo(24, 24, 24, c, 0.35, core=0.1),
            dev_body(rrect(10, 5, 28, 18, 4), c, 5, 23), *screen(13.5, 8, 21, 12, 2, c, hi, lo),
            L(rrect(11, 22.6, 26, 3, 1.5), fill="#3B404C"),
            dev_body(rrect(9, 25, 30, 18, 4), c, 25, 43), *screen(16.5, 28, 15, 11, 2, c, hi, lo),
            L(circle(12.6, 33.5, 1.6), fill=INK, alpha=0.85), L(circle(35.4, 31.8, 1.1), fill=c), L(circle(35.4, 36, 1.1), fill=INK, alpha=0.8)]

def slim():
    c, hi, lo = ORANGE, "#FFC3A6", "#B8461E"
    return [halo(24, 24, 24, c, 0.35, core=0.1),
            dev_body(rrect(4, 11, 19.4, 26, 4), c, 11, 37), dev_body(rrect(24.6, 11, 19.4, 26, 4), c, 11, 37),
            *screen(7, 14.5, 13.4, 19, 2, c, hi, lo), *screen(27.6, 14.5, 13.4, 19, 2, c, hi, lo),
            L(rrect(22.4, 13, 3.2, 22, 1.6), fill=Lin(0, 13, 0, 35, [(0, "#4A505E", 1), (1, "#15171C", 1)]))]

def handheld():
    c, hi, lo = AMBER, "#FFE2B0", "#C27416"
    plus = "M10.4,22.2 H12.4 V20.2 H14.4 V22.2 H16.4 V24.2 H14.4 V26.2 H12.4 V24.2 H10.4 Z"
    return [halo(24, 24, 24, c, 0.35, core=0.1),
            dev_body(rrect(3, 13, 42, 22, 9), c, 13, 35), *screen(17, 16.5, 14, 15, 2, c, hi, lo),
            L(plus, fill=INK, alpha=0.9),
            L(circle(36.2, 21.8, 1.9), fill=Lin(0, 20, 0, 24, [(0, hi, 1), (1, c, 1)])),
            L(circle(39.4, 25.8, 1.9), fill=INK, alpha=0.85)]

def cartridge():
    c, hi, lo = TEAL, "#B5FFEC", ACC_LO
    body = "M12,6 H31 L38,13 V40 Q38,42 36,42 H12 Q10,42 10,40 V8 Q10,6 12,6 Z"
    ls = [halo(24, 24, 24, c, 0.35, core=0.1), dev_body(body, c, 6, 42),
          *screen(14, 11, 20, 15, 2, c, hi, lo),
          L("M16.5,16 H27 M16.5,19.5 H23", stroke="#04120E", sw=1.6, salpha=0.55)]
    for i in range(6):
        x = 14 + i * 3.6
        ls.append(L(rrect(x, 33, 2.2, 6, 0.8), fill=Lin(0, 33, 0, 39, [(0, hi, 1), (1, c, 0.7)])))
    return ls

def disc():
    c = VIOLET
    return [halo(24, 24, 24, c, 0.38, core=0.1),
            L(circle(24, 24, 18) + circle_ccw(24, 24, 3.2),
              fill=Lin(8, 8, 40, 40, [(0, "#E6D9FF", 1), (0.3, VIOLET, 1), (0.5, "#4CC9FF", 1), (0.7, VIOLET, 1), (1, "#5E3DB3", 1)]),
              stroke="#FFFFFF", sw=1.2, salpha=0.7),
            L(circle(24, 24, 12.5), stroke="#FFFFFF", sw=0.6, salpha=0.25),
            L(circle(24, 24, 7.6) + circle_ccw(24, 24, 3.2), fill="#0C0D11", stroke=INK, sw=0.8, salpha=0.6),
            L("M12.8,17.5 A13,13 0 0 1 19,11.2", stroke="#FFFFFF", sw=2.4, salpha=0.75),
            L("M35,30.5 A13,13 0 0 1 30.5,35", stroke="#FFFFFF", sw=1.6, salpha=0.45)]

def cloud():
    c, hi = BLUE, "#C6EEFF"
    body = ("M14,36 H34 C39.5,36 43,32.3 43,27.8 C43,23.6 39.8,20.3 35.8,19.9 C34.8,14.4 30.2,10.6 24.6,10.6 "
            "C20,10.6 16.1,13.3 14.4,17.2 C9,17.6 5,21.6 5,26.8 C5,31.8 9,36 14,36 Z")
    return [halo(24, 24, 24, c, 0.38, core=0.1),
            L(body, fill=Lin(0, 10, 0, 36, [(0, "#1E3846", 1), (1, CAP_BOT, 1)]),
              stroke=Lin(0, 10, 0, 36, [(0, "#FFFFFF", 0.9), (0.4, c, 1), (1, c, 0.5)]), sw=1.6),
            L("M15.4,18.6 C17.4,15 21,13.2 24.6,13.2", stroke="#FFFFFF", sw=1.4, salpha=0.5),
            halo(24.5, 25, 9, c, 0.6),
            L(poly([(21, 19.6), (30.4, 25), (21, 30.4)]), fill=Lin(0, 19.6, 0, 30.4, [(0, hi, 1), (1, c, 1)]), stroke=hi, sw=1.6),
            L("M18,41 H30", stroke=Lin(18, 0, 30, 0, [(0, c, 0), (0.5, c, 0.9), (1, c, 0)]), sw=1.4)]

ACC_LO = "#159A82"
def marks_assets():
    return [Asset(f"mark_{n}_lit", 48, 48, fn(), note=f"Illustrated platform mark: {n}")
            for n, fn in (("clamshell", clamshell), ("slim", slim), ("handheld", handheld),
                          ("cartridge", cartridge), ("disc", disc), ("cloud", cloud))]
