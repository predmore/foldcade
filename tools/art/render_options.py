import cairosvg, io
from PIL import Image, ImageDraw, ImageFont
from art import *
from appicon import CONCEPTS
FONT = "/usr/share/fonts/truetype/sand-box/google/Nunito/Nunito-VariableFont_wght.ttf"
def png(a, px, bg=None):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=to_svg(a, px=px, bg=bg).encode()))).convert("RGBA")
def masked(im, shape):
    m = Image.new("L", im.size, 0); d = ImageDraw.Draw(m); s = im.size[0]
    # launcher shows the inner 72/108 of the adaptive layer
    if shape == "circle": d.ellipse([0, 0, s-1, s-1], fill=255)
    else: d.rounded_rectangle([0, 0, s-1, s-1], radius=int(s*0.3), fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0)); out.paste(im, (0, 0), m); return out
def crop_view(im):
    s = im.size[0]; c = int(s*18/108); return im.crop((c, c, s-c, s-c))
if __name__ == "__main__":
    import sys
    W = 1500; sheet = Image.new("RGB", (W, 520), (0, 0, 0)); dr = ImageDraw.Draw(sheet)
    ft = ImageFont.truetype(FONT, 34); ft.set_variation_by_axes([800]); fs = ImageFont.truetype(FONT, 22)
    dr.text((40, 30), "Foldcade app icon: concept options", font=ft, fill=(244, 241, 234))
    for i, (name, fn) in enumerate(CONCEPTS.items()):
        x0 = 40 + i*480
        full = png(Asset(name, 108, 108, fn(), note=name), 432, bg="#000000")
        v = crop_view(full).resize((300, 300), Image.LANCZOS)
        sheet.paste(masked(v, "circle"), (x0, 110), masked(v, "circle"))
        sm = v.resize((120, 120), Image.LANCZOS)
        sheet.paste(masked(sm, "squircle"), (x0+320, 110), masked(sm, "squircle"))
        tiny = v.resize((48, 48), Image.LANCZOS); sheet.paste(masked(tiny, "circle"), (x0+356, 260), masked(tiny, "circle"))
        mono = png(Asset(name, 108, 108, fn(True)), 432)
        mv = crop_view(mono).resize((120, 120), Image.LANCZOS)
        tint = Image.new("RGBA", mv.size, (30, 46, 40, 255)); tint = masked(tint, "circle")
        glyph = Image.new("RGBA", mv.size, (200, 255, 228, 255)); tint.paste(glyph, (0, 0), mv)
        sheet.paste(tint, (x0+320, 330), masked(tint, "circle"))
        dr.text((x0, 440), name, font=ft, fill=(244, 241, 234))
        dr.text((x0+320, 460), "themed (mono)", font=fs, fill=(154, 149, 140))
    sheet.save(sys.argv[1])
