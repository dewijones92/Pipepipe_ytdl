#!/usr/bin/env bash
# Fetch everything the runtime tests need (into $WORK, all git-ignored):
#   - Termux CPython 3.13 + dependency closure, for x86_64 AND aarch64
#   - the official yt-dlp zipapp
#   - qemu-aarch64-static (extracted from the .deb, NOT installed -> no sudo)
source "$(dirname "$0")/lib.sh"

PKGS="python openssl ca-certificates libffi liblzma libbz2 libsqlite zlib libandroid-support libiconv ncurses readline"

fetch_deb(){ # $1 pkg  $2 arch  $3 destdir
  local pkg="$1" arch="$2" dest="$3" pre url deb
  pre="$(poolpre "$pkg")"; url="$TERMUX_POOL/$pre/$pkg/"
  deb="$(curl -fsSL "$url" 2>/dev/null | grep -oE "${pkg}_[0-9][^\"_]*_(${arch}|all)\.deb" | sort -u | tail -1 || true)"
  [ -z "$deb" ] && { echo "  WARN: no deb for $pkg ($arch)"; return 0; }
  mkdir -p "$dest"
  ( cd "$dest" && curl -fsSL -o "$pkg.deb" "$url$deb" && ar x "$pkg.deb" && tar xf data.tar.* \
    && rm -f "$pkg.deb" data.tar.* control.tar.* debian-binary )
  echo "  $arch  $pkg <- $deb"
}

for arch in x86_64 aarch64; do
  dest="$WORK/termux-$arch"
  if [ -d "$dest/data" ]; then
    echo "  $arch: already present (skip) -> $dest"
  else
    say "Fetch Termux python + deps ($arch)"
    for p in $PKGS; do fetch_deb "$p" "$arch" "$dest"; done
  fi
done

say "Fetch yt-dlp zipapp"
[ -f "$WORK/yt-dlp.pyz" ] || curl -fsSL -o "$WORK/yt-dlp.pyz" \
  https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
echo "  yt-dlp.pyz $(du -h "$WORK/yt-dlp.pyz" | cut -f1)"

say "Fetch qemu-aarch64-static (extract, no install)"
if [ ! -x "$WORK/qemu/usr/bin/qemu-aarch64-static" ]; then
  ( cd "$WORK" && apt-get download qemu-user-static \
    && deb="$(printf '%s\n' qemu-user-static_*.deb | head -1)" \
    && rm -rf qemu && mkdir qemu && dpkg-deb -x "$deb" qemu && rm -f "$deb" )
fi
"$WORK/qemu/usr/bin/qemu-aarch64-static" --version | head -1

say "All deps ready under $WORK"
