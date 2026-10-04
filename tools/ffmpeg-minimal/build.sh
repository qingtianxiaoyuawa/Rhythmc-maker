#!/bin/bash
# Cross-compile minimal, audio-only ffprobe.exe + ffplay.exe for Windows x64 (mingw-w64).
# Output: ./out/ffprobe.exe  ./out/ffplay.exe
# Copy them into src/main/resources/rhythmc_maker/native/windows-x86_64/
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${HERE}/.build"
mkdir -p "${WORK}"
cd "${WORK}"

FFVER=6.1.1
SDLVER=2.32.10-1
SDL_URL="https://mirrors.tuna.tsinghua.edu.cn/msys2/mingw/mingw64/mingw-w64-x86_64-SDL2-${SDLVER}-any.pkg.tar.zst"

echo "== FFmpeg source =="
[ -f "ffmpeg-${FFVER}.tar.xz" ] || wget -q --show-progress "https://ffmpeg.org/releases/ffmpeg-${FFVER}.tar.xz"
[ -d "ffmpeg-${FFVER}" ] || tar xf "ffmpeg-${FFVER}.tar.xz"

echo "== SDL2 (mingw-w64) for ffplay =="
SDLROOT="${WORK}/sdl2pkg/mingw64"
if [ ! -d "$SDLROOT" ]; then
  curl -L --fail -o SDL2.pkg.tar.zst "$SDL_URL"
  mkdir -p sdl2pkg
  tar --zstd -xf SDL2.pkg.tar.zst -C sdl2pkg 2>/dev/null || zstd -d -c SDL2.pkg.tar.zst | tar xf - -C sdl2pkg
fi
# The packaged sdl2.pc bakes an absolute prefix; repoint it at our tree.
sed -i "s|^prefix=.*|prefix=$SDLROOT|" "$SDLROOT/lib/pkgconfig/sdl2.pc"
export PKG_CONFIG_PATH="$SDLROOT/lib/pkgconfig"
export PKG_CONFIG_LIBDIR="$SDLROOT/lib/pkgconfig"

cd "ffmpeg-${FFVER}"
echo "== configure =="
./configure \
  --prefix="${HERE}/out" \
  --cross-prefix=x86_64-w64-mingw32- \
  --target-os=mingw32 --arch=x86_64 --enable-cross-compile --host-cc=gcc \
  --pkg-config=pkg-config --disable-autodetect \
  --disable-everything --enable-small --disable-network --disable-doc \
  --enable-avdevice --enable-indev=lavfi --disable-postproc --enable-swscale \
  --disable-programs --enable-ffprobe --disable-ffmpeg --enable-ffplay --enable-sdl2 \
  --enable-protocol=file,pipe \
  --enable-demuxer=lavfi,mov,mp3,flac,ogg,wav,aac,matroska \
  --enable-decoder=mp3,mp3float,flac,vorbis,opus,aac,alac,pcm_s16le,pcm_s24le,pcm_s32le,pcm_u8,pcm_f32le,pcm_f64le \
  --enable-parser=mpegaudio,flac,vorbis,opus,aac \
  --enable-filter=movie,amovie,aresample,asetnsamples,anull,anullsink,abuffer,abuffersink,highpass,bandpass,aspectralstats,volume,atempo,aformat,format,copy,afifo \
  --extra-cflags="-O2" \
  --extra-ldflags="-static -static-libgcc -L$SDLROOT/lib" \
  --extra-libs="-lmingw32 -lSDL2main -lSDL2 -lwinmm -lgdi32 -lole32 -loleaut32 -limm32 -lversion -luuid -lsetupapi"

echo "== make =="
make -j"$(nproc)"

mkdir -p "${HERE}/out"
cp -f ffprobe.exe ffplay.exe "${HERE}/out/"
ls -la "${HERE}/out/"
echo "DONE -> ${HERE}/out/"
