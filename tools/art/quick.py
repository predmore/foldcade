import sys, io, cairosvg
from PIL import Image
from art import *
import glyphs, marks
def grid(assets, px=96, cols=12, pad=16):
    rows = (len(assets) + cols - 1) // cols
    im = Image.new("RGB", (cols * (px + pad) + pad, rows * (px + pad) + pad), (0, 0, 0))
    for i, a in enumerate(assets):
        t = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=to_svg(a, px=px).encode()))).convert("RGBA")
        im.paste(t, (pad + (i % cols) * (px + pad), pad + (i // cols) * (px + pad)), t)
    return im
if __name__ == "__main__":
    grid(getattr(marks if sys.argv[1].startswith("marks") else glyphs, sys.argv[1])()).save(sys.argv[2])
