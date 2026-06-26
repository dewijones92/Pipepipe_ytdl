# Proof: youtubedl-android #304 reproduced in a real APK on Android 6.0 (API 23)

App: `apk-repro/` (depends on `io.github.junkfood02.youtubedl-android:library:0.17.3`, minSdk 23,
x86_64). Device: API-23 x86_64 emulator (`sbtest23`). The app calls `YoutubeDL.init()` then
`getInfo()`; init extracts the bundled Python, getInfo exec's it → the linker rejects it.

## Captured failure (logcat, TAG=YTDLREPRO)
```
I YTDLREPRO: MARKER init-start
I YTDLREPRO: MARKER init-ok; getInfo-start
W linker : /data/app/com.example.ytdlrepro-2/lib/x86_64/libpython.so: unused DT entry: type 0x1d arg 0x4d
W linker : .../python/usr/lib/libandroid-support.so: unused DT entry: type 0x1d arg 0x13f
W linker : .../python/usr/lib/libpython3.11.so.1.0: unused DT entry: type 0x1d arg 0x9024
E YTDLREPRO: com.yausername.youtubedl_android.YoutubeDLException:
   CANNOT LINK EXECUTABLE: cannot locate symbol "lockf"
   referenced by ".../python/usr/lib/libpython3.11.so.1.0"...
   page record for 0x7fc342fdf010 was not found (block_size=128)
        at com.yausername.youtubedl_android.YoutubeDL.execute(YoutubeDL.kt:232)
        at com.yausername.youtubedl_android.YoutubeDL.getInfo(YoutubeDL.kt:112)
I YTDLREPRO: MARKER RESULT_FAIL YoutubeDLException: ... cannot locate symbol "lockf" ...
```

## This matches issue #304 exactly
Original #304 (armeabi-v7a, Android 6.0.1):
```
WARNING: linker: libpython.so: unused DT entry: type 0x1d arg 0x4d
WARNING: linker: libandroid-support.so: unused DT entry: type 0x1d arg 0x151
WARNING: linker: libpython3.11.so.1.0: unused DT entry: type 0x1d arg 0x90f7
CANNOT LINK EXECUTABLE: cannot locate symbol "__aeabi_memcpy" referenced by libpython3.11.so.1.0
page record for ... was not found (block_size=128)
```
Same `unused DT entry` warnings, same `CANNOT LINK EXECUTABLE: cannot locate symbol … referenced
by libpython3.11.so.1.0`, same `page record … not found`. Only the missing symbol differs by ABI
(`lockf` on x86_64 vs `__aeabi_memcpy` on armeabi-v7a) — same root cause: the bundled CPython 3.11
is built for API 24+ and references libc symbols absent from API-23 Bionic, and the bundled
`libandroid-support.so` does not backfill them.

## Next: the fix
Provide the missing symbols in libpython's resolution scope (the bundled `libandroid-support.so`'s
job) so the interpreter links on API 23, then `getInfo()` should extract normally. See `shim/`.
