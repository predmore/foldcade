from art import *
from palette import *

def q_poly(pts, r):
    """Closed polygon with rounded corners (quadratic)."""
    n = len(pts); d = []
    for i in range(n):
        p0, p1, p2 = pts[i-1], pts[i], pts[(i+1) % n]
        def toward(a, b, dist):
            dx, dy = b[0]-a[0], b[1]-a[1]; L_ = math.hypot(dx, dy); t = min(dist / L_, 0.5)
            return (a[0]+dx*t, a[1]+dy*t)
        a = toward(p1, p0, r); b = toward(p1, p2, r)
        d.append(("M" if i == 0 else "L") + f"{f(a[0])},{f(a[1])}")
        d.append(f"Q{f(p1[0])},{f(p1[1])} {f(b[0])},{f(b[1])}")
    return "".join(d) + "Z"

def concept_a(mono=False):
    lid = [(33.5, 22), (74.5, 22), (79.5, 54.5), (28.5, 54.5)]
    scr = [(38, 26.8), (70, 26.8), (73.6, 49.8), (34.4, 49.8)]
    base = (27, 58, 54, 26, 7); bscr = (35, 62, 38, 17.5, 3.5)
    if mono:
        return [L(q_poly(lid, 5.5) + q_poly(scr, 2.5), fill="#FFFFFF", evenodd=True),
                L(rrect(31, 54.6, 46, 3.2, 1.6), fill="#FFFFFF"),
                L(rrect(*base) + rrect(*bscr), fill="#FFFFFF", evenodd=True),
                L(rrect(39.5, 70, 7, 6, 1.8), fill="#FFFFFF"), L(rrect(50.5, 70, 7, 6, 1.8), fill="#FFFFFF"),
                L(rrect(61.5, 70, 7, 6, 1.8), fill="#FFFFFF")]
    out = [
        halo(54, 40, 48, TEAL, 0.8, sx=1.0, sy=0.78, core=0.1),
        halo(54, 72, 40, AMBER, 0.6, sx=1.15, sy=0.62, core=0.1),
        L(q_poly(lid, 5.5), fill=Lin(0, 22, 0, 55, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
          stroke=Lin(0, 22, 0, 55, [(0, MINT, 0.95), (1, TEAL, 0.35)]), sw=1.4),
        L(q_poly(scr, 2.6), fill=Lin(38, 27, 70, 50, [(0, "#8AF7DF", 1), (0.5, TEAL, 1), (1, "#178F7B", 1)])),
        L("M35.5,44 C44,33 52,46 60,37 S69,30 72.5,33", stroke=Lin(35, 0, 73, 0, [(0, INK, 0.0), (0.45, "#FFFFFF", 0.9), (1, INK, 0.0)]), sw=1.5),
        L("M35.8,47.5 C45,40 54,49 63,43 S70,40 73,41", stroke=Lin(35, 0, 73, 0, [(0, ROSE, 0.0), (0.5, ROSE, 0.8), (1, ROSE, 0.0)]), sw=1.2),
        L(q_poly([(39, 27.8), (53, 27.8), (44, 48.8), (36, 48.8)], 2), fill=Lin(39, 28, 49, 49, [(0, "#FFFFFF", 0.30), (1, "#FFFFFF", 0.0)])),
        L(rrect(31, 54.4, 46, 4.4, 2.2), fill=Lin(0, 54, 0, 59, [(0, "#4A505E", 1), (1, "#15171C", 1)])),
        L(rrect(*base), fill=Lin(0, 58, 0, 84, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
          stroke=Lin(0, 58, 0, 84, [(0, "#FFE2B0", 0.9), (1, AMBER, 0.3)]), sw=1.4),
        L(rrect(*bscr), fill=Lin(35, 62, 73, 80, [(0, "#20140A", 1), (1, "#0B0704", 1)]),
          stroke=Lin(0, 62, 0, 80, [(0, AMBER, 0.9), (1, ORANGE, 0.5)]), sw=1.0),
    ]
    for x, c in ((39.5, ROSE), (50.5, AMBER), (61.5, VIOLET)):
        out.append(halo(x + 3.5, 73, 6.5, c, 0.55))
        out.append(L(rrect(x, 70, 7, 6, 1.8), fill=Lin(0, 70, 0, 76, [(0, "#FFFFFF", 0.95), (0.35, c, 1), (1, c, 0.8)])))
    out.append(L(rrect(36, 62.8, 36, 4.5, 2.2), fill=Lin(0, 63, 0, 68, [(0, "#FFFFFF", 0.22), (1, "#FFFFFF", 0)])))
    return out

def concept_b(mono=False):
    return _scaled(_concept_b(mono), 0.8)

def _concept_b(mono=False):
    if mono:
        return [L(rrect(26, 24, 56, 34, 9) + rrect(31.5, 29.5, 45, 23, 5), fill="#FFFFFF", evenodd=True),
                L(rrect(33, 61, 42, 24, 8) + rrect(38, 66, 32, 14, 4), fill="#FFFFFF", evenodd=True)]
    return [
        halo(54, 41, 46, TEAL, 0.40, sy=0.75),
        halo(54, 73, 36, ROSE, 0.34, sy=0.7),
        L("M18,66 C34,48 48,86 66,62 S88,44 92,50", stroke=Lin(18, 0, 92, 0, [(0, VIOLET, 0), (0.3, VIOLET, 0.6), (0.7, AMBER, 0.6), (1, AMBER, 0)]), sw=2.2),
        L(rrect(26, 24, 56, 34, 9), fill=Lin(0, 24, 0, 58, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
          stroke=Lin(0, 24, 0, 58, [(0, MINT, 0.95), (1, TEAL, 0.3)]), sw=1.5),
        L(rrect(31.5, 29.5, 45, 23, 5), fill=Lin(31, 29, 76, 52, [(0, "#8AF7DF", 1), (0.55, TEAL, 1), (1, "#168C78", 1)])),
        L(rrect(32.5, 30.3, 43, 8, 4), fill=Lin(0, 30, 0, 39, [(0, "#FFFFFF", 0.38), (1, "#FFFFFF", 0)])),
        L(rrect(33, 61, 42, 24, 8), fill=Lin(0, 61, 0, 85, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
          stroke=Lin(0, 61, 0, 85, [(0, "#FFC2CE", 0.95), (1, ROSE, 0.3)]), sw=1.5),
        L(rrect(38, 66, 32, 14, 4), fill=Lin(38, 66, 70, 80, [(0, "#FF9DB0", 1), (0.6, ROSE, 1), (1, "#C8325A", 1)])),
        L(rrect(39, 66.8, 30, 5, 2.4), fill=Lin(0, 66, 0, 72, [(0, "#FFFFFF", 0.35), (1, "#FFFFFF", 0)])),
    ]

def concept_c(mono=False):
    # side view of an open clamshell; light spills out of the hinge
    lid = "M30,74 L60,24"; base = "M30,76 L80,76"
    if mono:
        return [L(lid, stroke="#FFFFFF", sw=9), L(base, stroke="#FFFFFF", sw=9),
                L("M38,70 L57,37 Q60,33 62,38 L72,68 Q73,71 70,71 Z", fill="#FFFFFF", alpha=1)]
    return [
        halo(52, 58, 42, TEAL, 0.45, core=0.1),
        halo(56, 66, 28, AMBER, 0.45),
        L("M37,71 L58,35 Q61,31 63,36 L76,70 Q77,72 74,72 L39,72 Q36,72 37,71 Z",
          fill=Lin(60, 34, 56, 72, [(0, "#FFFFFF", 0.0), (0.25, MINT, 0.55), (1, AMBER, 0.75)])),
        L(lid, stroke=Lin(30, 74, 60, 24, [(0, CAP_BOT, 1), (1, CAP_TOP, 1)]), sw=9),
        L("M33.5,71 L61,25", stroke=Lin(30, 74, 60, 24, [(0, TEAL, 0.5), (1, MINT, 1)]), sw=1.4),
        L(base, stroke=Lin(30, 0, 80, 0, [(0, CAP_BOT, 1), (1, CAP_TOP, 1)]), sw=9),
        L("M33,72.4 L80,72.4", stroke=Lin(30, 0, 80, 0, [(0, AMBER, 0.6), (1, "#FFE2B0", 1)]), sw=1.4),
        L(circle(30.5, 75, 3.2), fill="#2C3039", stroke=MINT, sw=0.8, salpha=0.6),
    ]

CONCEPTS = {"A-open-clamshell": concept_a, "B-stacked-screens": concept_b, "C-light-spill": concept_c}

def _scaled(layers, k):
    for l in layers:
        l.scale = (k, k, 54, 54) if l.scale is None else (l.scale[0]*k, l.scale[1]*k, 54, 54) if False else l.scale
    # wrap: simplest is to scale every layer about the centre
    out = []
    for l in layers:
        if l.scale is None:
            l.scale = (k, k, 54, 54)
        out.append(l)
    return out
