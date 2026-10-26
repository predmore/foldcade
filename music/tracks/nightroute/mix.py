#!/usr/bin/env python3
"""Night Route's chip voices, mixed into fluidsynth's render of the piano and pad.

render.sh runs this after fluidsynth and before process.py. It reads <out>/raw_4x.wav,
synthesizes the chip parts from compose.score(), and writes raw_4x.wav back.

Each chip part is rendered for one loop with its note tails wrapped to the start, tiled
four times, then sent through its filters, echo, and reverb as one continuous stream.
So copies 3 and 4 are identical and the loop seam matches the continuous render.

Every voice starts softly: nothing attacks faster than 15 ms, and the piano and pad lose
their top before the chip voices join them. Levels are set by loudness, not peak: each part
sits a fixed number of LU against the piano and pad.

Usage:  mix.py --out DIR
"""
import argparse, os, sys
import numpy as np, soundfile as sf, pyloudnorm as pyln
from scipy.signal import butter, sosfilt, resample_poly, oaconvolve

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import compose  # noqa: E402

SR = 48000
SAMPLES_PER_BEAT = SR * 60 // compose.BPM
assert SAMPLES_PER_BEAT * compose.BPM == SR * 60
L = compose.LOOP_BEATS * SAMPLES_PER_BEAT

# Loudness of each part against the piano and pad, in LU, and its pan (-1 left, 1 right).
LEVELS = {
    'chippad': (-6.0, 0.0),
    'bass': (-9.0, 0.0),
    'arp': (-9.0, 0.35),
    'motif_a': (-5.0, -0.3),
    'motif_b': (-7.0, 0.3),
    'bell': (-13.0, -0.15),
}
# Low-pass per part, Hz.
LOWPASS = {'chippad': 3000, 'bass': 1200, 'arp': 3000, 'motif_a': 3500, 'motif_b': 2400, 'bell': 5000}
# Echo (wet level) and reverb send per part.
ECHO = {'arp': 0.25, 'motif_a': 0.35, 'motif_b': 0.35, 'bell': 0.3}
REVERB = {'chippad': 0.2, 'bass': 0.0, 'arp': 0.35, 'motif_a': 0.4, 'motif_b': 0.4, 'bell': 0.6}
# The piano and pad from fluidsynth lose everything above this before the mix, Hz.
KEYS_LOWPASS = 4000
# Bus EQ for small speakers: a low shelf below 250 Hz and a broad lift around 1 kHz, in dB.
SHELF_DB = -4.0
PRESENCE_DB = 3.0


def sample(beat):
    return int(round(compose.t(beat) * SAMPLES_PER_BEAT / compose.TPB))


def hz(pitch):
    return 440.0 * 2 ** ((pitch - 69) / 12)


def envelope(n, attack_s, decay_s, sustain, release_s, held):
    """Raised-cosine attack, exponential decay to sustain while held, then a release to silence."""
    i = np.arange(n) / SR
    rise = 0.5 - 0.5 * np.cos(np.pi * np.clip(i / attack_s, 0, 1))
    env = np.where(i < attack_s, rise, sustain + (1 - sustain) * np.exp(-(i - attack_s) / decay_s))
    held_s = held / SR
    if held_s > attack_s:
        at_release = sustain + (1 - sustain) * np.exp(-(held_s - attack_s) / decay_s)
    else:
        at_release = 0.5 - 0.5 * np.cos(np.pi * held_s / attack_s)
    after = i >= held_s
    env[after] = at_release * np.exp(-(i[after] - held_s) / (release_s / 5))
    return env


def blep(phase, dt):
    out = np.zeros_like(phase)
    lo = phase < dt
    x = phase[lo] / dt
    out[lo] = 2 * x - x * x - 1
    hi = phase > 1 - dt
    x = (phase[hi] - 1) / dt
    out[hi] = x * x + 2 * x + 1
    return out


def phase_of(freq, n, rate, vibrato_hz=0.0, vibrato_depth=0.0, vibrato_delay_s=0.0):
    """Running phase, with a vibrato that eases in after the delay."""
    i = np.arange(n) / rate
    depth = vibrato_depth * np.clip((i - vibrato_delay_s) / 0.6, 0, 1)
    inst = freq * (1 + depth * np.sin(2 * np.pi * vibrato_hz * i))
    return np.cumsum(inst) / rate % 1.0


def pulse(freq, n, duty, **vib):
    """Band-limited pulse (polyBLEP), zero mean."""
    phase = phase_of(freq, n, SR, **vib)
    dt = freq / SR
    naive = np.where(phase < duty, 1.0, -1.0)
    return naive + blep(phase, dt) - blep((phase - duty) % 1.0, dt) - (2 * duty - 1)


