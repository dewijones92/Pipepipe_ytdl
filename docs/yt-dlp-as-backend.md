# yt-dlp as the YouTube backend (instead of bespoke extractors)

PipePipe, SmartTube, NewPipe & co each re-implement the brittle "talk to YouTube" layer and
each break whenever YouTube changes ciphers / po-tokens / SABR:

| Project | YouTube engine |
|---|---|
| PipePipe | NewPipeExtractor (Java InnerTube scraping) |
| SmartTube | MediaServiceCore + youtubeapi (own InnerTube client) |
| yt-dlp | Python |

This documents how much of a *client* yt-dlp can actually back, verified against yt-dlp 2026.06.09.

## Capability coverage (verified)
Play (formats/URLs) · metadata · **search** (`youtube:search`) · **channels** (`youtube:tab`) ·
**playlists** · **comments** (`--write-comments`) · subtitles · chapters · **SponsorBlock** ·
live playback · **personalized feeds** subscriptions/recommended/history/notifications (with
`--cookies`) · **multi-service** BiliBili/niconico/SoundCloud (often *more* than NewPipeExtractor) ·
`--mark-watched`.

## Gaps and how they close
1. **Real-time live chat / danmu** — yt-dlp delivers live chat as an incrementally-written
   `live_chat.json`, not a callback API. **Prototyped** (`prototype/`): tail the file → real-time
   danmu events (73 in 25 s, ~2.2 s median cadence). → *plumbing, not a data gap.*
2. **Interactive write/account actions** (like/subscribe/post-comment/playlist-edit) — yt-dlp is
   read-only by design. But its **SAPISIDHASH auth machinery already exists** (`youtube/_base.py`)
   and it already performs an authenticated write (`_mark_watched`). So writes = a thin
   authenticated-InnerTube **sidecar** (POST `like/like`, `subscription/subscribe`,
   `comment/create_comment` … with a per-action token harvested from the read response).
   SmartTube's MediaServiceCore is the existence proof. → *addable sidecar, not a wall.*

## Proof it runs (Android 6.0 / API 23)
- yt-dlp + CPython + ffmpeg patched to load on API 23 (issue #304 fix; `proof/40–42`).
- **End-to-end playback** (`proof/44`): on-device yt-dlp resolves a stream URL → ExoPlayer plays
  it (`PLAYBACK_OK state=READY`, position advancing).
- **Full client loop** (`proof/45`): on-device yt-dlp **search → resolve → play**, incl. **live
  HLS**, with the video visibly rendering in a `PlayerView` (screenshot committed).

## Performance (measured, API-23 x86_64 emulator — `proof/45`)
| phase | time |
|---|---|
| `init` (one-time CPython start) | 504 ms |
| `search` (`ytsearch8`) | 2517 ms |
| `resolve` (1 video) | 2078 ms |
| ExoPlayer time-to-ready | 545 ms |

Tap-to-playing ≈ **~2.5 s**. Each yt-dlp call spawns the Python interpreter and runs the full
extractor, so it's slower than native NewPipeExtractor (sub-second). This is the central UX
tradeoff. Mitigations (persistent Python process, caching, prefetch-on-hover) are plausible but
unproven here. Per the user's steer, perf is not a blocker for *feasibility* — but it's the first
thing a maintainer will raise.

## Verdict
- **PipePipe (anonymous):** capability-complete via yt-dlp — incl. danmu (bridge). No writes needed.
  "Just use yt-dlp" is feasible.
- **SmartTube (logged-in):** reads via yt-dlp; the account write-actions need the InnerTube sidecar.
  Feasible, with that extra module.

## Acknowledged costs (out of scope for *feasibility*, real for *shipping*)
On-device footprint is **Python + a JS runtime** (current YouTube extraction needs deno/node), and
yt-dlp staying read-only means the write sidecar is the integrator's to build. The maintainers
rejected Python-in-app before — so this is "technically feasible," not "they'll adopt it."
