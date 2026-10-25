#!/usr/bin/env python3
"""Build every Foldcade art asset. Script and original outputs are MIT licensed."""
import io, os, shutil, math
import numpy as np, cairosvg
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from art import *
from palette import *
import icons, symbols, sprites

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = f"{OUT}/res"; SVG = f"{OUT}/svg"; PREV = f"{OUT}/previews"
FONT = "/usr/share/fonts/truetype/sand-box/google/Nunito/Nunito-VariableFont_wght.ttf"
def font(sz, w=700):
    ft = ImageFont.truetype(FONT, sz); ft.set_variation_by_axes([w]); return ft

def png(a, px, bg=None):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=to_svg(a, px=px, bg=bg).encode()))).convert("RGBA")

def write(a, sub):
    os.makedirs(f"{RES}/drawable", exist_ok=True); os.makedirs(f"{SVG}/{sub}", exist_ok=True)
    destination = f"{OUT}/themes/afterglow/icons" if a.name.startswith(("mark_", "ic_btn_", "ic_status_", "ic_badge_")) else f"{RES}/drawable"
    os.makedirs(destination, exist_ok=True)
    open(f"{destination}/{a.name}.xml", "w").write(to_vd(a))
    open(f"{SVG}/{sub}/{a.name}.svg", "w").write(to_svg(a))

for d in (RES, SVG, f"{OUT}/play", f"{OUT}/themes"):
    shutil.rmtree(d, ignore_errors=True)

# Theme icons: symbols, marks, and collections (tools/art/icons.py, docs/iconography.md)
icons.main()
ctrl, stat = symbols.controller_assets(), symbols.status_assets()
made = {a.name: a for a, _, _ in icons.assets()}
mk = [made[f"mark_{n}"] for n in ("nintendo_3ds", "game_boy", "snes", "library", "playstation_2", "cloud")]

# The app icon has its own script: tools/art/appicon.py

# ---------------- preview glow ----------------
# Afterglow draws its glows natively and its scenes in AGSL, so the pack ships no sprites or
# effect shaders: a file there that nothing reads would look like a hook a theme could use.
ACC = {"white": (255, 255, 255), "teal": (0x3E, 0xE6, 0xC0), "amber": (0xFF, 0xB5, 0x47), "rose": (0xFF, 0x5C, 0x7A),
       "violet": (0xB9, 0x8C, 0xFF), "blue": (0x4C, 0xC9, 0xFF), "mint": (0xC8, 0xFF, 0xE4)}
FOCUS_PATCH = sprites.focus_ninepatch(color=ACC["mint"])

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
    glowpatch = FOCUS_PATCH.crop((1, 1, 201, 201))
    for i, a in enumerate(mk):
        x = int(x0 + i * (ts2 + gap2)); y = y0
        if i == 1:
            g = glowpatch.resize((int(ts2 * 200 / 120), int(ts2 * 200 / 120)), Image.LANCZOS)
            base.alpha_composite(g, (x - int(ts2 * 40 / 120), y - int(ts2 * 40 / 120)))
        dr.rounded_rectangle([x, y, x + ts2, y + ts2], radius=int(ts2 * 0.28), fill=(0, 0, 0, 255),
                             outline=((200, 255, 228, 255) if i == 1 else (60, 62, 70, 255)), width=(4 if i == 1 else 2))
        m = png(a, int(ts2 * 0.72)); base.alpha_composite(m, (x + int(ts2 * 0.14), y + int(ts2 * 0.14)))
        lab = a.name.replace("mark_", "").replace("_", " ").title()
        ft = font(24, 700); dr.text((x + ts2 / 2 - dr.textlength(lab, font=ft) / 2, y + ts2 + 14), lab, font=ft, fill=(244, 241, 234))
    base.convert("RGB").save(path)
os.makedirs(f"{OUT}/themes/afterglow", exist_ok=True)
compose_preview(f"{OUT}/themes/afterglow/preview.png")
print("built", len(made), "icons")