def nes_triangle(freq, n, **vib):
    """The NES triangle: 32 steps of 4 bits. Rendered 4x over and brought down to keep it clean."""
    up = 4
    phase = phase_of(freq, n * up, SR * up, **vib)
    step = np.floor(phase * 32).astype(int)
    level = np.where(step < 16, 15 - step, step - 16)
    return resample_poly(level / 7.5 - 1.0, 1, up)[:n]


def render_part(notes, voice):
    """One loop of a part, mono, tails wrapped to the start."""
    out = np.zeros(L + SR * 12)
    fade = int(0.005 * SR)
    for (beat, beats, pitch, vel) in notes:
        start = sample(beat) % L
        held = sample(beat + beats) - sample(beat)
        wave = voice(pitch, held, vel / 127.0)
        wave[-fade:] *= np.linspace(1, 0, fade)
        out[start:start + len(wave)] += wave
    loop = out[:L].copy()
    tail = out[L:]
    loop[:len(tail)] += tail
    return loop


def chippad_voice(detune_cents):
    ratio = 2 ** (detune_cents / 1200)

    def voice(pitch, held, amp):
        n = held + int(2.5 * SR)
        env = envelope(n, 1.6, 8.0, 0.8, 2.0, held)
        return amp * env * nes_triangle(hz(pitch) * ratio, n)
    return voice


def bass_voice(pitch, held, amp):
    n = held + int(1.5 * SR)
    f = hz(pitch)
    i = np.arange(n) / SR
    env = envelope(n, 0.12, 4.0, 0.6, 1.0, held)
    return amp * env * (0.7 * nes_triangle(f, n) + 0.5 * np.sin(2 * np.pi * f * i))


def arp_voice(pitch, held, amp):
    n = held + int(0.6 * SR)
    env = envelope(n, 0.018, 0.35, 0.45, 0.45, held)
    return amp * env * nes_triangle(hz(pitch), n)


def motif_a_voice(pitch, held, amp):
    n = held + int(0.8 * SR)
    env = envelope(n, 0.06, 1.2, 0.7, 0.6, held)
    vib = dict(vibrato_hz=4.8, vibrato_depth=0.005, vibrato_delay_s=0.35)
    return amp * env * nes_triangle(hz(pitch), n, **vib)


def motif_b_voice(pitch, held, amp):
    n = held + int(0.8 * SR)
    env = envelope(n, 0.05, 0.9, 0.6, 0.6, held)
    vib = dict(vibrato_hz=4.2, vibrato_depth=0.004, vibrato_delay_s=0.4)
    return amp * env * pulse(hz(pitch), n, 0.25, **vib)


def bell_voice(pitch, held, amp):
    n = held + int(1.0 * SR)
    f = hz(pitch)
    i = np.arange(n) / SR
    env = envelope(n, 0.02, 1.8, 0.0, 0.8, held)
    return amp * env * (np.sin(2 * np.pi * f * i) + 0.25 * np.sin(2 * np.pi * 2 * f * i) * np.exp(-i / 0.6))


def tile(loop, total):
    reps = -(-total // len(loop))
    return np.tile(loop, reps)[:total]


def echo(x, delay_beats, feedback, lowpass_hz):
    """SNES-style echo: each repeat darker than the last. Returns the wet signal only."""
    d = int(round(delay_beats * SAMPLES_PER_BEAT))
    sos = butter(1, lowpass_hz, fs=SR, output='sos')
    zi = np.zeros((sos.shape[0], 2))
    wet = np.zeros_like(x)
    for s in range(d, len(x), d):
        e = min(s + d, len(x))
        src = x[s - d:e - d] + feedback * wet[s - d:e - d]
        block, zi = sosfilt(sos, src, zi=zi)
        wet[s:e] = block
    return wet


def reverb_ir(seconds, seed):
    """Stereo noise tail. Highs die faster than lows, like a soft room."""
    n = int(seconds * SR)
    i = np.arange(n) / SR
    rng = np.random.default_rng(seed)
    bands = [(butter(2, 700, fs=SR, output='sos'), 2.6),
             (butter(2, [700, 3000], btype='bandpass', fs=SR, output='sos'), 1.8),
             (butter(2, 3000, btype='highpass', fs=SR, output='sos'), 0.7)]
    ir = np.zeros((n, 2))
    for ch in range(2):
        raw = rng.standard_normal(n)
        for sos, rt in bands:
            ir[:, ch] += sosfilt(sos, raw) * np.exp(-6.9 * i / rt)
    ir *= np.minimum(i / 0.03, 1)[:, None]
    return ir / np.sqrt(np.sum(ir ** 2, axis=0))


def low_shelf(gain_db, f0):
    """RBJ low shelf, S = 1, as one second-order section."""
    a = 10 ** (gain_db / 40)
    w = 2 * np.pi * f0 / SR
    alpha = np.sin(w) / 2 * np.sqrt(2)
    c = np.cos(w)
    k = 2 * np.sqrt(a) * alpha
    b0, b1, b2 = a * ((a + 1) - (a - 1) * c + k), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - k)
    a0, a1, a2 = (a + 1) + (a - 1) * c + k, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - k
    return np.array([[b0 / a0, b1 / a0, b2 / a0, 1.0, a1 / a0, a2 / a0]])


