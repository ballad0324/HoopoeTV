package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.data.AppDataManager;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * 备份 / 恢复（公共，TV 与手机端共用同一目录与同一格式）。
 *
 * 目录：{外部存储}/tvbox_backup/{yyyy-MM-dd-HHmmss}/
 *        ├── sqlite   收藏/历史/搜索等数据库
 *        └── hawk     Hawk 偏好（含 cipher_key）
 *
 * 因此手机端做的备份，TV 端能直接恢复，反之亦然。
 *
 * 与 TV 版 BackupDialog 的唯一差异：这里**不会**自动清理超出 10 份的旧备份，
 * 删除一律由调用方显式触发，避免静默删用户数据。
 */
public class BackupHelper {

    public static final int OK = 0;
    public static final int ERR_DB = 1;
    public static final int ERR_HAWK = 2;
    public static final int ERR_UNKNOWN = 3;

    /** 公共外部存储里的备份根目录（TV 版与旧版本用的就是这个），可被文件管理器看到 */
    public static File legacyRoot() {
        return new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/tvbox_backup/");
    }

    /** 应用私有外部目录，Android 10+ 未授予「所有文件访问」时唯一能写的位置 */
    public static File appRoot() {
        Context c = App.getInstance();
        File f = c.getExternalFilesDir("tvbox_backup");
        if (f == null) f = new File(c.getFilesDir(), "tvbox_backup");
        return f;
    }

    private static int sMode = 0; // 0 未探测，1 公共目录可写，2 私有目录

    private static boolean canWrite(File dir) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File probe = new File(dir, ".probe");
            boolean ok = probe.exists() || probe.createNewFile();
            if (ok) probe.delete();
            return ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 实际写入用的根目录。
     * 优先公共目录（与 TV 端备份互通、文件管理器可见）；写不进去（Android 11+ 没给
     * 「所有文件访问」权限是最常见原因）就退回应用私有目录，保证备份/上传/恢复不因权限瘫痪。
     */
    public static File root() {
        if (sMode == 1) return legacyRoot();
        if (sMode == 2) return appRoot();
        File legacy = legacyRoot();
        if (canWrite(legacy)) {
            sMode = 1;
            return legacy;
        }
        File app = appRoot();
        canWrite(app);
        sMode = 2;
        return app;
    }

    /** 当前是否退到了私有目录（用于给用户提示为什么文件管理器里看不到） */
    public static boolean usingAppDir() {
        return root().equals(appRoot());
    }

    private static void collect(File dir, ArrayList<String> out) {
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (!f.isDirectory()) continue;
                String n = f.getName();
                if (!out.contains(n)) out.add(n);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 在两个目录里定位某个备份 */
    private static File find(String dirName) {        File a = new File(legacyRoot(), dirName);
        if (a.isDirectory()) return a;
        File b = new File(appRoot(), dirName);
        if (b.isDirectory()) return b;
        return new File(root(), dirName);
    }

    /** 供外部（云端上传等）定位某份备份，两个目录都会找 */
    public static File dirOf(String dirName) {
        return find(dirName);
    }

    /** 已有备份目录名，按名字倒序（新的在前）。两个目录都扫，兼容旧备份 */
    public static List<String> list() {
        ArrayList<String> result = new ArrayList<>();
        collect(legacyRoot(), result);
        File app = appRoot();
        if (!app.equals(legacyRoot())) collect(app, result);
        try {
            String[] arr = result.toArray(new String[0]);
            Arrays.sort(arr, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return b.compareTo(a);
                }
            });
            result.clear();
            for (String s : arr) result.add(s);
        } catch (Throwable ignored) {
        }
        return result;
    }

    /** 立即备份，成功返回备份目录名，失败返回 null */
    public static String backup(Context context) {
        if (context == null) return null;
        try {
            File dir = root();
            if (!dir.exists() && !dir.mkdirs()) return null;
            String name = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date());
            File backup = new File(dir, name);
            if (!backup.mkdirs()) return null;

            File db = new File(backup, "sqlite");
            if (!AppDataManager.backup(db)) {
                FileUtils.recursiveDelete(backup);
                return null;
            }
            JSONObject json = new JSONObject();
            SharedPreferences sp = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
            for (String key : sp.getAll().keySet()) json.put(key, sp.getString(key, ""));
            sp = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE);
            for (String key : sp.getAll().keySet()) json.put(key, sp.getString(key, ""));

            if (!FileUtils.writeSimple(json.toString().getBytes("UTF-8"), new File(backup, "hawk"))) {
                FileUtils.recursiveDelete(backup);
                return null;
            }
            return name;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 恢复指定备份，返回 OK / ERR_* */
    public static int restore(Context context, String dirName) {
        if (context == null || dirName == null) return ERR_UNKNOWN;
        try {
            File backup = find(dirName);
            if (!backup.exists()) return ERR_UNKNOWN;

            File db = new File(backup, "sqlite");
            if (!AppDataManager.restore(db)) return ERR_DB;

            byte[] data = FileUtils.readSimple(new File(backup, "hawk"));
            if (data == null) return ERR_HAWK;

            JSONObject json = new JSONObject(new String(data, "UTF-8"));
            Iterator<String> it = json.keys();
            SharedPreferences sp = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
            while (it.hasNext()) {
                String key = it.next();
                String value = json.getString(key);
                if (key.equals("cipher_key")) {
                    App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE)
                            .edit().putString(key, value).commit();
                } else {
                    sp.edit().putString(key, value).commit();
                }
            }
            return OK;
        } catch (Throwable ignored) {
            return ERR_UNKNOWN;
        }
    }

    /** 删除指定备份 */
    public static boolean delete(String dirName) {
        if (dirName == null) return false;
        try {
            File dir = find(dirName);
            if (!dir.exists()) return true;
            FileUtils.recursiveDelete(dir);
            return !dir.exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 恢复后重启到桌面入口 */
    public static void restartApp(Context context) {
        if (context == null) return;
        Intent i = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(i);
            System.exit(0);
        }
    }
}
