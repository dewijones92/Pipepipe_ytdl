# Pipepipe_ytdl

Proof and tooling for **running yt-dlp on old Android (6.0 / API 23)** — below its supposed API-24 floor — including a working fix for **youtubedl-android [#304](https://github.com/yausername/youtubedl-android/issues/304)** (the "cannot locate symbol … libpython" crash on API 23). Reproducible scripts + evidence live here.

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
scripts/50_build_fixed_python.sh # build the API-23-fixed youtubedl-android python for apk-repro/
# then: cd apk-repro && ./gradlew :app:assembleDebug   # real APK: youtubedl-android #304 fixed on API 23
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

- **yt-dlp-on-API-23**: proven + reproducible. All four suites pass on a real API-23 target — captured in [`proof/`](proof/).
- **youtubedl-android [#304](https://github.com/yausername/youtubedl-android/issues/304) (real APK)**: reproduced *and fixed* on Android 6.0 — [`apk-repro/`](apk-repro/) extracts a YouTube video on API 23 (`RESULT_OK title=Me at the zoo`). Fix builder: [`scripts/50_build_fixed_python.sh`](scripts/50_build_fixed_python.sh); evidence: [`proof/40_apk_repro_304.md`](proof/40_apk_repro_304.md), [`proof/41_apk_fix_304.md`](proof/41_apk_fix_304.md).
- **Upstreamed:** [yausername/youtubedl-android#351](https://github.com/yausername/youtubedl-android/pull/351) — focused PR with the python API-23 fix (shim + page-pad + minSdk 24→23).
- **Full API-23 (fork):** [dewijones92/youtubedl-android@master](https://github.com/dewijones92/youtubedl-android) extends the fix to **ffmpeg + aria2c** too. ffmpeg validated *encoding* (`lavfi testsrc → mpeg4`) on a real API-23 device — see [`proof/42_apk_ffmpeg_api23.md`](proof/42_apk_ffmpeg_api23.md). Canonical recipe: the fork's `tools/patch_natives_api23.sh`.
- **SponsorBlock-on-download (the original goal):** on API 23, on-device yt-dlp `--sponsorblock-remove` + the bundled ffmpeg physically cut the sponsor segment from a downloaded file — verified by duration delta (1511 s → 1458.29 s, matching the host reference exactly). See [`proof/46`](proof/46_apk_sponsorblock_download_api23.md).
- **PoC — yt-dlp *as* the YouTube backend (full client loop):** on API 23, on-device yt-dlp does **search → resolve → play** in ExoPlayer, incl. **live HLS** (`SEARCH_OK`/`RESOLVE_OK`/`PLAYBACK_OK state=READY`) — see [`proof/44`](proof/44_apk_play_api23.md) (progressive) and [`proof/45`](proof/45_apk_search_play_api23.md) (interactive search + live, with screenshots). Plus a live-chat→danmu bridge ([`prototype/`](prototype/)). Feasibility write-up: [`docs/yt-dlp-as-backend.md`](docs/yt-dlp-as-backend.md).

## License

Copyright (C) 2026 dewijones92. Licensed under the **GNU GPL v3.0** — see [`LICENSE`](LICENSE). Aligns with the GPLv3 ecosystem this builds on (NewPipe / PipePipe / youtubedl-android).
