package com.github.tvbox.osc.mobile.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * 底部 dock 容器：负责「在 dock 上左右滑动切换相邻模块」的手势。
 * 只有横向位移超过阈值时才拦截事件（此时不会触发点击）；横向位移不足则照常交给子项点击。
 */
public class MobileDockLayout extends LinearLayout {

    public interface OnDockSwipeListener {
        /**
         * @param direction 1 = 往左滑（切下一个），-1 = 往右滑（切上一个）
         */
        void onSwipe(int direction);
    }

    private OnDockSwipeListener swipeListener;
    private float downX;
    private float downY;
    private float threshold;
    private boolean consumed;

    public MobileDockLayout(Context context) {
        super(context);
        init(context);
    }

    public MobileDockLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MobileDockLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        threshold = ViewConfiguration.get(context).getScaledTouchSlop() * 3f;
    }

    public void setOnDockSwipeListener(OnDockSwipeListener listener) {
        this.swipeListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 在 dispatch 阶段判断手势：DOWN 之后任意一次横向位移超阈值即切换，
        // 不依赖 onInterceptTouchEvent（子项 clickable 时该回调并不总被派发）
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                consumed = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!consumed) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                        consumed = true;
                        if (swipeListener != null) {
                            swipeListener.onSwipe(dx < 0 ? 1 : -1);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
}
