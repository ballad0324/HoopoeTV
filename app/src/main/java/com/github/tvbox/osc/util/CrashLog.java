package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃日志落盘 + 下次启动回显。
 *
 * 目的：没有 USB 调试时也能拿到真实的闪退堆栈。
 * 位置：Android/data/<包名>/files/crash/last_crash.txt
 * 回显：下次进入手机端首页时用对话框显示，可一键复制。
 */
public final class CrashLog {

    private static final String DIR_NAME = "crash";
    private static final String FILE_NAME = "last_crash.txt";
    private static final long SHOW_WINDOW_MS = 24L * 60 * 60 * 1000;

    private static File sDir;
    private static Context sAppContext;

    private CrashLog() {
    }

    public static void install(Context context) {
        sAppContext = context.getApplicationContext();
        try {
            File base = context.getExternalFilesDir(null);
            if (base == null) base = context.getFilesDir();
            sDir = new File(base, DIR_NAME);
            if (!sDir.exists()) sDir.mkdirs();
        } catch (Throwable ignored) {
        }
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                write(ex, "uncaught/" + (thread == null ? "?" : thread.getName()));
                if (previous != null) previous.uncaughtException(thread, ex);
            }
        });
    }

    /** 主动记录一次异常（用于被 catch 住的场景），tag 写清楚发生在哪一步 */
    public static void write(Throwable ex, String tag) {
        try {
            if (sDir == null) return;
            if (!sDir.exists()) sDir.mkdirs();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            pw.println("tag: " + tag);
            pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL
                    + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            pw.println("version: " + versionText());
            pw.println();
            ex.printStackTrace(pw);
            pw.flush();
            File out = new File(sDir, FILE_NAME);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out, false);
            fos.write(sw.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    private static String versionText() {
        try {
            if (sAppContext == null) return "unknown";
            return sAppContext.getPackageManager()
                    .getPackageInfo(sAppContext.getPackageName(), 0).versionName;
        } catch (Throwable th) {
            return "unknown";
        }
    }

    public static File lastFile() {
        return sDir == null ? null : new File(sDir, FILE_NAME);
    }

    public static String readLast() {
        File f = lastFile();
        if (f == null || !f.exists()) return null;
        java.io.FileInputStream fis = null;
        try {
            fis = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) > 0) bos.write(buf, 0, len);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable th) {
            return null;
        } finally {
            try {
                if (fis != null) fis.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearLast() {
        File f = lastFile();
        try {
            if (f != null && f.exists()) f.delete();
        } catch (Throwable ignored) {
        }
    }

    /** 上次跑挂了就弹窗展示堆栈，带一键复制 */
    public static void showLastIfAny(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final File f = lastFile();
        if (f == null || !f.exists()) return;
        if (System.currentTimeMillis() - f.lastModified() > SHOW_WINDOW_MS) return;

        final String text = readLast();
        if (text == null || text.trim().isEmpty()) return;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (activity.isFinishing()) return;
                    TextView tv = new TextView(activity);
                    tv.setText(text);
                    tv.setTextSize(11);
                    tv.setTextIsSelectable(true);
                    int pad = (int) (activity.getResources().getDisplayMetrics().density * 12);
                    tv.setPadding(pad, pad, pad, pad);
                    ScrollView sv = new ScrollView(activity);
                    sv.addView(tv);
                    new AlertDialog.Builder(activity)
                            .setTitle("上次运行崩溃，可复制发我")
                            .setView(sv)
                            .setPositiveButton("复制", (d, w) -> copy(activity, text))
                            .setNeutralButton("忽略", (d, w) -> clearLast())
                            .setNegativeButton("关闭", null)
                            .show();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void copy(Activity activity, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("crash", text));
            Toast.makeText(activity, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            clearLast();
        } catch (Throwable ignored) {
        }
    }
}
