import io, os, re, glob
import xml.etree.ElementTree as ET
import cairosvg
from PIL import Image, ImageDraw
from art import *
import glyphs, marks
from build import png, font, OUT, RES, mask, view, ctrl, stat, mk, fg, mono

AN = "{http://schemas.android.com/apk/res/android}"
def old_mark(path, color):
    root = ET.parse(path).getroot(); body = []
    for p in root.iter("path"):
        d = p.get(AN + "pathData"); sw = p.get(AN + "strokeWidth", "1.6")
        body.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"/>')
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 24 24">{"".join(body)}</svg>'
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode()))).convert("RGBA")

W = 2000
sheet = Image.new("RGBA", (W, 4200), (0, 0, 0, 255)); dr = ImageDraw.Draw(sheet)
y = 40
def h1(t):
    global y
    dr.text((50, y), t, font=font(40, 800), fill=(244, 241, 234)); y += 64
def cap(t, x, yy, w):
    ft = font(15, 600); t = t.replace("ic_btn_", "").replace("ic_status_", "")
    dr.text((x + w / 2 - dr.textlength(t, font=ft) / 2, yy), t, font=ft, fill=(154, 149, 140))
def row(assets, px, cols, gapx, labels=True):
    global y
    for i, a in enumerate(assets):
        if i and i % cols == 0: y += px + (44 if labels else 20)
        x = 50 + (i % cols) * (px + gapx)
        im = png(a, px); sheet.alpha_composite(im, (x, y))
        if labels: cap(a.name, x - gapx // 2, y + px + 6, px + gapx)
    y += px + (60 if labels else 30)

dr.text((50, y), "Foldcade · Afterglow art kit", font=font(56, 800), fill=(244, 241, 234)); y += 76
dr.text((52, y), "Original art, CC BY-SA 4.0 · true black · soft, smooth glows · Nunito", font=font(26, 500), fill=(154, 149, 140)); y += 70

h1("App icon — A: open clamshell (adaptive + monochrome + legacy)")
full = png(Asset("x", 108, 108, fg.layers), 1296, bg="#000000"); v = view(full)
big = v.resize((340, 340), Image.LANCZOS)
sheet.alpha_composite(mask(big, "circle"), (50, y)); sheet.alpha_composite(mask(big.resize((240, 240), Image.LANCZOS), "square"), (420, y + 50))
m = view(png(Asset("m", 108, 108, mono.layers), 864)).resize((180, 180), Image.LANCZOS)
t = Image.new("RGBA", m.size, (24, 44, 38, 255)); t.paste(Image.new("RGBA", m.size, (200, 255, 228, 255)), (0, 0), m)
sheet.alpha_composite(mask(t, "circle"), (700, y + 80))
play = Image.open(f"{OUT}/play/ic_launcher-playstore.png").convert("RGBA").resize((260, 260), Image.LANCZOS)
sheet.alpha_composite(play, (920, y + 40))
xx = 1220
for dens in ("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi"):
    im = Image.open(f"{RES}/mipmap-{dens}/ic_launcher_round.png").convert("RGBA")
    sheet.alpha_composite(im, (xx, y + 300 - im.size[1] - 80)); xx += im.size[0] + 24
for txt, x in (("adaptive · circle", 120), ("squircle", 500), ("themed", 750), ("Play 512", 1000), ("legacy mipmaps", 1400)):
    dr.text((x, y + 350), txt, font=font(18, 600), fill=(154, 149, 140))
y += 410

h1("Controller glyphs — 24dp grid, glossy cap; filled = pressed/focused")
import re as _re
def _diamond_lit(n):
    return bool(_re.match(
        r"ic_btn_face_diamond_(?:n_[xyab]|x_[yxba]|blank_(?:top|left|right|bottom)|[yab])(?:_filled)?$", n))
outline = [a for a in ctrl if not a.name.endswith("_filled") and "dpad_" not in a.name
           and "press" not in a.name and not _diamond_lit(a.name)]
filled = [a for a in ctrl if a.name.endswith("_filled") and not _diamond_lit(a.name)]
extra = [a for a in ctrl if "dpad_" in a.name or "press" in a.name or _diamond_lit(a.name)]
row(outline, 96, 15, braw := 30) if False else row(outline, 96, 15, 30)
row(filled, 96, 15, 30); row(extra, 96, 15, 30)

h1("Status-island icons — 24dp")
row(stat, 72, 13, 72)

h1("Status islands in context (L1 / R1 chips lead each island)")
def island(x, items, w, yy):
    dr.rounded_rectangle([x, yy, x + w, yy + 76], radius=38, fill=(0, 0, 0, 255), outline=(70, 70, 76, 255), width=2)
    cx = x + 20
    for it in items:
        if isinstance(it, str):
            ft = font(34, 800); dr.text((cx, yy + 16), it, font=ft, fill=(244, 241, 234)); cx += dr.textlength(it, font=ft) + 16
        else:
            sheet.alpha_composite(png(it, 56), (int(cx), yy + 10)); cx += 64
A = {a.name: a for a in ctrl + stat}
island(50, [A["ic_btn_l1_filled"], A["ic_status_tools"], A["ic_status_settings"], A["ic_status_volume_2"]], 300, y)
island(420, [A["ic_btn_r1"], A["ic_status_bell_3"], A["ic_status_wifi_3"], A["ic_status_bluetooth"], A["ic_status_battery_60_charging"], "9:41"], 500, y)
y += 130

h1("Platform marks — current line glyphs (top) vs proposed illustrated (bottom, 48dp)")
accents = ["#FF5C7A", "#FF7A45", "#FFB547", "#3EE6C0", "#B98CFF", "#4CC9FF"]
for i, n in enumerate(["clamshell", "slim", "handheld", "cartridge", "disc", "cloud"]):
    im = old_mark(f"/workspace/foldcade-repo/app/src/main/res/drawable/mark_{n}.xml", accents[i]).resize((140, 140), Image.LANCZOS)
    sheet.alpha_composite(im, (60 + i * 300, y))
y += 160
row(mk, 200, 6, 100)

h1("Sprites — soft radial glow, ribbon hot spot, focus-glow nine-patch (dithered, no banding)")
x = 50
for p in ["glow_soft_256", "glow_soft_256_teal", "glow_soft_256_amber", "glow_soft_256_rose", "glow_soft_256_violet", "glow_soft_256_blue"]:
    im = Image.open(f"{OUT}/themes/afterglow/sprites/{p}.png").convert("RGBA").resize((220, 220), Image.LANCZOS)
    sheet.alpha_composite(im, (x, y)); cap(p, x, y + 226, 220); x += 250
y += 270; x = 50
for p in ["ribbon_hotspot", "ribbon_hotspot_teal", "ribbon_hotspot_amber"]:
    im = Image.open(f"{OUT}/themes/afterglow/sprites/{p}.png").convert("RGBA").resize((384, 96), Image.LANCZOS)
    sheet.alpha_composite(im, (x, y)); cap(p, x, y + 104, 384); x += 420
for p in ["focus_glow.9", "focus_glow_mint.9"]:
    im = Image.open(f"{RES}/drawable-nodpi/{p}.png").convert("RGBA")
    sheet.alpha_composite(im, (x, y - 40)); cap(p, x, y + 170, 202); x += 240
y += 230

h1("Theme preview (themes/afterglow/preview.png)")
pv = Image.open(f"{OUT}/themes/afterglow/preview.png").convert("RGBA").resize((1280, 720))
sheet.alpha_composite(pv, (50, y)); y += 760
sheet = sheet.crop((0, 0, W, y)).convert("RGB")
sheet.save(f"{OUT}/contact-sheet.png", optimize=True)
print(sheet.size)
