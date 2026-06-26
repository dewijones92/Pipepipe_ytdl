#!/usr/bin/env bash
# Build the API-23-fixed youtubedl-android Python package for apk-repro/, and drop in the
# latest yt-dlp. Two API-23 fixes are applied to the bundled CPython 3.11 package:
#   1) add the shim (lockf/preadv/pwritev/...) as a DT_NEEDED of libpython  -> fixes the
#      "cannot locate symbol" crash (issue #304)
#   2) page-pad every .so so the strict API-23 linker accepts a final LOAD segment that
#      ends at EOF -> fixes "invalid ELF file ... load segment past end of file"
# Needs: 01_build.sh (libshim), patchelf, zip, internet.  ABI: x86_64 (the emulator we boot).
source "$(dirname "$0")/lib.sh"
ABI=x86_64
AAR="$WORK/aar/jf-library-0.17.3.aar"
DEST="$ROOT/apk-repro/app/src/main/jniLibs/$ABI"
RAW="$ROOT/apk-repro/app/src/main/res/raw"

[ -f "$WORK/bin/libshim-$ABI.so" ] || { echo "run 01_build.sh first"; exit 1; }
mkdir -p "$WORK/aar"
[ -f "$AAR" ] || curl -fsSL -o "$AAR" "https://repo1.maven.org/maven2/io/github/junkfood02/youtubedl-android/library/0.17.3/library-0.17.3.aar"

PATCHELF="$WORK/patchelf/usr/bin/patchelf"
if [ ! -x "$PATCHELF" ]; then
  ( cd "$WORK" && apt-get download patchelf && deb=$(printf '%s\n' patchelf_*.deb | head -1) \
    && rm -rf patchelf && mkdir patchelf && dpkg-deb -x "$deb" patchelf && rm -f "$deb" )
fi

say "extract bundled python ($ABI) + apply the two API-23 fixes"
rm -rf "$WORK/aar-x" "$WORK/fixedpy"; mkdir -p "$WORK/fixedpy"
unzip -oq "$AAR" "jni/$ABI/libpython.zip.so" "jni/$ABI/libpython.so" -d "$WORK/aar-x"
unzip -oq "$WORK/aar-x/jni/$ABI/libpython.zip.so" -d "$WORK/fixedpy"
PY="$WORK/fixedpy"
cp "$WORK/bin/libshim-$ABI.so" "$PY/usr/lib/libshim.so"
"$PATCHELF" --add-needed libshim.so "$PY/usr/lib/libpython3.11.so.1.0"          # fix 1
padded=0
for so in $(find "$PY" -name '*.so*'); do
  sz=$(stat -c%s "$so"); pad=$(( (4096 - sz % 4096) % 4096 ))
  [ "$pad" -gt 0 ] && { head -c "$pad" /dev/zero >> "$so"; padded=$((padded+1)); }   # fix 2
done
echo "  shim NEEDED added; page-padded $padded libs"

say "repackage -> apk-repro jniLibs"
mkdir -p "$DEST"
( cd "$PY" && rm -f "$WORK/libpython-fixed.zip" && zip -qr -X "$WORK/libpython-fixed.zip" usr )
cp "$WORK/libpython-fixed.zip" "$DEST/libpython.zip.so"
"$PATCHELF" --remove-rpath "$WORK/aar-x/jni/$ABI/libpython.so" 2>/dev/null || true
cp "$WORK/aar-x/jni/$ABI/libpython.so" "$DEST/libpython.so"

say "drop in the latest yt-dlp (overrides the AAR's stale R.raw.ytdlp)"
mkdir -p "$RAW"
[ -f "$WORK/yt-dlp.pyz" ] || curl -fsSL -o "$WORK/yt-dlp.pyz" https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
cp "$WORK/yt-dlp.pyz" "$RAW/ytdlp"

say "done — build apk-repro with: cd apk-repro && ./gradlew :app:assembleDebug"
