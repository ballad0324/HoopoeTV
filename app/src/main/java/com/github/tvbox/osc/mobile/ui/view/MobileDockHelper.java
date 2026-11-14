package com.github.tvbox.osc.mobile.ui.view;

import com.github.tvbox.osc.mobile.ui.drawable.UiSkin;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.mobile.ui.activity.MobileDiscoverActivity;
import com.github.tvbox.osc.mobile.ui.activity.MobileSearchActivity;
import com.github.tvbox.osc.mobile.ui.activity.MobileHomeActivity;
import com.github.tvbox.osc.mobile.ui.activity.MobileLiveActivity;

/**
 * 手机端底部 dock 栏的统一绑定层：首页 / 直播 / 搜索 / 发现 四页共用同一份 view_mobile_dock.xml，
 * 这里统一处理选中态、点击跳转，以及「在 dock 上左右滑动切换到相邻模块」的手势。
 *
 * v1.5.6 起：第四位由「设置」改为「发现」，设置入口移到各页右上角（明暗切换图标右侧）。
 */
public class MobileDockHelper {

    public static final int TAB_VOD = 0;
    public static final int TAB_LIVE = 1;
    public static final int TAB_SEARCH = 2;
    public static final int TAB_DISCOVER = 3;

    /** 没有对应 dock 项的页面（如设置页）传它，四个 item 都不点亮 */
    public static final int TAB_NONE = -1;

    private MobileDockHelper() {
    }

    public static void bind(final Activity act, final int currentTab) {
        if (act == null) return;
        View navBar = act.findViewById(R.id.navBar);
        final LinearLayout navVod = act.findViewById(R.id.navVod);
        final LinearLayout navLive = act.findViewById(R.id.navLive);
        final LinearLayout navSearch = act.findViewById(R.id.navSearch);
        final LinearLayout navDiscover = act.findViewById(R.id.navDiscover);
        if (navVod == null || navLive == null || navSearch == null || navDiscover == null) return;

        // dock 玻璃胶囊（代码 new，四个页面共用）
        UiSkin.dock(navBar);

        paint(navVod, currentTab == TAB_VOD);
        paint(navLive, currentTab == TAB_LIVE);
        paint(navSearch, currentTab == TAB_SEARCH);
        paint(navDiscover, currentTab == TAB_DISCOVER);

        click(act, navVod, TAB_VOD, currentTab);
        click(act, navLive, TAB_LIVE, currentTab);
        click(act, navSearch, TAB_SEARCH, currentTab);
        click(act, navDiscover, TAB_DISCOVER, currentTab);

        bindSwipe(act, navBar, currentTab);
    }

    /** 选中态：图标染色 + 文案变主题蓝，未选中走暗色 */
    public static void paint(LinearLayout item, boolean on) {
        if (item == null) return;
        for (int i = 0; i < item.getChildCount(); i++) {
            View child = item.getChildAt(i);
            if (child instanceof ImageView) {
                ImageView icon = (ImageView) child;
                int color = on ? item.getResources().getColor(R.color.shell_primary)
                        : item.getResources().getColor(R.color.shell_text_dim);
                icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            } else if (child instanceof TextView) {
                TextView label = (TextView) child;
                label.setTextColor(on ? item.getResources().getColor(R.color.shell_primary)
                        : item.getResources().getColor(R.color.shell_text_dim));
                label.getPaint().setFakeBoldText(on);
                label.invalidate();
            }
        }
    }

    private static void click(final Activity act, View item, final int target, final int currentTab) {
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (target == currentTab) return;
                open(act, target);
            }
        });
    }

    /**
     * dock 栏上左右滑动切换相邻模块：由 MobileDockLayout 在横向位移超过阈值时回调，
     * 滑动不会误触点击，点击仍由 4 个 item 自己处理。
     */
    public static void bindSwipe(final Activity act, View navBar, final int currentTab) {
        if (!(navBar instanceof MobileDockLayout)) return;
        ((MobileDockLayout) navBar).setOnDockSwipeListener(new MobileDockLayout.OnDockSwipeListener() {
            @Override
            public void onSwipe(int direction) {
                // 设置页等「不属于 dock」的页面（TAB_NONE）不做滑动切换，避免误跳
                if (currentTab == TAB_NONE) return;
                int target = currentTab + direction;
                if (target >= TAB_VOD && target <= TAB_DISCOVER) {
                    open(act, target);
                }
            }
        });
    }

    private static void open(Activity act, int target) {
        Class<?> cls;
        switch (target) {
            case TAB_VOD:
                cls = MobileHomeActivity.class;
                break;
            case TAB_LIVE:
                cls = MobileLiveActivity.class;
                break;
            case TAB_SEARCH:
                cls = MobileSearchActivity.class;
                break;
            case TAB_DISCOVER:
                cls = MobileDiscoverActivity.class;
                break;
            default:
                return;
        }
        Intent intent = new Intent(act, cls);
        act.startActivity(intent);
        // 同级页面切换：离开非首页页面时收起自己，避免返回栈越堆越长；首页本身留在栈底
        if (!(act instanceof MobileHomeActivity)) {
            act.finish();
        }
    }
}
