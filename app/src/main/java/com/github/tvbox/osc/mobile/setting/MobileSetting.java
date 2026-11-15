package com.github.tvbox.osc.mobile.setting;

import com.github.tvbox.osc.setting.CommonSetting;
import com.orhanobut.hawk.Hawk;

/**
 * 手机端专有设置：只在手机端 UI 出现或生效，TV 端不读这些项。
 * 明暗模式、语言、播放器、数据源等两端共用项，一律走 CommonSetting，不要在这里另存一份。
 */
public class MobileSetting {

    /* ------------ 首页布局参数（对应 webhtv 手机端 UI 稿实测值） ------------ */

    /** 底部导航栏高度：64dp 会把 label 裁掉，实测 76dp 正常 */
    public static final int NAV_BAR_HEIGHT_DP = 76;
    /** 底部导航 icon 尺寸 */
    public static final int NAV_ICON_SIZE_DP = 22;
    /** 底部导航 label 字号 */
    public static final int NAV_LABEL_SP = 10;

    /** 顶部搜索胶囊高度 */
    public static final int SEARCH_BAR_HEIGHT_DP = 38;
    /** 搜索胶囊圆角（取高度一半做成胶囊） */
    public static final int SEARCH_BAR_RADIUS_DP = 19;

    /** 顶部 toolbar 标题字号 */
    public static final int TOOLBAR_TITLE_SP = 17;
    /** toolbar 内 logo 尺寸 */
    public static final int TOOLBAR_LOGO_SIZE_DP = 36;

    /** 分类 tab 字号 */
    public static final int TYPE_TAB_SP = 16;
    /** 分类 tab 选中下划线高度 */
    public static final int TYPE_TAB_INDICATOR_DP = 3;
    /** 分类 tab 下划线左右内缩 */
    public static final int TYPE_TAB_INDICATOR_INSET_DP = 6;

    /** 首页卡片圆角 */
    public static final int VOD_CARD_CORNER_DP = 11;

    /* ------------ 启动页 ------------ */

    /** 手机端使用独立的启动页（不再走 main 的 bitmap 背景） */
    public static final boolean USE_MOBILE_SPLASH = true;

    /* ------------ 手机端专有开关（TV 端不存在这些概念） ------------ */

    /** 首页顶栏搜索胶囊是否显示 */
    public static final String KEY_SHOW_SEARCH_BAR = "mobile_show_search_bar";
    /** 底部导航文字是否显示 */
    public static final String KEY_SHOW_NAV_LABEL = "mobile_show_nav_label";
    /** 分类 tab 选中下划线是否显示 */
    public static final String KEY_SHOW_TAB_INDICATOR = "mobile_show_tab_indicator";

    /* 三个显示开关已按需求固定为「开启」，设置页不再暴露；
       这里直接返回 true，避免设备上遗留的旧 Hawk 值把功能关掉。 */

    public static boolean isShowSearchBar() {
        return true;
    }

    public static void putShowSearchBar(boolean show) {
        Hawk.put(KEY_SHOW_SEARCH_BAR, show);
    }

    public static boolean isShowNavLabel() {
        return true;
    }

    public static void putShowNavLabel(boolean show) {
        Hawk.put(KEY_SHOW_NAV_LABEL, show);
    }

    public static boolean isShowTabIndicator() {
        return true;
    }

    public static void putShowTabIndicator(boolean show) {
        Hawk.put(KEY_SHOW_TAB_INDICATOR, show);
    }
}
