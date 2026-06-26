#!/usr/bin/env bash
# API-23 x86_64 emulator: prove the linker rejects an API-24-built lib while
# loading an API-23 one. Needs: 01_build.sh + /dev/kvm.  KEEP=1 leaves emu running.
source "$(dirname "$0")/lib.sh"
FAILED=0
say "API-23 x86_64 emulator: dlopen floor test"
boot_emulator "${AVD_X86:-sbtest23}" "system-images;android-23;google_apis;x86_64"

"$ADB" push "$WORK/bin/loader" "$WORK/bin/libneeds24.so" "$WORK/bin/libok23.so" /data/local/tmp/ >/dev/null
"$ADB" shell chmod 755 /data/local/tmp/loader
OK="$( "$ADB" shell 'cd /data/local/tmp && ./loader ./libok23.so'    2>&1 | tr -d '\r')"
N24="$("$ADB" shell 'cd /data/local/tmp && ./loader ./libneeds24.so' 2>&1 | tr -d '\r')"
echo "  libok23    -> $(echo "$OK"  | grep -oE 'DLOPEN_[A-Z]+' | head -1)"
echo "  libneeds24 -> $(echo "$N24" | grep -oE 'DLOPEN_FAIL.*' | head -1)"
echo "$OK"  | grep -q DLOPEN_OK   && pass "API-23 lib loads"             || fail "API-23 lib should load"
echo "$N24" | grep -q DLOPEN_FAIL && pass "API-24 lib rejected on API 23" || fail "API-24 lib should be rejected"

[ "${KEEP:-0}" = 1 ] || "$ADB" emu kill >/dev/null 2>&1 || true
[ "$FAILED" = 0 ] && say "RESULT: PASS" || { say "RESULT: FAIL"; exit 1; }
