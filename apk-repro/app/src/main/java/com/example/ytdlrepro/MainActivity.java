package com.example.ytdlrepro;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Minimal reproduction of youtubedl-android issue #304:
 * on Android 6.0 (API 23) the bundled CPython fails to load
 * ("cannot locate symbol ... referenced by libpython"). We call init() then getInfo(),
 * which execs the python binary and triggers the linker failure. The TAG "YTDLREPRO"
 * and the embedded markers make the result easy to grep out of logcat.
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
            String result;
            try {
                Log.i(TAG, "MARKER init-start");
                YoutubeDL.getInstance().init(getApplication());
                Log.i(TAG, "MARKER init-ok; getInfo-start");
                VideoInfo info = YoutubeDL.getInstance().getInfo(new YoutubeDLRequest(URL));
                result = "RESULT_OK title=" + (info != null ? info.getTitle() : "<null>");
            } catch (Throwable t) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                result = "RESULT_FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage();
                Log.e(TAG, "MARKER exception\n" + sw);
            }
            final String r = result;
            try { // also write full result to a pullable file (logcat truncates long lines)
                java.io.File f = new java.io.File(getExternalFilesDir(null), "result.txt");
                java.io.FileWriter fw = new java.io.FileWriter(f);
                fw.write(r); fw.close();
            } catch (Throwable ignored) {}
            Log.i(TAG, "MARKER " + r);
            runOnUiThread(() -> tv.setText(r));
        }, "ytdl-repro").start();
    }
}
