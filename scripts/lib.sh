#!/usr/bin/env bash
# Common config + helpers for the API-23 yt-dlp reproduction scripts.
# Override any of ANDROID_HOME / NDK / WORK via the environment.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
WORK="${WORK:-$ROOT/work}"
PROOF="${PROOF:-$ROOT/proof}"
mkdir -p "$WORK" "$PROOF"

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/code/android-sdk}}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
NDK="${NDK:-$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)}"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"

TERMUX_POOL="https://packages.termux.dev/apt/termux-main/pool/main"
# libc functions Bionic only added at API 24+, that the shim backfills:
SHIM_FUNCS="lockf preadv pwritev getifaddrs freeifaddrs if_nameindex if_freenameindex memfd_create"
# a stable, tiny video for the extraction test ("Me at the zoo", the first YouTube video)
TEST_VIDEO="https://www.youtube.com/watch?v=jNQXAC9IVRw"

say()  { printf '\n== %s ==\n' "$*"; }
pass() { printf 'PASS: %s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*"; FAILED=1; }

# termux deb pool prefix: libfoo -> libf, foo -> f
poolpre(){ case "$1" in lib*) echo "${1:0:4}";; *) echo "${1:0:1}";; esac; }
termux_prefix(){ echo "$WORK/termux-$1/data/data/com.termux/files/usr"; }  # $1 = arch
