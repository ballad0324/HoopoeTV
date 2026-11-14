package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.setting.CommonSetting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 磨砂主题「更换背景图」公共能力：两端共用、各自独立存储。
 * 选图（限制 1MB 内）-> 复制到应用私有目录 -> 记录绝对路径
 * -> 覆盖内容根 View 的背景（窗口 decor 会被布局根背景遮住，所以必须落在 root 上）。
 */
public final class GlassBgHelper {

    /** 后台线程安全提示：统一切回主线程再弹 */
    private static void toast(final Activity a, final String msg) {
        if (a == null) return;
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static final int REQ_BG_LIGHT = 10101;
    public static final int REQ_BG_DARK = 10102;

    /** 单张背景图体积上限：1MB */
    public static final long MAX_BYTES = 1024L * 1024L;

    private static final String DIR_WALLPAPER = "wallpaper";
    private static final int MAX_EDGE = 1600;

    private GlassBgHelper() {
    }

    private static String fileName(boolean night) {
        return "custom_bg_" + (night ? "dark" : "light") + (CommonSetting.isMobileUi() ? "_mobile" : "_tv");
    }

    public static boolean isNight(Context c) {
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 打开系统图片选择器；requestCode 与明亮/深色主题一一对应 */
    public static void pick(Activity a, boolean night) {
        int req = night ? REQ_BG_DARK : REQ_BG_LIGHT;
        String title = night ? "选择深色主题背景图" : "选择明亮主题背景图";
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            a.startActivityForResult(Intent.createChooser(intent, title), req);
        } catch (Exception e) {
            Toast.makeText(a, "系统没有可用的图片选择器，请改用文件管理器放置图片", Toast.LENGTH_LONG).show();
        }
    }

    /** onActivityResult 里调用；返回 true 表示已更新背景（调用方自行刷新界面） */
    public static boolean handleResult(Activity a, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_BG_LIGHT && requestCode != REQ_BG_DARK) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return false;
        boolean night = requestCode == REQ_BG_DARK;
        Uri uri = data.getData();
        try {
            File dir = new File(a.getFilesDir(), DIR_WALLPAPER);
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(a, "无法创建背景图目录", Toast.LENGTH_SHORT).show();
                return false;
            }
            File out = new File(dir, fileName(night));
            File tmp = new File(dir, out.getName() + ".tmp");
            InputStream is = a.getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(a, "读取图片失败", Toast.LENGTH_SHORT).show();
                return false;
            }
            FileOutputStream fos = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int len;
            long total = 0;
            boolean tooBig = false;
            while ((len = is.read(buf)) > 0) {
                total += len;
                if (total > MAX_BYTES) {
                    tooBig = true;
                    break;
                }
                fos.write(buf, 0, len);
            }
            fos.flush();
            fos.close();
            is.close();
            if (tooBig) {
                tmp.delete();
                Toast.makeText(a, String.format("图片超过 1MB 上限（%.1fMB），请先压缩后再选",
                        total / (1024.0 * 1024.0)), Toast.LENGTH_LONG).show();
                return false;
            }
            Bitmap check = decodeScaled(tmp.getAbsolutePath());
            if (check == null) {
                tmp.delete();
                Toast.makeText(a, "这不是可识别的图片文件", Toast.LENGTH_LONG).show();
                return false;
            }
            check.recycle();
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) {
                tmp.delete();
                Toast.makeText(a, "保存背景图失败", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (night) CommonSetting.putGlassBgDark(out.getAbsolutePath());
            else CommonSetting.putGlassBgLight(out.getAbsolutePath());
            Toast.makeText(a, night ? "深色主题背景已更新" : "明亮主题背景已更新", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(a, "设置背景失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** 恢复默认内置背景 */
    public static void reset(Activity a, boolean night) {
        if (night) CommonSetting.putGlassBgDark("");
        else CommonSetting.putGlassBgLight("");
        apply(a);
    }

    /**
     * 设置页「背景图地址」输入框用：从 http(s) 直链或本机路径直接取图。
     * 大小、可解码、1080×1920 以上的校验与选图路径一致；成功后在主线程 apply 并回调刷新。
     */
    public static void importUrl(final Activity a, final boolean night, final String src, final Runnable onDone) {
        if (a == null || TextUtils.isEmpty(src)) return;
        final String trimmed = src.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File tmp = null;
                InputStream is = null;
                try {
                    File dir = new File(a.getFilesDir(), DIR_WALLPAPER);
                    if (!dir.exists() && !dir.mkdirs()) {
                        toast(a, "无法创建背景图目录");
                        return;
                    }
                    final File out = new File(dir, fileName(night));
                    tmp = new File(dir, out.getName() + ".tmp");
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(trimmed).openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(20000);
                        conn.setInstanceFollowRedirects(true);
                        is = conn.getInputStream();
                    } else {
                        String path = trimmed.startsWith("file://") ? trimmed.substring(7) : trimmed;
                        is = new FileInputStream(new File(path));
                    }
                    if (is == null) {
                        toast(a, "读取图片失败");
                        return;
                    }
                    FileOutputStream fos = new FileOutputStream(tmp);
                    byte[] buf = new byte[8192];
                    int len;
                    long total = 0;
                    boolean tooBig = false;
                    while ((len = is.read(buf)) > 0) {
                        total += len;
                        if (total > MAX_BYTES) {
                            tooBig = true;
                            break;
                        }
                        fos.write(buf, 0, len);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    is = null;
                    if (tooBig) {
                        tmp.delete();
                        toast(a, String.format("图片超过 1MB 上限（%.1fMB），请先压缩",
                                total / (1024.0 * 1024.0)));
                        return;
                    }
                    Bitmap check = decodeScaled(tmp.getAbsolutePath());
                    if (check == null) {
                        tmp.delete();
                        toast(a, "这不是可识别的图片（链接可能不是图片直链）");
                        return;
                    }
                    check.recycle();
                    if (out.exists() && !out.delete()) {
                        tmp.delete();
                        toast(a, "旧背景图无法覆盖");
                        return;
                    }
                    if (!tmp.renameTo(out)) {
                        tmp.delete();
                        toast(a, "保存背景图失败");
                        return;
                    }
                    if (night) CommonSetting.putGlassBgDark(out.getAbsolutePath());
                    else CommonSetting.putGlassBgLight(out.getAbsolutePath());
                    final String ok = night ? "深色主题背景已更新" : "明亮主题背景已更新";
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(a, ok);
                            apply(a);
                            if (onDone != null) onDone.run();
                        }
                    });
                } catch (Exception e) {
                    if (tmp != null) tmp.delete();
                    toast(a, "设置背景失败：" + e.getMessage());
                } finally {
                    try {
                        if (is != null) is.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    /**
     * 应用背景到内容根 View（不是窗口 decor，否则会被布局的背景盖住）。
     * @return true 表示当前已应用自定义图；false 表示已恢复布局自带背景
     */
    public static boolean apply(Activity a) {
        if (a == null) return false;
        View target = resolveRoot(a);
        if (target == null) return false;
        boolean night = isNight(a);
        String path = CommonSetting.getGlassBgPath(night);
        File f = (path == null || path.trim().isEmpty()) ? null : new File(path);
        if (f == null || !f.exists()) {
            restoreOriginal(target);
            return false;
        }
        Bitmap bmp = decodeScaled(path);
        if (bmp == null) {
            restoreOriginal(target);
            return false;
        }
        Object saved = target.getTag(R.id.tag_glass_bg_original);
        if (!(saved instanceof Drawable)) {
            target.setTag(R.id.tag_glass_bg_original, target.getBackground());
        }
        BitmapDrawable bd = new BitmapDrawable(a.getResources(), bmp);
        bd.setGravity(Gravity.FILL);
        int scrim = night ? 0x70000000 : 0x66FFFFFF;
        LayerDrawable ld = new LayerDrawable(new Drawable[]{bd, new ColorDrawable(scrim)});
        target.setBackground(ld);
        return true;
    }

    private static void restoreOriginal(View target) {
        Object saved = target.getTag(R.id.tag_glass_bg_original);
        if (saved instanceof Drawable) {
            target.setBackground((Drawable) saved);
            target.setTag(R.id.tag_glass_bg_original, null);
        }
    }

    /** 内容区第一个子 View：真正铺满屏幕、同时也承载布局自带背景的那一层 */
    private static View resolveRoot(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) return content.getChildAt(0);
        return a.getWindow().getDecorView();
    }

    /** 当前主题是否有自定义背景 */
    public static boolean hasCustom(Context c) {
        String path = CommonSetting.getGlassBgPath(isNight(c));
        return path != null && !path.trim().isEmpty() && new File(path).exists();
    }

    private static Bitmap decodeScaled(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            int edge = Math.max(o.outWidth, o.outHeight);
            while (edge / sample > MAX_EDGE) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, d);
        } catch (Throwable t) {
            return null;
        }
    }
}