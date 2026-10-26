#!/usr/bin/env python3
"""Night Route, Foldcade's second home loop. Original composition, written in code.

D minor, 60 bpm, 180 beats = exactly 180.000 s. Built to stay in the background.
Nothing keeps a beat. Each layer runs on its own clock, and the clocks only line up
once a loop, so no stretch of it comes round again inside the loop:

  harmony   nine different chords of 16 to 24 beats, through-composed
  motif A   triangle, every 36 beats, stepped through the scale each time
  motif B   soft pulse, every 45 beats, answering lower down
  bell      every 60 beats
  arpeggio  triangle, fades in and out over 90 beats

The motifs and bell use the D minor pentatonic (D F G A C), which sits well over every
chord, so wherever two clocks cross the notes agree.

The piano and pad render through fluidsynth from the MIDI this script writes. The chip
voices are synthesized by mix.py from the same score.

Usage:  compose.py [--variant a] [--out DIR]
Writes <out>/foldcade_home_a_loop.mid      (one loop, editable, loop markers)
       <out>/foldcade_home_a_render4x.mid  (loop x4 + tail, for rendering only)
"""
import argparse, itertools, math, os, random
import mido

TITLE = 'Night Route'
TPB = 480
BPM = 60  # 1000000 us per beat, so the loop is exactly 180 s
LOOP_BEATS = 180
LOOP_TICKS = LOOP_BEATS * TPB

# SoundFont channels
KEYS, PAD = 0, 1

# ---------------------------------------------------------------- harmony
# name: (root pitch class, upper-structure intervals above the root, bass pitch class)
CHORDS = {
    'Dm9':       (2, [3, 7, 10, 14], 2),
    'Bbmaj9':    (10, [4, 7, 11, 14], 10),
    'Gm11':      (7, [3, 7, 10, 17], 7),
    'F6/9':      (5, [4, 7, 9, 14], 5),
    'Dm11/C':    (2, [3, 7, 10, 17], 0),
    'Ebmaj9#11': (3, [4, 11, 14, 18], 3),
    'Bb6/9':     (10, [4, 7, 9, 14], 10),
    'Gm9':       (7, [3, 7, 10, 14], 7),
    'C9sus4':    (0, [5, 7, 10, 14], 0),
}

# (chord, beats). Nine different chords at uneven lengths, so no stretch of the
# harmony comes back and the changes never settle into a meter.
HARMONY = [
    ('Dm9', 24), ('Bbmaj9', 20), ('Gm11', 16), ('F6/9', 20), ('Dm11/C', 24),
    ('Ebmaj9#11', 16), ('Bb6/9', 20), ('Gm9', 24), ('C9sus4', 16),
]
assert sum(beats for _, beats in HARMONY) == LOOP_BEATS


def chord_spans():
    start = 0
    for name, beats in HARMONY:
        yield start, beats, name
        start += beats


def chord_at(beat):
    beat %= LOOP_BEATS
    for start, beats, name in chord_spans():
        if start <= beat < start + beats:
            return name
    raise AssertionError(beat)


# D minor pentatonic
SCALE_PCS = (2, 5, 7, 9, 0)


def scale_step(pitch, steps):
    """Move a scale tone by whole steps of the pentatonic."""
    tones = [p for p in range(24, 108) if p % 12 in SCALE_PCS]
    return tones[tones.index(pitch) + steps]


# Clocks. Each: cycle and offset in beats, notes as (beat, beats, pitch), and how many
# scale steps each pass moves the phrase. The cycles divide the loop.
MOTIF_A = dict(cycle=36, offset=4, shifts=[0, 1, -1, 2, 0],
               notes=[(0, 1.5, 69), (1.5, 0.5, 72), (2, 3, 74), (6, 1, 72), (7, 4, 69)])
MOTIF_B = dict(cycle=45, offset=26, shifts=[0, 2, 1, -1],
               notes=[(0, 1, 67), (1, 1, 65), (2, 2.5, 62), (5, 1, 65), (6, 3.5, 67)])
BELL = dict(cycle=60, offset=92, shifts=[0, -1, 1],
            notes=[(0, 6, 86), (1.5, 6, 81)])
ARP_CYCLE, ARP_OFFSET, ARP_ON, ARP_FADE = 90, 60, 50, 12
for clock in (MOTIF_A, MOTIF_B, BELL):
    assert LOOP_BEATS % clock['cycle'] == 0 and len(clock['shifts']) == LOOP_BEATS // clock['cycle']
