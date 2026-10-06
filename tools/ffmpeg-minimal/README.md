# ffmpeg-minimal

Reproducible recipe for the bundled Windows `ffprobe.exe` and `ffplay.exe`:
minimal, audio-only FFmpeg cross-builds that replace the previous full static
builds with a fraction of the size.

| Binary | Before | After |
|---|---:|---:|
| `ffprobe.exe` (6.1.1, gyan "essentials", 82.7 MB) | 82.7 MB | **2.3 MB** |
| `ffplay.exe` (4.2.2, full static, 64.5 MB) | 64.5 MB | **4.3 MB** |

## Why

Rhythmc maker only ever uses these two tools for audio:

1. **ffprobe** — read audio duration (`-show_entries format=duration`) and run the
   onset / spectral-flux filter chain used by `BpmDetector`
   (`amovie → aresample → asetnsamples → highpass/bandpass → aspectralstats`).
2. **ffplay** — play the chart audio (`-nodisp ... -af volume,aresample,asetrate`).

Neither touches video. The previous builds shipped every video codec/accelerator
(x264, x265, aom, vpx, nvenc, cuda, libvmaf ...) plus, for ffplay, an entirely
different FFmpeg version (4.2.2 vs 6.1.1 for ffprobe). The minimal builds keep
exactly the required features, unify both on 6.1.1, and cut ~140 MB in total.

## Requirements

- Linux or WSL with `mingw-w64` (`x86_64-w64-mingw32-gcc`), `make`, `nasm`,
  `pkg-config`, `wget`/`curl`, `zstd`.
- FFmpeg source and the SDL2 mingw package are fetched by the script.

## Build

```sh
bash tools/ffmpeg-minimal/build.sh
```

Outputs `./out/ffprobe.exe` and `./out/ffplay.exe`. Copy both to:

```
src/main/resources/rhythmc_maker/native/windows-x86_64/
```

## Verify

1. `bash tools/ffmpeg-minimal/gen_samples.sh` — makes a 120 BPM click track in
   every supported format (wav/mp3/flac/ogg/m4a/aac).
2. `python verify.py <reference_ffprobe> <candidate_ffprobe> <sample_dir>` —
   every format × every filter must be byte-identical (duration + flux CSV).
3. ffplay: run the mod's exact invocation on each sample and assert exit code 0:
   `ffplay -nodisp -autoexit -loglevel error -probesize 32 -analyzeduration 0 -ss 0 -i <file> -vn -af volume=1.0 -t 1`

## Feature set (why these flags)

| Group | Enabled |
|---|---|
| protocols | `file`, `pipe` |
| demuxers | `lavfi`, `mov`, `mp3`, `flac`, `ogg`, `wav`, `aac`, `matroska` |
| decoders | `mp3`, `mp3float`, `flac`, `vorbis`, `opus`, `aac`, `alac`, pcm (`s16le`,`s24le`,`s32le`,`u8`,`f32le`,`f64le`) |
| parsers | `mpegaudio`, `flac`, `vorbis`, `opus`, `aac` |
| filters | `movie`/`amovie`, `aresample`, `asetrate`, `asetnsamples`, `anull`, `highpass`, `bandpass`, `aspectralstats`, `volume`, `aformat`, `format`, `copy`, `afifo`, `abuffer`, `abuffersink` |
| programs | `ffprobe`, `ffplay` |
| external | SDL2 (static, mingw-w64) for ffplay |

Notes:
- `--enable-avdevice --enable-indev=lavfi` is required: `-f lavfi -i ...` resolves
  through the avdevice `lavfi` input device.
- `--enable-swscale` is required because `ffplay` lists `swscale` in its deps even
  though this mod only plays audio.
- The SDL2 package is pulled from a public MSYS2 mirror (fast, non-GitHub). The
  `sdl2.pc` prefix is rewritten at build time to the extracted tree.
