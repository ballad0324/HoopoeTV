package com.github.tvbox.osc.setting;

import android.content.res.Configuration;

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

    /* ===================== 聚合搜索（两端共用） ===================== */

    public static final String KEY_AGG_SEARCH = "agg_search";

    /** 当前是否处于深色配置（仅供个别控件的取色判断；手机端已固定明亮） */
    public static boolean isNightUi() {
        return (App.getInstance().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
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

    /** 搜索展示：0 文字列表 / 1 缩略图（与电视端同键，手机端搜索页按此切换） */
    public static int getSearchView() {
        return Hawk.get(HawkConfig.SEARCH_VIEW, 0);
    }

    /** 快速搜索：开启后手机端搜索页边输边搜 */
    public static boolean isFastSearch() {
        return Hawk.get(HawkConfig.FAST_SEARCH_MODE, false);
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

    /* 代理服务器设置已移除：EXO 的"失败换代理重试"逻辑一并删掉，见 ExoMediaPlayer */

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
        return getApiUrlStored();
    }

    public static void putApiUrl(String url) {
        Hawk.put(HawkConfig.API_URL, url == null ? "" : url);
    }

    /** 用户自填的接口地址原文（不含任何内置兜底），给「接口地址」输入框回填用 */
    public static String getApiUrlStored() {
        String s = Hawk.get(HawkConfig.API_URL, "");
        return s == null ? "" : s;
    }

    /** 直播源 / EPG 是否被用户手动设置过（用于区分“用户填过”与“还是出厂空值”） */
    private static final String KEY_LIVE_SET = "live_url_manual";
    private static final String KEY_EPG_SET = "epg_url_manual";

    /**
     * 直播源：只认存储值。
     * v1.7.20 起出厂不内置任何源 —— 没填过、没口令拉取过就是空串，直播页会提示「未配置直播源」。
     */
    public static String getLiveUrl() {
        String v = Hawk.get(HawkConfig.LIVE_URL, "");
        return v == null ? "" : v;
    }

    public static void putLiveUrl(String url) {
        Hawk.put(HawkConfig.LIVE_URL, url == null ? "" : url);
        Hawk.put(KEY_LIVE_SET, true);
    }

    /** EPG：同样只认存储值，未配置就是空串 */
    public static String getEpgUrl() {
        String v = Hawk.get(HawkConfig.EPG_URL, "");
        return v == null ? "" : v;
    }

    public static void putEpgUrl(String url) {
        Hawk.put(HawkConfig.EPG_URL, url == null ? "" : url);
        Hawk.put(KEY_EPG_SET, true);
    }

    /* ===================== 默认源（口令解锁后写入，v1.7.20） ===================== */

    /** 拉取默认源需要的口令；改这里等于换密码 */
    public static final String BUILTIN_PASS = "98555576";

    private static final String KEY_BUILTIN_UNLOCK = "mobile_builtin_unlock";
    /** 一次性清空标记：从旧版本升上来时执行一次“清空全部数据源” */
    private static final String KEY_SRC_RESET = "src_reset_v4";

    /** 默认三件套的密文（XOR + Base64）。明文地址能反编译拿到，口令只是弱防护 */
    private static final String SRC_SALT = "hoopoe-ui-2026";
    private static final String ENC_API = "ABsbAFVKAkFaAwACBBhcW0FBX1AXQ1kbBB9TRgFBHxgfSlkDC0JKU1RR";
    private static final String ENC_LIVE = "ABsbAFVKAkFaAwACBBhcW0FBX1AXQ1kbAx9ITxwXQBkBEUgHD0xRVRxbWxo=";
    private static final String ENC_EPG = "ABsbAFVKAkFaAwACBBhcW0FBX1AXQ1kbAx9ITxwXQAADBFQXCE5ZHkpbBA==";

    private static String decodeSource(String b64) {
        try {
            byte[] body = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
            byte[] salt = SRC_SALT.getBytes("UTF-8");
            for (int i = 0; i < body.length; i++) {
                body[i] = (byte) (body[i] ^ salt[i % salt.length]);
            }
            return new String(body, "UTF-8");
        } catch (Throwable th) {
            return "";
        }
    }

    public static String defaultApiUrl() {
        return decodeSource(ENC_API);
    }

    public static String defaultLiveUrl() {
        return decodeSource(ENC_LIVE);
    }

    public static String defaultEpgUrl() {
        return decodeSource(ENC_EPG);
    }

    public static boolean isBuiltinUnlocked() {
        try {
            Boolean v = Hawk.get(KEY_BUILTIN_UNLOCK, Boolean.FALSE);
            return v != null && v;
        } catch (Throwable th) {
            return false;
        }
    }

    public static void putBuiltinUnlocked(boolean unlocked) {
        Hawk.put(KEY_BUILTIN_UNLOCK, unlocked);
    }

    /** 接口 / 直播 / EPG 三项是否都已配置（仅用于设置页显示状态） */
    public static boolean hasAllSources() {
        return !getApiUrl().trim().isEmpty()
                && !getLiveUrl().trim().isEmpty()
                && !getEpgUrl().trim().isEmpty();
    }

    /** 口令校验通过后：把默认三件套写进本机，并记住已解锁 */
    public static void applyDefaultSource() {
        Hawk.put(HawkConfig.API_URL, defaultApiUrl());
        putLiveUrl(defaultLiveUrl());
        putEpgUrl(defaultEpgUrl());
        putBuiltinUnlocked(true);
        markConfigDirty();
    }

    /** 清空全部数据源：三项一起清、撤销解锁（双端共用同一组键，清完两端都空） */
    public static void clearAllSource() {
        Hawk.put(HawkConfig.API_URL, "");
        Hawk.put(HawkConfig.LIVE_URL, "");
        Hawk.put(HawkConfig.EPG_URL, "");
        Hawk.put(KEY_LIVE_SET, false);
        Hawk.put(KEY_EPG_SET, false);
        putBuiltinUnlocked(false);
        markConfigDirty();
    }

    /** 升级后的一次性清空：出厂不再内置任何源，老用户的旧地址也一起清掉 */
    public static void resetSourcesOnce() {
        boolean done = Hawk.get(KEY_SRC_RESET, false);
        if (done) {
            return;
        }
        Hawk.put(KEY_SRC_RESET, true);
        clearAllSource();
    }

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

    
}
