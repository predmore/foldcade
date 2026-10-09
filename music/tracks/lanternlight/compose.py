#!/usr/bin/env python3
"""Lanternlight, Foldcade's home loop. Original composition, written in code.

The notes are the approved variant A arrangement. sketch1 and b stay in this file so the
piece can still be rendered those ways; CI ships variant a only.

Usage:  compose.py --variant {sketch1,a,b} [--out DIR]
  sketch1 : original arrangement (music box, harp, warm pad, strings)
  a       : gentle retro (square lead + vibraphone double, pizzicato arp, quieter pad)
  b       : more chiptune (square lead + square echo, saw arp, sine bass, very quiet pad)

Writes <out>/foldcade_home_<variant>_loop.mid      (one loop, editable, loop markers)
       <out>/foldcade_home_<variant>_render4x.mid  (loop x4 + tail, for rendering only)
Same piece in every variant: D major, 72 bpm, 4/4, 24 bars (A/B/C) = 96 beats = exactly 80.000 s.
"""
import argparse, math, os, random
import mido

TPB = 480
BPM = 72
BARS = 24
BEATS_PER_BAR = 4
LOOP_BEATS = BARS * BEATS_PER_BAR
LOOP_TICKS = LOOP_BEATS * TPB

# Channel roles
PAD, LOW, LEAD, ARP, ECHO, DBL = 0, 1, 2, 3, 4, 5

# ---------------------------------------------------------------- the piece
# Chord voicings: (low notes, upper voicing for pad)
CH = {
    'Dmaj9':  ([38, 45], [57, 61, 64, 66]),
    'Bm9':    ([35, 42], [54, 57, 61, 62]),
    'Gmaj7':  ([43, 50], [54, 59, 62, 66]),
    'Gmaj9':  ([43, 50], [57, 59, 62, 66]),
    'Asus2':  ([45, 52], [52, 57, 59, 64]),
    'Asus4':  ([45, 52], [52, 57, 62, 64]),
    'A':      ([45, 52], [52, 57, 61, 64]),
    'A7sus4': ([45, 52], [55, 57, 62, 64]),
    'Em9':    ([40, 47], [55, 59, 62, 66]),
    'F#m7':   ([42, 49], [57, 61, 64, 66]),
}
A_PROG = [[(0, 4, 'Dmaj9')], [(0, 4, 'Bm9')], [(0, 4, 'Gmaj7')], [(0, 4, 'Asus2')],
          [(0, 4, 'Em9')], [(0, 4, 'F#m7')], [(0, 4, 'Gmaj9')], [(0, 2, 'Asus4'), (2, 2, 'A')]]
B_PROG = [[(0, 4, 'Gmaj9')], [(0, 4, 'Asus2')], [(0, 4, 'F#m7')], [(0, 4, 'Bm9')],
          [(0, 4, 'Em9')], [(0, 4, 'F#m7')], [(0, 4, 'Gmaj7')], [(0, 4, 'A7sus4')]]
HARMONY = A_PROG + B_PROG + A_PROG
MEL = [
    [(2, 81, 1), (3, 76, 1)], [(0, 78, 3)], [(1, 83, 1), (2, 81, 1), (3, 78, 1)], [(0, 76, 3)],
    [(0.5, 79, 1), (1.5, 78, 0.5), (2, 74, 2)], [(1, 73, 1), (2, 76, 2)],
    [(0, 81, 1.5), (1.5, 78, 0.5), (2, 83, 2)], [(0, 81, 2), (2, 76, 1), (3, 73, 1)],
    [(0, 83, 1.5), (1.5, 86, 0.5), (2, 85, 2)], [(0, 81, 3), (3, 76, 1)],
    [(0, 78, 1), (1, 81, 1), (2, 85, 2)], [(0, 86, 2), (2, 85, 1), (3, 81, 1)],
    [(0, 83, 3), (3, 79, 1)], [(0, 78, 2), (2, 76, 1), (3, 73, 1)],
    [(0, 74, 1), (1, 78, 1), (2, 83, 2)], [(0, 81, 2), (2, 79, 1), (3, 76, 1)],
    [(1, 85, 1), (2, 81, 2)], [], [(0.5, 83, 1), (1.5, 78, 2.5)], [],
    [(1, 79, 1), (2, 78, 1), (3, 76, 1)], [(0, 73, 3)], [(2, 81, 2)], [(0, 76, 2), (2, 73, 2)],
]
assert len(HARMONY) == BARS and len(MEL) == BARS

