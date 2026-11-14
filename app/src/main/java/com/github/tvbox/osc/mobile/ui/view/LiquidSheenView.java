package com.github.tvbox.osc.mobile.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.github.tvbox.osc.R;

/**
 * 玻璃扫光层：沿着圆角矩形斜铺一条柔光带，周期性从左侧掠过。
 * 这是 iOS 26 Liquid Glass 里最直观的「玻璃是活的」信号，静态截图看不出来，必须靠动画。
 * 自身不拦截触摸（不 clickable，onTouchEvent 不消费），可以安全盖在 toolbar 上。
 */
public class LiquidSheenView extends View {

    /** 光带宽度占控件宽度的比例 */
    private static final float BAND_RATIO = 0.42f;
    /** 一个周期里真正扫过的时间占比，剩余时间静默，避免闪烁 */
    private static final float VISIBLE_RATIO = 0.55f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path clipPath = new Path();
    private final RectF clipRect = new RectF();

    private ValueAnimator animator;
    private float progress;
    private float cornerRadiusPx;
    private int sheenColor;

    public LiquidSheenView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        sheenColor = getResources().getColor(R.color.liquid_sheen);
        cornerRadiusPx = dp(22f);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    public LiquidSheenView corner(float dpValue) {
        cornerRadiusPx = dp(dpValue);
        invalidate();
        return this;
    }

    public void start() {
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(3600);
        animator.setStartDelay(500);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t = progress / VISIBLE_RATIO;
        if (t > 1f) return;

        float band = w * BAND_RATIO;
        float cx = -band + t * (w + band * 2f);
        LinearGradient gradient = new LinearGradient(
                cx, 0f, cx + band, h * 0.85f,
                new int[]{0x00FFFFFF, sheenColor, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        paint.setStyle(Paint.Style.FILL);

        clipRect.set(0f, 0f, (float) w, (float) h);
        clipPath.reset();
        clipPath.addRoundRect(clipRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(clipRect, paint);
        canvas.restoreToCount(save);
    }
}