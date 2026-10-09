"""Controller glyphs, status-island icons, refined platform marks."""
from art import *
from palette import *
from fontpaths import text_path

ACC = TEAL          # focus accent for pressed/focused controller glyphs
ACC_HI = "#9BFBE4"; ACC_LO = "#159A82"; ON_ACC = "#04120E"

def cap_layers(shape, filled, accent=ACC, hi=ACC_HI, lo=ACC_LO, y0=0, y1=24, halo_spec=None):
    """A glossy key cap: (soft halo) + body gradient + rim + top sheen."""
    out = []
    if filled and halo_spec:
        cx, cy, r, sx, sy = halo_spec
        out.append(halo(cx, cy, r, accent, 0.55, sx=sx, sy=sy, core=0.15))
    if filled:
        out.append(L(shape, fill=Lin(0, y0, 0, y1, [(0, hi, 1), (0.45, accent, 1), (1, lo, 1)]),
                     stroke=Lin(0, y0, 0, y1, [(0, "#FFFFFF", 0.9), (1, accent, 0.6)]), sw=1.0))
    else:
        out.append(L(shape, fill=Lin(0, y0, 0, y1, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
                     stroke=Lin(0, y0, 0, y1, [(0, "#E9E4DA", 0.95), (1, "#7C786F", 0.55)]), sw=1.2))
    return out

def sheen(shape_top, y0, y1, a=0.28):
    return L(shape_top, fill=Lin(0, y0, 0, y1, [(0, "#FFFFFF", a), (1, "#FFFFFF", 0)]))

def label(text, cx, cy, h, filled, w=900):
    return L(text_path(text, cx, cy, h, wght=w), fill=(ON_ACC if filled else INK))

# ---------------- controller ----------------
def face(letter, filled):
    sh = circle(12, 12, 8.8)
    ls = cap_layers(sh, filled, y0=3.2, y1=20.8, halo_spec=(12, 12, 12, 1, 1))
    ls.append(sheen(rrect(6.2, 4.6, 11.6, 6.2, 3.1), 4.4, 11))
    ls.append(label(letter, 12, 12.2, 7.2, filled))
    return ls

def shoulder(text, side, trigger, filled):
    if trigger:   # taller cap, curved top
        x, y, w, h = 2.5, 4.5, 19, 15
        r = (7.5, 3.5, 3, 3) if side == "L" else (3.5, 7.5, 3, 3)
    else:         # bumper: wide, outer top corner rounder
        x, y, w, h = 1.5, 6, 21, 12
        r = (6, 3, 3, 3) if side == "L" else (3, 6, 3, 3)
    sh = rrect(x, y, w, h, r)
    ls = cap_layers(sh, filled, y0=y, y1=y+h, halo_spec=(12, 12, 12, 1, 0.85))
    ls.append(sheen(rrect(x+1.6, y+1.2, w-3.2, h*0.38, (r[0]*0.7, r[1]*0.7, 1, 1)), y+1, y+h*0.5))
    ls.append(label(text, 12, y + h/2 + (0.6 if trigger else 0.2), 6.4 if not trigger else 6.6, filled))
    if trigger:  # little grip ridges above the label
        ls.append(L(f"M8.5,{f(y+3.2)} H15.5", stroke=(ON_ACC if filled else INK), sw=0.7, salpha=0.35))
    return ls

DPAD_ARMS = {"up": (8.6, 2, 6.8, 9.2), "down": (8.6, 12.8, 6.8, 9.2),
             "left": (2, 8.6, 9.2, 6.8), "right": (12.8, 8.6, 9.2, 6.8)}
DPAD_TRI = {"up": [(12, 4.4), (10.2, 6.9), (13.8, 6.9)], "down": [(12, 19.6), (10.2, 17.1), (13.8, 17.1)],
            "left": [(4.4, 12), (6.9, 10.2), (6.9, 13.8)], "right": [(19.6, 12), (17.1, 10.2), (17.1, 13.8)]}
HALO_AT = {"up": (12, 6.5), "down": (12, 17.5), "left": (6.5, 12), "right": (17.5, 12)}

def dpad_path():
    a, b, c, d, r = 2, 8.6, 15.4, 22, 1.8
    return ("M%s,%s" % (f(b + r), f(a)) +
            f"H{f(c-r)}Q{f(c)},{f(a)} {f(c)},{f(a+r)}V{f(b)}H{f(d-r)}Q{f(d)},{f(b)} {f(d)},{f(b+r)}"
            f"V{f(c-r)}Q{f(d)},{f(c)} {f(d-r)},{f(c)}H{f(c)}V{f(d-r)}Q{f(c)},{f(d)} {f(c-r)},{f(d)}"
            f"H{f(b+r)}Q{f(b)},{f(d)} {f(b)},{f(d-r)}V{f(c)}H{f(a+r)}Q{f(a)},{f(c)} {f(a)},{f(c-r)}"
            f"V{f(b+r)}Q{f(a)},{f(b)} {f(a+r)},{f(b)}H{f(b)}V{f(a+r)}Q{f(b)},{f(a)} {f(b+r)},{f(a)}Z")

def dpad(active=None, filled=False):
    ls = []
    if active:
        hx, hy = HALO_AT[active]
        ls.append(halo(hx, hy, 9, ACC, 0.6, core=0.15))
    ls += cap_layers(dpad_path(), filled and active is None, y0=2, y1=22,
                     halo_spec=(12, 12, 12, 1, 1))
    if active:
        x, y, w, h = DPAD_ARMS[active]
        r = {"up": (1.8, 1.8, 0, 0), "down": (0, 0, 1.8, 1.8), "left": (1.8, 0, 0, 1.8), "right": (0, 1.8, 1.8, 0)}[active]
        ls.append(L(rrect(x, y, w, h, r), fill=Lin(0, y, 0, y + h, [(0, ACC_HI, 1), (0.5, ACC, 1), (1, ACC_LO, 1)])))
    ls.append(sheen(rrect(9.4, 2.8, 5.2, 4, (1.4, 1.4, 0, 0)), 2.6, 7, 0.25))
    centre_fill = ON_ACC if (filled and active is None) else "#000000"
    ls.append(L(circle(12, 12, 1.9), fill=centre_fill, alpha=0.55))
    for k, pts in DPAD_TRI.items():
        on = (k == active) or (filled and active is None)
        ls.append(L(poly(pts), fill=(ON_ACC if on else INK), alpha=(0.9 if on else 0.55)))
    return ls

def pill_button(kind, filled):
    """Generic (non-Thor) Start / Select: glossy horizontal pill caps."""
    x, y, w, h = 3, 7, 18, 10
    sh = rrect(x, y, w, h, 5)
    ls = cap_layers(sh, filled, y0=y, y1=y+h, halo_spec=(12, 12, 12, 1, 0.7))
    ls.append(sheen(rrect(x+1.5, y+1, w-3, 3.6, 1.8), y+1, y+5))
    ink = ON_ACC if filled else INK
    if kind == "start":   # forward chevron-play
        ls.append(L(poly([(10.4, 9.4), (15, 12), (10.4, 14.6)]), fill=ink, stroke=ink, sw=0.9))
    else:                 # select: two stacked bars + bullet
        for yy in (10.4, 13.6):
            ls.append(L(circle(8.9, yy, 0.85), fill=ink))
            ls.append(L(f"M11,{f(yy)} H15.6", stroke=ink, sw=1.3))
    return ls



def back_return_path():
    """AYN Thor Back mark: one filled CCW return arrow (arc + head as a single union).
    Near-full ring; arrowhead at the top end points left (CCW), overlapping the stroke end.
    """
    cx, cy = 12.0, 12.0
    r_out, r_in = 5.50, 3.90
    r_mid = (r_out + r_in) / 2
    # Math angles (0=east, CCW, y-up). Screen: (cx + r*cos, cy - r*sin).
    # Arrive at top (pi/2): CCW tangent = left. Tail sits past a small gap on the upper-left.
    a_end = math.pi / 2
    gap = math.radians(50)
    a0 = a_end + gap                 # ~140°
    a1 = a_end + 2 * math.pi        # full CCW travel back to top

    def xy(a, r):
        return (cx + r * math.cos(a), cy - r * math.sin(a))

    n = 64
    # Outer arc a0 → a1
    pts = [xy(a0 + (a1 - a0) * i / n, r_out) for i in range(n + 1)]

    # Arrowhead fused at the end. Tangent (CCW) and outward normal at a_end in screen space:
    # d/da (cos a, -sin a) = (-sin a, -cos a); at pi/2 → (-1, 0) left.
    # outward = (cos a, -sin a); at pi/2 → (0, -1) up.
    tx, ty = -1.0, 0.0
    nx, ny = 0.0, -1.0
    tip_len = 2.55
    head_half = 2.15          # half-width of head base (wider than stroke → reads as a real tip)
    # Pull the head base slightly back along -tangent so it overlaps the arc body (no seam)
    overlap = 1.05
    mid_end = xy(a_end, r_mid)
    base_c = (mid_end[0] - tx * overlap, mid_end[1] - ty * overlap)
    tip = (mid_end[0] + tx * tip_len, mid_end[1] + ty * tip_len)
    # Wings of the head (perpendicular to tangent), centred on base_c
    wing_out = (base_c[0] + nx * head_half, base_c[1] + ny * head_half)
    wing_in = (base_c[0] - nx * head_half, base_c[1] - ny * head_half)

    # Leave the outer arc a little early so the wing covers the cut, then tip, then inner wing
    cut = a1 - math.radians(8)
    # rebuild outer up to cut
    pts = [xy(a0 + (cut - a0) * i / n, r_out) for i in range(n + 1)]
    pts.append(wing_out)
    pts.append(tip)
    pts.append(wing_in)
    # Inner arc from cut back to a0
    for i in range(n + 1):
        a = cut - (cut - a0) * i / n
        pts.append(xy(a, r_in))
    return poly(pts)


def round_system(kind, filled):
    """AYN Thor system buttons: small round glossy caps with outline marks.
    square = Select (top-left), triangle = Start (top-right, ▷ in a ring),
    home = bottom-left house, back = bottom-right CCW return arrow.
    Neutral ink; no PlayStation colours.
    """
    sh = circle(12, 12, 8.8)
    ls = cap_layers(sh, filled, y0=3.2, y1=20.8, halo_spec=(12, 12, 12, 1, 1))
    ls.append(sheen(rrect(6.2, 4.6, 11.6, 6.2, 3.1), 4.4, 11))
    ink = ON_ACC if filled else INK
    if kind == "square":
        # Small square outline centred on the cap
        ls.append(L(rrect(8.5, 8.5, 7.0, 7.0, 0.85), fill=ink, alpha=0, stroke=ink, sw=1.45))
    elif kind == "triangle":
        # Circle ring with a right-pointing play triangle (▷) outline inside
        ls.append(L(circle(12, 12, 5.35), fill=ink, alpha=0, stroke=ink, sw=1.3))
        tri = poly([(10.05, 9.15), (15.15, 12), (10.05, 14.85)])
        ls.append(L(tri, fill=ink, alpha=0, stroke=ink, sw=1.3))
    elif kind == "home":
        # House outline + small door
        ls.append(L("M7.6,12.1 L12,8.2 L16.4,12.1 M9.1,11 V15.4 Q9.1,16 9.7,16 H14.3 Q14.9,16 14.9,15.4 V11",
                    stroke=ink, sw=1.5))
        ls.append(L(rrect(11, 13.1, 2, 2.9, (1, 1, 0, 0)), fill=ink))
    else:  # back: CCW circular return — single filled path, head fused to arc end (points left)
        ls.append(L(back_return_path(), fill=ink))
    return ls

def home(filled):
    return round_system("home", filled)

# Face-button diamond seats (centre of each miniature glossy cap)
FACE_SEATS = {"top": (12, 5.15), "left": (5.15, 12), "right": (18.85, 12), "bottom": (12, 18.85)}
# Letter maps — neutral ink only (no brand colours). App picks layout from detected mapping.
FACE_LAYOUTS = {
    "n": {"top": "X", "left": "Y", "right": "A", "bottom": "B"},  # nintendo-style
    "x": {"top": "Y", "left": "X", "right": "B", "bottom": "A"},  # xbox-style
    "blank": {"top": None, "left": None, "right": None, "bottom": None},  # position-only
}

def face_diamond(layout="n", lit_seat=None, filled=False):
    """Compact face-button diamond. lit_seat is 'top'/'left'/'right'/'bottom' or None.
    filled = pressed look. Letters stay neutral (ink / on-accent); never brand-coloured.
    """
    letters = FACE_LAYOUTS[layout]
    ls = []
    r = 3.55
    if filled:
        ls.append(halo(12, 12, 12.2, ACC, 0.35 if lit_seat else 0.4, core=0.1))
    for seat, (cx, cy) in FACE_SEATS.items():
        letter = letters[seat]
        is_lit = (seat == lit_seat)
        # Teal when this seat is lit, or when the whole diamond is pressed with no single focus,
        # or when pressed+lit (other seats also go teal so the cluster reads as held).
        on = is_lit or (filled and lit_seat is None) or (filled and lit_seat is not None)
        if is_lit:
            ls.append(halo(cx, cy, 5.6, ACC, 0.55, core=0.15))
        sh = circle(cx, cy, r)
        if on:
            ls.append(L(sh, fill=Lin(0, cy - r, 0, cy + r, [(0, ACC_HI, 1), (0.45, ACC, 1), (1, ACC_LO, 1)]),
                        stroke="#FFFFFF", sw=0.7, salpha=0.8))
            if letter:
                ls.append(label(letter, cx, cy + 0.2, 3.5, True))
        else:
            ls.append(L(sh, fill=Lin(0, cy - r, 0, cy + r, [(0, CAP_TOP, 1), (1, CAP_BOT, 1)]),
                        stroke=Lin(0, cy - r, 0, cy + r, [(0, "#E9E4DA", 0.95), (1, "#7C786F", 0.55)]), sw=0.8))
            if letter:
                ls.append(label(letter, cx, cy + 0.2, 3.5, False))
    return ls

def _emit_diamond(A, name, layout, lit_seat, filled, note, aliases=()):
    layers = face_diamond(layout, lit_seat, filled)
    A.append(Asset(name, 24, 24, layers, note=note))
    for al in aliases:
        A.append(Asset(al, 24, 24, layers, note=f"Alias of {name}. {note}"))

def stick(side, pressed=False, filled=False):
    ls = []
    hot = pressed or filled
    if hot:
        ls.append(halo(12, 12, 12, ACC, 0.5, core=0.2))
    # gate well
    ls.append(L(circle(12, 12, 10.6), fill=Rad(12, 12, 10.6, [(0, "#000000", 1), (0.75, "#0E1014", 1), (1, "#1E2128", 1)]),
                stroke=(ACC if hot else "#6E6A62"), sw=0.8, salpha=(0.8 if hot else 0.6)))
    if pressed:   # press ripples
        ls.append(L(circle(12, 12, 9.2), stroke=ACC, sw=0.6, salpha=0.55))
    capr = 6.9 if pressed else 7.4
    ls.append(L(circle(12, 12, capr),
                fill=Rad(10.2, 9.6, capr * 1.4, ([(0, ACC_HI, 1), (0.55, ACC, 1), (1, ACC_LO, 1)] if hot else
                                                   [(0, "#3A3F4A", 1), (0.6, "#1C1F26", 1), (1, "#0B0C10", 1)])),
                stroke=("#FFFFFF" if hot else "#E9E4DA"), sw=0.9, salpha=(0.75 if hot else 0.7)))
    ls.append(L(circle(12, 12, capr - 1.9), stroke=(ON_ACC if hot else "#000000"), sw=0.7, salpha=0.35))
    ls.append(L(rrect(8.4, 6.1, 7.2, 2.6, 1.3), fill="#FFFFFF", alpha=0.22))
    ls.append(label(side, 12, 12.4, 5.2, hot))
    if pressed:   # inward chevrons: "press"
        ink = ON_ACC
        ls.append(L("M12,1.3 V3.4 M10.8,2.4 L12,3.6 L13.2,2.4", stroke=ACC, sw=0.8))
    return ls

def controller_assets():
    A = []
    for v, fl in (("", False), ("_filled", True)):
        for k in "ABXY":
            A.append(Asset(f"ic_btn_{k.lower()}{v}", 24, 24, face(k, fl), note=f"Face button {k}"))
        for t, side, trig in (("L1", "L", False), ("R1", "R", False), ("L2", "L", True), ("R2", "R", True)):
            A.append(Asset(f"ic_btn_{t.lower()}{v}", 24, 24, shoulder(t, side, trig, fl), note=f"Shoulder {t}"))
        A.append(Asset(f"ic_btn_start{v}", 24, 24, pill_button("start", fl), note="Start (generic / non-Thor)"))
        A.append(Asset(f"ic_btn_select{v}", 24, 24, pill_button("select", fl), note="Select (generic / non-Thor)"))
        A.append(Asset(f"ic_btn_square{v}", 24, 24, round_system("square", fl),
                       note="Thor Select: round cap, square outline (top-left)"))
        A.append(Asset(f"ic_btn_triangle{v}", 24, 24, round_system("triangle", fl),
                       note="Thor Start: round cap, ▷ outline in a ring (top-right)"))
        A.append(Asset(f"ic_btn_home{v}", 24, 24, round_system("home", fl),
                       note="Thor Home: round cap, house outline (bottom-left)"))
        A.append(Asset(f"ic_btn_back{v}", 24, 24, round_system("back", fl),
                       note="Thor Back: round cap, CCW return arrow (bottom-right)"))
        A.append(Asset(f"ic_btn_dpad{v}", 24, 24, dpad(None, fl), note="D-pad"))
        A.append(Asset(f"ic_btn_lstick{v}", 24, 24, stick("L", False, fl), note="Left stick"))
        A.append(Asset(f"ic_btn_rstick{v}", 24, 24, stick("R", False, fl), note="Right stick"))
    # ---- face diamonds: nintendo (_n), xbox (_x), blank; every variant has _filled ----
    for filled, suf in ((False, ""), (True, "_filled")):
        # Nintendo-style (X top, Y left, A right, B bottom). Old bare names alias the nintendo set.
        n_aliases = ("ic_btn_face_diamond",) if not filled else ("ic_btn_face_diamond_filled",)
        _emit_diamond(A, f"ic_btn_face_diamond_n{suf}", "n", None, filled,
                      "Nintendo-style face diamond (X top, Y left, A right, B bottom)",
                      aliases=n_aliases)
        for seat, letter in (("top", "x"), ("left", "y"), ("right", "a"), ("bottom", "b")):
            # Old ic_btn_face_diamond_{y,a,b}{,_filled} alias nintendo lit seats.
            # Old ic_btn_face_diamond_x is NOT an alias — that name is the xbox-style base.
            aliases = ()
            if letter != "x":
                aliases = (f"ic_btn_face_diamond_{letter}{suf}",)
            _emit_diamond(A, f"ic_btn_face_diamond_n_{letter}{suf}", "n", seat, filled,
                          f"Nintendo-style diamond, {seat} ({letter.upper()}) lit",
                          aliases=aliases)
        # Xbox-style (Y top, X left, B right, A bottom)
        _emit_diamond(A, f"ic_btn_face_diamond_x{suf}", "x", None, filled,
                      "Xbox-style face diamond (Y top, X left, B right, A bottom)")
        for seat, letter in (("top", "y"), ("left", "x"), ("right", "b"), ("bottom", "a")):
            _emit_diamond(A, f"ic_btn_face_diamond_x_{letter}{suf}", "x", seat, filled,
                          f"Xbox-style diamond, {seat} ({letter.upper()}) lit")
        # Position-only (unlabeled) when mapping is unknown
        _emit_diamond(A, f"ic_btn_face_diamond_blank{suf}", "blank", None, filled,
                      "Unlabeled face diamond (mapping unknown)")
        for seat in ("top", "left", "right", "bottom"):
            _emit_diamond(A, f"ic_btn_face_diamond_blank_{seat}{suf}", "blank", seat, filled,
                          f"Unlabeled face diamond, {seat} lit")
    for d in ("up", "down", "left", "right"):
        A.append(Asset(f"ic_btn_dpad_{d}", 24, 24, dpad(d), note=f"D-pad {d} highlighted"))
    A.append(Asset("ic_btn_lstick_press", 24, 24, stick("L", True), note="Left stick press (L3)"))
    A.append(Asset("ic_btn_rstick_press", 24, 24, stick("R", True), note="Right stick press (R3)"))
    return A

# ---------------- status island ----------------
def ink_grad(y0, y1):
    return Lin(0, y0, 0, y1, [(0, "#FFFFFF", 1), (1, "#CFC9BD", 1)])

BELL = ("M12,3.2 C8.6,3.2 6.6,5.9 6.6,9.2 V12.6 L5,15.2 Q4.6,16.2 5.7,16.2 H18.3 Q19.4,16.2 19,15.2 "
        "L17.4,12.6 V9.2 C17.4,5.9 15.4,3.2 12,3.2 Z")
def bell(variant=None):
    ls = [halo(12, 11, 10, AMBER, 0.22, sy=0.9),
          L(BELL, fill=Lin(0, 3, 0, 16, [(0, "#FFE7BE", 1), (0.55, AMBER, 1), (1, "#D9862A", 1)])),
          L("M8.6,6.6 Q9.6,4.9 11.6,4.6", stroke="#FFFFFF", sw=1.0, salpha=0.6),
          L("M9.4,17.2 H14.6 A2.6,2.6 0 0 1 9.4,17.2 Z", fill=Lin(0, 17, 0, 20, [(0, "#FFE7BE", 1), (1, AMBER, 1)]))]
    if variant == "dot":
        ls += [halo(17.6, 5.6, 6, ROSE, 0.7), L(circle(17.6, 5.6, 3.1), fill=Lin(0, 2.5, 0, 8.7, [(0, "#FFB3C1", 1), (1, ROSE, 1)]),
               stroke="#000000", sw=1.2)]
    elif variant:
        txt = variant
        w = 7.6 if len(txt) == 1 else 10.4
        x = 23.4 - w
        ls += [halo(x + w/2, 6.4, 7, ROSE, 0.6),
               L(rrect(x, 2.4, w, 8, 4), fill=Lin(0, 2.4, 0, 10.4, [(0, "#FF9DB0", 1), (1, "#E23E62", 1)]), stroke="#000000", sw=1.0),
               L(text_path(txt, x + w/2, 6.4, 4.6, wght=900), fill="#FFFFFF")]
    return ls

def wifi(bars, off=False):
    cx, cy = 12, 18.6
    ls = []
    if bars and not off:
        ls.append(halo(12, 13, 11, TEAL, 0.10 + 0.05 * bars, sy=0.8))
    radii = [(0, 2.0), (4.2, 6.7), (8.6, 11.1), (13.0, 15.5)]
    for i, (r0, r1) in enumerate(radii):
        lit = (not off) and i < bars
        a0, a1 = math.radians(-135), math.radians(-45)
        if r0 == 0:
            d = circle(cx, cy - 0.4, r1)
        else:
            p = lambda r, a: (cx + r * math.cos(a), cy + r * math.sin(a))
            o0, o1, i1, i0 = p(r1, a0), p(r1, a1), p(r0, a1), p(r0, a0)
            d = (f"M{f(o0[0])},{f(o0[1])}A{f(r1)},{f(r1)} 0 0 1 {f(o1[0])},{f(o1[1])}"
                 f"L{f(i1[0])},{f(i1[1])}A{f(r0)},{f(r0)} 0 0 0 {f(i0[0])},{f(i0[1])}Z")
        if lit:
            ls.append(L(d, fill=Lin(0, cy - r1, 0, cy, [(0, "#B5FFEC", 1), (1, TEAL, 1)]), stroke=TEAL, sw=0.9, salpha=0.6))
        else:
            ls.append(L(d, fill=DIM, alpha=0.9, stroke=DIM, sw=0.9))
    if off:
        ls += [L("M4.5,4.5 L19.5,19.5", stroke="#000000", sw=3.2),
               L("M4.5,4.5 L19.5,19.5", stroke=ROSE, sw=1.7)]
    return ls

def battery(level, charging=False):
    x, y, w, h = 2.2, 7, 17.8, 10
    low = level <= 15 and not charging
    col, hi, lo = (ROSE, "#FFB3C1", "#C8325A") if low else ((AMBER, "#FFE2B0", "#D9862A") if charging else (TEAL, "#B5FFEC", ACC_LO))
    ls = []
    if level > 0 or charging:
        ls.append(halo(x + w/2, 12, 11, col, 0.18, sy=0.6))
    ls.append(L(rrect(x, y, w, h, 3), fill="#0C0D11", stroke=Lin(0, y, 0, y+h, [(0, "#FFFFFF", 0.95), (1, "#9A958C", 0.8)]), sw=1.3))
    ls.append(L(rrect(x + w + 0.6, 10, 1.9, 4, (0, 1, 1, 0)), fill="#CFC9BD"))
    fw = (w - 3.2) * max(0, min(level, 100)) / 100
    if fw > 0.2:
        ls.append(L(rrect(x + 1.6, y + 1.6, fw, h - 3.2, min(1.6, fw / 2)), fill=Lin(0, y+1.6, 0, y+h-1.6, [(0, hi, 1), (0.5, col, 1), (1, lo, 1)])))
        ls.append(L(rrect(x + 1.6, y + 1.6, fw, 1.8, min(0.9, fw / 2)), fill="#FFFFFF", alpha=0.35))
    if charging:
        bolt = poly([(12.6, 4.6), (7.6, 12.6), (11.2, 12.6), (9.8, 19.4), (15, 11), (11.4, 11), (12.6, 4.6)])
        ls += [halo(11.3, 12, 7, AMBER, 0.5),
               L(bolt, fill=Lin(0, 4.6, 0, 19.4, [(0, "#FFF6DD", 1), (1, AMBER, 1)]), stroke="#000000", sw=1.1)]
    return ls

def bluetooth(on=True):
    col = BLUE if on else DIM
    d = "M7.4,8 L16.4,16.2 L12,20.2 V3.8 L16.4,7.8 L7.4,16"
    ls = []
    if on: ls.append(halo(12, 12, 11, BLUE, 0.28, sx=0.7))
    ls.append(L(d, stroke=(Lin(0, 3.8, 0, 20.2, [(0, "#C6EEFF", 1), (1, BLUE, 1)]) if on else DIM), sw=1.9))
    if not on:
        ls += [L("M5,5 L19,19", stroke="#000000", sw=3), L("M5,5 L19,19", stroke=ROSE, sw=1.6)]
    return ls

SPEAKER = "M3.6,9.6 Q3.6,8.8 4.4,8.8 H7.4 L11.4,5.2 Q12.4,4.4 12.4,5.7 V18.3 Q12.4,19.6 11.4,18.8 L7.4,15.2 H4.4 Q3.6,15.2 3.6,14.4 Z"
def volume(level):
    ls = [halo(9, 12, 10, VIOLET, 0.22 if level else 0.0),
          L(SPEAKER, fill=Lin(0, 4.6, 0, 19.4, [(0, "#E6D9FF", 1), (1, VIOLET, 1)])),
          L("M5,9.6 H7.2", stroke="#FFFFFF", sw=0.9, salpha=0.5)]
    if level == 0:
        ls += [L("M15.4,9.2 L20.6,14.8 M20.6,9.2 L15.4,14.8", stroke=ROSE, sw=1.7)]
    for i, r in enumerate((3.6, 6.4, 9.2)):
        if i < level:
            ls.append(L(f"M{f(12.6 + r*0.55)},{f(12 - r*0.83)} A{f(r)},{f(r)} 0 0 1 {f(12.6 + r*0.55)},{f(12 + r*0.83)}",
                        stroke=Lin(0, 3, 0, 21, [(0, "#E6D9FF", 1), (1, VIOLET, 1)]), sw=1.6))
    return ls

def gear():
    import math as m
    pts = []
    n = 8
    for i in range(n * 2):
        a0 = m.pi * 2 * i / (n * 2) - m.pi / 2
        r = 9.4 if i % 2 == 0 else 7.2
        for da in (-0.17, 0.17):
            pts.append((12 + r * m.cos(a0 + da), 12 + r * m.sin(a0 + da)))
    body = poly(pts)
    return [halo(12, 12, 11.5, MINT, 0.18),
            L(body + circle_ccw(12, 12, 3.2), fill=Lin(0, 2.6, 0, 21.4, [(0, "#FFFFFF", 1), (1, "#AFA99E", 1)]),
              stroke="#FFFFFF", sw=0.8, salpha=0.5),
            L(circle(12, 12, 5.6), stroke="#000000", sw=0.8, salpha=0.25),
            L(circle(12, 12, 2.0), fill=TEAL)]

def wrench():
    d = ("M14.9,3.4 a5.2,5.2 0 0 0 -4.6,7 L3.9,16.8 a2.3,2.3 0 0 0 3.3,3.3 L13.6,13.7 "
         "a5.2,5.2 0 0 0 7,-4.6 L17.6,12 L14.1,11.4 L13.5,7.9 L16.4,4.9 a5.2,5.2 0 0 0 -1.5,-1.5 Z")
    return [halo(12, 12, 11.5, AMBER, 0.2),
            L(d, fill=Lin(4, 20, 20, 4, [(0, "#B8B2A6", 1), (0.6, "#FFFFFF", 1), (1, "#FFE2B0", 1)]), stroke="#FFFFFF", sw=0.6, salpha=0.5),
            L(circle(5.6, 18.4, 0.9), fill="#000000", alpha=0.6)]

def tools():  # tools island glyph: wrench crossed with a small slider knob
    return [halo(12, 12, 11.5, AMBER, 0.2),
            L("M4,7 H20 M4,12 H20 M4,17 H20", stroke=DIM, sw=1.6),
            L("M4,7 H9 M4,12 H15 M4,17 H7", stroke=Lin(4, 0, 15, 0, [(0, AMBER, 1), (1, "#FFE2B0", 1)]), sw=1.6),
            L(circle(9, 7, 2.1), fill=INK, stroke="#000000", sw=0.8),
            L(circle(15, 12, 2.1), fill=INK, stroke="#000000", sw=0.8),
            L(circle(7, 17, 2.1), fill=INK, stroke="#000000", sw=0.8)]

def power():
    return [halo(12, 12.5, 11.5, ROSE, 0.25),
            L("M7.6,6.8 A7.6,7.6 0 1 0 16.4,6.8", stroke=Lin(0, 6, 0, 21, [(0, "#FFC2CE", 1), (1, ROSE, 1)]), sw=2.0),
            L("M12,3.2 V11.4", stroke="#FFFFFF", sw=2.0)]

def status_assets():
    A = [Asset("ic_status_bell", 24, 24, bell(), note="Notifications"),
         Asset("ic_status_bell_dot", 24, 24, bell("dot"), note="Notifications, unread dot")]
    for n in list(range(1, 10)) + ["9+"]:
        A.append(Asset(f"ic_status_bell_{'9plus' if n == '9+' else n}", 24, 24, bell(str(n)), note=f"Notifications, {n} unread"))
    for b in range(5):
        A.append(Asset(f"ic_status_wifi_{b}", 24, 24, wifi(b), note=f"Wi-Fi {b} bars"))
    A.append(Asset("ic_status_wifi_off", 24, 24, wifi(0, off=True), note="Wi-Fi off"))
    for lv in (0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100):
        A.append(Asset(f"ic_status_battery_{lv}", 24, 24, battery(lv), note=f"Battery {lv}%"))
        A.append(Asset(f"ic_status_battery_{lv}_charging", 24, 24, battery(lv, True), note=f"Battery {lv}%, charging"))
    A += [Asset("ic_status_bluetooth", 24, 24, bluetooth(True), note="Bluetooth on"),
          Asset("ic_status_bluetooth_off", 24, 24, bluetooth(False), note="Bluetooth off")]
    for v in range(4):
        A.append(Asset(f"ic_status_volume_{v}" if v else "ic_status_volume_mute", 24, 24, volume(v), note=f"Volume level {v}"))
    A += [Asset("ic_status_settings", 24, 24, gear(), note="Settings gear"),
          Asset("ic_status_wrench", 24, 24, wrench(), note="Wrench"),
          Asset("ic_status_tools", 24, 24, tools(), note="Tools island (sliders)"),
          Asset("ic_status_power", 24, 24, power(), note="Power")]
    return A
