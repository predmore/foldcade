#!/usr/bin/env python3
"""Generate the Afterglow theme's original art.

The script is part of the GPLv3 program. The files it writes — font, wallpapers,
marks, preview, and sounds — are the CC BY-SA 4.0 theme art.
"""

from __future__ import annotations

import math
import struct
import subprocess
import wave
import zipfile
from pathlib import Path

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
ZIP_PATH = REPO / "app" / "src" / "main" / "assets" / "themes" / "afterglow.zip"
SPECIMEN = Path("/tmp/afterglow-specimen.png")

UPM = 1000
STROKE = 92
R = STROKE / 2
CAP_TOP = 700
CAP_BOT = 0
X_TOP = 500
ASC = 740
DESC = -210
BEARING = 56


def _xy(cx: float, cy: float, radius: float, angle: float) -> tuple[float, float]:
    return (cx + radius * math.cos(angle), cy + radius * math.sin(angle))


def _arc_points(cx: float, cy: float, radius: float, a0: float, a1: float, steps: int):
    for i in range(1, steps + 1):
        yield _xy(cx, cy, radius, a0 + (a1 - a0) * i / steps)


def _shoelace(glyph) -> float:
    if glyph.numberOfContours <= 0:
        return 0.0
    area = 0.0
    coords = glyph.coordinates
    start = 0
    for end in glyph.endPtsOfContours:
        for index in range(start, end):
            x1, y1 = coords[index]
            x2, y2 = coords[index + 1]
            area += x1 * y2 - x2 * y1
        x1, y1 = coords[end]
        x2, y2 = coords[start]
        area += x1 * y2 - x2 * y1
        start = end + 1
    return area / 2


