#!/usr/bin/env bash
# API-23 x86_64 emulator (real Android-6.0 kernel): full ladder
#   interpreter -> ssl/ctypes/socket imports -> yt-dlp --version -> extract from YouTube
# Needs: 00_fetch_deps.sh + 01_build.sh + /dev/kvm.  KEEP=1 leaves emu running.
source "$(dirname "$0")/lib.sh"
FAILED=0
P=/data/data/com.termux/files/usr
PFX="$(termux_prefix x86_64)"
[ -d "$PFX" ] || { echo "missing $PFX (run 00_fetch_deps.sh)"; exit 1; }

say "API-23 x86_64 emulator: python -> ssl -> yt-dlp -> YouTube"
boot_emulator "${AVD_X86:-sbtest23}" "system-images;android-23;google_apis;x86_64"

say "push Termux prefix + shim + yt-dlp"
"$ADB" shell "rm -rf $P; mkdir -p /data/data/com.termux/files"
"$ADB" push "$PFX" "$P" >/dev/null
"$ADB" push "$WORK/bin/libshim-x86_64.so" /data/local/tmp/libshim.so >/dev/null
"$ADB" push "$WORK/yt-dlp.pyz" /data/local/tmp/yt-dlp.pyz >/dev/null
"$ADB" push "$ROOT/src/testb.py" /data/local/tmp/testb.py >/dev/null
"$ADB" shell "chmod -R 755 $P/bin"

ENV="env LD_PRELOAD=/data/local/tmp/libshim.so PYTHONHOME=$P LD_LIBRARY_PATH=$P/lib SSL_CERT_FILE=$P/etc/tls/cert.pem HOME=/data/local/tmp TMPDIR=/data/local/tmp PATH=$P/bin:/system/bin"
flt(){ grep -vE "unused DT entry|page record for|DT_FLAGS_1|__bionic_open_tzdata"; }
run(){ "$ADB" shell "$ENV $P/bin/python3.13 $*" 2>&1 | tr -d '\r' | flt; }

say "ladder"
A="$(run -c 'import sys;print("PYV",sys.version.split()[0])')"; echo "  A: $A"
echo "$A" | grep -q "PYV 3.13" && pass "interpreter runs" || fail "interpreter"
B="$(run /data/local/tmp/testb.py)"; echo "  B: $B"
echo "$B" | grep -q "imports OK" && pass "ssl/ctypes/socket/... import" || fail "imports"
C="$(run /data/local/tmp/yt-dlp.pyz --version)"; echo "  C: yt-dlp $C"
echo "$C" | grep -qE "^[0-9]{4}\." && pass "yt-dlp runs" || fail "yt-dlp --version"
D="$(run /data/local/tmp/yt-dlp.pyz --no-warnings --simulate --skip-download -e "$TEST_VIDEO")"; echo "  D: extract -> $D"
[ -n "$D" ] && pass "yt-dlp extracts from YouTube" || fail "youtube extraction"

[ "${KEEP:-0}" = 1 ] || "$ADB" emu kill >/dev/null 2>&1 || true
[ "$FAILED" = 0 ] && say "RESULT: PASS" || { say "RESULT: FAIL"; exit 1; }
