#!/usr/bin/env python3
"""Cut a seamless loop from the 4x render, optional retro post, normalize, verify, encode.
Usage: process.py --variant {sketch1,a,b} [--out DIR] [--title TITLE]"""
import argparse, json, subprocess
import numpy as np, soundfile as sf, pyloudnorm as pyln
from scipy.signal import resample_poly, butter, lfilter

ap = argparse.ArgumentParser()
ap.add_argument('--variant', default='a')
ap.add_argument('--out')
ap.add_argument('--title', default='Lanternlight')
ap.add_argument('--composer', default='Foldcade project')
ap.add_argument('--license', default='GPLv3')
args = ap.parse_args()
V = args.variant
D = args.out or f'out/{V}'
base = f'{D}/foldcade_home_{V}'
SR = 48000
L = 80 * SR
raw, sr = sf.read(f'{D}/raw_4x.wav', dtype='float64')
assert sr == SR and len(raw) >= 4 * L
print('raw render: %.2fs, raw peak %.2f dBFS' % (len(raw) / SR, 20 * np.log10(np.abs(raw).max())))

POST = {
    # variant b: subtle bit-depth reduction (10-bit, 12% wet) then gentle 2nd-order low-pass at 9.5 kHz.
    # Applied to the whole continuous 4x render BEFORE cutting, so the loop seam stays sample-continuous
    # (causal filter state carries across copies exactly as it would in a continuous stream).
    'b': dict(bits=10, wet=0.12, lp_hz=9500),
}
if V in POST:
    p = POST[V]
    pk = np.abs(raw).max()
    x = raw / pk                                   # crush on a full-scale signal, then restore level
    q = 2 ** (p['bits'] - 1)
    crushed = np.round(x * q) / q
    x = (1 - p['wet']) * x + p['wet'] * crushed
    b, a = butter(2, p['lp_hz'] / (SR / 2))
    x = lfilter(b, a, x, axis=0)
    raw = x * pk
    print('post: %d-bit crush %.0f%% wet, %d Hz low-pass' % (p['bits'], p['wet'] * 100, p['lp_hz']))

loop2 = raw[L:2 * L]; loop3 = raw[2 * L:3 * L]
meter = pyln.Meter(SR)
lufs = meter.integrated_loudness(loop2); peak = np.abs(loop2).max()
g_l = 10 ** ((-16 - lufs) / 20); g_p = 10 ** (-3 / 20) / peak; gain = min(g_l, g_p)
print('pre-norm: %.2f LUFS, peak %.2f dBFS; gain %.2f dB (%s-limited)' % (
    lufs, 20 * np.log10(peak), 20 * np.log10(gain), 'loudness' if g_l <= g_p else 'peak'))
loop = loop2 * gain; loop3n = loop3 * gain

def db(x): return float(20 * np.log10(max(x, 1e-12)))
res = {'variant': V, 'length_s': len(loop) / SR, 'integrated_lufs': meter.integrated_loudness(loop),
       'sample_peak_dbfs': db(np.abs(loop).max()),
       'true_peak_dbfs_est': db(np.abs(resample_poly(loop, 4, 1, axis=0)).max()),
       'clipped_samples': int((np.abs(loop) >= 0.999).sum())}
mono = loop.mean(axis=1)
w = SR // 20
rms_db = 20 * np.log10(np.maximum(np.sqrt(np.mean(mono[:len(mono)//w*w].reshape(-1, w) ** 2, axis=1)), 1e-12))
res.update(rms50ms_min_db=float(rms_db.min()), rms50ms_median_db=float(np.median(rms_db)),
           windows_below_minus50db=int((rms_db < -50).sum()))
rms1 = np.sqrt(np.mean(mono[:len(mono)//SR*SR].reshape(-1, SR) ** 2, axis=1))
res['rms1s_range_db'] = db(rms1.max()) - db(rms1.min())
two = np.concatenate([loop, loop])
d = np.abs(np.diff(two, axis=0)).max(axis=1)
res['seam_sample_jump'] = float(d[L - 1]); res['typical_jump_p99'] = float(np.percentile(d, 99))
res['seam_jump_percentile'] = float((d < d[L - 1]).mean() * 100)
w250 = SR // 4
res['seam_250ms_rms_step_db'] = db(np.sqrt(np.mean(loop[:w250] ** 2))) - db(np.sqrt(np.mean(loop[-w250:] ** 2)))
# Real continuous copy2->copy3 transition vs our artificial loop seam, 0.5 s each side
cont = np.concatenate([loop2[-SR // 2:], loop3[:SR // 2]]) * gain
art = np.concatenate([loop[-SR // 2:], loop[:SR // 2]])
res['seam_vs_continuous_max_abs_diff'] = float(np.abs(cont - art).max())
diff = loop - loop3n
res['copy2_vs_copy3_residual_db_rel'] = db(np.sqrt(np.mean(diff ** 2))) - db(np.sqrt(np.mean(loop ** 2)))
for k, v in res.items():
    print(f'{k}: {v:.4f}' if isinstance(v, float) else f'{k}: {v}')

sf.write(f'{base}_loop.wav', loop.astype(np.float32), SR, subtype='FLOAT')
sf.write(f'{base}_loop_x2.wav', two.astype(np.float32), SR, subtype='FLOAT')
def run(*a): subprocess.run(a, check=True, capture_output=True)
meta = [
    '-metadata', f'title={args.title}',
    '-metadata', f'artist={args.composer}',
    '-metadata',
    f'comment=Composition by {args.composer}. Licensed under {args.license} with the Foldcade repository. '
    'Rendered with MuseScore General 0.2 (MIT). Loop: whole file 0-80 s.',
]
run('ffmpeg', '-y', '-i', f'{base}_loop.wav', '-c:a', 'libvorbis', '-q:a', '5', *meta, f'{base}_loop.ogg')
run('ffmpeg', '-y', '-i', f'{base}_loop.wav', '-c:a', 'libmp3lame', '-b:a', '192k', *meta, f'{base}_loop.mp3')
run('ffmpeg', '-y', '-i', f'{base}_loop_x2.wav', '-c:a', 'libmp3lame', '-b:a', '192k', *meta, f'{base}_loop_x2_preview.mp3')
run('ffmpeg', '-y', '-i', f'{base}_loop.ogg', '-f', 'wav', '-c:a', 'pcm_f32le', '/tmp/ogg_dec.wav')
od, _ = sf.read('/tmp/ogg_dec.wav')
res['ogg_decoded_samples'] = len(od); res['ogg_decoded_peak_dbfs'] = db(np.abs(od).max())
print('ogg decoded: %d samples (expected %d), peak %.2f dBFS' % (len(od), L, res['ogg_decoded_peak_dbfs']))
json.dump(res, open(f'{D}/checks.json', 'w'), indent=2)
