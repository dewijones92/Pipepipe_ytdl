package com.example.ytdlrepro;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import com.yausername.youtubedl_android.mapper.VideoFormat;

import java.io.File;
import java.net.URL;

/**
 * PoC: "yt-dlp as the YouTube backend" on Android 6.0 (API 23), end to end:
 *   yt-dlp resolves a stream URL on-device  ->  ExoPlayer (media3) plays it.
 * Reports RESOLVE_* then PLAYBACK_* (state + position) to result.txt / logcat(TAG=YTDLREPRO).
 */
public class MainActivity extends Activity {
    static final String TAG = "YTDLREPRO";
    static final String VIDEO = "https://www.youtube.com/watch?v=jNQXAC9IVRw"; // "Me at the zoo"
    private TextView tv;
    private final StringBuilder report = new StringBuilder();
    private final StringBuilder playerErr = new StringBuilder();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        tv = new TextView(this); tv.setText("resolving..."); setContentView(tv);
        new Thread(() -> {
            String url = resolve();
            if (url != null) runOnUiThread(() -> startPlayback(url));
            else finishReport();
        }, "resolve").start();
    }

    private String resolve() {
        try {
            YoutubeDL.getInstance().init(getApplication());
            YoutubeDLRequest req = new YoutubeDLRequest(VIDEO);
            req.addOption("-f", "18/best[ext=mp4]/best");
            VideoInfo info = YoutubeDL.getInstance().getInfo(req);
            String u = info.getUrl();
            if ((u == null || u.isEmpty()) && info.getFormats() != null)
                for (VideoFormat f : info.getFormats())
                    if ("18".equals(f.getFormatId())) { u = f.getUrl(); break; }
            if (u == null || u.isEmpty()) { report.append("RESOLVE_FAIL no url\n"); return null; }
            report.append("RESOLVE_OK title=").append(info.getTitle())
                  .append(" host=").append(new URL(u).getHost()).append("\n");
            return u;
        } catch (Throwable t) {
            report.append("RESOLVE_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n");
            return null;
        }
    }

    private void startPlayback(String url) {
        try {
            tv.setText(report + "\nplaying...");
            ExoPlayer player = new ExoPlayer.Builder(this).build();
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException e) {
                    playerErr.append(e.getErrorCodeName()).append(": ").append(e.getMessage());
                }
            });
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.setPlayWhenReady(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                int st = player.getPlaybackState();
                long pos = player.getCurrentPosition(), dur = player.getDuration();
                String name = st == Player.STATE_READY ? "READY" : st == Player.STATE_BUFFERING ? "BUFFERING"
                        : st == Player.STATE_ENDED ? "ENDED" : "IDLE";
                boolean ok = (st == Player.STATE_READY || st == Player.STATE_ENDED) && pos > 0 && playerErr.length() == 0;
                report.append("PLAYBACK_").append(ok ? "OK" : "FAIL")
                      .append(" state=").append(name).append(" pos=").append(pos).append("ms dur=").append(dur).append("ms");
                if (playerErr.length() > 0) report.append(" err=").append(playerErr);
                player.release();
                finishReport();
            }, 9000);
        } catch (Throwable t) {
            report.append("PLAYBACK_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            finishReport();
        }
    }

    private void finishReport() {
        final String r = report.toString();
        try {
            File f = new File(getExternalFilesDir(null), "result.txt");
            java.io.FileWriter w = new java.io.FileWriter(f); w.write(r); w.close();
        } catch (Throwable ignored) {}
        Log.i(TAG, "MARKER\n" + r);
        runOnUiThread(() -> tv.setText(r));
    }
}