# ---------------------------------------------------------------- arrangements
# channel: (bank, program 0-indexed, CC7 volume, CC10 pan, CC91 reverb, CC93 chorus)
VARIANTS = {
    'sketch1': dict(
        channels={PAD: (0, 89, 92, 64, 90, 60),    # Warm Pad
                  LOW: (0, 49, 70, 58, 90, 40),    # Slow Strings
                  LEAD: (0, 10, 88, 72, 105, 25),  # Music Box
                  ARP: (0, 46, 74, 52, 95, 20),    # Harp
                  ECHO: (0, 8, 62, 80, 110, 30)},  # Celesta
        humanize=True, low='strings', pad_vel=52, lead_vel=62, lead_len=1.0,
        arp_len=1.0, arp_vel=44, echo_vel=34, echo_oct=12, vibrato=0, double=None),
    'a': dict(
        channels={PAD: (0, 89, 68, 64, 65, 15),    # Warm Pad, quieter
                  LOW: (0, 49, 60, 58, 65, 10),    # Slow Strings, quiet
                  LEAD: (0, 80, 74, 70, 60, 0),    # Square Lead (GM 81)
                  DBL: (0, 11, 95, 58, 65, 5),     # Vibraphone (GM 12) unison double
                  ARP: (0, 45, 84, 50, 55, 0),     # Strings Pizzicato (GM 46)
                  ECHO: (0, 8, 66, 80, 70, 5)},    # Celesta echo in section B
        humanize=False, low='strings', pad_vel=48, lead_vel=50, lead_len=0.92,
        arp_len=0.45, arp_vel=46, echo_vel=30, echo_oct=12, vibrato=22, double=40),
    'b': dict(
        channels={PAD: (0, 89, 50, 64, 60, 10),    # Warm Pad, very quiet
                  LOW: (8, 80, 62, 64, 25, 0),     # Sine Wave (bank 8) bass
                  LEAD: (0, 80, 74, 66, 50, 0),    # Square Lead (GM 81)
                  ARP: (0, 81, 50, 54, 45, 0),     # Saw Lead (GM 82), very low
                  ECHO: (0, 80, 56, 84, 55, 0)},   # Square echo in section B
        humanize=False, low='chipbass', pad_vel=44, lead_vel=48, lead_len=0.88,
        arp_len=0.22, arp_vel=30, echo_vel=26, echo_oct=0, vibrato=18, double=None),
}


def t(beat):
    return int(round(beat * TPB))


