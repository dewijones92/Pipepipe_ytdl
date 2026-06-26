# Design: SponsorBlock-on-download for PipePipe

**Status: designed, not implemented.** This is the original goal and the clean upstream-PR target
(GitHub issue #1088, "use sponsorblock during downloading").

## Goal
When downloading, physically cut SponsorBlock segments out of the saved media file, so e.g. a
downloaded audio playlist plays without sponsor reads. Gated by a setting; reuse the segment data
the app already fetches for playback.

## Why FFmpeg, not yt-dlp
yt-dlp's `--sponsorblock-remove` isn't available: PipePipe added yt-dlp in Jan 2025 and then
**dropped it** (the `YtdlpHelper` is dead/commented-out in v5.2.0). The shipped app downloads via the
NewPipe **"giga" downloader** and already bundles **FFmpegKit** (used by the BiliBili/NicoNico muxers
and the HLS path). So the natural implementation is a new FFmpeg post-processing pass.

## Download architecture (v5.2.0)
`DownloadDialog` → `DownloadManagerService.startMission` → `DownloadMission` → `notifyFinished`
→ `doPostprocessing` → `psAlgorithm.run()`. Post-processing is a plugin model (`Postprocessing`
+ `getAlgorithm(name,args)`), one instance + one `process()` per mission. FFmpegKit is invoked over
SAF via `FFmpegKitConfig.getSafParameter(...)`.

## Segment source
`StreamInfo.getSponsorBlockSegments()` — populated by the extractor during extraction; the dialog
already holds `currentInfo`, so segments are available with **no re-fetch**. Fields:
`uuid / startTime / endTime` (milliseconds in the client) `/ category / action`. Filter to
`action == SKIP`, real uuids (drop `TEMP`/empty + POI/highlight points), and to categories whose
existing `sponsor_block_category_<cat>_key` pref is enabled.

## The cut (FFmpeg strategy)
SponsorBlock segments are *interior* intervals → not a single trim. Invert to the complement
"keep-ranges" over `[0, duration]`, cut each with `-c copy`, then concat:
1. per keep-range: `-ss S -to E -i in -c copy -avoid_negative_ts make_zero piece_i`
2. `-f concat -safe 0 -i list -c copy -movflags +faststart out`

Stream-copy = fast + lossless, boundaries snap to the nearest keyframe (acceptable for v1; re-encode
would be frame-accurate but slow/lossy and is new territory for this codebase). **Safety:** run on a
scratch copy, check `ReturnCode.isSuccess`, overwrite the original only on success (the existing
muxers don't check return codes — don't repeat that).

## Three download paths (all must be covered or it silently no-ops)
1. **DASH video+audio** (has post-processing) → append the cut to the tail of `Postprocessing.run`.
2. **Progressive single-file a/v** (`psName == null`, no post-processing runs) → a dedicated
   `SponsorBlockCutPass` so `notifyFinished` triggers `doPostprocessing`.
3. **HLS** (bypasses post-processing entirely) → hook between `remuxWithFfmpeg` and
   `copyOutputToStorage` in `HlsDownloader.kt` (pure File-based, easiest).

(Worth investigating: a single hook at the mission-finished point, since all three paths converge on
writing the final file into `mission.storage`.)

## Files
- **create** `postprocessing/SponsorBlockCutter.java` (shared FFmpeg cutter), `postprocessing/SponsorBlockCutPass.java`
- **modify** `download/DownloadDialog.java` (+checkbox, read segments at confirm),
  `service/DownloadManagerService.java` (+EXTRA marshalling), `get/DownloadMission.java`
  (+`String sponsorBlockCutSegments`, Serializable, default null), `postprocessing/Postprocessing.java`
  (+`ALGORITHM_SPONSORBLOCK_CUT` + cut tail), `get/HlsDownloader.kt` (+hook),
  `res/layout/download_dialog.xml` (+checkbox), `res/values/settings_keys.xml` +
  `res/xml/download_settings.xml` (new bool pref).

## Open product decisions (proposed defaults)
- Scope: **audio + video** (issue is about audio playlists; code is shared).
- Categories: **reuse the user's existing per-category SponsorBlock prefs**.
- Cut: **stream-copy** (lossless, keyframe-approx) for v1; no re-encode.
- HLS: **included** (YouTube increasingly serves HLS).
- Toggle placement: **Download settings** (works even if playback SponsorBlock is off).

## Test plan (to build alongside)
- Unit: keep-range inversion from segments + duration (edge cases: segment at 0, overlapping, past end).
- Device: download mp4 (DASH), m4a (progressive audio), webm, and an HLS source on an API-35 emulator;
  assert output duration ≈ original − Σ(cut segments) and the file still plays.
