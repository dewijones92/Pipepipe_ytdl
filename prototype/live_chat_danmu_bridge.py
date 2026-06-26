#!/usr/bin/env python3
"""
Prototype: bridge yt-dlp's incrementally-written `live_chat.json` into a real-time danmu
(bullet-comment) event stream — the "last mile" between yt-dlp and a PipePipe-style overlay.

yt-dlp delivers YouTube live chat as a *file* it appends to (a downloader), not a callback API.
This shows that's enough: launch yt-dlp's live-chat download, tail the growing file, and emit
one danmu event per message as it arrives. `parse_action()` is the portable core — the same
logic runs in Kotlin on Android against youtubedl-android's live_chat output.

Usage:   YTDLP=path/to/yt-dlp.pyz  ./live_chat_danmu_bridge.py <youtube-live-url-or-id> [tail_secs]
Needs:   yt-dlp + a JS runtime (node) for current YouTube extraction.
"""
import sys, os, json, time, subprocess, tempfile, shutil, signal


def parse_action(obj, out):
    """Recursively extract live-chat text messages -> (ts_epoch, author, text). Portable core."""
    if isinstance(obj, dict):
        r = obj.get("liveChatTextMessageRenderer")
        if r:
            text = "".join(run.get("text", "") for run in r.get("message", {}).get("runs", []))
            author = r.get("authorName", {}).get("simpleText", "?")
            ts = r.get("timestampUsec")
            out.append((int(ts) / 1e6 if ts else None, author, text))
        for v in obj.values():
            parse_action(v, out)
    elif isinstance(obj, list):
        for v in obj:
            parse_action(v, out)


def on_danmu(ts, author, text):
    """Sink — an app would push this onto the bullet-comment overlay."""
    print(f"DANMU [{time.strftime('%H:%M:%S')}] {author}: {text}", flush=True)


def main():
    if len(sys.argv) < 2:
        print("usage: live_chat_danmu_bridge.py <url-or-id> [tail_secs]"); sys.exit(2)
    target = sys.argv[1]
    if "://" not in target:
        target = "https://www.youtube.com/watch?v=" + target
    tail_secs = int(sys.argv[2]) if len(sys.argv) > 2 else 25
    ytdlp = os.environ.get("YTDLP", "yt-dlp")
    runner = [sys.executable, ytdlp] if ytdlp.endswith(".pyz") else [ytdlp]

    work = tempfile.mkdtemp(prefix="danmu_")
    chatfile = os.path.join(work, "chat.live_chat.json")
    cmd = runner + ["--quiet", "--no-warnings", "--js-runtimes", "node",
                    "--skip-download", "--write-subs", "--sub-langs", "live_chat",
                    "--no-part", "-o", os.path.join(work, "chat.%(ext)s"), target]
    print(f"# yt-dlp live chat -> {target}", flush=True)
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    start_deadline = time.time() + 30
    while not os.path.exists(chatfile) and time.time() < start_deadline and proc.poll() is None:
        time.sleep(0.3)
    if not os.path.exists(chatfile):
        print("# no live_chat file (stream not live / extraction failed)")
        proc.terminate(); shutil.rmtree(work, ignore_errors=True); sys.exit(1)

    count, deadline = 0, time.time() + tail_secs
    with open(chatfile, encoding="utf-8") as f:
        f.seek(0, os.SEEK_END)             # start from 'now' — only stream NEW (live) messages
        while time.time() < deadline:
            line = f.readline()
            if not line:
                if proc.poll() is not None:
                    break
                time.sleep(0.2); continue
            line = line.strip()
            if not line:
                continue
            msgs = []
            try:
                parse_action(json.loads(line), msgs)
            except Exception:
                continue
            for ts, author, text in msgs:
                if text:
                    on_danmu(ts, author, text); count += 1

    proc.send_signal(signal.SIGINT)
    try:
        proc.wait(timeout=5)
    except Exception:
        proc.kill()
    shutil.rmtree(work, ignore_errors=True)
    print(f"# emitted {count} live danmu events in ~{tail_secs}s", flush=True)


if __name__ == "__main__":
    main()
