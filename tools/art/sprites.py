"""Raster sprites: smooth-falloff glows, dithered to 8-bit so there is no banding."""
import numpy as np
from PIL import Image

RNG = np.random.default_rng(7)

def falloff(u):
    """Same curve as the vector halos: gaussian body, forced to exactly 0 at u=1, C1-smooth."""
    u = np.clip(u, 0, 1)
    return np.exp(-4.2 * u * u) * (1 - u) ** 1.5

def to8(x):
    """Float [0,1] -> uint8 with triangular-PDF dither (kills 8-bit banding in soft gradients)."""
    n = (RNG.random(x.shape) - RNG.random(x.shape))
    return np.clip(np.round(x * 255 + n), 0, 255).astype(np.uint8)

def rgba(alpha, color=(255, 255, 255)):
    h, w = alpha.shape
    a = to8(alpha)
    a[alpha <= 0] = 0
    out = np.zeros((h, w, 4), np.uint8)
    out[..., 0], out[..., 1], out[..., 2] = color
    out[..., 3] = a
    for i, c in enumerate(color):
        out[..., i] = np.where(a > 0, c, 0)
    return Image.fromarray(out, "RGBA")

def radial_glow(size):
    c = (size - 1) / 2
    y, x = np.mgrid[0:size, 0:size]
    u = np.hypot(x - c, y - c) / (size / 2)
    return falloff(u)

def hotspot(w=256, h=64):
    cx, cy = (w - 1) / 2, (h - 1) / 2
    y, x = np.mgrid[0:h, 0:w]
    u = np.hypot((x - cx) / (w / 2), (y - cy) / (h / 2))
    core = np.exp(-((x - cx) / (w * 0.10)) ** 2 - ((y - cy) / (h * 0.10)) ** 2) * 0.35
    return np.clip(falloff(u) + core * (u < 1), 0, 1)

def rrect_sdf(x, y, cx, cy, hw, hh, r):
    qx = np.abs(x - cx) - (hw - r); qy = np.abs(y - cy) - (hh - r)
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r

def focus_ninepatch(inner=120, radius=34, margin=40, color=(255, 255, 255), peak=0.85):
    """Outer glow ring around a rounded tile; hollow inside so the tile paints on top."""
    S = inner + 2 * margin
    y, x = np.mgrid[0:S, 0:S] + 0.5
    d = rrect_sdf(x, y, S / 2, S / 2, inner / 2, inner / 2, radius)
    a = np.where(d > 0, peak * falloff(d / margin), 0.0)
    # 1.5px soft inner lip so the glow meets the tile edge without a seam
    a = np.where((d <= 0) & (d > -1.5), peak * (1 + d / 1.5), a)
    img = np.array(rgba(a, color))
    out = np.zeros((S + 2, S + 2, 4), np.uint8)
    out[1:-1, 1:-1] = img
    blk = np.array([0, 0, 0, 255], np.uint8)
    s0, s1 = margin + radius + 1, S - margin - radius + 1        # stretch: straight edge segment
    out[0, s0:s1] = blk; out[s0:s1, 0] = blk
    p0, p1 = margin + 1, S - margin + 1                          # padding: the tile's content box
    out[-1, p0:p1] = blk; out[p0:p1, -1] = blk
    return Image.fromarray(out, "RGBA")

def blur(img, sigma):
    """Separable-free FFT gaussian on a float image."""
    h, w = img.shape
    fy = np.fft.fftfreq(h)[:, None]; fx = np.fft.fftfreq(w)[None, :]
    g = np.exp(-2 * (np.pi * sigma) ** 2 * (fx ** 2 + fy ** 2))
    return np.real(np.fft.ifft2(np.fft.fft2(img) * g))
