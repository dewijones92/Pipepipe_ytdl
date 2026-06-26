# Proof: youtubedl-android's ffmpeg encodes on Android 6.0 (API 23)

Extends the #304 fix beyond python to the `ffmpeg` module. Same recipe (api23_shim as
`DT_NEEDED` + page-pad every `.so`), plus the ffmpeg package bundles `libc++_shared.so`
(its C++ libs need it). Run in `apk-repro/` on the API-23 x86_64 emulator: the app calls
`FFmpeg.init()` (on-device unzip of the patched 165-lib package) then execs the ffmpeg
binary on a real lavfi encode.

## Captured result (pulled from the app)
```
FFMPEG_OK exit=0 out=7356B
--- ffmpeg output tail ---
Input #0, lavfi, from 'testsrc=duration=1:size=128x128:rate=10':
  Stream #0:0: Video: wrapped_avframe, rgb24, 128x128, 10 fps
Stream mapping:
  Stream #0:0 -> #0:0 (wrapped_avframe (native) -> mpeg4 (native))
Output #0, mp4, to '/data/user/0/com.example.ytdlrepro/files/ff_out.mp4':
  Stream #0:0: Video: mpeg4 (mp4v / 0x7634706D), yuv420p, 128x128, q=2-31, 200 kb/s, 10 fps
frame=   10 fps=0.0 q=2.0 Lsize=       7KiB time=00:00:01.00 bitrate=  58.8kbits/s speed= 108x

YTDLP_OK title=Me at the zoo
```

ffmpeg loaded all ~165 bundled libs and produced a valid 7 KB mp4 on Android 6.0; python
extraction (`YTDLP_OK`) still passes in the same run.

## Upstreamed to the fork
The fork's `master` (dewijones92/youtubedl-android) applies the identical recipe to **all**
native modules — python, ffmpeg, aria2c — via `tools/patch_natives_api23.sh`, with
`library`/`common`/`ffmpeg`/`aria2c`/`app` all at `minSdk 23`. (aria2c is patched by the same
recipe but not separately runtime-tested.) The focused python-only change remains PR #351.