def _reverse_contour(glyph, start: int, end: int):
    coords = glyph.coordinates
    flags = glyph.flags
    count = end - start + 1
    for offset in range(count // 2):
        left = start + offset
        right = end - offset
        coords[left], coords[right] = coords[right], coords[left]
        flags[left], flags[right] = flags[right], flags[left]


def _replay(pen, builder, positive: bool):
    temp = TTGlyphPen(None)
    builder(temp)
    glyph = temp.glyph()
    if glyph.numberOfContours == 1 and (positive and _shoelace(glyph) < 0 or not positive and _shoelace(glyph) > 0):
        _reverse_contour(glyph, 0, glyph.endPtsOfContours[0])
    glyph.draw(pen, None)


def add_circle(pen, cx, cy, radius, steps=28, clockwise=False):
    def draw(target):
        pts = []
        for i in range(steps):
            angle = 2 * math.pi * i / steps
            if clockwise:
                angle = -angle
            pts.append(_xy(cx, cy, radius, angle))
        target.moveTo(pts[0])
        for point in pts[1:]:
            target.lineTo(point)
        target.closePath()

    _replay(pen, draw, positive=not clockwise)


def add_capsule(pen, x0, y0, x1, y1, radius=R, steps=8):
    def draw(target):
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        if length < 0.01:
            add_circle(target, x0, y0, radius, steps * 2)
            return
        angle = math.atan2(dy, dx)
        start = angle + math.pi / 2
        target.moveTo(_xy(x0, y0, radius, start))
        target.lineTo(_xy(x1, y1, radius, start))
        for point in _arc_points(x1, y1, radius, start, start - math.pi, steps):
            target.lineTo(point)
        target.lineTo(_xy(x0, y0, radius, start - math.pi))
        for point in _arc_points(x0, y0, radius, start - math.pi, start - 2 * math.pi, steps):
            target.lineTo(point)
        target.closePath()

    _replay(pen, draw, positive=True)


def add_arc(pen, cx, cy, mid_r, a0_deg, a1_deg, radius=R, steps=18):
    def draw(target):
        a0 = math.radians(a0_deg)
        a1 = math.radians(a1_deg)
        outer = mid_r + radius
        inner = max(radius * 0.2, mid_r - radius)
        target.moveTo(_xy(cx, cy, outer, a0))
        for point in _arc_points(cx, cy, outer, a0, a1, steps):
            target.lineTo(point)
        sweep = math.pi if a1 >= a0 else -math.pi
        cap = _xy(cx, cy, mid_r, a1)
        for point in _arc_points(cap[0], cap[1], radius, a1, a1 + sweep, 8):
            target.lineTo(point)
        for point in _arc_points(cx, cy, inner, a1, a0, steps):
            target.lineTo(point)
        cap0 = _xy(cx, cy, mid_r, a0)
        for point in _arc_points(cap0[0], cap0[1], radius, a0 + math.pi, a0 + math.pi + sweep, 8):
            target.lineTo(point)
        target.closePath()

    _replay(pen, draw, positive=True)


def add_ring(pen, cx, cy, outer, inner, steps=32):
    add_circle(pen, cx, cy, outer, steps, clockwise=False)
    add_circle(pen, cx, cy, inner, steps, clockwise=True)


class Ink:
    def __init__(self, pen):
        self.pen = pen

    def line(self, x0, y0, x1, y1, radius=R):
        add_capsule(self.pen, x0, y0, x1, y1, radius)

    def v(self, x, y0, y1, radius=R):
        self.line(x, y0, x, y1, radius)

    def h(self, x0, x1, y, radius=R):
        self.line(x0, y, x1, y, radius)

    def arc(self, cx, cy, mid, a0, a1, radius=R, steps=18):
        add_arc(self.pen, cx, cy, mid, a0, a1, radius, steps)

    def ring(self, cx, cy, outer, inner):
        add_ring(self.pen, cx, cy, outer, inner)

    def dot(self, cx, cy, radius):
        add_circle(self.pen, cx, cy, radius)


def empty_glyph():
    return TTGlyphPen(None).glyph()


def draw_glyph(draw) -> object:
    pen = TTGlyphPen(None)
    draw(Ink(pen))
    return pen.glyph()


def bounds(glyph):
    if glyph.numberOfContours <= 0 or len(glyph.coordinates) == 0:
        return 0, 0, 0, 0
    xs = [glyph.coordinates[i][0] for i in range(len(glyph.coordinates))]
    ys = [glyph.coordinates[i][1] for i in range(len(glyph.coordinates))]
    return min(xs), min(ys), max(xs), max(ys)


# Shared cap skeleton. Ink is centered on these lines; the stroke hangs R past them.
L = 118
RT = 500
CX = (L + RT) / 2
TOP = CAP_TOP - R
BOT = R
MID = 350


def cap_glyphs():
    g = {}

    def put(name, draw):
        g[name] = draw_glyph(draw)

    put("A", lambda p: (
        p.line(L + 36, BOT, CX, TOP),
        p.line(RT - 36, BOT, CX, TOP),
        p.h(L + 70, RT - 70, 300),
    ))
    put("B", lambda p: (
        p.v(L, BOT, TOP),
        p.arc(L + 150, 515, 150, -70, 70, steps=16),
        p.arc(L + 130, 200, 150, -80, 80, steps=16),
        p.h(L, L + 170, TOP),
        p.h(L, L + 150, MID),
        p.h(L, L + 150, BOT),
    ))
    put("C", lambda p: p.arc(CX, 360, 250, 50, 310, steps=22))
    put("D", lambda p: (
        p.v(L, BOT, TOP),
        p.arc(L + 40, 360, 250, -70, 70, steps=20),
        p.h(L, L + 120, TOP),
        p.h(L, L + 120, BOT),
    ))
    put("E", lambda p: (
        p.v(L, BOT, TOP),
        p.h(L, RT, TOP),
        p.h(L, RT - 70, MID),
        p.h(L, RT, BOT),
    ))
    put("F", lambda p: (
        p.v(L, BOT, TOP),
        p.h(L, RT, TOP),
        p.h(L, RT - 40, MID),
    ))
    put("G", lambda p: (
        p.arc(CX, 360, 250, 40, 320, steps=22),
        p.h(CX, RT + 10, MID),
        p.v(RT + 10, BOT + 40, MID),
    ))
    put("H", lambda p: (
        p.v(L, BOT, TOP),
        p.v(RT, BOT, TOP),
        p.h(L, RT, MID),
    ))
    put("I", lambda p: (
        p.v(CX, BOT, TOP),
        p.h(CX - 110, CX + 110, TOP),
        p.h(CX - 110, CX + 110, BOT),
    ))
    put("J", lambda p: (
        p.v(RT - 40, 220, TOP),
        p.arc(CX - 20, 220, 150, 190, 360, steps=16),
        p.h(CX - 80, RT + 20, TOP),
    ))
    put("K", lambda p: (
        p.v(L, BOT, TOP),
        p.line(L + 20, MID, RT, TOP),
        p.line(L + 40, MID - 20, RT, BOT),
    ))
    put("L", lambda p: (
        p.v(L, BOT, TOP),
        p.h(L, RT, BOT),
    ))
    put("M", lambda p: (
        p.v(90, BOT, TOP),
        p.v(560, BOT, TOP),
        p.line(90, TOP, 325, 250),
        p.line(560, TOP, 325, 250),
    ))
    put("N", lambda p: (
        p.v(L, BOT, TOP),
        p.v(RT, BOT, TOP),
        p.line(L, TOP, RT, BOT),
    ))
    put("O", lambda p: p.ring(CX, 360, 310, 310 - STROKE))
    put("P", lambda p: (
        p.v(L, BOT, TOP),
        p.arc(L + 150, 520, 145, -75, 75, steps=16),
        p.h(L, L + 160, TOP),
        p.h(L, L + 140, 380),
    ))
    put("Q", lambda p: (
        p.ring(CX, 380, 300, 300 - STROKE),
        p.line(CX + 40, 260, RT + 30, BOT - 10),
    ))
    put("R", lambda p: (
        p.v(L, BOT, TOP),
        p.arc(L + 145, 530, 140, -70, 70, steps=16),
        p.h(L, L + 150, TOP),
        p.h(L, L + 130, 400),
        p.line(L + 80, 400, RT, BOT),
    ))
    put("S", lambda p: (
        p.arc(CX + 10, 510, 150, 20, 230, steps=16),
        p.arc(CX - 10, 210, 150, 200, 410, steps=16),
    ))
    put("T", lambda p: (
        p.h(L - 20, RT + 20, TOP),
        p.v(CX, BOT, TOP),
    ))
    put("U", lambda p: (
        p.v(L, 250, TOP),
        p.v(RT, 250, TOP),
        p.arc(CX, 250, (RT - L) / 2, 180, 360, steps=16),
    ))
    put("V", lambda p: (
        p.line(L, TOP, CX, BOT),
        p.line(RT, TOP, CX, BOT),
    ))
    put("W", lambda p: (
        p.line(70, TOP, 190, BOT),
        p.line(190, BOT, 315, TOP - 80),
        p.line(315, TOP - 80, 440, BOT),
        p.line(440, BOT, 560, TOP),
    ))
    put("X", lambda p: (
        p.line(L, BOT, RT, TOP),
        p.line(L, TOP, RT, BOT),
    ))
    put("Y", lambda p: (
        p.line(L, TOP, CX, MID),
        p.line(RT, TOP, CX, MID),
        p.v(CX, BOT, MID),
    ))
    put("Z", lambda p: (
        p.h(L, RT, TOP),
        p.line(RT, TOP, L, BOT),
        p.h(L, RT, BOT),
    ))
    return g


def lower_glyphs():
    g = {}
    xh = X_TOP - R
    base = R
    mid = (xh + base) / 2
    ll = 130
    rr = 430
    cc = (ll + rr) / 2
    asc = ASC - R
    desc = DESC + R

    def put(name, draw):
        g[name] = draw_glyph(draw)

    put("a", lambda p: (
        p.ring(cc + 10, mid, 175, 175 - STROKE),
        p.v(rr, base, xh),
    ))
    put("b", lambda p: (
        p.v(ll, base, asc),
        p.ring(cc + 30, mid, 165, 165 - STROKE),
    ))
    put("c", lambda p: p.arc(cc, mid, 175, 45, 315, steps=18))
    put("d", lambda p: (
        p.v(rr, base, asc),
        p.ring(cc - 10, mid, 165, 165 - STROKE),
    ))
    put("e", lambda p: (
        p.arc(cc, mid, 175, 10, 330, steps=18),
        p.h(ll - 10, rr + 10, mid),
    ))
    put("f", lambda p: (
        p.v(ll + 40, desc + 40, xh),
        p.arc(ll + 150, xh, 110, 80, 200, steps=12),
        p.h(ll - 10, rr - 40, mid + 40),
    ))
    put("g", lambda p: (
        p.ring(cc - 10, mid, 165, 165 - STROKE),
        p.v(rr, desc, xh),
        p.arc(cc, desc + 20, 155, 200, 360, steps=14),
    ))
    put("h", lambda p: (
        p.v(ll, base, asc),
        p.arc(cc + 20, xh - 80, 140, 180, 360, steps=14),
        p.v(rr - 10, base, xh - 40),
    ))
    put("i", lambda p: (
        p.v(ll + 20, base, xh),
        p.dot(ll + 20, asc - 20, 42),
    ))
    put("j", lambda p: (
        p.v(rr - 80, desc + 80, xh),
        p.arc(rr - 200, desc + 80, 120, 200, 360, steps=12),
        p.dot(rr - 80, asc - 20, 42),
    ))
    put("k", lambda p: (
        p.v(ll, base, asc),
        p.line(ll, mid, rr, xh),
        p.line(ll + 30, mid - 10, rr, base),
    ))
    put("l", lambda p: p.v(ll + 30, base, asc))
    put("m", lambda p: (
        p.v(90, base, xh),
        p.arc(210, xh - 70, 110, 180, 360, steps=12),
        p.v(300, base, xh - 30),
        p.arc(410, xh - 70, 110, 180, 360, steps=12),
        p.v(510, base, xh - 30),
    ))
    put("n", lambda p: (
        p.v(ll, base, xh),
        p.arc(cc + 10, xh - 80, 140, 180, 360, steps=14),
        p.v(rr, base, xh - 40),
    ))
    put("o", lambda p: p.ring(cc, mid, 190, 190 - STROKE))
    put("p", lambda p: (
        p.v(ll, desc, xh),
        p.ring(cc + 20, mid, 160, 160 - STROKE),
    ))
    put("q", lambda p: (
        p.v(rr, desc, xh),
        p.ring(cc - 10, mid, 160, 160 - STROKE),
    ))
    put("r", lambda p: (
        p.v(ll, base, xh),
        p.arc(cc + 20, xh - 40, 130, 80, 190, steps=12),
    ))
    put("s", lambda p: (
        p.arc(cc, mid + 90, 110, 20, 230, steps=14),
        p.arc(cc, mid - 90, 110, 200, 410, steps=14),
    ))
    put("t", lambda p: (
        p.v(ll + 50, base + 30, asc - 40),
        p.h(ll, rr - 20, xh + 20),
        p.arc(ll + 140, base + 80, 90, 180, 340, steps=10),
    ))
    put("u", lambda p: (
        p.v(ll, mid, xh),
        p.v(rr, base, xh),
        p.arc(cc, mid, (rr - ll) / 2, 180, 360, steps=14),
    ))
    put("v", lambda p: (
        p.line(ll, xh, cc, base),
        p.line(rr, xh, cc, base),
    ))
    put("w", lambda p: (
        p.line(70, xh, 170, base),
        p.line(170, base, 280, xh - 40),
        p.line(280, xh - 40, 390, base),
        p.line(390, base, 500, xh),
    ))
    put("x", lambda p: (
        p.line(ll, base, rr, xh),
        p.line(ll, xh, rr, base),
    ))
    put("y", lambda p: (
        p.line(ll, xh, cc, mid - 40),
        p.line(rr, xh, ll + 20, desc),
    ))
    put("z", lambda p: (
        p.h(ll, rr, xh),
        p.line(rr, xh, ll, base),
        p.h(ll, rr, base),
    ))
    return g


def digit_glyphs():
    g = {}
    left = 120
    right = 460
    cx = (left + right) / 2
    top = TOP
    bot = BOT
    mid = MID

    def put(name, draw):
        g[name] = draw_glyph(draw)

    put("zero", lambda p: p.ring(cx, 360, 280, 280 - STROKE))
    put("one", lambda p: (
        p.v(cx + 20, bot, top),
        p.line(cx - 80, top - 80, cx + 20, top),
        p.h(cx - 90, cx + 110, bot),
    ))
    put("two", lambda p: (
        p.arc(cx, 500, 170, 10, 190, steps=16),
        p.line(right - 20, 420, left, bot),
        p.h(left, right, bot),
    ))
    put("three", lambda p: (
        p.arc(cx - 20, 545, 125, 155, -35, steps=14),
        p.arc(cx - 10, 185, 145, 40, -145, steps=14),
    ))
    put("four", lambda p: (
        p.v(right - 40, bot, top),
        p.line(right - 40, top, left, mid),
        p.h(left, right + 10, mid),
    ))
    put("five", lambda p: (
        p.h(left, right, top),
        p.v(left, mid + 30, top),
        p.h(left, cx + 20, mid + 30),
        p.arc(cx + 10, 195, 145, 25, -155, steps=14),
    ))
    put("six", lambda p: (
        p.arc(cx, 470, 180, 40, 180, steps=14),
        p.ring(cx, 210, 175, 175 - STROKE),
    ))
    put("seven", lambda p: (
        p.h(left, right, top),
        p.line(right, top, left + 80, bot),
    ))
    put("eight", lambda p: (
        p.ring(cx, 530, 155, 155 - STROKE),
        p.ring(cx, 200, 170, 170 - STROKE),
    ))
    put("nine", lambda p: (
        p.ring(cx, 525, 155, 155 - STROKE),
        p.v(right - 36, 210, 545),
        p.arc(cx + 10, 210, 130, 200, 360, steps=12),
    ))
    return g


def punct_glyphs():
    g = {}

    def put(name, draw):
        g[name] = draw_glyph(draw)

    put("space", lambda p: None)
    put("exclam", lambda p: (p.v(200, 220, TOP), p.dot(200, 70, 42)))
    put("quotedbl", lambda p: (p.v(160, 480, TOP), p.v(280, 480, TOP)))
    put("numbersign", lambda p: (
        p.line(180, BOT, 260, TOP),
        p.line(300, BOT, 380, TOP),
        p.h(120, 440, 460),
        p.h(140, 460, 240),
    ))
    put("dollar", lambda p: (
        digit_draw_s(p),
        p.v(CX, BOT - 30, TOP + 30, radius=28),
    ))
    put("percent", lambda p: (
        p.ring(170, 540, 110, 110 - 70),
        p.line(460, TOP, 140, BOT),
        p.ring(400, 180, 110, 110 - 70),
    ))
    put("ampersand", lambda p: (
        p.arc(250, 520, 140, 20, 280, steps=14),
        p.line(180, 400, 420, BOT),
        p.arc(300, 180, 140, 200, 400, steps=12),
    ))
    put("quotesingle", lambda p: p.v(200, 480, TOP, radius=36))
    put("parenleft", lambda p: p.arc(280, 360, 180, 110, 250, steps=14))
    put("parenright", lambda p: p.arc(160, 360, 180, -70, 70, steps=14))
    put("asterisk", lambda p: (
        p.v(220, 430, 650, radius=32),
        p.line(120, 500, 320, 600, radius=32),
        p.line(120, 600, 320, 500, radius=32),
    ))
    put("plus", lambda p: (p.h(80, 420, 360), p.v(250, 200, 520)))
    put("comma", lambda p: (
        p.dot(200, 80, 42),
        p.line(200, 80, 150, -40, radius=28),
    ))
    put("hyphen", lambda p: p.h(80, 360, 320, radius=36))
    put("period", lambda p: p.dot(180, 70, 46))
    put("slash", lambda p: p.line(80, BOT, 420, TOP))
    put("colon", lambda p: (p.dot(200, 460, 46), p.dot(200, 160, 46)))
    put("semicolon", lambda p: (
        p.dot(200, 460, 46),
        p.dot(200, 160, 42),
        p.line(200, 160, 150, 40, radius=28),
    ))
    put("less", lambda p: (
        p.line(400, 520, 120, 360),
        p.line(120, 360, 400, 200),
    ))
    put("equal", lambda p: (p.h(80, 420, 430), p.h(80, 420, 280)))
    put("greater", lambda p: (
        p.line(120, 520, 400, 360),
        p.line(400, 360, 120, 200),
    ))
    put("question", lambda p: (
        p.arc(250, 500, 160, 20, 200, steps=14),
        p.v(250, 240, 340),
        p.dot(250, 80, 42),
    ))
    put("at", lambda p: (
        p.ring(300, 340, 110, 50),
        p.arc(300, 360, 230, 30, 330, steps=18),
    ))
    put("bracketleft", lambda p: (
        p.v(220, BOT, TOP),
        p.h(220, 360, TOP),
        p.h(220, 360, BOT),
    ))
    put("backslash", lambda p: p.line(80, TOP, 420, BOT))
    put("bracketright", lambda p: (
        p.v(280, BOT, TOP),
        p.h(140, 280, TOP),
        p.h(140, 280, BOT),
    ))
    put("asciicircum", lambda p: (
        p.line(80, 420, 200, TOP),
        p.line(200, TOP, 320, 420),
    ))
    put("underscore", lambda p: p.h(40, 480, -80, radius=32))
    put("grave", lambda p: p.line(140, TOP - 40, 240, TOP, radius=32))
    put("braceleft", lambda p: (
        p.arc(240, 560, 90, 80, 200, steps=10),
        p.arc(160, 360, 90, 240, 120, steps=8),
        p.arc(240, 160, 90, 160, 280, steps=10),
    ))
    put("bar", lambda p: p.v(200, BOT, TOP, radius=32))
    put("braceright", lambda p: (
        p.arc(160, 560, 90, -20, 100, steps=10),
        p.arc(240, 360, 90, 60, 300, steps=8),
        p.arc(160, 160, 90, -80, 20, steps=10),
    ))
    put("asciitilde", lambda p: (
        p.arc(180, 360, 90, 200, 360, steps=10),
        p.arc(320, 360, 90, 20, 180, steps=10),
    ))
    put("quoteright", lambda p: p.line(160, 520, 230, TOP, radius=34))
    put("quoteleft", lambda p: p.line(240, 520, 170, TOP, radius=34))
    put("endash", lambda p: p.h(40, 420, 320, radius=36))
    put("emdash", lambda p: p.h(20, 560, 320, radius=36))
    return g


def digit_draw_s(p: Ink):
    p.arc(CX, 520, 150, 20, 220, steps=14)
    p.arc(CX, 210, 150, 200, 400, steps=14)


ASCII = {
    " ": "space",
    "!": "exclam",
    '"': "quotedbl",
    "#": "numbersign",
    "$": "dollar",
    "%": "percent",
    "&": "ampersand",
    "'": "quotesingle",
    "(": "parenleft",
    ")": "parenright",
    "*": "asterisk",
    "+": "plus",
    ",": "comma",
    "-": "hyphen",
    ".": "period",
    "/": "slash",
    "0": "zero",
    "1": "one",
    "2": "two",
    "3": "three",
    "4": "four",
    "5": "five",
    "6": "six",
    "7": "seven",
    "8": "eight",
    "9": "nine",
    ":": "colon",
    ";": "semicolon",
    "<": "less",
    "=": "equal",
    ">": "greater",
    "?": "question",
    "@": "at",
    "[": "bracketleft",
    "\\": "backslash",
    "]": "bracketright",
    "^": "asciicircum",
    "_": "underscore",
    "`": "grave",
    "{": "braceleft",
    "|": "bar",
    "}": "braceright",
    "~": "asciitilde",
}


def build_font(path: Path):
    glyphs = {".notdef": draw_glyph(lambda p: (
        p.h(80, 480, 640, radius=28),
        p.h(80, 480, 80, radius=28),
        p.v(80, 80, 640, radius=28),
        p.v(480, 80, 640, radius=28),
    ))}
    glyphs.update(cap_glyphs())
    glyphs.update(lower_glyphs())
    glyphs.update(digit_glyphs())
    glyphs.update(punct_glyphs())
    for ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
        if ch not in glyphs:
            raise SystemExit(f"missing {ch}")
    order = [".notdef"] + [name for name in glyphs if name != ".notdef"]
    cmap = {}
    for char, name in ASCII.items():
        cmap[ord(char)] = name
    for char in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz":
        cmap[ord(char)] = char
    cmap[0x2019] = "quoteright"
    cmap[0x2018] = "quoteleft"
    cmap[0x2013] = "endash"
    cmap[0x2014] = "emdash"
    cmap[0x00A0] = "space"
    cmap[0x202F] = "space"
    cmap[0x2009] = "space"
    cmap[0x2007] = "space"
    metrics = {}
    for name, glyph in glyphs.items():
        xmin, _, xmax, _ = bounds(glyph)
        if glyph.numberOfContours <= 0:
            advance = 280 if name == "space" else 560
            metrics[name] = (advance, 0)
        else:
            metrics[name] = (int(round(xmax + BEARING)), int(round(xmin)))
    metrics["space"] = (300, 0)
    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=900, descent=-260)
    fb.setupOS2(
        sTypoAscender=900,
        sTypoDescender=-260,
        sTypoLineGap=0,
        usWinAscent=900,
        usWinDescent=260,
        sCapHeight=700,
        sxHeight=500,
        usWeightClass=500,
        usWidthClass=5,
        achVendID="FOLD",
        fsType=0,
    )
    fb.setupPost()
    fb.setupNameTable(
        {
            "copyright": "Copyright 2026 Foldcade contributors. Licensed under CC BY-SA 4.0.",
            "familyName": "Foldcade Afterglow",
            "styleName": "Regular",
            "uniqueFontIdentifier": "Foldcade Afterglow Regular",
            "fullName": "Foldcade Afterglow",
            "version": "Version 1.000",
            "psName": "FoldcadeAfterglow-Regular",
            "licenseDescription": "Creative Commons Attribution-ShareAlike 4.0 International",
            "licenseInfoURL": "https://creativecommons.org/licenses/by-sa/4.0/",
            "designer": "Foldcade contributors",
            "description": "Original geometric sans drawn for the Foldcade Afterglow theme.",
        }
    )
    fb.save(str(path))


