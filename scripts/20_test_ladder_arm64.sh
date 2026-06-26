#!/usr/bin/env bash
# arm64 via qemu-user on the image's REAL bionic libc: full ladder
#   interpreter -> ssl/ctypes/socket imports -> yt-dlp --version -> extract from YouTube
# Needs: 00_fetch_deps.sh + 01_build.sh + debugfs + the arm64-v8a API-23 image.
# NOTE: qemu-user routes syscalls to the HOST kernel (proves userspace/ABI, not an
#       Android-6.0 kernel). DNS is pre-seeded via a hosts file (no netd in the sandbox).
source "$(dirname "$0")/lib.sh"
FAILED=0
QEMU="$WORK/qemu/usr/bin/qemu-aarch64-static"
IMGDIR="$ANDROID_HOME/system-images/android-23/google_apis/arm64-v8a"
PFX="$(termux_prefix aarch64)"
[ -d "$PFX" ] || { echo "missing $PFX (run 00_fetch_deps.sh)"; exit 1; }
[ -x "$QEMU" ] || { echo "missing qemu (run 00_fetch_deps.sh)"; exit 1; }
[ -f "$IMGDIR/system.img" ] || { echo "  installing arm64 image"; yes | "$SDKMANAGER" "system-images;android-23;google_apis;arm64-v8a" >/dev/null 2>&1; }
IMG="$IMGDIR/system.img"
RFS="$WORK/armroot"; GUESTP=/data/data/com.termux/files/usr

say "extract bionic from system image + assemble arm64 root"
rm -rf "$RFS"; mkdir -p "$RFS/system/bin" "$RFS/system/lib64" "$RFS/system/etc" "$RFS$(dirname "$GUESTP")"
LIBS="$(debugfs -R "ls /lib64" "$IMG" 2>/dev/null | tr -s ' \t' '\n' | grep '\.so$' | sort -u)"
for f in $LIBS; do debugfs -R "dump /lib64/$f $RFS/system/lib64/$f" "$IMG" >/dev/null 2>&1 || true; done
debugfs -R "dump /bin/linker64 $RFS/system/bin/linker64" "$IMG" >/dev/null 2>&1
cp -an "$PFX"/lib/*.so* "$RFS/system/lib64/" 2>/dev/null || true
cp "$WORK/bin/libshim-arm64.so" "$RFS/system/lib64/libshim.so"
cp -a "$PFX" "$RFS$GUESTP"
chmod +x "$RFS$GUESTP/bin/python3.13" 2>/dev/null || true
echo "  bionic libc: $(stat -c '%s bytes' "$RFS/system/lib64/libc.so" 2>/dev/null || echo MISSING)"

# host-side DNS (bare qemu-user has no Android netd/property service)
: > "$RFS/system/etc/hosts"
for h in www.google.com www.youtube.com youtubei.googleapis.com m.youtube.com youtube.com; do
  ip="$(getent ahostsv4 "$h" 2>/dev/null | awk '{print $1; exit}')"; [ -n "$ip" ] && echo "$ip $h" >> "$RFS/system/etc/hosts"
done

flt(){ grep -vE "unused DT entry|page record for|DT_FLAGS_1|__bionic_open_tzdata"; }
run(){ "$QEMU" -L "$RFS" -E LD_LIBRARY_PATH=/system/lib64 -E LD_PRELOAD=/system/lib64/libshim.so \
  -E SSL_CERT_FILE="$RFS$GUESTP/etc/tls/cert.pem" -E ANDROID_ROOT=/system -E ANDROID_DATA=/data \
  -E ANDROID_DNS_MODE=local -E HOME=/tmp -E TMPDIR=/tmp \
  "$RFS$GUESTP/bin/python3.13" "$@" 2>&1 | flt; }

say "ladder (aarch64)"
A="$(run -c 'import os,sys;print("PYV",sys.version.split()[0],os.uname().machine)')"; echo "  A: $A"
echo "$A" | grep -q "aarch64" && pass "aarch64 interpreter runs" || fail "interpreter"
B="$(run "$ROOT/src/testb.py")"; echo "  B: $B"
echo "$B" | grep -q "imports OK" && pass "ssl/ctypes/socket/... import" || fail "imports"
C="$(run "$WORK/yt-dlp.pyz" --version)"; echo "  C: yt-dlp $C"
echo "$C" | grep -qE "^[0-9]{4}\." && pass "yt-dlp runs" || fail "yt-dlp --version"
D="$(run "$WORK/yt-dlp.pyz" --no-warnings --simulate --skip-download -e "$TEST_VIDEO")"; echo "  D: extract -> $D"
[ -n "$D" ] && pass "yt-dlp extracts from YouTube (aarch64)" || fail "youtube extraction"

[ "$FAILED" = 0 ] && say "RESULT: PASS" || { say "RESULT: FAIL"; exit 1; }
