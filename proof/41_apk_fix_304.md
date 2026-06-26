# Proof: youtubedl-android #304 FIXED in a real APK on Android 6.0 (API 23)

Same app (`apk-repro/`), same API-23 x86_64 emulator. After applying the fixes below,
`YoutubeDL.getInfo()` extracts a real YouTube video:

```
RESULT_OK title=Me at the zoo
```

## The fixes (two distinct API-23 incompatibilities + one staleness issue)

Reproducible via `scripts/50_build_fixed_python.sh` (patches the bundled CPython 3.11 package).

1. **Missing libc symbols (issue #304 itself).** CPython 3.11 is built for API 24+ and references
   `lockf` (and `preadv`/`pwritev`; `__aeabi_memcpy` on armeabi-v7a) which API-23 Bionic doesn't
   export, and the bundled `libandroid-support.so` doesn't backfill. **Fix:** ship an
   `LD_PRELOAD`-style shim (`shim/api23_shim.c`) and add it as a `DT_NEEDED` of `libpython3.11.so.1.0`
   (`patchelf --add-needed libshim.so`). → resolves *"cannot locate symbol lockf"*.

2. **Segment-ends-at-EOF rejected by the old linker.** After (1), some bundled `.so`s (e.g.
   pycryptodome's `_raw_ecb.abi3.so`) failed with *"invalid ELF file … load segment past end of
   file"* — the API-23 linker won't map a final LOAD segment whose `p_offset+p_filesz` reaches EOF
   (newer Bionic zero-fills; old doesn't). **Fix:** page-pad every `.so` to a 4096-byte boundary
   (append zeros). → all extension modules load.

3. **Stale bundled yt-dlp.** With (1)+(2), Python + yt-dlp run fully and reach YouTube over TLS, but
   the AAR's ~2024 yt-dlp hits YouTube's current anti-bot wall:
   `HTTP 400 … ERROR: Please sign in`. Not an API-23 issue (same on any device). **Fix:** drop in the
   latest yt-dlp (override `R.raw.ytdlp`). → `RESULT_OK title=Me at the zoo`.

## Evidence chain (logcat / pulled result.txt)
```
baseline:        CANNOT LINK EXECUTABLE: cannot locate symbol "lockf" referenced by libpython3.11.so.1.0
+ shim NEEDED:   OSError: Cannot load native module ... _raw_ecb.abi3.so: load segment past end of file
+ page-pad:      ERROR: [youtube] Please sign in   (HTTP 400 — stale bundled yt-dlp; all modules loaded)
+ latest yt-dlp: RESULT_OK title=Me at the zoo
```

## What this means for upstream (#304)
Android-6 (API 23) support for youtubedl-android is recoverable. The library-side fix is to (a)
backfill the few API-24 libc symbols in the bundled `libandroid-support.so` (or ship the shim and
reference it), and (b) page-pad the bundled `.so`s. Both are mechanical and verified here.