def plate(color: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (512, 512), (0, 0, 0, 255))
    pixels = image.load()
    for y in range(512):
        for x in range(512):
            dist = math.hypot(x - 256, y - 256) / 290
            if dist >= 1:
                continue
            strength = (1 - dist) ** 1.55 * 0.72
            pixels[x, y] = tuple(int(channel * strength) for channel in color) + (255,)
    return image


def glow_mark(color: tuple[int, int, int], draw_mark) -> Image.Image:
    base = plate(color)
    ink = (244, 241, 234, 255)
    glow = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(glow), (*color, 210))
    glow = glow.filter(ImageFilter.GaussianBlur(14))
    mark = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(mark), ink)
    base.alpha_composite(glow)
    base.alpha_composite(mark)
    return base


def draw_dual(draw: ImageDraw.ImageDraw, fill):
    draw.rounded_rectangle((108, 156, 404, 230), radius=28, outline=fill, width=30)
    draw.rounded_rectangle((108, 282, 404, 356), radius=28, outline=fill, width=30)


def draw_pocket(draw: ImageDraw.ImageDraw, fill):
    draw.rounded_rectangle((166, 96, 346, 416), radius=40, outline=fill, width=32)


def draw_desk(draw: ImageDraw.ImageDraw, fill):
    draw.rounded_rectangle((96, 128, 416, 384), radius=36, outline=fill, width=30)
    draw.line((132, 196, 380, 196), fill=fill, width=24)


