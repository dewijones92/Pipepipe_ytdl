# Prototype: yt-dlp live chat → real-time danmu bridge

Proves the "last mile" for using yt-dlp as a YouTube backend in a streaming client like
PipePipe: yt-dlp delivers YouTube **live chat** as a `live_chat.json` file it *appends to*
(it's a downloader, not a callback API). [`live_chat_danmu_bridge.py`](live_chat_danmu_bridge.py)
shows that's enough — launch yt-dlp's live-chat download, **tail the growing file**, and emit one
danmu (bullet-comment) event per message as it arrives.

## Run
```sh
YTDLP=../work/yt-dlp.pyz ./live_chat_danmu_bridge.py <youtube-live-url-or-id> [tail_secs]
# needs: yt-dlp + node (JS runtime) for current YouTube extraction
```

## Captured output (live synthwave-radio stream, ~25s; handles genericized)
```
DANMU [19:03:55] @viewer_a: messages arrive live, parsed from yt-dlp's growing file
DANMU [19:03:55] @viewer_b: real author + text per event
DANMU [19:03:55] @viewer_c: ...
DANMU [19:04:05] @viewer_d: next poll's batch
# emitted 73 live danmu events in ~25s
```

## What it proves / how it maps to the app
- **`parse_action()` is the portable core** — recursively pulls `liveChatTextMessageRenderer`
  → `(timestamp, author, text)`. The identical ~15 lines run in Kotlin on Android against
  youtubedl-android's `live_chat.json`.
- A PipePipe-style overlay replaces `on_danmu()` with "push onto the bullet-comment view".
- Tailing across the Python-subprocess boundary is the only real glue; the data is all there.

## Honest caveats (measured)
- **Batched cadence:** yt-dlp emits a poll's worth of messages at once (~every few seconds);
  an overlay would time-spread each batch across the interval (trivial). Median inter-message
  gap measured ~2.2s.
- **~tens-of-seconds behind live:** mostly YouTube's own intentional live-chat delay (a native
  danmu client sees the same), not a yt-dlp deficiency.
- **JS runtime required:** current YouTube extraction needs deno/node — so an on-device yt-dlp
  backend bundles Python **and** a JS engine (a dependency fact, separate from runtime perf).

Conclusion: danmu via yt-dlp is a **plumbing** task (file-tail), not a data gap.
