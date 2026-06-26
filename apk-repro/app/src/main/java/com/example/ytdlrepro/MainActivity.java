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
import androidx.media3.ui.PlayerView;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import com.yausername.youtubedl_android.mapper.VideoFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PoC: a yt-dlp-backed YouTube *client loop* on Android 6.0 (API 23):
 *   yt-dlp SEARCH -> resolve the top result -> ExoPlayer PLAY.
 * Auto-runs a query so it's verifiable headlessly. Reports SEARCH / RESOLVE / PLAYBACK markers.
 */
public class MainActivity extends Activity {
    static final String TAG = "YTDLREPRO";
    static final String QUERY = "lofi hip hop";
    private TextView tv;
    private final StringBuilder report = new StringBuilder();
    private final StringBuilder playerErr = new StringBuilder();
    private long prepNanos = 0L;     // when prepare() was called
    private long readyMs = -1L;      // time-to-first-ready since prepare()

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        tv = new TextView(this); tv.setText("searching..."); setContentView(tv);
        new Thread(() -> {
            long t0 = System.nanoTime();
            try { YoutubeDL.getInstance().init(getApplication()); } catch (Throwable t) {
                report.append("INIT_FAIL ").append(t.getMessage()); finishReport(); return;
            }
            long tInit = ms(t0);
            long t1 = System.nanoTime();
            String[] top = search(QUERY, 8);          // {id, title}
            long tSearch = ms(t1);
            if (top == null) { finishReport(); return; }
            long t2 = System.nanoTime();
            String url = resolve(top);                // resolve the top result
            long tResolve = ms(t2);
            if (url == null) { finishReport(); return; }
            report.append("TIMING init=").append(tInit).append("ms search=").append(tSearch)
                  .append("ms resolve=").append(tResolve).append("ms\n");
            runOnUiThread(() -> startPlayback(url));
        }, "client").start();
    }

    /** Elapsed milliseconds since a System.nanoTime() mark. */
    private static long ms(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000L; }

    /** yt-dlp search -> list of results; returns the top {id,title} or null. */
    private String[] search(String query, int n) {
        try {
            YoutubeDLRequest req = new YoutubeDLRequest("ytsearch" + n + ":" + query);
            req.addOption("--flat-playlist");
            req.addOption("--print", "%(id)s|%(title)s");
            YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);
            List<String[]> results = new ArrayList<>();
            for (String line : resp.getOut().split("\n")) {
                line = line.trim();
                int bar = line.indexOf('|');
                if (bar > 0) results.add(new String[]{line.substring(0, bar), line.substring(bar + 1)});
            }
            if (results.isEmpty()) { report.append("SEARCH_FAIL no results\n"); return null; }
            report.append("SEARCH_OK n=").append(results.size())
                  .append(" top='").append(results.get(0)[1]).append("'\n");
            for (int i = 1; i < Math.min(results.size(), 4); i++)
                report.append("   #").append(i + 1).append(" ").append(results.get(i)[1]).append("\n");
            return results.get(0);
        } catch (Throwable t) {
            report.append("SEARCH_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n");
            return null;
        }
    }

    /** Resolve a video id -> playable stream URL via yt-dlp. */
    private String resolve(String[] idTitle) {
        try {
            YoutubeDLRequest req = new YoutubeDLRequest("https://www.youtube.com/watch?v=" + idTitle[0]);
            req.addOption("-f", "18/best[ext=mp4][height<=?480]/best");
            VideoInfo info = YoutubeDL.getInstance().getInfo(req);
            String u = info.getUrl();
            if ((u == null || u.isEmpty()) && info.getFormats() != null && !info.getFormats().isEmpty())
                u = info.getFormats().get(info.getFormats().size() - 1).getUrl();
            if (u == null || u.isEmpty()) { report.append("RESOLVE_FAIL no url\n"); return null; }
            report.append("RESOLVE_OK playing='").append(info.getTitle()).append("'\n");
            return u;
        } catch (Throwable t) {
            report.append("RESOLVE_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n");
            return null;
        }
    }

    private void startPlayback(String url) {
        try {
            ExoPlayer player = new ExoPlayer.Builder(this).build();
            PlayerView pv = new PlayerView(this);   // visible video surface
            pv.setUseController(false);
            setContentView(pv);
            pv.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException e) {
                    playerErr.append(e.getErrorCodeName()).append(": ").append(e.getMessage());
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && readyMs < 0) readyMs = ms(prepNanos);
                }
            });
            player.setMediaItem(MediaItem.fromUri(url));
            prepNanos = System.nanoTime();
            player.prepare();
            player.setPlayWhenReady(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                int st = player.getPlaybackState();
                long pos = player.getCurrentPosition();
                String name = st == Player.STATE_READY ? "READY" : st == Player.STATE_BUFFERING ? "BUFFERING"
                        : st == Player.STATE_ENDED ? "ENDED" : "IDLE";
                boolean ok = (st == Player.STATE_READY || st == Player.STATE_ENDED) && pos > 0 && playerErr.length() == 0;
                report.append("PLAYBACK_").append(ok ? "OK" : "FAIL").append(" state=").append(name)
                      .append(" pos=").append(pos).append("ms ttf=").append(readyMs).append("ms");
                if (playerErr.length() > 0) report.append(" err=").append(playerErr);
                finishReport();   // keep the player running so the emulator screenshot captures live video
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
