package com.github.tvbox.osc.util;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;

/**
 * 缓存统计与清理（公共，TV 与手机端共用）。
 *
 * 之前这套逻辑写死在 ModelSettingFragment 的私有方法里，手机端要清缓存只能复制一份。
 * 抽出来之后两端是同一套口径：都算 app 内部缓存 + 外部缓存两个目录。
 */
public class CacheHelper {

    /** 参与统计与清理的目录 */
    public static File[] dirs(Context context) {
        ArrayList<File> list = new ArrayList<>();
        if (context == null) return list.toArray(new File[0]);
        try {
            if (context.getCacheDir() != null) list.add(context.getCacheDir());
        } catch (Exception ignored) {
        }
        try {
            if (context.getExternalCacheDir() != null) list.add(context.getExternalCacheDir());
        } catch (Exception ignored) {
        }
        return list.toArray(new File[0]);
    }

    /** 当前缓存总占用（字节） */
    public static long size(Context context) {
        long total = 0;
        for (File dir : dirs(context)) total += dirSize(dir);
        return total;
    }

    /** 清空所有缓存目录，返回释放的字节数 */
    public static long clear(Context context) {
        long freed = 0;
        for (File dir : dirs(context)) freed += clearDir(dir);
        return freed;
    }

    private static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            long s = 0;
            if (kids != null) for (File k : kids) s += dirSize(k);
            return s;
        }
        return f.length();
    }

    private static long clearDir(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            long s = 0;
            if (kids != null) for (File k : kids) s += clearDir(k);
            return s;
        }
        long len = f.length();
        return f.delete() ? len : 0;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
