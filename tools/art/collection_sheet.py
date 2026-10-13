#!/usr/bin/env python3
"""Contact sheet for collection art, composed the way the host will draw it.

Usage: collection_sheet.py OUTDIR. Writes sheet.png (everything), grid.png (a bottom-screen strip at
Thor size) and hero.png (top-screen size). The tile and hero mocks copy the app's numbers: a #1C1C20
card with 0.28 corners, the art at 0.92, resting art at 0.58 alpha, and a soft accent glow behind.
"""
import io, os, sys
import numpy as np, cairosvg
from PIL import Image, ImageDraw, ImageFont
from art import *
import platforms, collection_art as col

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "..", "..", "themes", "afterglow", "font.ttf")
INK = (244, 241, 234); MUTED = (154, 149, 140); SURFACE = (0x1C, 0x1C, 0x20); FOCUS = (0xC8, 0xFF, 0xE4)

def font(sz): return ImageFont.truetype(FONT, sz)

def png(a, px):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=to_svg(a, px=px).encode()))).convert("RGBA")

def rgb(hexc): h = hexc.lstrip("#"); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def modulate(im, tint):
    """What ColorFilter.tint(tint, BlendMode.Modulate) does to the body: multiply each channel."""
    a = np.asarray(im).astype(np.float32)
    a[..., :3] *= np.array(rgb(tint), np.float32) / 255
    return Image.fromarray(a.round().astype(np.uint8), "RGBA")

def mark(name):
    """A platform mark or a collection glyph by mark name."""
    key = name.replace("-", "_")
    if key in col.GLYPHS: return Asset(key, 64, 64, col.GLYPHS[key][0]())
    return Asset(key, 64, 64, platforms.MARKS[key][0]())

def accent(name):
    key = name.replace("-", "_")
    return (col.GLYPHS.get(key) or platforms.MARKS[key])[1]

BODY, FACE = col.collection_assets()[:2]

