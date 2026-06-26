# Pipepipe_ytdl

Experiments and tooling around **PipePipe** (an Android NewPipe fork) and **yt-dlp on old Android**. Two strands:

1. **SponsorBlock-on-download for PipePipe** — *the original goal*: cut SponsorBlock segments out of files at download time. Designed (FFmpeg post-processing approach); **not yet implemented**.
2. **Running yt-dlp on Android 6.0 / API 23** — *a side-quest, now proven*: "can modern yt-dlp run below its supposed API-24 floor?" Answer: **yes**, with a ~8-function shim. Reproducible scripts + evidence live here.

## TL;DR — what's proven

| Target | Environment | yt-dlp result |
|---|---|---|
| **x86_64**, Android 6.0 (API 23) | full Android emulator (real kernel) | extracts real YouTube metadata ✓ |
| **arm64**, Android 6.0 (API 23) | qemu-user on the image's real bionic `libc` | extracts real YouTube metadata ✓ |

Real CPython 3.13 + OpenSSL 3.6.3 + yt-dlp 2026.06.09, on Android-6.0 `libc`, bridged by [`shim/api23_shim.c`](shim/api23_shim.c) — 8 libc functions Bionic only added at API 24+.

## Background — why this isn't trivial

- PipePipe briefly bundled yt-dlp (Jan 2025) then **dropped it** (`drop yt-dlp` commit): a Python runtime bloats the APK and forces `minSdk 24` (Python needs API 24). v5.2.0 ships **no** yt-dlp — it uses NewPipeExtractor + FFmpegKit.
- The "API-24 floor" is real at the binary level: Bionic *version-tags* symbols (e.g. `getifaddrs@LIBC_N`), so a lib built for API 24 won't resolve them on API 23.
- But the gap is tiny and syscall-backed: `lockf, preadv, pwritev, getifaddrs, freeifaddrs, if_nameindex, if_freenameindex, memfd_create`. The shim backfills them — exactly what Termux does via its own `libandroid-support.so`.

## Reproduce

Prereqs: Linux x86_64, Android SDK (cmdline-tools + an API-23 system image), NDK, `/dev/kvm` (for the x86 emulator), `debugfs`, `dpkg-deb`, internet.

```sh
scripts/00_fetch_deps.sh         # termux python+deps (x86_64 & aarch64), yt-dlp.pyz, qemu-user
scripts/01_build.sh              # build shim (x86_64 + arm64) + the dlopen "floor" test bins
scripts/30_symbol_gap.sh         # static: prove the shim covers the >API-23 gap (both arches)
scripts/10_test_floor_x86.sh     # API-23 x86 emulator: API-24 lib is rejected, API-23 lib loads
scripts/11_test_ladder_x86.sh    # API-23 x86 emulator: python -> ssl -> yt-dlp -> YouTube
scripts/20_test_ladder_arm64.sh  # qemu-user: arm64 bionic -> python -> ssl -> yt-dlp -> YouTube
```

Captured evidence is committed under [`proof/`](proof/). See [`docs/findings.md`](docs/findings.md) for the full write-up.

## Honest caveats

- The arm64 run is under **qemu-user**, which routes syscalls to the *host* kernel — so it proves the arm64 *userspace/ABI*, not an Android-6.0 *kernel*. The one untested edge: `memfd_create` (Linux 3.17+) on a real ~2015 device kernel, if ever called (yt-dlp doesn't).
- **"It runs" ≠ "ship it in PipePipe."** Doing that means bundling the Python runtime (tens of MB), maintaining the shim + a custom build, and a yt-dlp that goes stale on-device — which is why the maintainer removed it. Feasible, not advisable.

## Big-picture goals

- Upstream the worthwhile parts via PRs/forks to the appropriate repos.
- Keep reproducible test scripts that prove the claims.
- Document as we go; commit frequently.

## Status

- **yt-dlp-on-API-23**: proven (x86_64 + arm64). Scripts being hardened into this repo for reproducibility.
- **SponsorBlock-on-download**: researched + designed; implementation pending.
