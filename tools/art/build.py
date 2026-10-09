#!/usr/bin/env python3
"""Build every Foldcade art asset. GPLv3 program; its outputs are CC BY-SA 4.0 art."""
import io, os, shutil, math
import numpy as np, cairosvg
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from art import *
from palette import *
import glyphs, marks, appicon, sprites

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = f"{OUT}/res"; SVG = f"{OUT}/svg"; PREV = f"{OUT}/previews"
FONT = "/usr/share/fonts/truetype/sand-box/google/Nunito/Nunito-VariableFont_wght.ttf"
def font(sz, w=700):
    ft = ImageFont.truetype(FONT, sz); ft.set_variation_by_axes([w]); return ft

def png(a, px, bg=None):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=to_svg(a, px=px, bg=bg).encode()))).convert("RGBA")

def write(a, sub):
    os.makedirs(f"{RES}/drawable", exist_ok=True); os.makedirs(f"{SVG}/{sub}", exist_ok=True)
    open(f"{RES}/drawable/{a.name}.xml", "w").write(to_vd(a))
    open(f"{SVG}/{sub}/{a.name}.svg", "w").write(to_svg(a))

for d in (RES, SVG, f"{OUT}/play", f"{OUT}/themes", f"{OUT}/shaders"):
    shutil.rmtree(d, ignore_errors=True)

ctrl, stat, mk = glyphs.controller_assets(), glyphs.status_assets(), marks.marks_assets()
for a in ctrl: write(a, "controller")
for a in stat: write(a, "status")
for a in mk: write(a, "marks")

# ---------------- app icon (concept A) ----------------
K = 0.86
fg_layers = []
for l in appicon.concept_a():
    if l.scale and isinstance(l.fill, Rad):        # squashed halo: rebuild at scaled centre/radius
        sx, sy, cx, cy = l.scale; r = l.fill.r * K
        ncx, ncy = 54 + (cx - 54) * K, 54 + (cy - 54) * K
        fg_layers.append(halo(ncx, ncy, r, l.fill.stops[0][1], l.fill.stops[0][2], sx=sx, sy=sy, core=0.1))
    else:
        l.scale = (K, K, 54, 54); fg_layers.append(l)
mono_layers = []
for l in appicon.concept_a(mono=True):
    l.scale = (K, K, 54, 54); mono_layers.append(l)
fg = Asset("ic_launcher_foreground", 108, 108, fg_layers, note="Adaptive icon foreground: open clamshell in Afterglow light")
bgA = Asset("ic_launcher_background", 108, 108, [L("M0,0H108V108H0Z", fill="#000000")], note="Adaptive icon background: true black")
mono = Asset("ic_launcher_monochrome", 108, 108, mono_layers, note="Adaptive icon monochrome layer (themed icons)")
for a in (fg, bgA, mono): write(a, "app-icon")
os.makedirs(f"{RES}/mipmap-anydpi-v26", exist_ok=True)
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')
for n in ("ic_launcher", "ic_launcher_round"):
    open(f"{RES}/mipmap-anydpi-v26/{n}.xml", "w").write(adaptive)
full = png(Asset("x", 108, 108, fg.layers), 1728, bg="#000000")   # 16x
def view(im):  # the 72/108 a launcher shows
    s = im.size[0]; c = round(s * 18 / 108); return im.crop((c, c, s - c, s - c))
def mask(im, shape, ss=4):
    s = im.size[0]; m = Image.new("L", (s * ss, s * ss), 0); d = ImageDraw.Draw(m)
    if shape == "circle": d.ellipse([0, 0, s * ss - 1, s * ss - 1], fill=255)
    else: d.rounded_rectangle([0, 0, s * ss - 1, s * ss - 1], radius=int(s * ss * 0.22), fill=255)
    m = m.resize((s, s), Image.LANCZOS); out = Image.new("RGBA", im.size, (0, 0, 0, 0)); out.paste(im, (0, 0), m); return out
v = view(full)
for dens, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    os.makedirs(f"{RES}/mipmap-{dens}", exist_ok=True)
    pad = round(px / 48)   # legacy icons leave 1dp of breathing room
    body = v.resize((px - 2 * pad, px - 2 * pad), Image.LANCZOS)
    for n, shp in (("ic_launcher", "square"), ("ic_launcher_round", "circle")):
        c = Image.new("RGBA", (px, px), (0, 0, 0, 0)); m = mask(body, shp); c.paste(m, (pad, pad), m)
        c.save(f"{RES}/mipmap-{dens}/{n}.png", optimize=True)
os.makedirs(f"{OUT}/play", exist_ok=True)
c = round(1728 * 9 / 108)
full.crop((c, c, 1728 - c, 1728 - c)).resize((512, 512), Image.LANCZOS).convert("RGB").save(f"{OUT}/play/ic_launcher-playstore.png")

# ---------------- sprites ----------------
SP = f"{OUT}/themes/afterglow/sprites"; os.makedirs(SP, exist_ok=True)
ACC = {"white": (255, 255, 255), "teal": (0x3E, 0xE6, 0xC0), "amber": (0xFF, 0xB5, 0x47), "rose": (0xFF, 0x5C, 0x7A),
       "violet": (0xB9, 0x8C, 0xFF), "blue": (0x4C, 0xC9, 0xFF), "mint": (0xC8, 0xFF, 0xE4)}
for sz in (256, 512):
    sprites.rgba(sprites.radial_glow(sz)).save(f"{SP}/glow_soft_{sz}.png")
for k in ("teal", "amber", "rose", "violet", "blue"):
    sprites.rgba(sprites.radial_glow(256), ACC[k]).save(f"{SP}/glow_soft_256_{k}.png")