def collection(px, tint, glyph):
    """Body x tint, then the face, then the glyph clipped into the well."""
    out = modulate(png(BODY, px), tint)
    out.alpha_composite(png(FACE, px))
    x, y, w, h, r = col.WELL
    u = px / 64
    g = col.GLYPH_BOX * u
    gi = png(mark(glyph), round(g))
    layer = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    cx, cy = (x + w / 2) * u, (y + h / 2) * u
    layer.alpha_composite(gi, (round(cx - gi.size[0] / 2), round(cy - gi.size[1] / 2)))
    m = Image.new("L", (px * 4, px * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle([x * u * 4, y * u * 4, (x + w) * u * 4, (y + h) * u * 4], radius=r * u * 4, fill=255)
    m = m.resize((px, px), Image.LANCZOS)
    clip = Image.new("RGBA", (px, px), (0, 0, 0, 0)); clip.paste(layer, (0, 0), m)
    out.alpha_composite(clip)
    return out

def glow(size, color, peak, reach):
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    u = np.hypot(xx - size / 2, yy - size / 2) / reach
    a = np.clip(np.exp(-4.2 * u * u) * np.clip(1 - u, 0, 1) ** 1.5, 0, 1) * peak
    im = np.zeros((size, size, 4), np.uint8); im[..., :3] = color; im[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(im, "RGBA")

def tile(canvas, x, y, ts, art, accent_hex, focused=False):
    """A grid tile as PanelScreen draws it."""
    acc = rgb(accent_hex)
    pad = ts
    g = glow(ts + 2 * pad, acc, 1.0 if focused else 0.22, ts / 2 + (64 if focused else 18) * ts / 230)
    canvas.alpha_composite(g, (x - pad, y - pad))
    card = Image.new("RGBA", (ts * 4, ts * 4), (0, 0, 0, 0)); d = ImageDraw.Draw(card)
    rad = ts * 0.28 * 4
    d.rounded_rectangle([0, 0, ts * 4 - 1, ts * 4 - 1], radius=rad, fill=SURFACE + (255,))
    if focused:
        d.rounded_rectangle([0, 0, ts * 4 - 1, ts * 4 - 1], radius=rad, outline=FOCUS + (255,), width=4 * 4)
    else:
        d.rounded_rectangle([5, 5, ts * 4 - 6, ts * 4 - 6], radius=rad, outline=acc + (107,), width=10)
    card = card.resize((ts, ts), Image.LANCZOS)
    a = art.resize((round(ts * 0.92), round(ts * 0.92)), Image.LANCZOS)
    if not focused:
        aa = np.asarray(a).copy(); aa[..., 3] = (aa[..., 3] * 0.58).astype(np.uint8); a = Image.fromarray(aa, "RGBA")
    inner = Image.new("RGBA", (ts, ts), (0, 0, 0, 0)); inner.alpha_composite(a, ((ts - a.size[0]) // 2, (ts - a.size[1]) // 2))
    m = Image.new("L", (ts * 4, ts * 4), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, ts * 4 - 1, ts * 4 - 1], radius=rad, fill=255)
    m = m.resize((ts, ts), Image.LANCZOS)
    clipped = Image.new("RGBA", (ts, ts), (0, 0, 0, 0)); clipped.paste(inner, (0, 0), m)
    card.alpha_composite(clipped)
    canvas.alpha_composite(card, (x, y))

def label(dr, text, cx, y, size=20, fill=MUTED):
    ft = font(size); dr.text((cx - dr.textlength(text, font=ft) / 2, y), text, font=ft, fill=fill)

# Home as the player might arrange it: system folders, then their collections.
HOME = [("folder", "nintendo-3ds", None, "3DS"), ("folder", "nintendo-ds", None, "DS"),
        ("coll", "star", "gold", "Favorites"), ("coll", "clock", "teal", "Recently played"),
        ("folder", "game-boy-advance", None, "GBA"), ("folder", "android-games", None, "Android Games"),
        ("coll", "sword", "indigo", "RPGs"), ("coll", "two-players", "coral", "Couch co-op"),
        ("folder", "emulators", None, "Emulators"), ("coll", "nintendo-3ds", "rose", "3DS faves"),
        ("coll", "hourglass", "slate", "Backlog"), ("coll", "leaf", "mint", "Cozy")]

def art_for(kind, name, tint, px):
    return collection(px, col.TINTS[tint], name) if kind == "coll" else png(mark(name), px)

def accent_for(kind, name, tint):
    return col.TINTS[tint] if kind == "coll" else accent(name)

def grid_strip(path):
    """The bottom screen at 1:1 (1240 x 1080): three rows, four columns on screen."""
    W, H = 1240, 1080
    im = Image.new("RGBA", (W, H), (0, 0, 0, 255)); dr = ImageDraw.Draw(im)
    ts, gx, gy = 236, 64, 70
    x0 = (W - (4 * ts + 3 * gx)) // 2; y0 = 70
    for i, (kind, name, tint, title) in enumerate(HOME):
        c, r = i // 3, i % 3
        x, y = x0 + c * (ts + gx), y0 + r * (ts + gy)
        tile(im, x, y, ts, art_for(kind, name, tint, 512), accent_for(kind, name, tint), focused=(i == 6))
    im.convert("RGB").save(path)
    return im

def hero(path):
    """The top screen's big art for a folder and a collection, side by side (each 853 x 480 dp at 2.25x)."""
    W, H = 1920, 1080
    im = Image.new("RGBA", (W * 2 + 40, H), (0, 0, 0, 255)); dr = ImageDraw.Draw(im)
    for i, (kind, name, tint, title, sub) in enumerate([("folder", "nintendo-3ds", None, "3DS", "27 games"),
                                                         ("coll", "sword", "indigo", "RPGs", "14 games")]):
        ox = i * (W + 40)
        dr.rectangle([ox, 0, ox + W - 1, H - 1], outline=(40, 40, 46), width=2)
        sz = 640; acc = rgb(accent_for(kind, name, tint))
        g = glow(sz * 2, acc, 0.45, sz * 0.8); im.alpha_composite(g, (ox + 200 + sz // 2 - sz, 220 + sz // 2 - sz))
        a = art_for(kind, name, tint, round(sz * 0.92))
        im.alpha_composite(a, (ox + 200 + (sz - a.size[0]) // 2, 220 + (sz - a.size[1]) // 2))
        dr.text((ox + 960, 420), title, font=font(110), fill=INK)
        dr.text((ox + 964, 560), sub, font=font(48), fill=MUTED)
    im.convert("RGB").save(path)

def sheet(path):
    W = 2200
    im = Image.new("RGBA", (W, 6400), (0, 0, 0, 255)); dr = ImageDraw.Draw(im); y = 40
    def h1(t, sub=None):
        nonlocal y
        dr.text((50, y), t, font=font(44), fill=INK); y += 62
        if sub: dr.text((52, y), sub, font=font(24), fill=MUTED); y += 44
        y += 10
    dr.text((50, y), "Foldcade · Afterglow collections", font=font(60), fill=INK); y += 100

    h1("Folders and collections side by side, at grid size", "Folders: a device on the dark card. Collections: a tinted folder with the glyph in its well.")
    ts = 200
    for i, (kind, name, tint, title) in enumerate(HOME):
        x = 60 + i % 8 * (ts + 60); yy = y + i // 8 * (ts + 80)
        tile(im, x, yy, ts, art_for(kind, name, tint, 512), accent_for(kind, name, tint), focused=(i in (2, 6)))
        label(dr, title, x + ts / 2, yy + ts + 10)
    y += 2 * (ts + 80) + 30

    h1("Small (96 px) and tiny (56 px): still a folder, still a glyph")
    x = 60
    for kind, name, tint, title in HOME[:8]:
        tile(im, x, y, 96, art_for(kind, name, tint, 256), accent_for(kind, name, tint)); x += 130
    x += 40
    for kind, name, tint, title in HOME[:8]:
        tile(im, x, y + 20, 56, art_for(kind, name, tint, 128), accent_for(kind, name, tint)); x += 80
    y += 150

    h1("How one is drawn", "body (greys)  x  tint  +  face (never tinted)  +  glyph in the well  =  collection")
    px = 220; x = 60
    steps = [(png(BODY, px), "body"), (modulate(png(BODY, px), col.TINTS["violet"]), "x violet"),
             (png(FACE, px), "face"), (png(mark("ghost"), px), "glyph"), (collection(px, col.TINTS["violet"], "ghost"), "collection")]
    for im_, cap in steps:
        im.alpha_composite(im_, (x, y)); label(dr, cap, x + px / 2, y + px + 6); x += px + 70
    y += px + 60

    h1("Ten tints", "One body in greys, multiplied by the tint at draw time (BlendMode.Modulate). Gloss and well stay untinted.")
    px = 190
    glyph_cycle = ["star", "heart", "trophy", "ghost", "shield", "gem", "clock", "leaf", "puzzle", "skull"]
    for i, (tname, tc) in enumerate(col.TINTS.items()):
        x = 60 + i * (px + 20)
        im.alpha_composite(collection(px, tc, glyph_cycle[i]), (x, y))
        label(dr, tname, x + px / 2, y + px + 6)
    y += px + 60
    for i, (tname, tc) in enumerate(col.TINTS.items()):
        x = 60 + i * (px + 20)
        im.alpha_composite(collection(px, tc, "star"), (x, y))
    y += px + 40

    h1("Theme glyphs (alone, as in the picker)", "Star = Favorites, clock = Recently played.")
    px = 160; cols = 11
    for i, (n, (fn, acc, about)) in enumerate(col.GLYPHS.items()):
        x = 60 + i % cols * (px + 34); yy = y + i // cols * (px + 50)
        im.alpha_composite(png(Asset(n, 64, 64, fn()), px), (x, yy))
        label(dr, n.replace("_", " "), x + px / 2, yy + px + 6)
    y += 2 * (px + 50) + 20

    h1("Every glyph on a collection, at grid size")
    ts = 170; cols = 11; tints = list(col.TINTS.values())
    for i, n in enumerate(col.GLYPHS):
        x = 60 + i % cols * (ts + 25); yy = y + i // cols * (ts + 40)
        t = tints[i % len(tints)]
        tile(im, x, yy, ts, collection(512, t, n), t)
    y += 2 * (ts + 40) + 20

    h1("Platform glyphs on collections", "A collection can still wear a device; the folder shape keeps it apart from the real folder.")
    ts = 170
    pairs = [("nintendo-3ds", "rose"), ("nintendo-ds", "coral"), ("game-boy-advance", "indigo"), ("snes", "violet"),
             ("playstation", "slate"), ("genesis", "rose"), ("android-games", "mint"), ("moonlight", "sky"),
             ("pc", "sky"), ("console", "gold"), ("library", "teal")]
    for i, (n, t) in enumerate(pairs):
        x = 60 + i * (ts + 25)
        tile(im, x, y, ts, collection(512, col.TINTS[t], n), col.TINTS[t])
    y += ts + 40
    for i, (n, t) in enumerate(pairs):
        x = 60 + i * (ts + 25)
        tile(im, x, y, ts, png(mark(n), 512), accent(n))
    y += ts + 50

    h1("Hero size")
    for i, (n, t) in enumerate([("star", "gold"), ("clock", "teal"), ("sword", "indigo"), ("mushroom", "rose")]):
        im.alpha_composite(collection(480, col.TINTS[t], n), (60 + i * 520, y))
    y += 520
    im = im.crop((0, 0, W, y)).convert("RGB")
    im.save(path)

if __name__ == "__main__":
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    sheet(f"{out}/sheet.png"); grid_strip(f"{out}/grid.png"); hero(f"{out}/hero.png")
    print("wrote", out)