def draw_beam(draw: ImageDraw.ImageDraw, fill):
    draw.rounded_rectangle((132, 286, 196, 380), radius=18, fill=fill)
    draw.rounded_rectangle((224, 214, 288, 380), radius=18, fill=fill)
    draw.rounded_rectangle((316, 132, 380, 380), radius=18, fill=fill)


MARKS = {
    "dual": ((255, 96, 144), draw_dual),
    "pocket": ((64, 214, 255), draw_pocket),
    "desk": ((255, 186, 72), draw_desk),
    "beam": ((188, 156, 255), draw_beam),
}


def wallpaper(width: int, height: int, spots) -> Image.Image:
    image = Image.new("RGB", (width, height), (0, 0, 0))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            red = green = blue = 0.0
            for cx, cy, radius, color, peak in spots:
                dist = math.hypot(x - cx, y - cy) / radius
                if dist >= 1:
                    continue
                strength = (1 - dist) ** 2 * peak
                red += color[0] * strength
                green += color[1] * strength
                blue += color[2] * strength
            pixels[x, y] = (min(255, int(red)), min(255, int(green)), min(255, int(blue)))
    return image


def black_fraction(image: Image.Image) -> float:
    black = 0
    total = image.size[0] * image.size[1]
    for pixel in image.getdata():
        if pixel == (0, 0, 0):
            black += 1
    return black / total


