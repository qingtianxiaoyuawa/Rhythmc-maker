#!/bin/bash
# Generate a short 120 BPM click track in every audio format the mod supports,
# so verify.py has inputs to compare. Requires python3 and ffmpeg.
set -e
OUT="${1:-$(dirname "$0")/.samples}"
mkdir -p "$OUT"

python3 - "$OUT/test_click.wav" <<'PY'
import math, struct, wave, sys
out = sys.argv[1]; rate = 44100; bpm = 120.0; beats = 32
dur = beats * 60.0 / bpm; n = int(rate * dur); frames = bytearray()
for i in range(n):
    t = i / rate
    ph = t * bpm / 60.0
    frac = ph - int(ph)
    click = math.exp(-frac * 60.0) * 0.8 if frac < 0.15 else 0.0
    bed = 0.03 * math.sin(2 * math.pi * 220.0 * t)
    v = max(-1.0, min(1.0, click + bed))
    frames += struct.pack("<h", int(v * 32767))
with wave.open(out, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(bytes(frames))
PY

for spec in "mp3 libmp3lame" "flac flac" "ogg libvorbis" "m4a aac" "aac aac"; do
  set -- $spec
  ffmpeg -y -v error -i "$OUT/test_click.wav" -c:a "$2" "$OUT/test_click.$1"
done
ls -la "$OUT"
