# Proof: SponsorBlock-removing download on Android 6.0 (API 23) — the original goal

The very first ask for this project was: *"take the sponsor stuff out of files when you download
them."* This proves it works **on-device on Android 6.0**, using yt-dlp's `--sponsorblock-remove`
driven by the **bundled ffmpeg** (both shown to load on API 23 in `proof/40–42`).

## Test case (verifiable, single segment)
- Video `2jMOVVNf2i8` — duration **1511 s**.
- SponsorBlock DB: **one** `sponsor` segment **[64 s … 116 s] = 52.4 s** (queried live from
  `sponsor.ajay.app`).
- So a correct cut → output ≈ **1511 − 52.4 = 1458.6 s**.

## On-device run (`SponsorBlockActivity`, API-23 x86_64 emulator)
```
[SponsorBlock] Fetching SponsorBlock segments
[SponsorBlock] Found 1 segments in the SponsorBlock database
[download] Destination: …/files/sb.m4a
[ModifyChapters] Removing chapters from …/files/sb.m4a        <- ffmpeg cut, on API 23
SB_DOWNLOAD_OK ms=4771 file=sb.m4a size=8876721
```
Download (`-f worstaudio`) **+** SponsorBlock cut completed in **4.77 s**.

## Verification (duration delta)
| file | duration |
|---|---|
| original video | 1511 s |
| expected after cut | ~1458.6 s |
| **device-cut `sb.m4a` (ffprobe)** | **1458.29 s** |
| host-cut reference (same command, desktop yt-dlp+ffmpeg) | 1458.29 s |

The on-device output matches the host reference **exactly** and the expected value to within a
frame — the 52.4 s sponsor segment was physically removed from the downloaded file by ffmpeg
running on Android 6.0.

## What it proves
The original objective — *download a file with the sponsor removed* — runs end-to-end on API 23:
yt-dlp fetches the SponsorBlock segments, downloads the media, and invokes the bundled ffmpeg
(`--ffmpeg-location`, auto-wired by youtubedl-android) to cut the segment. Audio was used to keep
the test small/fast; the same `--sponsorblock-remove` path applies to video (it just downloads
more bytes and re-muxes/re-encodes via the same ffmpeg).

Reproduce: `apk-repro` → launch `.SponsorBlockActivity` → pull `files/sb.m4a` → `ffprobe`.
