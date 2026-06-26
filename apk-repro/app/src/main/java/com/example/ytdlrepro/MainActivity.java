package com.example.ytdlrepro;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import com.yausername.ffmpeg.FFmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * API-23 (Android 6.0) validation for the patched youtubedl-android:
 *   1. FFMPEG test — init FFmpeg (on-device unzip of the patched libffmpeg.zip.so) and
 *      exec the ffmpeg binary on a real lavfi encode; proves all ~165 ffmpeg libs load
 *      and encode on API 23.
 *   2. YTDLP test — init + getInfo (python extraction) regression.
 * Results are logged (TAG=YTDLREPRO) and written to getExternalFilesDir()/result.txt.
 */
public class MainActivity extends Activity {
    static final String TAG = "YTDLREPRO";
    static final String URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"; // "Me at the zoo"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final TextView tv = new TextView(this);
        tv.setText("running...");
        setContentView(tv);

        new Thread(() -> {
            String r = ffmpegTest() + "\n\n" + ytdlpTest();
            try {
                File f = new File(getExternalFilesDir(null), "result.txt");
                java.io.FileWriter fw = new java.io.FileWriter(f);
                fw.write(r); fw.close();
            } catch (Throwable ignored) {}
            Log.i(TAG, "MARKER\n" + r);
            final String rr = r;
            runOnUiThread(() -> tv.setText(rr));
        }, "repro").start();
    }

    private String ffmpegTest() {
        try {
            Log.i(TAG, "MARKER ffmpeg-init");
            FFmpeg.getInstance().init(getApplication());
            String binDir = getApplicationInfo().nativeLibraryDir;
            String ffmpegBin = binDir + "/libffmpeg.so";
            File ffLib = new File(getNoBackupFilesDir(), "youtubedl-android/packages/ffmpeg/usr/lib");
            File outFile = new File(getFilesDir(), "ff_out.mp4");
            outFile.delete();
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegBin, "-hide_banner", "-nostdin",
                    "-f", "lavfi", "-i", "testsrc=duration=1:size=128x128:rate=10",
                    "-c:v", "mpeg4", "-y", outFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.environment().put("LD_LIBRARY_PATH", ffLib.getAbsolutePath() + ":" + binDir);
            Log.i(TAG, "MARKER ffmpeg-exec " + ffmpegBin);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("WARNING: linker:")) continue; // benign API-23 DT_RUNPATH noise
                    out.append(line).append("\n");
                }
            }
            int exit = p.waitFor();
            boolean ok = exit == 0 && outFile.exists() && outFile.length() > 0;
            String tail = out.toString().trim();
            if (tail.length() > 1200) tail = tail.substring(tail.length() - 1200);
            return "FFMPEG_" + (ok ? "OK" : "FAIL") + " exit=" + exit
                    + " out=" + (outFile.exists() ? outFile.length() + "B" : "absent")
                    + "\n--- ffmpeg output tail ---\n" + tail;
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "MARKER ffmpeg-exception\n" + sw);
            return "FFMPEG_FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String ytdlpTest() {
        try {
            YoutubeDL.getInstance().init(getApplication());
            VideoInfo info = YoutubeDL.getInstance().getInfo(new YoutubeDLRequest(URL));
            return "YTDLP_OK title=" + (info != null ? info.getTitle() : "<null>");
        } catch (Throwable t) {
            return "YTDLP_FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }
}