def write_wav(path: Path, samples: list[float], rate: int = 44100):
    with wave.open(str(path), "w") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        frames = b"".join(struct.pack("<h", int(max(-1, min(1, sample)) * 32767)) for sample in samples)
        handle.writeframes(frames)


def tone(duration: float, freq_at, harmonics=0.12, rate: int = 44100) -> list[float]:
    count = int(rate * duration)
    samples = []
    for i in range(count):
        t = i / rate
        env = math.sin(math.pi * min(1, t / duration)) ** 0.6
        attack = min(1, t / 0.008)
        freq = freq_at(t)
        wave_value = math.sin(2 * math.pi * freq * t) + harmonics * math.sin(4 * math.pi * freq * t)
        samples.append(wave_value * 0.45 * env * attack)
    return samples


def build_sounds(directory: Path):
    directory.mkdir(parents=True, exist_ok=True)
    clips = {
        "move": tone(0.045, lambda t: 780),
        "activate": tone(0.12, lambda t: 640 if t < 0.05 else 960),
        "back": tone(0.1, lambda t: 560 - 240 * (t / 0.1)),
        "notify": tone(0.15, lambda t: 880 if t < 0.07 else 1175, harmonics=0.08),
    }
    for name, samples in clips.items():
        wav = directory / f"{name}.wav"
        ogg = directory / f"{name}.ogg"
        write_wav(wav, samples)
        subprocess.run(
            ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "3", str(ogg)],
            check=True,
        )
        wav.unlink()


