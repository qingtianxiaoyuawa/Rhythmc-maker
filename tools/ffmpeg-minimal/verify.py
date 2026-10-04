#!/usr/bin/env python3
"""Compare a candidate ffprobe.exe against a reference build.

For every supported audio format and every filter the mod uses, the two binaries
must produce byte-identical output (duration string + spectral-flux CSV).

Usage:
    python verify.py <reference_ffprobe> <candidate_ffprobe> <sample_dir>

<sample_dir> must contain the same short click track in wav/mp3/flac/ogg/m4a/aac
(see gen_samples.sh). Run from Windows or any OS that can execute both binaries.
"""
import subprocess, hashlib, os, sys

if len(sys.argv) != 4:
    print(__doc__)
    sys.exit(2)

reference, candidate, sample_dir = sys.argv[1], sys.argv[2], sys.argv[3]
formats = ["wav", "mp3", "flac", "ogg", "m4a", "aac"]
filters = [("anull", "anull"), ("highpass250", "highpass=f=250"),
           ("highpass1000", "highpass=f=1000"), ("bandpass", "bandpass=f=2000:w=1600")]


def run(exe, args):
    p = subprocess.run([exe] + args, capture_output=True, text=True, errors="replace")
    return p.returncode, p.stdout, p.stderr


def avfilter_name(path):
    # ffmpeg filter option escaping: backslashes -> /, colon -> \:
    return path.replace("\\", "/").replace(":", "\\:")


ok = True
for ext in formats:
    path = os.path.join(sample_dir, f"test_click.{ext}")
    if not os.path.isfile(path):
        print(f"[{ext}] MISSING {path}")
        ok = False
        continue
    args = ["-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", path]
    a, b = run(reference, args), run(candidate, args)
    same = a[0] == 0 and b[0] == 0 and a[1].strip() == b[1].strip()
    ok &= same
    print(f"[{ext}] duration ref={a[1].strip()!r} cand={b[1].strip()!r} {'OK' if same else 'DIFF'}")
    ff = avfilter_name(path)
    for fname, onset in filters:
        filt = (f"amovie=filename='{ff}',aresample=16000,asetnsamples=n=512,{onset},"
                f"aspectralstats=measure=all")
        args = ["-v", "error", "-f", "lavfi", "-i", filt, "-show_frames",
                "-show_entries", "frame_tags=lavfi.aspectralstats.1.flux,lavfi.aspectralstats.2.flux",
                "-of", "csv=p=0"]
        a, b = run(reference, args), run(candidate, args)
        ha = hashlib.md5(a[1].encode()).hexdigest()[:12]
        hb = hashlib.md5(b[1].encode()).hexdigest()[:12]
        s = a[0] == 0 and b[0] == 0 and ha == hb and len(a[1]) > 0
        ok &= s
        print(f"    {fname:14s} ref={ha} cand={hb} {'OK' if s else 'DIFF'}")
        if not s:
            print("      ref_err", a[2].strip()[:160], "\n      cand_err", b[2].strip()[:160])

print("\nRESULT:", "ALL_OK" if ok else "HAS_DIFF")
sys.exit(0 if ok else 1)
