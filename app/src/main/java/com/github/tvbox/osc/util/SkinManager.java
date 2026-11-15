package com.github.tvbox.osc.util;

import android.content.Context;
import android.util.TypedValue;

import com.github.tvbox.osc.R;
import com.orhanobut.hawk.Hawk;

/**
 * 七套色系主题的调度中心。
 *
 * 设计原则：不把颜色散落进业务代码。每套主题只覆盖两个可变量（主色 / 次色），
 * 由 res/values/themes_skin.xml 里的 Theme.Skin0..6 提供；布局与代码统一通过
 * ?attr/skin_accent、?attr/skin_accent2 取色，切换主题时只需要 setTheme + 重建界面。
 * 玻璃填充、文字色、明暗模式仍沿用原来的 values / values-night，不受主题影响。
 */
public final class SkinManager {

    public static final int SKIN_COUNT = 7;

    /** 存 Hawk 的下标，0..6 */
    private static final String KEY_SKIN = "hoopoe_skin_index";

    public static final String[] NAMES = {
            "奈飞红", "哆啦蓝", "百事深蓝", "鸣人橙", "小黄黄", "八神粉", "樱花粉"
    };
    public static final String[] NAMES_EN = {
            "Netflix Red", "Doraemon Blue", "Pepsi Deep Blue", "Naruto Orange", "Minion Yellow", "Yagami Pink", "Sakura Pink"
    };

    /** 与 themes_skin.xml 一一对应 */
    private static final int[] THEMES = {
            R.style.Theme_Skin0,
            R.style.Theme_Skin1,
            R.style.Theme_Skin2,
            R.style.Theme_Skin3,
            R.style.Theme_Skin4,
            R.style.Theme_Skin5,
            R.style.Theme_Skin6
    };

    /** 主色（用于主题解析失败时兜底、以及 Compose 侧读取） */
    public static final int[] ACCENT = {
            0xFFD81F26, 0xFF18A2E7, 0xFF004B93, 0xFFFF7439, 0xFFFFD55E, 0xFFFF2F70, 0xFFFD9BDB
    };
    public static final int[] ACCENT2 = {
            0xFFF2A03A, 0xFFFF6FB5, 0xFF3E8BD8, 0xFFFFC14D, 0xFF7A5A16, 0xFFB3204E, 0xFF9B6BD4
    };

    /** 底图整体色调：alpha 很低（约 8%），只染一层色相，不改变明暗 */

    private SkinManager() {
    }

    public static int get() {
        try {
            int v = Hawk.get(KEY_SKIN, 0);
            return (v >= 0 && v < SKIN_COUNT) ? v : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void set(int index) {
        if (index < 0 || index >= SKIN_COUNT) index = 0;
        try {
            Hawk.put(KEY_SKIN, index);
        } catch (Throwable ignored) {
        }
    }

    public static int themeRes() {
        return THEMES[get()];
    }

    public static int themeRes(int index) {
        if (index < 0 || index >= SKIN_COUNT) index = 0;
        return THEMES[index];
    }

    public static String name(int index) {
        if (index < 0 || index >= SKIN_COUNT) index = 0;
        return CommonSettingBridge.isEnglish() ? NAMES_EN[index] : NAMES[index];
    }

    public static String[] allNames() {
        return CommonSettingBridge.isEnglish() ? NAMES_EN : NAMES;
    }

    /** 当前主题的主色 */
    public static int accent(Context c) {
        return resolve(c, R.attr.skin_accent, ACCENT[get()]);
    }

    /** 当前主题的次色 */
    public static int accent2(Context c) {
        return resolve(c, R.attr.skin_accent2, ACCENT2[get()]);
    }

    /** 主色上的文字色：小黄黄/樱花粉这类亮底用黑字，其余用白字 */
    public static int onAccent(Context c) {
        return isLightColor(accent(c)) ? 0xFF1A1C22 : 0xFFFFFFFF;
    }

    /** 判断颜色是否偏亮（相对亮度 > 0.45 视为亮色） */
    public static boolean isLightColor(int color) {
        double lum = 0.2126 * linear(((color >> 16) & 0xFF) / 255.0)
                + 0.7152 * linear(((color >> 8) & 0xFF) / 255.0)
                + 0.0722 * linear((color & 0xFF) / 255.0);
        return lum > 0.45;
    }

    private static double linear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }


    /** 从当前 Theme 里解析任意 skin_* 属性 */
    public static int resolve(Context c, int attr, int fallback) {
        if (c == null || attr == 0) return fallback;
        try {
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(attr, tv, true) && tv.data != 0) {
                return tv.data;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /** 简单桥接，避免在 util 层直接依赖 setting 层的语言判断细节 */
    private static final class CommonSettingBridge {
        static boolean isEnglish() {
            try {
                return com.github.tvbox.osc.setting.CommonSetting.getLanguage() == 1;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