def build_preview(path: Path, font_path: Path, marks: dict[str, Image.Image]):
    image = Image.new("RGB", (1280, 720), (0, 0, 0))
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(str(font_path), 72)
    small = ImageFont.truetype(str(font_path), 28)
    draw.text((64, 48), "Afterglow", font=font, fill=(244, 241, 234))
    draw.text((64, 140), "Foldcade", font=small, fill=(154, 149, 140))
    x = 64
    for name in ("dual", "pocket", "desk", "beam"):
        tile = marks[name].resize((220, 220), Image.Resampling.LANCZOS)
        image.paste(tile, (x, 280))
        x += 250
    image.save(path, optimize=True)


def write_zip(destination: Path):
    destination.parent.mkdir(parents=True, exist_ok=True)
    names = [
        "theme.json",
        "LICENSE",
        "font.ttf",
        "wallpaper-top.png",
        "wallpaper-bottom.png",
        "preview.png",
        "sounds/move.ogg",
        "sounds/activate.ogg",
        "sounds/back.ogg",
        "sounds/notify.ogg",
        "marks/dual.png",
        "marks/pocket.png",
        "marks/desk.png",
        "marks/beam.png",
    ]
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            archive.write(ROOT / name, arcname=name)


def write_specimen(font_path: Path):
    font = ImageFont.truetype(str(font_path), 78)
    label = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
    sample = ImageFont.truetype(str(font_path), 42)
    image = Image.new("RGB", (1800, 1400), (0, 0, 0))
    draw = ImageDraw.Draw(image)
    samples = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz0123456789"
    cell = 150
    for index, char in enumerate(samples):
        column = index % 12
        row = index // 12
        x = 20 + column * cell
        y = 20 + row * cell
        draw.rectangle((x, y, x + cell - 8, y + cell - 8), outline=(40, 40, 40))
        draw.text((x + 16, y + 8), char, font=font, fill=(244, 241, 234))
        draw.text((x + 12, y + 100), char, font=label, fill=(120, 120, 120))
    draw.text((20, 980), "Launch on top    3:32 AM    100% +    Wi-Fi", font=sample, fill=(244, 241, 234))
    draw.text((20, 1060), "One  Two  Three  Four  Five  Six  Seven  Eight", font=sample, fill=(244, 241, 234))
    draw.text((20, 1140), "Couldn’t reach the library. Afterglow.", font=sample, fill=(244, 241, 234))
    image.save(SPECIMEN)


