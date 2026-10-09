#!/usr/bin/env python3
"""Cut a seamless loop from the 4x render, optional retro post, normalize, verify, encode.
Usage: process.py --variant {sketch1,a,b} [--out DIR] [--title TITLE]"""
import argparse, json, subprocess
import numpy as np, soundfile as sf, pyloudnorm as pyln
from scipy.ndimage import maximum_filter1d
from scipy.signal import resample_poly, butter, lfilter

# Integrated loudness for the shipped loop, and the true-peak ceiling of the
# encoded file. The wav limiter sits 0.8 dB under that ceiling so Vorbis does
# not push the packaged true peak back over -1 dBTP.
TARGET_LUFS = -12.0
TRUE_PEAK_DBTP = -1.0
LIMIT_DBTP = -1.8


def true_peak_db(audio, sr):
    peak = float(np.abs(resample_poly(audio, 4, 1, axis=0)).max())
    return 20.0 * np.log10(max(peak, 1e-12))


def limit_true_peak(audio, sr, ceiling_dbtp, lookahead_s=0.005, release_s=0.05):
    """4x oversampled lookahead limiter. The ceiling is dBTP."""
    ceiling = 10 ** (ceiling_dbtp / 20.0)
    up = resample_poly(audio, 4, 1, axis=0)
    mag = np.max(np.abs(up), axis=1)
    hop = sr * 4
    lookahead = max(1, int(round(lookahead_s * hop)))
    env = maximum_filter1d(mag, size=lookahead, mode='nearest', origin=-(lookahead // 2))
    desired = np.ones(env.shape, dtype=np.float64)
    hot = env > ceiling
    desired[hot] = ceiling / env[hot]
    coef = np.exp(-1.0 / (release_s * hop))
    keep = 1.0 - coef
    gain = np.empty(len(desired), dtype=np.float64)
    level = 1.0
    for index in range(len(desired)):
        target = desired[index]
        if target < level:
            level = target
        else:
            level = coef * level + keep * target
        gain[index] = level
    limited = up * gain[:, None]
    down = resample_poly(limited, 1, 4, axis=0)
    count = len(audio)
    if len(down) >= count:
        return down[:count]
    pad = np.zeros((count - len(down), audio.shape[1]), dtype=np.float64)
    return np.concatenate([down, pad], axis=0)


def master_loudness(audio, sr, meter, target_lufs, ceiling_dbtp, loop_start, loop_len):
    """Gain the continuous span to the integrated target, then true-peak limit it."""
    out = np.asarray(audio, dtype=np.float64).copy()
    for step in range(4):
        region = out[loop_start:loop_start + loop_len]
        loud = float(meter.integrated_loudness(region))
        delta = target_lufs - loud
        peak = true_peak_db(region, sr)
        print('master pass %d: %.3f LUFS, true peak %.3f dBTP, delta %+.2f dB' % (
            step, loud, peak, delta))
        if abs(delta) <= 0.25 and peak <= ceiling_dbtp:
            return out
        if abs(delta) > 0.05:
            out *= 10 ** (delta / 20.0)
        out = limit_true_peak(out, sr, ceiling_dbtp)
    return out

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

loop2 = raw[L:2 * L]
meter = pyln.Meter(SR)
lufs = meter.integrated_loudness(loop2)
peak = np.abs(loop2).max()
print('pre-norm: %.2f LUFS, peak %.2f dBFS; target %.1f LUFS, true peak %.1f dBTP' % (
    lufs, 20 * np.log10(peak), TARGET_LUFS, TRUE_PEAK_DBTP))
# Master from one second before copy 2 through copy 3. The warmup lets the
# limiter settle so the loop seam matches the continuous copy2->copy3 join.
warm = SR
span = raw[L - warm:3 * L]
mastered = master_loudness(span, SR, meter, TARGET_LUFS, LIMIT_DBTP, warm, L)
loop = mastered[warm:warm + L]
loop3n = mastered[warm + L:warm + 2 * L]

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
cont = np.concatenate([loop[-SR // 2:], loop3n[:SR // 2]])
art = np.concatenate([loop[-SR // 2:], loop[:SR // 2]])
res['seam_vs_continuous_max_abs_diff'] = float(np.abs(cont - art).max())
diff = loop - loop3n
res['copy2_vs_copy3_residual_db_rel'] = db(np.sqrt(np.mean(diff ** 2))) - db(np.sqrt(np.mean(loop ** 2)))
for k, v in res.items():
    print(f'{k}: {v:.4f}' if isinstance(v, float) else f'{k}: {v}')

def run(*a): subprocess.run(a, check=True, capture_output=True)
meta = [
    '-metadata', f'title={args.title}',
    '-metadata', f'artist={args.composer}',
    '-metadata',
    f'comment=Composition by {args.composer}. Licensed under {args.license} with the Foldcade repository. '
    'Rendered with MuseScore General 0.2 (MIT). Loop: whole file 0-80 s.',
]
def encode_ogg():
    run('ffmpeg', '-y', '-i', f'{base}_loop.wav', '-c:a', 'libvorbis', '-q:a', '5', *meta, f'{base}_loop.ogg')
    run('ffmpeg', '-y', '-i', f'{base}_loop.ogg', '-f', 'wav', '-c:a', 'pcm_f32le', '/tmp/ogg_dec.wav')
    decoded, _ = sf.read('/tmp/ogg_dec.wav')
    return decoded

# Vorbis can raise the peak. Trim the mastered loop until the packaged file
# is at or below the -1 dBTP ceiling, then measure that file.
for _ in range(4):
    sf.write(f'{base}_loop.wav', loop.astype(np.float32), SR, subtype='FLOAT')
    decoded = encode_ogg()
    packaged_peak = true_peak_db(decoded, SR)
    print('ogg true peak %.3f dBTP' % packaged_peak)
    if packaged_peak <= TRUE_PEAK_DBTP:
        break
    trim = TRUE_PEAK_DBTP - 0.05 - packaged_peak
    gain = 10 ** (trim / 20.0)
    loop *= gain
    loop3n *= gain
    print('ogg exceeded -1 dBTP; trimmed %.2f dB' % trim)
else:
    sf.write(f'{base}_loop.wav', loop.astype(np.float32), SR, subtype='FLOAT')
    decoded = encode_ogg()

res['integrated_lufs'] = float(meter.integrated_loudness(loop))
res['sample_peak_dbfs'] = db(np.abs(loop).max())
res['true_peak_dbfs_est'] = true_peak_db(loop, SR)
res['clipped_samples'] = int((np.abs(loop) >= 0.999).sum())
res['ogg_decoded_samples'] = len(decoded)
res['ogg_decoded_peak_dbfs'] = db(np.abs(decoded).max())
res['ogg_true_peak_dbtp'] = true_peak_db(decoded, SR)
print('ogg decoded: %d samples (expected %d), peak %.2f dBFS, true peak %.2f dBTP' % (
    len(decoded), L, res['ogg_decoded_peak_dbfs'], res['ogg_true_peak_dbtp']))
print('final: %.3f LUFS, wav true peak %.3f dBTP' % (res['integrated_lufs'], res['true_peak_dbfs_est']))
two = np.concatenate([loop, loop])
sf.write(f'{base}_loop_x2.wav', two.astype(np.float32), SR, subtype='FLOAT')
run('ffmpeg', '-y', '-i', f'{base}_loop.wav', '-c:a', 'libmp3lame', '-b:a', '192k', *meta, f'{base}_loop.mp3')
run('ffmpeg', '-y', '-i', f'{base}_loop_x2.wav', '-c:a', 'libmp3lame', '-b:a', '192k', *meta, f'{base}_loop_x2_preview.mp3')
json.dump(res, open(f'{D}/checks.json', 'w'), indent=2)