def peak_eq(gain_db, f0, q):
    """RBJ peaking EQ as one second-order section."""
    a = 10 ** (gain_db / 40)
    w = 2 * np.pi * f0 / SR
    alpha = np.sin(w) / (2 * q)
    c = np.cos(w)
    a0 = 1 + alpha / a
    return np.array([[(1 + alpha * a) / a0, -2 * c / a0, (1 - alpha * a) / a0, 1.0, -2 * c / a0, (1 - alpha / a) / a0]])


def pan(x, position):
    angle = (position + 1) * np.pi / 4
    return np.stack([x * np.cos(angle), x * np.sin(angle)], axis=1)


def loudness(meter, stereo):
    return float(meter.integrated_loudness(stereo[L:2 * L]))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    args = ap.parse_args()
    raw_path = os.path.join(args.out, 'raw_4x.wav')
    raw, sr = sf.read(raw_path, dtype='float64')
    assert sr == SR and raw.ndim == 2 and len(raw) >= 4 * L
    total = len(raw)
    raw = sosfilt(butter(2, KEYS_LOWPASS, fs=SR, output='sos'), raw, axis=0)
    parts = compose.score()
    meter = pyln.Meter(SR)
    ref = loudness(meter, raw)
    print('mix: piano and pad %.2f LUFS' % ref)

    ir = reverb_ir(3.2, 7)
    bus = raw.copy()
    send = np.zeros_like(raw)
    voices = {'bass': bass_voice, 'arp': arp_voice, 'motif_a': motif_a_voice,
              'motif_b': motif_b_voice, 'bell': bell_voice}
    for name, (rel, position) in LEVELS.items():
        lp = butter(2, LOWPASS[name], fs=SR, output='sos')
        if name == 'chippad':
            # Two triangles a few cents apart, one each side, for a slow shimmer.
            left = sosfilt(lp, tile(render_part(parts[name], chippad_voice(-4)), total))
            right = sosfilt(lp, tile(render_part(parts[name], chippad_voice(4)), total))
            stereo = np.stack([left, right], axis=1)
        else:
            x = sosfilt(lp, tile(render_part(parts[name], voices[name]), total))
            stereo = pan(x, position)
            if name in ECHO:
                # The left repeats on the dotted quarter, the right on the half note.
                wet = np.stack([echo(x, 1.5, 0.4, 1500), echo(x, 2.0, 0.35, 1300)], axis=1)
                stereo = stereo + ECHO[name] * wet
        gain = 10 ** ((ref + rel - loudness(meter, stereo)) / 20)
        stereo *= gain
        print('mix: %-7s %+.1f LU, gain %+.1f dB' % (name, rel, 20 * np.log10(gain)))
        bus += stereo
        send += stereo * REVERB[name]

    wet = np.stack([oaconvolve(send[:, ch], ir[:, ch])[:total] for ch in range(2)], axis=1)
    bus += 0.6 * wet
    # No sub rumble, no top octave. The shelf thins the low mids that a handheld's
    # speakers cannot play, so mastering to the same LUFS sounds as loud there too.
    bus = sosfilt(butter(2, 35, btype='highpass', fs=SR, output='sos'), bus, axis=0)
    bus = sosfilt(low_shelf(SHELF_DB, 250), bus, axis=0)
    bus = sosfilt(peak_eq(PRESENCE_DB, 1000, 0.7), bus, axis=0)
    bus = sosfilt(butter(2, 9000, fs=SR, output='sos'), bus, axis=0)
    peak = np.abs(bus).max()
    if peak > 0.9:
        bus *= 0.9 / peak
    sf.write(raw_path, bus.astype(np.float32), SR, subtype='FLOAT')
    print('mix: wrote %s, %.2f LUFS before mastering' % (raw_path, loudness(meter, bus)))


if __name__ == '__main__':
    main()