def main():
    font_path = ROOT / "font.ttf"
    build_font(font_path)
    write_specimen(font_path)
    marks = {}
    mark_dir = ROOT / "marks"
    mark_dir.mkdir(exist_ok=True)
    for name, (color, drawer) in MARKS.items():
        image = glow_mark(color, drawer)
        image.save(mark_dir / f"{name}.png", optimize=True)
        marks[name] = image
    top = wallpaper(
        1920,
        1080,
        [
            (1760, 70, 620, (170, 120, 255), 0.34),
            (110, 1010, 540, (255, 90, 140), 0.28),
        ],
    )
    bottom = wallpaper(
        1240,
        1080,
        [
            (1140, 50, 460, (64, 214, 255), 0.30),
            (70, 1030, 420, (255, 186, 72), 0.26),
        ],
    )
    top.save(ROOT / "wallpaper-top.png", optimize=True)
    bottom.save(ROOT / "wallpaper-bottom.png", optimize=True)
    print(f"top black {black_fraction(top):.1%}")
    print(f"bottom black {black_fraction(bottom):.1%}")
    build_sounds(ROOT / "sounds")
    build_preview(ROOT / "preview.png", font_path, marks)
    write_zip(ZIP_PATH)
    print(f"zip {ZIP_PATH} {ZIP_PATH.stat().st_size} bytes")


if __name__ == "__main__":
    main()
