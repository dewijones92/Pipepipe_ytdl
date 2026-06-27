package com.example.ytdlrepro;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import java.io.File;

/**
 * The ORIGINAL goal: take the sponsor out of a file when you download it — on Android 6.0 (API 23).
 * Downloads worst audio of a known video and runs yt-dlp's --sponsorblock-remove, which invokes the
 * bundled ffmpeg to cut the segment. Verifiable: the output duration drops by the sponsor length.
 *
 * Video 2jMOVVNf2i8: duration 1511s, one 'sponsor' segment [64s..116s] = 52.4s -> expect ~1459s out.
 */
public class SponsorBlockActivity extends Activity {
    static final String TAG = "YTDLREPRO";
    static final String VIDEO = "https://www.youtube.com/watch?v=2jMOVVNf2i8";
    private TextView tv;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        tv = new TextView(this); tv.setText("sponsorblock download…"); setContentView(tv);
        new Thread(() -> {
            StringBuilder r = new StringBuilder();
            try {
                YoutubeDL.getInstance().init(getApplication());
                FFmpeg.getInstance().init(getApplication());   // so yt-dlp's --ffmpeg-location resolves
            } catch (Throwable t) { write(r.append("INIT_FAIL ").append(t.getMessage()).toString()); return; }
            try {
                File dir = getExternalFilesDir(null);
                File[] existing = dir.listFiles();
                if (existing != null) for (File f : existing) if (f.getName().startsWith("sb.")) f.delete();

                YoutubeDLRequest req = new YoutubeDLRequest(VIDEO);
                req.addOption("-f", "worstaudio");
                req.addOption("--sponsorblock-remove", "sponsor");
                req.addOption("-o", new File(dir, "sb.%(ext)s").getAbsolutePath());

                long t0 = System.nanoTime();
                YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);
                long ms = (System.nanoTime() - t0) / 1_000_000L;

                for (String line : resp.getOut().split("\n")) {
                    String l = line.trim();
                    if (l.contains("SponsorBlock") || l.contains("Removing chapters")
                            || l.contains("ModifyChapters") || l.contains("[download] Destination"))
                        r.append(l).append("\n");
                }
                File produced = null;
                File[] after = dir.listFiles();
                if (after != null) for (File f : after)
                    if (f.getName().startsWith("sb.") && !f.getName().contains("uncut")) produced = f;
                r.append("SB_DOWNLOAD_OK ms=").append(ms);
                if (produced != null) r.append(" file=").append(produced.getName()).append(" size=").append(produced.length());
                write(r.toString());
            } catch (Throwable t) {
                write(r.append("SB_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).toString());
            }
        }, "sb").start();
    }

    private void write(String s) {
        try {
            File f = new File(getExternalFilesDir(null), "sb_result.txt");
            java.io.FileWriter w = new java.io.FileWriter(f); w.write(s); w.close();
        } catch (Throwable ignored) {}
        Log.i(TAG, "SBMARKER\n" + s);
        runOnUiThread(() -> tv.setText(s));
    }
}
