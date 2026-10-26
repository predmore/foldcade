"""Afterglow's verbs: what Y's menu and Edit home do to a tile, each a one-ink symbol on the
64-unit grid, drawn like the collection glyphs (marks.py): one bold silhouette with its details
cut out. They show as round tiles at the top of a tile's menu and at 32dp beside a row's words,
so every cut is at least 2.5 units and nothing relies on a thin line.

A verb ships only as symbol_<name>. It is chrome, never a thing on the grid, so it has no mark.
"""
import math
from art import *
from objects import *

rr = rrect
ci = circle

def _arm_points(s, b, w, t):
    """One arm of a four-way arrow, pointing up from the centre: shaft half-width [s], the head's base
    [b] from the centre, its half-width [w], and its tip [t] from the centre."""
    return [(-s, -s), (-s, -b), (-w, -b), (0, -t), (w, -b), (s, -b)]

def move():
    """Move: four arrows out from the middle, the way a picked-up tile walks."""
    pts = []
    for k in range(4):
        a = math.radians(90 * k); ca, sa = math.cos(a), math.sin(a)
        pts += [(32 + x * ca - y * sa, 32 + x * sa + y * ca) for x, y in _arm_points(3.6, 14, 10, 25)]
    return [outline(rpoly(pts, 1.2))]

def add_to():
    """Add to: a tile with a plus, in front of the edge of the shelf it joins."""
    return [outline("M8,20 Q8,15 13,15 V45 Q13,50 18,50 H48 Q48,55 43,55 H13 Q8,55 8,50 Z"),
            outline(rr(17, 8, 39, 39, 8)), outline(_plus(36.5, 27.5, 11, 5.6))]

def _plus(cx, cy, arm, w):
    """A plus with square-cut ends, [arm] from the centre and [w] wide."""
    h = w / 2
    return poly([(cx - h, cy - arm), (cx + h, cy - arm), (cx + h, cy - h), (cx + arm, cy - h), (cx + arm, cy + h),
                 (cx + h, cy + h), (cx + h, cy + arm), (cx - h, cy + arm), (cx - h, cy + h), (cx - arm, cy + h),
                 (cx - arm, cy - h), (cx - h, cy - h)])

def art():
    """Art: a picture with hills and a sun cut out of it."""
    return [outline(rr(6, 10, 52, 44, 8)),
            outline(rpoly([(13, 47), (25.5, 29), (34, 40), (40.5, 33), (51, 47)], 2)),
            outline(ci(43, 21.5, 4.6))]

def saves():
    """Saves: a floppy disk, its shutter and its label cut out."""
    return [outline(rpoly([(8, 8), (45, 8), (56, 19), (56, 56), (8, 56)], [4.5, 2, 4.5, 4.5, 4.5])),
            outline(rr(18, 8, 23, 15, (0, 0, 2.5, 2.5))), outline(rr(32.5, 11, 5, 8.5, 1.2)),
            outline(rr(16, 33, 32, 17, 3.5))]

def rename():
    """Rename: a pencil, its tip down and to the left, writing on a line."""
    t = lambda d: xf(d, s=0.92, deg=45, dx=1.5, dy=-3)
    return [outline(t("M25,22 H39 V46 L32,57 L25,46 Z")), outline(t(rr(25, 7, 14, 12, (4, 4, 1, 1)))),
            outline(t("M29.4,51 H34.6 L32,55 Z")),
            outline(rr(30, 52.5, 26, 5.5, 2.75), sym=1)]

def icon():
    """Icon: a rounded tile with a star cut out, the glyph a collection wears."""
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 16 if i % 2 == 0 else 7.2
        pts.append((32 + r * math.cos(a), 33.5 + r * math.sin(a)))
    return [outline(rr(7, 7, 50, 50, 14)), outline(rpoly(pts, 1.6))]

def new_collection():
    """New collection: a folder with a plus cut into its front."""
    folder = ("M6,16 Q6,11 11,11 H23.5 Q26,11 27.6,13 L30.6,17 H53 Q58,17 58,22 V51 Q58,55 54,55 "
              "H10 Q6,55 6,51 Z")
    return [outline(folder), outline(_plus(32, 36, 10, 5.6))]

def select():
    """Select: a box with a tick cut out."""
    return [outline(rr(8, 8, 48, 48, 12)),
            outline("M18.5,32.5 L23.4,27.6 L29,33.2 L41.4,20.8 L46.3,25.7 L29,43 Z")]