def compose(cfg):
    rng = random.Random(7)
    hum = cfg['humanize']
    events = []

    def note(ch, start_beat, length_beats, pitch, vel, jitter=0.0):
        s = t(start_beat + (rng.random() * jitter if (jitter and hum) else 0))
        e = max(s + 1, t(start_beat + length_beats))
        vel = max(1, min(127, int(vel)))
        events.append((s, 1, mido.Message('note_on', channel=ch, note=pitch, velocity=vel)))
        events.append((e, 0, mido.Message('note_off', channel=ch, note=pitch, velocity=0)))

    def r(a, b):  # velocity humanization (only when humanize=True)
        return rng.randint(a, b) if hum else 0

    def cc(beat, ch, ctl, val, order=-1):
        events.append((t(beat), order, mido.Message('control_change', channel=ch, control=ctl, value=int(val))))

    for bar in range(BARS):
        b0 = bar * BEATS_PER_BAR
        section = bar // 8
        for (sb, ln, name) in HARMONY[bar]:
            low, upper = CH[name]
            start = b0 + sb
            for i, n in enumerate(upper):
                note(PAD, start, ln, n, cfg['pad_vel'] + r(-3, 3) - i)
            if cfg['low'] == 'strings':
                for n in low:
                    note(LOW, start, ln, n, 46 + r(-3, 3))
            else:  # simple chip bass on a sine voice, one octave up for small speakers
                root, fifth = low[0] + 12, low[1] + 12
                if ln >= 4:
                    pat = [(0, 1.25, root, 62), (1.5, 0.4, root, 50), (2, 1.25, fifth, 56), (3.5, 0.4, root, 48)]
                else:
                    pat = [(0, 1.25, root, 62), (1.5, 0.4, fifth, 50)]
                for (o, l, p, v) in pat:
                    note(LOW, start + o, l, p, v)
            tones = sorted(set(upper + [n + 12 for n in upper]))
            tones = [n for n in tones if 62 <= n <= 81][:6]
            pattern = [0, 1, 2, 3, 4, 3, 2, 1]
            sparse = (bar in (0, 1)) or (bar >= 20)
            for k in range(int(ln * 2)):
                if sparse and k % 2:
                    continue
                if section == 1 and k == 7:
                    continue
                idx = pattern[(int(sb * 2) + k) % 8] % len(tones)
                v = cfg['arp_vel'] - (6 if sparse else 0) + (6 if k % 4 == 0 else 0) + r(-4, 4)
                note(ARP, start + k * 0.5, cfg['arp_len'], tones[idx], v, jitter=0.03)
        for (beat, pitch, ln) in MEL[bar]:
            v = cfg['lead_vel'] + r(-5, 5) + (4 if section == 1 else 0)
            st = b0 + beat
            note(LEAD, st, ln * cfg['lead_len'], pitch, v, jitter=0.02)
            if cfg['vibrato']:  # delayed vibrato (CC1 ramps in after the attack)
                cc(st, LEAD, 1, 0, order=2)
                if ln >= 1:
                    for j, frac in enumerate((0.25, 0.5, 0.75, 1.0)):
                        cc(st + 0.3 + 0.12 * j, LEAD, 1, cfg['vibrato'] * frac)
            if cfg['double']:
                note(DBL, st, ln, pitch, cfg['double'])
            if section == 1:
                note(ECHO, st + 0.75, ln * (cfg['lead_len'] if cfg['echo_oct'] == 0 else 1),
                     pitch + cfg['echo_oct'], cfg['echo_vel'] + r(-4, 4))

    # Expression swells (CC11); periods divide the loop so start == end
    for step in range(LOOP_BEATS * 4):
        beat = step / 4
        cc(beat, PAD, 11, 88 + 22 * math.sin(2 * math.pi * beat / 8 - math.pi / 2))
        if cfg['low'] == 'strings':
            cc(beat, LOW, 11, 80 + 20 * math.sin(2 * math.pi * beat / 16 - math.pi / 2))
    return events


def setup_msgs(cfg):
    msgs = []
    for ch, (bank, prog, vol, pan, rev, cho) in cfg['channels'].items():
        msgs += [mido.Message('control_change', channel=ch, control=0, value=bank),
                 mido.Message('program_change', channel=ch, program=prog),
                 mido.Message('control_change', channel=ch, control=7, value=vol),
                 mido.Message('control_change', channel=ch, control=10, value=pan),
                 mido.Message('control_change', channel=ch, control=91, value=rev),
                 mido.Message('control_change', channel=ch, control=93, value=cho),
                 mido.Message('control_change', channel=ch, control=1, value=0),
                 mido.Message('control_change', channel=ch, control=11, value=100)]
    return msgs


def build(cfg, events, name, copies, tail_beats=0, markers=False):
    mid = mido.MidiFile(type=0, ticks_per_beat=TPB)
    tr = mido.MidiTrack()
    mid.tracks.append(tr)
    tr.append(mido.MetaMessage('track_name', name=name, time=0))
    tr.append(mido.MetaMessage(
        'text',
        text='Composer: Foldcade project. License: GPLv3 with the Foldcade repository.',
        time=0,
    ))
    tr.append(mido.MetaMessage('set_tempo', tempo=mido.bpm2tempo(BPM), time=0))
    tr.append(mido.MetaMessage('time_signature', numerator=4, denominator=4, time=0))
    tr.append(mido.MetaMessage('key_signature', key='D', time=0))
    for m in setup_msgs(cfg):
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
    ap.add_argument('--variant', choices=sorted(VARIANTS), default='a')
    ap.add_argument('--out', help='directory for the MIDI files')
    a = ap.parse_args()
    cfg = VARIANTS[a.variant]
    ev = compose(cfg)
    d = a.out or os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out', a.variant)
    os.makedirs(d, exist_ok=True)
    title = 'Lanternlight'
    build(cfg, ev, title, 1, markers=True).save(os.path.join(d, f'foldcade_home_{a.variant}_loop.mid'))
    build(cfg, ev, title, 4, tail_beats=8).save(os.path.join(d, f'foldcade_home_{a.variant}_render4x.mid'))
    print(a.variant, 'loop seconds:', LOOP_BEATS * 60 / BPM)
