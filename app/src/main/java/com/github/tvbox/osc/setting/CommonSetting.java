package com.github.tvbox.osc.setting;

import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 公共设置层：TV 端与手机端两套 UI 共用的设置项统一走这里。
 *
 * 设计原则：
 * 1. 一个设置项只有一个偏好键，键名沿用已有的 HawkConfig 体系，不新造平行键；
 * 2. 两端的设置页面只负责“显示与交互”，读写一律调本类，避免两端各自实现导致取值不一致；
 * 3. 只在某一端存在的设置项不进本类，分别放到 TvSetting / MobileSetting。
 */
public class CommonSetting {

    /* ===================== 明暗模式（两端共用） ===================== */

    public static final String KEY_NIGHT_MODE = "night_mode";
    public static final String KEY_AGG_SEARCH = "agg_search";

    public static final int NIGHT_FOLLOW_SYSTEM = 0;
    public static final int NIGHT_LIGHT = 1;
    public static final int NIGHT_DARK = 2;

    private static final int[] NIGHT_OPTIONS = {NIGHT_FOLLOW_SYSTEM, NIGHT_LIGHT, NIGHT_DARK};

    public static int getNightMode() {
        // 默认明亮：未设置过时不再默认进深色
        int mode = Hawk.get(KEY_NIGHT_MODE, NIGHT_LIGHT);
        return isNightMode(mode) ? mode : NIGHT_LIGHT;
    }

    public static void putNightMode(int mode) {
        int value = isNightMode(mode) ? mode : NIGHT_LIGHT;
        Hawk.put(KEY_NIGHT_MODE, value);
        applyNightMode(value);
    }

    /** 设置页展示用的序号：0 跟随系统 / 1 明亮 / 2 深色 */
    public static int getNightModeIndex() {
        int mode = getNightMode();
        for (int i = 0; i < NIGHT_OPTIONS.length; i++) {
            if (NIGHT_OPTIONS[i] == mode) return i;
        }
        return 1;
    }

    public static void putNightModeIndex(int index) {
        putNightMode(index >= 0 && index < NIGHT_OPTIONS.length ? NIGHT_OPTIONS[index] : NIGHT_LIGHT);
    }

    public static String getNightModeName() {
        switch (getNightMode()) {
            case NIGHT_LIGHT:
                return "明亮";
            case NIGHT_DARK:
                return "深色";
            default:
                return "跟随系统";
        }
    }

    public static void applyNightMode() {
        applyNightMode(getNightMode());
    }

