#!/usr/bin/env bash
# Static proof: which symbols the real CPython needs beyond API-23 base libs,
# and that the shim covers them. Catches unversioned API-24 additions too.
# Needs: 00_fetch_deps.sh + 01_build.sh first.
source "$(dirname "$0")/lib.sh"
FAILED=0

gap_report(){ # $1 arch(x86_64|arm64)  $2 ndk-triple  $3 termux-arch
  local arch="$1" triple="$2" tarch="$3"
  local SYS="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple/23"
  local lp; lp="$(termux_prefix "$tarch")/lib/libpython3.13.so"
  [ -f "$lp" ] || { fail "$arch: missing $lp (run 00_fetch_deps.sh)"; return; }
  local base und gap shimexp resid
  base="$(for l in libc libm libdl liblog; do "$TC/llvm-nm" -D --defined-only "$SYS/$l.so" 2>/dev/null | awk '{print $NF}'; done | sed 's/@.*//' | sort -u)"
  und="$("$TC/llvm-nm" -D --undefined-only "$lp" | awk '{print $NF}' | sed 's/@.*//' | grep -v '^$' | sort -u)"
  gap="$(comm -23 <(echo "$und") <(echo "$base"))"
  shimexp="$("$TC/llvm-nm" -D --defined-only "$WORK/bin/libshim-$arch.so" 2>/dev/null | awk '$2 ~ /[TtWw]/{print $NF}' | sort -u)"
  resid="$(comm -23 <(echo "$gap") <(echo "$shimexp"))"
  echo "  $arch  libpython gap vs API-23 base libs : $(echo $gap)"
  if [ -z "$resid" ]; then pass "$arch: shim covers the entire gap"; else fail "$arch: UNCOVERED -> $(echo $resid)"; fi
}

say "Cross-arch symbol gap analysis"
gap_report x86_64 x86_64-linux-android  x86_64
gap_report arm64  aarch64-linux-android aarch64

[ "$FAILED" = 0 ] && say "RESULT: PASS" || { say "RESULT: FAIL"; exit 1; }