hs = sprites.hotspot()
sprites.rgba(hs).save(f"{SP}/ribbon_hotspot.png")
for k in ("teal", "amber"):
    sprites.rgba(hs, ACC[k]).save(f"{SP}/ribbon_hotspot_{k}.png")
os.makedirs(f"{RES}/drawable-nodpi", exist_ok=True)
sprites.focus_ninepatch().save(f"{RES}/drawable-nodpi/focus_glow.9.png")
sprites.focus_ninepatch(color=ACC["mint"]).save(f"{RES}/drawable-nodpi/focus_glow_mint.9.png")
os.makedirs(f"{OUT}/shaders", exist_ok=True)
shutil.copy(f"{OUT}/src/focus_glow.agsl", f"{OUT}/shaders/focus_glow.agsl")

# ---------------- theme preview (1280x720) ----------------
def ribbon_layer(W, H, pts_fn, color, width, sigma, peak, seed):
    core = Image.new("F", (W, H), 0.0); d = ImageDraw.Draw(core)
    xs = np.linspace(-40, W + 40, 600); ys = pts_fn(xs)
    # width swells and tapers; brightness drifts (the 'alive' look Jon asked for)
    for i in range(len(xs) - 1):
        t = i / len(xs)
        wmul = 0.55 + 0.45 * math.sin(math.pi * t) * (0.8 + 0.2 * math.sin(7 * t + seed))
        b = (0.55 + 0.45 * math.sin(math.pi * t)) * (0.85 + 0.15 * math.sin(11 * t + seed))
        d.line([(xs[i], ys[i]), (xs[i + 1], ys[i + 1])], fill=float(b), width=max(1, int(width * wmul)))
    a = np.array(core)
    halo_ = sprites.blur(a, sigma) * 2.2 + sprites.blur(a, sigma * 0.3) * 0.9
    return np.clip(halo_ * peak, 0, 1)[..., None] * np.array(color, float)[None, None, :] / 255

def compose_preview(path, W=1280, H=720):
    acc = np.zeros((H, W, 3))
    acc += ribbon_layer(W, H, lambda x: 330 + 120 * np.sin(x / 260 + 0.6) + 40 * np.sin(x / 90), ACC["teal"], 4, 10, 0.8, 1)
    acc += ribbon_layer(W, H, lambda x: 420 + 90 * np.sin(x / 300 + 2.2), ACC["amber"], 3, 9, 0.65, 2)
    acc += ribbon_layer(W, H, lambda x: 250 + 70 * np.sin(x / 210 + 4.0), ACC["violet"], 3, 9, 0.45, 3)
    acc = acc / (1 + acc * 0.6)             # gentle tone-map: overlaps brighten, never clip hard
    base = Image.fromarray(np.stack([sprites.to8(np.clip(acc[..., i], 0, 1)) for i in range(3)], -1), "RGB").convert("RGBA")
    dr = ImageDraw.Draw(base)
    # islands
    def island(x, y, items, w):
        dr.rounded_rectangle([x, y, x + w, y + 52], radius=26, fill=(0, 0, 0, 255), outline=(70, 70, 76, 255), width=2)
        cx = x + 14
        for it in items:
            if isinstance(it, str):
                ft = font(24, 800); dr.text((cx, y + 11), it, font=ft, fill=(244, 241, 234)); cx += dr.textlength(it, font=ft) + 12
            else:
                im = png(it, 40); base.alpha_composite(im, (int(cx), y + 6)); cx += 44
    A = {a.name: a for a in ctrl + stat}
    island(36, 30, [A["ic_btn_l1"], A["ic_status_tools"], A["ic_status_settings"]], 162)
    island(W - 36 - 330, 30, [A["ic_btn_r1"], A["ic_status_bell_2"], A["ic_status_wifi_4"], A["ic_status_battery_80"], "9:41"], 330)
    dr.text((60, 120), "Afterglow", font=font(84, 600), fill=(244, 241, 234))
    dr.text((64, 222), "Foldcade · true black, soft light", font=font(30, 500), fill=(154, 149, 140))
    # tiles
    ts, gap, y0 = 196, 36, 404
    x0 = (W - (6 * ts + 5 * gap) * 0.86) / 2
    ts2 = int(ts * 0.86); gap2 = int(gap * 0.86)
    glowpatch = Image.open(f"{RES}/drawable-nodpi/focus_glow_mint.9.png").crop((1, 1, 201, 201))
    for i, a in enumerate(mk):
        x = int(x0 + i * (ts2 + gap2)); y = y0
        if i == 1:
            g = glowpatch.resize((int(ts2 * 200 / 120), int(ts2 * 200 / 120)), Image.LANCZOS)
            base.alpha_composite(g, (x - int(ts2 * 40 / 120), y - int(ts2 * 40 / 120)))
        dr.rounded_rectangle([x, y, x + ts2, y + ts2], radius=int(ts2 * 0.28), fill=(0, 0, 0, 255),
                             outline=((200, 255, 228, 255) if i == 1 else (60, 62, 70, 255)), width=(4 if i == 1 else 2))
        m = png(a, int(ts2 * 0.72)); base.alpha_composite(m, (x + int(ts2 * 0.14), y + int(ts2 * 0.14)))
        lab = a.name.replace("mark_", "").replace("_lit", "").replace("slim", "slim dual").title()
        ft = font(24, 700); dr.text((x + ts2 / 2 - dr.textlength(lab, font=ft) / 2, y + ts2 + 14), lab, font=ft, fill=(244, 241, 234))
    base.convert("RGB").save(path)
os.makedirs(f"{OUT}/themes/afterglow", exist_ok=True)
compose_preview(f"{OUT}/themes/afterglow/preview.png")
print("built", len(ctrl), "controller,", len(stat), "status,", len(mk), "marks")