    public static boolean isNightUi() {
        return (App.getInstance().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean isNightMode(int mode) {
        for (int option : NIGHT_OPTIONS) {
            if (option == mode) return true;
        }
        return false;
    }

    private static void applyNightMode(int mode) {
        AppCompatDelegate.setDefaultNightMode(toDelegateNightMode(mode));
    }

    private static int toDelegateNightMode(int mode) {
        if (mode == NIGHT_LIGHT) return AppCompatDelegate.MODE_NIGHT_NO;
        if (mode == NIGHT_DARK) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /* ===================== 语言（两端共用） ===================== */

    /** 0 中文 / 1 英文，键沿用 HawkConfig.HOME_LOCALE */
    public static int getLanguage() {
        return Hawk.get(HawkConfig.HOME_LOCALE, 0);
    }

    public static void putLanguage(int language) {
        Hawk.put(HawkConfig.HOME_LOCALE, language);
    }

    /* ===================== 播放器（两端共用） ===================== */

    /** 0 系统 1 ijk 2 exo 10 MXPlayer，键沿用 HawkConfig.PLAY_TYPE */
    public static int getPlayType() {
        return Hawk.get(HawkConfig.PLAY_TYPE, 1);
    }

    public static void putPlayType(int playType) {
        Hawk.put(HawkConfig.PLAY_TYPE, playType);
    }

    /** 0 texture / 2 surface */
    public static int getPlayRender() {
        return Hawk.get(HawkConfig.PLAY_RENDER, 0);
    }

    public static void putPlayRender(int render) {
        Hawk.put(HawkConfig.PLAY_RENDER, render);
    }

    public static int getPlayScale() {
        return Hawk.get(HawkConfig.PLAY_SCALE, 0);
    }

    public static void putPlayScale(int scale) {
        Hawk.put(HawkConfig.PLAY_SCALE, scale);
    }

    /** 0 系统 1 ijk 2 exo，键沿用 HawkConfig.LIVE_PLAYER_TYPE */
    public static int getLivePlayerType() {
        return Hawk.get(HawkConfig.LIVE_PLAYER_TYPE, 0);
    }

    public static void putLivePlayerType(int type) {
        Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, type);
    }

    /* ===================== 网络（两端共用） ===================== */

    /** 0 关闭 / 1 AliDns / 2 自定义，键沿用 HawkConfig.DOH_URL */
    public static int getDohUrl() {
        return Hawk.get(HawkConfig.DOH_URL, 0);
    }

    public static void putDohUrl(int doh) {
        Hawk.put(HawkConfig.DOH_URL, doh);
    }

    public static String getProxyServer() {
        return Hawk.get(HawkConfig.PROXY_SERVER, "");
    }

    public static void putProxyServer(String proxy) {
        Hawk.put(HawkConfig.PROXY_SERVER, proxy);
    }

    /* ===================== 字幕（两端共用） ===================== */

    public static int getSubtitleTextSize() {
        return Hawk.get(HawkConfig.SUBTITLE_TEXT_SIZE, 0);
    }

    public static void putSubtitleTextSize(int size) {
        Hawk.put(HawkConfig.SUBTITLE_TEXT_SIZE, size);
    }

    public static int getSubtitleTimeDelay() {
        return Hawk.get(HawkConfig.SUBTITLE_TIME_DELAY, 0);
    }

    public static void putSubtitleTimeDelay(int delayMs) {
        Hawk.put(HawkConfig.SUBTITLE_TIME_DELAY, delayMs);
    }

    /* ===================== 直播（两端共用） ===================== */

    public static boolean isLiveChannelReverse() {
        return Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
    }

    public static boolean isLiveCrossGroup() {
        return Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
    }

    public static boolean isLiveShowNetSpeed() {
        return Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
    }

    public static boolean isLiveShowTime() {
        return Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
    }

    /* ===================== 数据源（两端共用） ===================== */

    /** 聚合搜索：默认开，开启后搜索会在所有可搜索站点并发检索 */
    public static boolean isAggSearch() {
        return Hawk.get(KEY_AGG_SEARCH, true);
    }

    public static void putAggSearch(boolean enabled) {
        Hawk.put(KEY_AGG_SEARCH, enabled);
    }

    public static String getApiUrl() {
        return Hawk.get(HawkConfig.API_URL, "");
    }

    public static void putApiUrl(String url) {
        Hawk.put(HawkConfig.API_URL, url);
    }

    /** 用户自填的接口地址原文（不含内置源兜底），给「接口地址」输入框回填用 */
    public static String getApiUrlStored() {
        String s = Hawk.get(HawkConfig.API_URL, "");
        return s == null ? "" : s;
    }

    public static String getLiveUrl() {
        return Hawk.get(HawkConfig.LIVE_URL, "");
    }

    public static void putLiveUrl(String url) {
        Hawk.put(HawkConfig.LIVE_URL, url);
    }

    public static String getEpgUrl() {
        return Hawk.get(HawkConfig.EPG_URL, "");
    }

    public static void putEpgUrl(String url) {
        Hawk.put(HawkConfig.EPG_URL, url);
    }

    /* ===================== 源模式（v1.6.38） ===================== */

    /** 源模式存储键：0 = 内置源，1 = 解析源（用户自填接口），未设置时按登录态推断 */
    private static final String KEY_SOURCE_MODE = "mobile_source_mode";

    public static int getSourceModeIndex() {
        try {
            return Hawk.get(KEY_SOURCE_MODE, -1);
        } catch (Throwable th) {
            return -1;
        }
    }

    public static void putSourceModeIndex(int index) {
        Hawk.put(KEY_SOURCE_MODE, index);
    }

    /** 首页/搜索等取源时统一走这里：内置源 or 用户自填的解析源 */
    public static boolean useBuiltinSource() {
        int mode = getSourceModeIndex();
        if (mode == 0) return true;
        if (mode == 1) return false;
        // 没手动切过：登录用户走内置源，未登录走自填源（v1.6.36 的默认规则）
        try {
            return com.github.tvbox.osc.mobile.ui.MobileUser.isLoggedIn();
        } catch (Throwable th) {
            return false;
        }
    }

    /* ===================== 内置源（已改为云端下发） ===================== */

    /** 兜底三件套：云端拉不到时仍可用，首次启动也按这套初始化 */
    public static final String BUILTIN_API_URL = "http://43.226.44.105:6066/api.php/tvboxcfg";
    public static final String BUILTIN_LIVE_URL = "http://43.226.44.105:6061/zytx/interface.m3u";
    public static final String BUILTIN_EPG_URL = "http://43.226.44.105:6061/zytx/playback.xml";

    /* ===================== 配置即时生效（改完接口/数据源不用重启 App） ===================== */

    /** 配置变更标记：设置页写接口地址或换数据源时置位，首页 onResume 消费后重新加载配置 */
    public static final String KEY_CONFIG_DIRTY = "cfg_dirty";

    public static void markConfigDirty() {
        Hawk.put(KEY_CONFIG_DIRTY, true);
    }

    /** 读取并清除标记；返回 true 表示需要重新加载配置 */
    public static boolean consumeConfigDirty() {
        boolean dirty = Hawk.get(KEY_CONFIG_DIRTY, false);
        if (dirty) Hawk.put(KEY_CONFIG_DIRTY, false);
        return dirty;
    }

    /* ===================== UI 身份判断（可在设置里运行时切换） ===================== */

    /** 界面模式：电视端 */
    public static final int UI_TV = 0;
    /** 界面模式：手机端 */
    public static final int UI_MOBILE = 1;

    public static final String KEY_UI_MODE = "ui_mode";

    /** 首次启动的默认值来自构建变体：mobile flavor 默认手机端，其余默认电视端 */
    public static int getUiModeDefault() {
        return BuildConfig.UI_MOBILE ? UI_MOBILE : UI_TV;
    }

    public static int getUiMode() {
        int mode = Hawk.get(KEY_UI_MODE, getUiModeDefault());
        return (mode == UI_MOBILE || mode == UI_TV) ? mode : getUiModeDefault();
    }

    public static void putUiMode(int mode) {
        Hawk.put(KEY_UI_MODE, mode == UI_MOBILE ? UI_MOBILE : UI_TV);
    }

    public static boolean isMobileUi() {
        return getUiMode() == UI_MOBILE;
    }

    public static String getUiModeName() {
        return isMobileUi() ? "手机端" : "电视端";
    }

    /** 手机端 UI 才应用明暗模式，TV 端保持原有固定深色主题，避免破坏已调好的电视配色 */
    public static void applyNightModeOnStartup() {
        if (isMobileUi()) {
            applyNightMode();
        }
    }

    /* ===================== 磨砂背景图（两端共用，值=本地文件绝对路径） ===================== */

    // 手机端与电视端背景图各自独立：按 UI 模式 + 明暗共四套
    public static final String KEY_GLASS_BG_LIGHT_MOBILE = "glass_bg_light_mobile";
    public static final String KEY_GLASS_BG_DARK_MOBILE = "glass_bg_dark_mobile";
    public static final String KEY_GLASS_BG_LIGHT_TV = "glass_bg_light_tv";
    public static final String KEY_GLASS_BG_DARK_TV = "glass_bg_dark_tv";

    private static String glassBgKey(boolean night) {
        if (isMobileUi()) {
            return night ? KEY_GLASS_BG_DARK_MOBILE : KEY_GLASS_BG_LIGHT_MOBILE;
        }
        return night ? KEY_GLASS_BG_DARK_TV : KEY_GLASS_BG_LIGHT_TV;
    }

    public static String getGlassBgLight() {
        return Hawk.get(glassBgKey(false), "");
    }

    public static void putGlassBgLight(String path) {
        Hawk.put(glassBgKey(false), path == null ? "" : path);
    }

    public static String getGlassBgDark() {
        return Hawk.get(glassBgKey(true), "");
    }

    public static void putGlassBgDark(String path) {
        Hawk.put(glassBgKey(true), path == null ? "" : path);
    }
    /** night=true 取深色主题背景图，否则取明亮主题 */
    public static String getGlassBgPath(boolean night) {
        return Hawk.get(glassBgKey(night), "");
    }

    public static final String BG_SPEC_HINT = "支持 PNG / JPG / WebP；竖屏建议 1080×1920 及以上（9:16），横屏建议 1920×1080（16:9），单张 ≤ 1MB";
}
