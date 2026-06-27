package com.example.ytdlrepro;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PoC: an *interactive* yt-dlp-backed YouTube mini-client on Android 6.0 (API 23) — one app that
 * unifies both project goals:
 *   type a query -> yt-dlp SEARCH -> tap a result -> RESOLVE -> ExoPlayer PLAY
 *                                 -> long-press a result -> DOWNLOAD with the sponsor cut out.
 * On launch it auto-runs a default query and auto-plays the top hit, so the loop is still
 * verifiable headlessly (writes SEARCH/RESOLVE/PLAYBACK/DOWNLOAD markers to result.txt).
 */
public class MainActivity extends Activity {
    static final String TAG = "YTDLREPRO";
    static final String QUERY = "lofi hip hop";

    private EditText queryBox;
    private ArrayAdapter<String> adapter;
    private final List<String[]> results = new ArrayList<>();   // {id, title}
    private PlayerView playerView;
    private ExoPlayer player;

    private final StringBuilder report = new StringBuilder();
    private final StringBuilder playerErr = new StringBuilder();
    private long prepNanos = 0L;
    private long readyMs = -1L;
    private boolean autoPlayPending = true;   // auto-play the top hit of the first (auto) search

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(buildUi());
        new Thread(() -> {
            try {
                YoutubeDL.getInstance().init(getApplication());
                FFmpeg.getInstance().init(getApplication());   // needed for --sponsorblock-remove (download)
            } catch (Throwable t) {
                report.append("INIT_FAIL ").append(t.getMessage()); finishReport(); return;
            }
            String q = getIntent().getStringExtra("query");
            final String query = (q == null || q.trim().isEmpty()) ? QUERY : q.trim();
            runOnUiThread(() -> { queryBox.setText(query); doSearch(query); });
        }, "init").start();
    }

    /** Programmatic layout: [search box | button] / results list / video surface. */
    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        queryBox = new EditText(this);
        queryBox.setHint("search YouTube");
        queryBox.setSingleLine(true);
        bar.addView(queryBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button go = new Button(this);
        go.setText("Search");
        go.setOnClickListener(v -> doSearch(queryBox.getText().toString().trim()));
        bar.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("tap = play   ·   long-press = download without sponsor");
        hint.setPadding(16, 8, 16, 8);
        root.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> { if (pos < results.size()) resolveAndPlay(pos); });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos < results.size()) downloadNoSponsor(pos);
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        // weight 1 so list + player split the space below the bar (a fixed height bigger
        // than the screen would squeeze the list to zero — the earlier layout bug).
        root.addView(playerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private static long ms(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000L; }

    /** yt-dlp search -> populate the list (off the UI thread). */
    private void doSearch(String query) {
        if (query.isEmpty()) return;
        Toast.makeText(this, "searching: " + query, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            long t1 = System.nanoTime();
            final List<String[]> found = new ArrayList<>();
            try {
                YoutubeDLRequest req = new YoutubeDLRequest("ytsearch8:" + query);
                req.addOption("--flat-playlist");
                req.addOption("--print", "%(id)s|%(title)s");
                YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);
                for (String line : resp.getOut().split("\n")) {
                    line = line.trim();
                    int bar = line.indexOf('|');
                    if (bar > 0) found.add(new String[]{line.substring(0, bar), line.substring(bar + 1)});
                }
            } catch (Throwable t) {
                report.append("SEARCH_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n");
                finishReport(); return;
            }
            long tSearch = ms(t1);
            if (found.isEmpty()) { report.append("SEARCH_FAIL no results\n"); finishReport(); return; }
            report.append("SEARCH_OK n=").append(found.size()).append(" top='").append(found.get(0)[1])
                  .append("' search=").append(tSearch).append("ms\n");
            runOnUiThread(() -> {
                results.clear(); results.addAll(found);
                adapter.clear();
                for (String[] r : found) adapter.add(r[1]);
                adapter.notifyDataSetChanged();
                // First (auto) search can self-drive an action for headless verification:
                //   `--es action download` -> download top result w/o sponsor (long-press equivalent)
                //   default                 -> auto-play top result (`--ez autoplay false` to disable)
                if (autoPlayPending) {
                    autoPlayPending = false;
                    final Handler h = new Handler(Looper.getMainLooper());
                    if ("download".equals(getIntent().getStringExtra("action"))) {
                        h.postDelayed(() -> { if (!results.isEmpty()) downloadNoSponsor(0); }, 1000);
                    } else if (getIntent().getBooleanExtra("autoplay", true)) {
                        h.postDelayed(() -> { if (!results.isEmpty()) resolveAndPlay(0); }, 12000);
                    }
                }
            });
        }, "search").start();
    }

    /** Resolve the tapped result via yt-dlp, then play it. */
    private void resolveAndPlay(int pos) {
        final String[] idTitle = results.get(pos);
        Toast.makeText(this, "resolving: " + idTitle[1], Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            long t2 = System.nanoTime();
            String url;
            try {
                YoutubeDLRequest req = new YoutubeDLRequest("https://www.youtube.com/watch?v=" + idTitle[0]);
                req.addOption("-f", "18/best[ext=mp4][height<=?480]/best");
                VideoInfo info = YoutubeDL.getInstance().getInfo(req);
                url = info.getUrl();
                if ((url == null || url.isEmpty()) && info.getFormats() != null && !info.getFormats().isEmpty())
                    url = info.getFormats().get(info.getFormats().size() - 1).getUrl();
                if (url == null || url.isEmpty()) { report.append("RESOLVE_FAIL no url\n"); finishReport(); return; }
                report.append("RESOLVE_OK playing='").append(info.getTitle()).append("' resolve=").append(ms(t2)).append("ms\n");
            } catch (Throwable t) {
                report.append("RESOLVE_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n");
                finishReport(); return;
            }
            final String furl = url;
            runOnUiThread(() -> startPlayback(furl));
        }, "resolve").start();
    }

    /** The original goal, in-app: download the long-pressed result with the sponsor cut out. */
    private void downloadNoSponsor(int pos) {
        final String[] idTitle = results.get(pos);
        Toast.makeText(this, "downloading (no sponsor): " + idTitle[1], Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dir = getExternalFilesDir(null);
                File[] ex = dir.listFiles();
                if (ex != null) for (File f : ex) if (f.getName().startsWith("dl_")) f.delete();
                YoutubeDLRequest req = new YoutubeDLRequest("https://www.youtube.com/watch?v=" + idTitle[0]);
                req.addOption("-f", "worstaudio");
                req.addOption("--sponsorblock-remove", "sponsor");
                req.addOption("-o", new File(dir, "dl_" + idTitle[0] + ".%(ext)s").getAbsolutePath());
                long t0 = System.nanoTime();
                YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);
                long took = ms(t0);
                int segs = 0; boolean cut = false;
                for (String line : resp.getOut().split("\n")) {
                    if (line.contains("SponsorBlock") && line.contains("Found")) {
                        for (String tok : line.split("\\s+")) try { segs = Integer.parseInt(tok); break; } catch (NumberFormatException ignored) {}
                    }
                    if (line.contains("Removing chapters")) cut = true;
                }
                File produced = null;
                File[] after = dir.listFiles();
                if (after != null) for (File f : after)
                    if (f.getName().startsWith("dl_") && !f.getName().contains("uncut")) produced = f;
                final String msg = "DOWNLOAD_OK segs=" + segs + (cut ? " (sponsor cut)" : "")
                        + (produced != null ? " file=" + produced.getName() + " size=" + produced.length() : "")
                        + " " + took + "ms";
                report.append(msg).append("\n");
                finishReport();
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                report.append("DOWNLOAD_FAIL ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n");
                finishReport();
            }
        }, "dl").start();
    }

    private void startPlayback(String url) {
        try {
            if (player == null) {
                player = new ExoPlayer.Builder(this).build();
                player.addListener(new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException e) {
                        playerErr.append(e.getErrorCodeName()).append(": ").append(e.getMessage());
                    }
                    @Override public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY && readyMs < 0) readyMs = ms(prepNanos);
                    }
                });
                playerView.setPlayer(player);
            }
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
    }
}
