#!/usr/bin/env bash
# Build the shim (x86_64 + arm64) and the dlopen "floor" demo binaries.
source "$(dirname "$0")/lib.sh"
mkdir -p "$WORK/bin"

say "Build API-23 shim (both arches)"
"$TC/x86_64-linux-android23-clang"  -shared -fPIC -O2 -o "$WORK/bin/libshim-x86_64.so" "$ROOT/shim/api23_shim.c"
"$TC/aarch64-linux-android23-clang" -shared -fPIC -O2 -o "$WORK/bin/libshim-arm64.so"  "$ROOT/shim/api23_shim.c"

say "Build dlopen floor-test binaries (x86_64)"
# libneeds24: built for API 24, references getifaddrs@LIBC_N -> must FAIL to load on API 23
"$TC/x86_64-linux-android24-clang" -shared -fPIC -O2 -o "$WORK/bin/libneeds24.so" "$ROOT/src/needs24.c"
# libok23: built for API 23 -> must load fine
"$TC/x86_64-linux-android23-clang" -shared -fPIC -O2 -o "$WORK/bin/libok23.so"    "$ROOT/src/ok23.c"
# loader: dlopen()s a .so and prints success or the linker error
"$TC/x86_64-linux-android23-clang" -fPIE -pie -O2 -o "$WORK/bin/loader" "$ROOT/src/loader.c" -ldl

echo "  built:"; ls -1 "$WORK/bin"
printf "  shim exports: "
"$TC/llvm-readelf" --dyn-symbols "$WORK/bin/libshim-arm64.so" \
  | grep -oE "($(echo "$SHIM_FUNCS" | tr ' ' '|'))$" | sort -u | tr '\n' ' '; echo
say "Build OK"
