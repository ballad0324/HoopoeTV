package com.github.tvbox.osc.mobile.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.mobile.ui.view.LiquidSheenView;

/**
 * Liquid Glass 动效工具：液态按压、图标弹跳、内容淡入、顶部玻璃扫光。
 * 所有方法都只做视觉，不消费触摸事件，不改变原有点击逻辑。
 */
public final class LiquidMotion {

    private static final String TAG_SHEEN = "liquid_header_sheen";

    private LiquidMotion() {
    }

    public static float dp(View v, float value) {
        return value * v.getResources().getDisplayMetrics().density;
    }

    /** 按下收缩到 0.95，松手 spring 回弹 */
    public static void bindPress(View v) {
        bindPress(v, 0.95f);
    }

    /** 可定制收缩比例的液态按压；返回 false 保证 onClick 仍然触发 */
    public static void bindPress(final View v, final float pressedScale) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().cancel();
                        view.animate()
                                .scaleX(pressedScale)
                                .scaleY(pressedScale)
                                .setDuration(120)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().cancel();
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(280)
                                .setInterpolator(new OvershootInterpolator(1.6f))
                                .start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** 图标弹一下：缩放 + 淡入，用于底部导航切换 */
    public static void popIcon(View v) {
        if (v == null) return;
        v.setAlpha(0.4f);
        v.setScaleX(0.82f);
        v.setScaleY(0.82f);
        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .start();
    }

    /** 列表项进场：上浮 + 淡入，delay 用于错落 */
    public static void reveal(View v, long delayMs) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(v, 14f));
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** 卡片/页面的浮起：轻微放大淡入 */
    public static void liftIn(View v) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    /**
     * 在 toolbar 玻璃上盖一层周期性扫光。
     *
     * @param leftRightDp 与 toolbar 左右外边距一致，保证扫光边界贴合玻璃
     */
    public static void attachHeaderSheen(Activity act, float topDp, float heightDp,
                                         float leftRightDp, float cornerDp) {
        if (act == null) return;
        ViewGroup content = act.findViewById(android.R.id.content);
        if (content == null) return;
        if (content.findViewWithTag(TAG_SHEEN) != null) return;

        LiquidSheenView sheen = new LiquidSheenView(act);
        sheen.setTag(TAG_SHEEN);
        sheen.corner(cornerDp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(sheen, heightDp));
        lp.gravity = Gravity.TOP;
        lp.topMargin = (int) dp(sheen, topDp);
        lp.leftMargin = (int) dp(sheen, leftRightDp);
        lp.rightMargin = (int) dp(sheen, leftRightDp);
        sheen.setLayoutParams(lp);
        content.addView(sheen);
        sheen.start();
    }
}