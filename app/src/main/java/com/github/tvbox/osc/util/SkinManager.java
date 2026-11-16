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


    /* ==================== 「背景即主题」语义 token ====================
     * 七套色值见 res/values/themes_skin.xml（与 HoopoeTV-七套主题-实现规范 逐字一致）。
     * 布局里优先直接用 ?attr/skin_bg_page 这类引用；代码里必须取色时走下面这组方法。
     */

    public static int bgPage(Context c) {
        return resolve(c, R.attr.skin_bg_page, TOKEN_BG_PAGE[get()]);
    }

    public static int bgCard(Context c) {
        return resolve(c, R.attr.skin_bg_card, TOKEN_BG_CARD[get()]);
    }

    public static int bgFloat(Context c) {
        return resolve(c, R.attr.skin_bg_float, TOKEN_BG_FLOAT[get()]);
    }

    public static int bgModal(Context c) {
        return resolve(c, R.attr.skin_bg_modal, TOKEN_BG_MODAL[get()]);
    }

    public static int text1(Context c) {
        return resolve(c, R.attr.skin_text_1, TOKEN_TEXT1[get()]);
    }

    public static int text2(Context c) {
        return resolve(c, R.attr.skin_text_2, TOKEN_TEXT2[get()]);
    }

    public static int text3(Context c) {
        return resolve(c, R.attr.skin_text_3, TOKEN_TEXT3[get()]);
    }

    public static int text4(Context c) {
        return resolve(c, R.attr.skin_text_4, TOKEN_TEXT4[get()]);
    }

    public static int btnBg(Context c) {
        return resolve(c, R.attr.skin_btn_bg, TOKEN_BTN_BG[get()]);
    }

    public static int btnFg(Context c) {
        return resolve(c, R.attr.skin_btn_fg, TOKEN_BTN_FG[get()]);
    }

    /** 卡片内评分色 */
    public static int rating(Context c) {
        return resolve(c, R.attr.skin_rating, TOKEN_RATING[get()]);
    }

    /** 海报上的评分色（05/07 必须换成亮色，否则深棕数字在暗海报上读不出） */
    public static int ratingOnPoster(Context c) {
        return resolve(c, R.attr.skin_rating_poster, TOKEN_RATING_POSTER[get()]);
    }

    public static int lineColor(Context c) {
        return resolve(c, R.attr.skin_line, TOKEN_LINE[get()]);
    }

    public static int frame(Context c) {
        return resolve(c, R.attr.skin_frame, TOKEN_FRAME[get()]);
    }

    /** 主题圆角（px） */
    public static int radiusPx(Context c) {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (c != null && c.getTheme().resolveAttribute(R.attr.skin_radius, tv, true)) {
                return (int) tv.getDimension(c.getResources().getDisplayMetrics());
            }
        } catch (Throwable ignored) {
        }
        return (int) (12 * c.getResources().getDisplayMetrics().density);
    }

    /** 当前主题是否为亮色底（05 小黄黄 / 07 樱花粉） */
    public static boolean isLight() {
        return LIGHT[get()];
    }

    /** 兜底色（Theme 解析失败时用），与 themes_skin.xml 保持一致 */
    private static final int[] TOKEN_BG_PAGE = {
            0xFF1A0507, 0xFF07203A, 0xFF000000, 0xFF2A1206, 0xFFFFE155, 0xFF3A0620, 0xFFFFE3EA
    };
    private static final int[] TOKEN_BG_CARD = {
            0xFF260B0E, 0xFF0D2C4C, 0xFF0B0B0D, 0xFF3A1B0B, 0xFFFFFCEA, 0xFF4C0A2B, 0xFFFFFFFF
    };
    private static final int[] TOKEN_BG_FLOAT = {
            0xFF341014, 0xFF143A62, 0xFF15161A, 0xFF4A240F, 0xFFFFF3B8, 0xFF5E1036, 0xFFFFD3DD
    };
    private static final int[] TOKEN_BG_MODAL = {
            0xFF43151A, 0xFF1B4977, 0xFF202127, 0xFF5A2E13, 0xFFFFFDF6, 0xFF721642, 0xFFFFFFFF
    };
    private static final int[] TOKEN_TEXT1 = {
            0xFFFFF1F0, 0xFFE8F3FF, 0xFFF5F7FA, 0xFFFFEFDD, 0xFF241C00, 0xFFFFE9F3, 0xFF2A1218
    };
    private static final int[] TOKEN_TEXT2 = {
            0xFFD8A9A6, 0xFFA9C4DC, 0xFFB7BCC6, 0xFFE0B48D, 0xFF4A3D00, 0xFFE0A8C2, 0xFF5E3C46
    };
    private static final int[] TOKEN_TEXT3 = {
            0xFFA87C79, 0xFF7E9BB4, 0xFF858B95, 0xFFB78C68, 0xFF6B5900, 0xFFB77F9C, 0xFF8A5F6A
    };
    private static final int[] TOKEN_TEXT4 = {
            0xFF7A5754, 0xFF5C778F, 0xFF5F6672, 0xFF8C6847, 0xFF8A7500, 0xFF8E5F77, 0xFFA9838D
    };
    private static final int[] TOKEN_BTN_BG = {
            0xFFFFFFFF, 0xFFEAF4FF, 0xFFFFFFFF, 0xFFFFEFDD, 0xFF241C00, 0xFFFFE9F3, 0xFF3A2228
    };
    private static final int[] TOKEN_BTN_FG = {
            0xFF1A0507, 0xFF07203A, 0xFF000000, 0xFF2A1206, 0xFFFFE155, 0xFF3A0620, 0xFFFFE3EA
    };
    private static final int[] TOKEN_RATING = {
            0xFFF0C64B, 0xFFFFD166, 0xFFFFC94D, 0xFFFFC069, 0xFF8A6000, 0xFFFFC24D, 0xFFB4562A
    };
    private static final int[] TOKEN_RATING_POSTER = {
            0xFFF5CF57, 0xFFFFD166, 0xFFFFC94D, 0xFFFFC069, 0xFFFFE39A, 0xFFFFC24D, 0xFFFFCFAE
    };
    private static final int[] TOKEN_LINE = {
            0x1AFFFFFF, 0x1AFFFFFF, 0x1AFFFFFF, 0x1AFFFFFF, 0x12000000, 0x1AFFFFFF, 0x12000000
    };
    private static final int[] TOKEN_FRAME = {
            0xFF4A2022, 0xFF1E4A70, 0xFF2C3037, 0xFF5C3317, 0xFFE8CC55, 0xFF6A1740, 0xFFEBC3CE
    };
    private static final boolean[] LIGHT = { false, false, false, false, true, false, true };

    /** 从当前 Theme 里解析任意 skin_* 属性 */
    public static int resolve(Context c, int attr, int fallback) {        if (c == null || attr == 0) return fallback;
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
