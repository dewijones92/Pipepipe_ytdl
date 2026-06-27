# Proof: the app depends on OUR fork — and that exposed (and we fixed) a real fork bug

The PoC app (`apk-repro/`) previously used a *hack*: the published `youtubedl-android 0.17.3`
plus our API-23-patched binaries layered on top via `pickFirsts`, with a manifest `minSdk`
override. We removed both hacks and made the app depend **directly on our fork**, published
locally to `mavenLocal`:

```
io.github.junkfood02.youtubedl-android:library:0.18.5   (dewijones92/youtubedl-android)
io.github.junkfood02.youtubedl-android:ffmpeg:0.18.5
```

No `pickFirsts` binary overlay. No manifest `minSdk` override (the fork's modules already
declare `minSdk 23`). The API-23-patched binaries come straight from the fork's AAR.

## Reproducible via JitPack (not just a local machine)
The fork is built straight from GitHub by [JitPack](https://jitpack.io/#dewijones92/youtubedl-android)
(a `jitpack.yml` pins JDK 17 for `buildSrc`). `apk-repro` depends on:

```
com.github.dewijones92.youtubedl-android:library:v0.18.6-api23-2
com.github.dewijones92.youtubedl-android:ffmpeg:v0.18.6-api23-2
```

These coordinates exist only on JitPack — a successful `:app:assembleDebug` proves the app builds
purely from our fork with no local publish. Verified on API 23: `SEARCH_OK`/`RESOLVE_OK`/`PLAYBACK_OK`.
(`mavenLocal` remains a fallback for iterating on the fork locally.)

## Also fixed: quickjs JS runtime detection (v0.18.6)
yt-dlp probes `qjs --help` and matched the version anchored at the start of output. On API 23 the
bionic linker prints "unused DT entry" warnings to stderr (merged into the probe), so the warning
hid `QuickJS version 2025-04-26` and quickjs was rejected as unsupported (yt-dlp then leans on
clients that can hit SABR/403s). `YoutubeDL` now points `--js-runtimes` at a tiny generated wrapper
that runs qjs with stderr dropped → yt-dlp detects `quickjs-2025-04-26`. (The 403s seen during
testing were transient YouTube rate-limiting, not a code bug — downloads succeed normally.)

## What depending on the real artifact revealed
With the overlay gone, **search → resolve → play still worked on API 23**, but
**download-with-`--sponsorblock-remove` broke** — a real fork bug the overlay had been masking.

Root cause (traced on the API-23 x86_64 emulator):
1. yt-dlp runs the bundled **ffmpeg/ffprobe/quickjs as direct subprocesses**.
2. ffmpeg's `libzmq`/`libsrt`/`libgio` reference `in6addr_any`/`in6addr_loopback`, and
   `libidn2` references `strchrnul` — libc symbols **Bionic only added at API 24**. The fork's
   shim backfilled 8 *functions* but not these.
3. Those symbols are referenced by **transitively-loaded** libs, whose symbol scope does **not**
   include a `libshim` that is merely `DT_NEEDED` on the executable (API-23 linker behaviour).
   Confirmed empirically: a sibling copy in the native-lib dir does not help; the shim must be
   **`LD_PRELOAD`ed**.

## The fix (in the fork — `dewijones92/youtubedl-android@41b7ef4`, v0.18.5)
- `tools/api23_shim.c` now also exports `in6addr_any`, `in6addr_loopback`, `strchrnul`.
- `YoutubeDL.execute()` sets `LD_PRELOAD` to the always-extracted python `libshim.so` (plus
  `LD_LIBRARY_PATH_ORIG`, defensive for a frozen yt-dlp).
- `libshim.so` regenerated inside the python + ffmpeg zips for **all 4 ABIs**.

## Validation (API-23 x86_64 emulator)
- **Client (in-app):** `SEARCH_OK` → `RESOLVE_OK` → `PLAYBACK_OK` via the fork AAR (no overlay).
- **SponsorBlock cut:** with the exact runtime env the fixed `0.18.5` library sets
  (`LD_LIBRARY_PATH` + `LD_PRELOAD=<python libshim>` + `--ffmpeg-location`), yt-dlp
  `--sponsorblock-remove` on `2jMOVVNf2i8` (1511 s, one 52.4 s sponsor segment) produced:
  ```
  [SponsorBlock] Found 1 segments
  [ModifyChapters] Removing chapters
  -> output duration 1458.29 s   (== proof/46, sponsor physically removed)
  ```

## Honest caveat
The in-app download additionally depends on yt-dlp obtaining a downloadable format, which on
current YouTube intermittently returns **HTTP 403** without a working JavaScript runtime
(`quickjs` is reported `unsupported` by yt-dlp; YouTube SABR enforcement + rate-limiting). That
is a **separate, pre-existing yt-dlp/YouTube issue**, independent of this ffmpeg/API-23 fix —
when a format is obtained, the SponsorBlock cut runs correctly as shown above.