assert LOOP_BEATS % ARP_CYCLE == 0


# ---------------------------------------------------------------- voicing
def chord_pcs(name):
    root, ivs, _ = CHORDS[name]
    return root, [(root + i) % 12 for i in ivs]


def voice_lead(prev, name, lo=55, hi=79):
    """Pick the octave of each upper-structure tone so the hand moves least."""
    _, pcs = chord_pcs(name)
    options = [[p for p in range(lo, hi + 1) if p % 12 == pc] for pc in pcs]
    best, best_cost = None, None
    for combo in itertools.product(*options):
        v = sorted(combo)
        if len(set(v)) < len(v) or v[-1] - v[0] > 14:
            continue
        cost = sum(abs(a - b) for a, b in zip(v, prev))
        cost += 1.0 * abs((v[0] + v[-1]) / 2 - 65)
        cost += 6.0 * sum(1 for a, b in zip(v, v[1:]) if b - a == 1)
        cost += 1.5 * max(0, 10 - (v[-1] - v[0]))
        if best_cost is None or cost < best_cost:
            best, best_cost = v, cost
    return best


def voicings():
    """Voicing per chord start. Runs the loop twice so the first chord leads from the last."""
    prev = [57, 60, 64, 65]
    out = {}
    for _ in range(2):
        for start, _, name in chord_spans():
            prev = voice_lead(prev, name)
            out[start] = prev
    return out


def bass_root(name):
    bass = CHORDS[name][2]
    return 38 + (bass - 2) % 12  # D2..C#3


# ---------------------------------------------------------------- the score
def clock_notes(clock):
    out = []
    for k, shift in enumerate(clock['shifts']):
        base = clock['offset'] + k * clock['cycle']
        for (beat, beats, pitch) in clock['notes']:
            out.append(((base + beat) % LOOP_BEATS, beats, scale_step(pitch, shift)))
    return out


def arp_level(beat):
    """0..1. The arpeggio fades in, holds, fades out, then rests."""
    phase = (beat - ARP_OFFSET) % ARP_CYCLE
    if phase >= ARP_ON:
        return 0.0
    edge = min(phase, ARP_ON - phase)
    return min(1.0, edge / ARP_FADE) ** 1.5


