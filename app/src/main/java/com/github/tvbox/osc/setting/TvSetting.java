package com.github.tvbox.osc.setting;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * TV 端专有设置：只在电视端 UI 出现或生效，手机端不读这些项。
 * 与 CommonSetting 的边界：凡是手机端也需要读的，一律上移到 CommonSetting。
 */
public class TvSetting {

    /* ------------ 首页 T 型双栏布局参数（已实机调过，不要随手改） ------------ */

    /** 左侧分类栏宽度 */
    public static final int HOME_LEFT_PANE_DP = 196;
    /** 左右面板之间的留白（左 marginEnd + 右 marginStart 各一半） */
    public static final int HOME_PANE_GAP_DP = 10;
    /** 面板圆角 */
    public static final int HOME_PANE_CORNER_DP = 20;
    /** 左侧分类条目文字字号 */
    public static final int HOME_NAV_ITEM_SP = 18;
    /** 左侧分类条目最小高度 */
    public static final int HOME_NAV_ITEM_MIN_HEIGHT_DP = 48;

    /* ------------ 首页内容偏好 ------------ */

    /** 0 豆瓣 1 推荐 2 历史，键沿用 HawkConfig.HOME_REC */
    public static int getHomeRec() {
        return Hawk.get(HawkConfig.HOME_REC, 0);
    }

    public static void putHomeRec(int rec) {
        Hawk.put(HawkConfig.HOME_REC, rec);
    }

    /** true = Grid 卡片流，false = Line 列表流 */
    public static boolean isHomeRecGrid() {
        return Hawk.get(HawkConfig.HOME_REC_STYLE, true);
    }

    public static void putHomeRecGrid(boolean grid) {
        Hawk.put(HawkConfig.HOME_REC_STYLE, grid);
    }

    public static int getHomeNum() {
        return Hawk.get(HawkConfig.HOME_NUM, 10);
    }

    public static void putHomeNum(int num) {
        Hawk.put(HawkConfig.HOME_NUM, num);
    }

    public static boolean isHomeShowSource() {
        return Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false);
    }

    public static void putHomeShowSource(boolean show) {
        Hawk.put(HawkConfig.HOME_SHOW_SOURCE, show);
    }

    /** true = 搜索入口在上，false = 在下 */
    public static boolean isHomeSearchUp() {
        return Hawk.get(HawkConfig.HOME_SEARCH_POSITION, true);
    }

    /** true = 功能菜单在上，false = 在下 */
    public static boolean isHomeMenuUp() {
        return Hawk.get(HawkConfig.HOME_MENU_POSITION, true);
    }

    /** 启动时直接进直播 */
    public static boolean isHomeDefaultShowLive() {
        return Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false);
    }

    public static void putHomeDefaultShowLive(boolean show) {
        Hawk.put(HawkConfig.HOME_DEFAULT_SHOW, show);
    }

    /** 首页缩略图预览（TV 端大屏才开；手机端不走这个开关） */
    public static boolean isShowPreview() {
        return Hawk.get(HawkConfig.SHOW_PREVIEW, true);
    }

    public static void putShowPreview(boolean show) {
        Hawk.put(HawkConfig.SHOW_PREVIEW, show);
    }

    /* ------------ 电视端焦点行为 ------------ */

    /** 左侧分类选中后是否立即切页（TV 端焦点跟随用） */
    public static final boolean NAV_SELECT_SWITCH_IMMEDIATE = true;
    /** 选中到切页的防抖延迟（毫秒） */
    public static final int NAV_SWITCH_DEBOUNCE_MS = 80;
}
