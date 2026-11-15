package com.github.tvbox.osc.mobile.ui.drawable;

import android.view.View;

import com.github.tvbox.osc.R;

/**
 * 静态圆角背景挂载（液态玻璃引擎已整体移除后的替代实现）。
 *
 * 全部改用 res/drawable 里的标准 layer-list / shape，不再有任何自绘 Drawable、
 * 不再有 RuntimeShader、不再有模糊位图采样；布局里没挂背景的地方也能统一上色。
 */
public final class UiSkin {

    private UiSkin() {
    }

    /** 大块面板（首页面板 / 详情信息卡）：方案② 30% 不透明，透出底部海报墙 */
    public static void card(View view) {
        set(view, R.drawable.shape_card_panel);
    }

    /** 列表行卡 */
    public static void row(View view) {
        set(view, R.drawable.bg_vod_card);
    }

    /** 底部 dock 胶囊 */
    public static void dock(View view) {
        set(view, R.drawable.shape_dock_pill);
    }

    /** 顶部导航条 */
    public static void nav(View view) {
        set(view, R.drawable.shape_nav_pill);
    }

    /** 大胶囊按钮（立即播放） */
    public static void pill(View view) {
        set(view, R.drawable.bg_play_cta);
    }

    /** 圆形图标按钮（下载 / 文件夹 / 收藏） */
    public static void circle(View view) {
        set(view, R.drawable.shape_btn_circle);
    }

    /** 线路 chip：选中实心，未选中描边 */
    public static void chip(View view, boolean on) {
        set(view, on ? R.drawable.shape_capsule_on : R.drawable.shape_capsule_track);
    }

    /** 集数格子：选中态走 selector 的 state_selected */
    public static void cell(View view, boolean on) {
        if (view == null) return;
        view.setSelected(on);
        view.setBackgroundResource(R.drawable.bg_series_cell);
    }

    /** 页面底 */
    public static void backdrop(View view) {
        set(view, R.drawable.shape_page_bg);
    }

    private static void set(View view, int resId) {
        if (view == null) return;
        try {
            view.setBackgroundResource(resId);
        } catch (Throwable ignored) {
        }
    }
}