def score():
    """Every note in one loop: {part: [(beat, beats, pitch, velocity 0-127)]}.

    Beats are within the loop. Notes may run past its end; renderers wrap them.
    """
    rng = random.Random(1990)
    parts = {k: [] for k in ('keys', 'pad', 'chippad', 'bass', 'arp', 'motif_a', 'motif_b', 'bell')}
    voices = voicings()

    def add(part, beat, beats, pitch, vel):
        parts[part].append((beat, beats, pitch, max(1, min(127, int(round(vel))))))

    for start, beats, name in chord_spans():
        v = voices[start]
        root, pcs = chord_pcs(name)

        for p in v:
            add('pad', start, beats, p, 50)
            add('chippad', start, beats, p + 12, 70)

        # Bass: legato, the root, then the fifth through the longer chords.
        r = bass_root(name)
        first = min(beats, 12)
        add('bass', start, first, r, 84)
        if beats > 12:
            add('bass', start + 12, beats - 12, r + 7 if beats >= 20 else r, 70)

        # Piano: a slow rolled chord at the change, then a few single notes, unhurried.
        for i, p in enumerate(v[1:]):
            add('keys', start + 0.25 + i * 0.22, beats - 1, p, 34 - 3 * i)
        upper = [p for p in range(67, 82) if p % 12 in set(pcs) | {root}]
        for j in range(beats // 8):
            at = start + 5 + j * 7.5 + rng.uniform(-0.75, 0.75)
            add('keys', at, 4, rng.choice(upper), rng.randint(24, 32))

    for part, clock in (('motif_a', MOTIF_A), ('motif_b', MOTIF_B), ('bell', BELL)):
        for (beat, beats, pitch) in clock_notes(clock):
            add(part, beat, beats, pitch, 90)

    # Arpeggio over the chord tones, 7 steps against the beat's 2.
    pattern = [0, 2, 1, 3, 2, 4, 3]
    for step in range(LOOP_BEATS * 2):
        beat = step / 2
        level = arp_level(beat)
        if level <= 0.02:
            continue
        name = chord_at(beat)
        root, pcs = chord_pcs(name)
        tones = [p for p in range(62, 78) if p % 12 in set(pcs) | {root}]
        add('arp', beat, 0.6, tones[pattern[step % len(pattern)] % len(tones)], 100 * level)
    return parts


# ---------------------------------------------------------------- MIDI
# channel: (bank, program 0-indexed, CC7 volume, CC10 pan, CC91 reverb, CC93 chorus)
CHANNELS = {
    KEYS: (8, 0, 100, 58, 100, 20),   # Mellow Grand Piano (bank 8)
    PAD: (0, 89, 70, 70, 90, 50),     # Warm Pad (GM 90)
}


def t(beat):
    return int(round(beat * TPB))


def compose():
    parts = score()
    events = []
    for part, ch in (('keys', KEYS), ('pad', PAD)):
        for (beat, beats, pitch, vel) in parts[part]:
            s = t(beat) % LOOP_TICKS
            e = s + max(1, t(beats))
            events.append((s, 1, mido.Message('note_on', channel=ch, note=pitch, velocity=vel)))
            events.append((e, 0, mido.Message('note_off', channel=ch, note=pitch, velocity=0)))
    # Sustain pedal: down just after each change, up just before the next.
    for start, beats, _ in chord_spans():
        events.append((t(start + 0.1), -1, mido.Message('control_change', channel=KEYS, control=64, value=127)))
        events.append((t(start + beats - 0.15), -1, mido.Message('control_change', channel=KEYS, control=64, value=0)))
    # Slow pad swell (CC11). The period divides the loop so start == end.
    for step in range(LOOP_BEATS):
        events.append((t(step), -1, mido.Message(
            'control_change', channel=PAD, control=11,
            value=int(round(96 + 16 * math.sin(2 * math.pi * step / 30 - math.pi / 2))))))
    return events


def setup_msgs():
    msgs = []
    for ch, (bank, prog, vol, pan, rev, cho) in CHANNELS.items():
        msgs += [mido.Message('control_change', channel=ch, control=0, value=bank),
                 mido.Message('program_change', channel=ch, program=prog),
                 mido.Message('control_change', channel=ch, control=7, value=vol),
                 mido.Message('control_change', channel=ch, control=10, value=pan),
                 mido.Message('control_change', channel=ch, control=91, value=rev),
                 mido.Message('control_change', channel=ch, control=93, value=cho),
                 mido.Message('control_change', channel=ch, control=1, value=0),
                 mido.Message('control_change', channel=ch, control=11, value=100)]
    return msgs


def build(events, copies, tail_beats=0, markers=False):
    mid = mido.MidiFile(type=0, ticks_per_beat=TPB)
    tr = mido.MidiTrack()
    mid.tracks.append(tr)
    tr.append(mido.MetaMessage('track_name', name=TITLE, time=0))
    tr.append(mido.MetaMessage(
        'text',
        text='Composer: Foldcade project. License: MIT with the Foldcade repository.',
        time=0,
    ))
    tr.append(mido.MetaMessage('set_tempo', tempo=mido.bpm2tempo(BPM), time=0))
    tr.append(mido.MetaMessage('time_signature', numerator=4, denominator=4, time=0))
    tr.append(mido.MetaMessage('key_signature', key='Dm', time=0))
    for m in setup_msgs():
        tr.append(m.copy(time=0))
    allev = []
    for c in range(copies):
        allev += [(tk + c * LOOP_TICKS, o, m) for (tk, o, m) in events]
    if markers:
        allev.append((0, -2, mido.MetaMessage('marker', text='loopStart')))
        allev.append((LOOP_TICKS, -2, mido.MetaMessage('marker', text='loopEnd')))
    allev.sort(key=lambda x: (x[0], x[1]))
    now = 0
    for tk, _, m in allev:
        tr.append(m.copy(time=tk - now))
        now = tk
    end = copies * LOOP_TICKS + t(tail_beats)
    tr.append(mido.MetaMessage('end_of_track', time=max(0, end - now)))
    return mid


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--variant', choices=['a'], default='a')
    ap.add_argument('--out', help='directory for the MIDI files')
    a = ap.parse_args()
    ev = compose()
    d = a.out or os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out', a.variant)
    os.makedirs(d, exist_ok=True)
    build(ev, 1, markers=True).save(os.path.join(d, 'foldcade_home_a_loop.mid'))
    build(ev, 4, tail_beats=12).save(os.path.join(d, 'foldcade_home_a_render4x.mid'))
    print(a.variant, 'loop seconds:', LOOP_BEATS * 60 / BPM)