def restart():
    """Restart: a ring that turns back to the top, its arrow pointing on."""
    cx, cy, r, w = 32, 33.5, 17.5, 7
    head_at = math.radians(-102)
    hx, hy = cx + r * math.cos(head_at), cy + r * math.sin(head_at)
    # The head points along the ring, clockwise, past the gap at the top.
    tx, ty = -math.sin(head_at), math.cos(head_at)
    nx, ny = math.cos(head_at), math.sin(head_at)
    tip = (hx + tx * 12.5, hy + ty * 12.5)
    side = lambda k: (hx + nx * k - tx * 1.5, hy + ny * k - ty * 1.5)
    return [outline(arc_band(cx, cy, r, w, -60, 250)),
            outline(rpoly([side(11), tip, side(-11)], 1.4), sym=1)]

def undo():
    """Undo: an arrow that turns back on itself, pointing left."""
    return [outline(rpoly([(7, 24), (22, 11.5), (22, 36.5)], 1.6)),
            line("M20,24 H38 A12.5,12.5 0 0 1 38,49 H27", 7)]

EYE = "M5,32 Q18.5,12 32,12 Q45.5,12 59,32 Q45.5,52 32,52 Q18.5,52 5,32 Z"

def show():
    """Show: an open eye."""
    return [outline(EYE), outline(ci(32, 32, 11.5)), outline(ci(32, 32, 5.5))]

def hide():
    """Hide: an eye shut, its lashes down."""
    lid = "M7,22 Q32,52 57,22"
    lashes = "M13.5,31.5 L8,39 M24.5,39 L22,48 M39.5,39 L42,48 M50.5,31.5 L56,39"
    return [line(lid, 7.5), line(lashes, 6.5)]

def remove():
    """Remove: a disc with a bar cut across it, taking the tile off its spot."""
    return [outline(ci(32, 32, 25)), outline(rr(18, 28.75, 28, 6.5, 3.25))]

CAN = "M13.5,20 H50.5 L47.2,53 Q46.8,57 42.8,57 H21.2 Q17.2,57 16.8,53 Z"
LID = rr(8, 11, 48, 6, 3)
HANDLE = "M24,11 V9 Q24,5 28,5 H36 Q40,5 40,9 V11 H35.5 V9.5 H28.5 V11 Z"

def delete():
    """Delete: a bin with its lid on."""
    return [outline(CAN), outline(LID), outline(HANDLE),
            outline(rr(23, 27, 4, 22, 2)), outline(rr(30, 27, 4, 22, 2)), outline(rr(37, 27, 4, 22, 2))]

def remove_download():
    """Remove download: the bin, with a down arrow cut where its ribs would be."""
    return [outline(CAN), outline(LID), outline(HANDLE),
            outline("M29,26 H35 V37 H41.5 L32,49 L22.5,37 H29 Z")]

def sort():
    """Sort: three bars, longest first, beside an arrow down."""
    return [outline(rr(7, 12, 30, 7, 3.5)), outline(rr(7, 28.5, 22, 7, 3.5)), outline(rr(7, 45, 14, 7, 3.5)),
            outline(rr(44, 9, 7, 34, 3.5)),
            outline(rpoly([(36, 39), (59, 39), (47.5, 55)], 1.6), sym=1)]

def tidy():
    """Tidy: a broom, sweeping."""
    t = lambda d: xf(d, deg=-32, dx=1, dy=-1)
    return [outline(t(capsule(32, 5, 32, 24, 6.5))), outline(t(rr(20, 24, 24, 8, 3))),
            outline(t(rpoly([(19, 34.5), (45, 34.5), (52, 58), (12, 58)], [2, 2, 2, 2]))),
            outline(t(rr(24.5, 42, 3.4, 11, 1.7))), outline(t(rr(30.3, 42, 3.4, 11, 1.7))), outline(t(rr(36.1, 42, 3.4, 11, 1.7)))]

VERBS = {
    "move": (move, "Move: four arrows out from the middle"),
    "add_to": (add_to, "Add to: a tile with a plus, joining a shelf"),
    "art": (art, "Art: a picture with hills and a sun"),
    "saves": (saves, "Saves: a floppy disk"),
    "rename": (rename, "Rename: a pencil on a line"),
    "icon": (icon, "Icon: a tile with a star cut out"),
    "new_collection": (new_collection, "New collection: a folder with a plus"),
    "select": (select, "Select: a box with a tick"),
    "restart": (restart, "Restart: a ring turning back to the top"),
    "undo": (undo, "Undo: an arrow turning back"),
    "show": (show, "Show: an open eye"),
    "hide": (hide, "Hide: a shut eye"),
    "remove": (remove, "Remove: a disc with a bar across it"),
    "delete": (delete, "Delete: a bin"),
    "remove_download": (remove_download, "Remove download: a bin with a down arrow"),
    "sort": (sort, "Sort: bars, longest first, beside an arrow down"),
    "tidy": (tidy, "Tidy: a broom"),
}
