# Proof: yt-dlp-backed YouTube playback on Android 6.0 (API 23)

End-to-end PoC in `apk-repro/`: yt-dlp resolves a YouTube stream URL **on-device**, then
ExoPlayer (media3) plays it — the core loop a "PipePipe-on-yt-dlp" client would use.

## Result (API-23 x86_64 emulator)
```
RESOLVE_OK  title=Me at the zoo  host=rr2---sn-8vq54vox03-cgnl.googlevideo.com
PLAYBACK_OK state=READY pos=7413ms dur=18947ms
```
- **Resolve:** on-device yt-dlp (patched per #304, no host JS runtime) selected format 18
  (`video/mp4`); an HTTP range probe returned **206 + 256 KB** from googlevideo (live, fetchable).
- **Play:** ExoPlayer reached **`STATE_READY`**, playback position advanced to **7.4 s** of the
  ~19 s clip with **no `PlaybackException`** — i.e. it actually played.

## Why it matters
Confirms the whole feasibility thesis with a *running* artifact, not just analysis: a YouTube
video plays via **yt-dlp + ExoPlayer on Android 6.0**. Combined with the earlier findings
(search/channels/playlists/comments/multi-service + danmu bridge + write-actions sidecar), the
extraction layer that PipePipe/SmartTube each re-implement *can* be yt-dlp.

## Caveats
- Headless verification (state + advancing position); a visible `PlayerView` is the trivial next step.
- Format 18 (progressive) resolves without a JS runtime; adaptive/high-res formats may need
  deno/node on-device (the JS-runtime dependency noted elsewhere).
- Single hardcoded video — the next increment is a real search → pick → play flow.
