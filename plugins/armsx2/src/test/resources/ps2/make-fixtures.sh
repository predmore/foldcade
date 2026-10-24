#!/bin/sh
# Regenerates the CHD fixtures in this directory. Needs python3 and chdman from Homebrew rom-tools.
set -eu
cd "$(dirname "$0")"
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

python3 - "$workdir" << 'PY'
import sys
out = sys.argv[1]
text = b"BOOT2 = cdrom0:\\SLUS_123.45;1\nVER = 1.00\nVMODE = NTSC\n"
sectors = 300
image = bytearray(sectors * 2048)
pvd = 16 * 2048
image[pvd] = 1
image[pvd + 1:pvd + 6] = b"CD001"

def put_le(at, value):
    image[at] = value & 0xFF
    image[at + 1] = (value >> 8) & 0xFF
    image[at + 2] = (value >> 16) & 0xFF
    image[at + 3] = (value >> 24) & 0xFF

def write_record(at, lba, size, flags, name):
    padded = 33 + len(name)
    if padded % 2:
        padded += 1
    image[at] = padded
    put_le(at + 2, lba)
    put_le(at + 10, size)
    image[at + 25] = flags
    image[at + 32] = len(name)
    image[at + 33:at + 33 + len(name)] = name
    return at + padded

write_record(pvd + 156, 20, 2048, 2, b"\x00")
at = 20 * 2048
at = write_record(at, 20, 2048, 2, b"\x00")
at = write_record(at, 20, 2048, 2, b"\x01")
write_record(at, 21, len(text), 0, b"SYSTEM.CNF;1")
image[21 * 2048:21 * 2048 + len(text)] = text
open(out + "/disc.iso", "wb").write(image)

raw = bytearray(sectors * 2352)
for sector in range(sectors):
    at = sector * 2352
    raw[at] = 0
    for index in range(1, 11):
        raw[at + index] = 0xFF
    raw[at + 11] = 0
    raw[at + 15] = 2
    raw[at + 24:at + 24 + 2048] = image[sector * 2048:(sector + 1) * 2048]
open(out + "/disc.bin", "wb").write(raw)
open(out + "/disc.cue", "w").write('FILE "disc.bin" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n')
open(out + "/cooked.cue", "w").write('FILE "disc.iso" BINARY\n TRACK 01 MODE1/2048\n INDEX 01 00:00:00\n')

# Five audio frames pad out to eight, so the data track starts on the next hunk.
audio_frames = 5
open(out + "/audio.bin", "wb").write(bytes(audio_frames * 2352))
open(out + "/audio.cue", "w").write(
    'FILE "audio.bin" BINARY\n TRACK 01 AUDIO\n INDEX 01 00:00:00\n'
    'FILE "disc.bin" BINARY\n TRACK 02 MODE2/2352\n INDEX 01 00:00:00\n'
)

pregap_frames = 150
open(out + "/pregap.bin", "wb").write(bytes(pregap_frames * 2352) + raw)
open(out + "/pregap.cue", "w").write(
    'FILE "pregap.bin" BINARY\n TRACK 01 MODE2/2352\n INDEX 00 00:00:00\n INDEX 01 00:02:00\n'
)
PY

here=$(pwd)
chdman createdvd -f -i "$workdir/disc.iso" -o "$here/dvd-default.chd"
chdman createdvd -f -i "$workdir/disc.iso" -o "$here/dvd-zlib.chd" -c zlib
chdman createdvd -f -i "$workdir/disc.iso" -o "$here/dvd-lzma.chd" -c lzma
chdman createdvd -f -i "$workdir/disc.iso" -o "$here/dvd-huff.chd" -c huff
chdman createdvd -f -i "$workdir/disc.iso" -o "$here/dvd-none.chd" -c none
chdman createdvd -f -i "$workdir/disc.iso" -o "$here/dvd-flac.chd" -c flac
chdman createdvd -f -i "$workdir/disc.iso" -o "$here/dvd-zstd.chd" -c zstd
chdman createcd -f -i "$workdir/disc.cue" -o "$here/cd-cdlz.chd" -c cdlz
chdman createcd -f -i "$workdir/disc.cue" -o "$here/cd-cdzl.chd" -c cdzl
chdman createcd -f -i "$workdir/cooked.cue" -o "$here/cd-cooked.chd"
chdman createcd -f -i "$workdir/audio.cue" -o "$here/cd-audio-first.chd"
chdman createcd -f -i "$workdir/pregap.cue" -o "$here/cd-pregap.chd"
chdman createcd -f -i "$workdir/disc.cue" -o "$here/cd-zlib.chd" -c zlib
chdman createcd -f -i "$workdir/disc.cue" -o "$here/cd-lzma.chd" -c lzma
chdman createcd -f -i "$workdir/disc.cue" -o "$here/cd-huff.chd" -c huff

# chdman will not rewrite metadata on a compressed CHD. Append a CHCD table and
# point the header at it, in both byte orders the old format shows up in.
python3 - "$here/cd-audio-first.chd" "$here/cd-chcd.chd" "$here/cd-chcd-le.chd" << 'PY'
import re, struct, sys
src = open(sys.argv[1], "rb").read()
codes = {"MODE1": 0, "MODE1_RAW": 1, "MODE2": 2, "MODE2_FORM1": 3, "MODE2_FORM2": 4,
         "MODE2_FORM_MIX": 5, "MODE2_RAW": 6, "AUDIO": 7}

def tracks(data):
    off = int.from_bytes(data[48:56], "big")
    found = []
    while off:
        tag = data[off:off + 4]
        length = int.from_bytes(data[off + 5:off + 8], "big")
        nxt = int.from_bytes(data[off + 8:off + 16], "big")
        if tag == b"CHT2":
            text = data[off + 16:off + 16 + length].split(b"\x00", 1)[0].decode()
            kind = re.search(r"TYPE:(\S+)", text).group(1)
            frames = int(re.search(r"FRAMES:(\d+)", text).group(1))
            extra = ((frames + 3) // 4) * 4 - frames
            found.append([codes[kind], 2, 2352, 0, frames, extra])
        off = nxt
    return found

def chcd(endian, rows):
    words = [len(rows)]
    for row in rows:
        words += row
    words += [0] * (6 * (99 - len(rows)))
    return struct.pack(endian + f"{len(words)}I", *words)

rows = tracks(src)
for endian, dest in ((">", sys.argv[2]), ("<", sys.argv[3])):
    payload = chcd(endian, rows)
    record = b"CHCD" + bytes([1]) + len(payload).to_bytes(3, "big") + bytes(8) + payload
    out = bytearray(src) + record
    out[48:56] = len(src).to_bytes(8, "big")
    open(dest, "wb").write(out)
PY
