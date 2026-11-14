package com.github.tvbox.osc.mobile.setting;

/**
 * 手机端交互参数集中处（D 级：真机调手感只需要改这一个文件）。
 *
 * 全部是「拍出来的经验值」，没有实机验证前不要扩散到各 Activity 里写死，
 * 否则以后调手感要翻好几个文件。
 */
public class MobileUiTuning {

    /* ---------------- 播放页手势 ---------------- */

    /** 起手判定阈值：位移超过这个距离才认定为滑动手势（dp） */
    public static final int GESTURE_START_SLOP_DP = 16;
    /** 单击判定允许的最大位移（dp） */
    public static final int TAP_MAX_MOVE_DP = 10;
    /** 单击判定允许的最长按压时长（ms） */
    public static final int TAP_MAX_MS = 260;
    /** 双击判定窗口（ms） */
    public static final int DOUBLE_TAP_MS = 320;
    /** 横滑调进度的灵敏度：从按下到屏幕最右（按满屏宽的比例）对应整片时长 */
    public static final float SEEK_SENSITIVITY_SCREEN_RATIO = 0.75f;
    /** 竖滑的行程基准：从按下到屏幕高度的这个比例对应满量程（亮度或音量） */
    public static final float VERTICAL_FULL_RANGE_SCREEN_RATIO = 0.6f;
    /** 手势提示气泡自动隐藏延时（ms） */
    public static final int HINT_AUTO_HIDE_MS = 900;
    /** 亮度下限，防止调到全黑看不见 */
    public static final float BRIGHTNESS_MIN = 0.03f;

    /* ---------------- 续播 ---------------- */

    /** 片尾回零阈值：距离结束不足这个时长就记为 0，下次从头播（ms） */
    public static final long TAIL_RESET_MS = 10 * 1000L;

    /* ---------------- 选集抽屉 ---------------- */

    /** 抽屉里剧集网格的列数 */
    public static final int SERIES_PANEL_COLUMNS = 5;

    /* ---------------- 直播 ---------------- */

    /** 直播画面比例循环顺序（与 VideoView 常量对应） */
    public static final int[] LIVE_SCALE_CYCLE = new int[]{
            0, // SCREEN_SCALE_DEFAULT
            1, // SCREEN_SCALE_16_9
            2, // SCREEN_SCALE_4_3
            3  // SCREEN_SCALE_MATCH_PARENT
    };
}
