package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.github.tvbox.osc.util.LanSyncHelper;

/**
 * 局域网直连同步面板（手机端与电视端共用同一个面板）。
 *
 * 一台设备在这里读出「本机地址」，另一台设备把它填进输入框，即可在同一 WiFi 下
 * 直接互传收藏与设置 —— 不经过任何服务器、数据库或第三方云盘。
 */
public class LanSyncDialog {

    private final Activity act;
    private AlertDialog dialog;
    private EditText etPeer;
    private TextView tvStatus;
    private final Handler main = new Handler(Looper.getMainLooper());

    public LanSyncDialog(Activity act) {
        this.act = act;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                act.getResources().getDisplayMetrics());
    }

    public void show() {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FF14171F"));
        int pad = dp(22);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(act);
        title.setText("局域网直连同步");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.getPaint().setFakeBoldText(true);
        root.addView(title);

        TextView tvLocal = new TextView(act);
        String local = LanSyncHelper.localBase();
        if (local == null) {
            tvLocal.setText("本机没有连上 WiFi（请先连同一个路由，再回到这里）");
            tvLocal.setTextColor(Color.parseColor("#FFFF8A80"));
        } else if (!LanSyncHelper.serverRunning()) {
            tvLocal.setText("本机地址：" + local + "\n本机局域网服务没起来，对方暂时连不过来；可先用这里的「连接测试」连对方");
            tvLocal.setTextColor(Color.parseColor("#FFFFD180"));
        } else {
            tvLocal.setText("本机地址：" + local + "\n把这一串填到另一台设备上，就能连过来");
            tvLocal.setTextColor(Color.parseColor("#B3FFFFFF"));
        }
        tvLocal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(tvLocal, lp);

        etPeer = new EditText(act);
        etPeer.setHint("对方地址，如 192.168.1.8:9978");
        etPeer.setTextColor(Color.WHITE);
        etPeer.setHintTextColor(Color.parseColor("#66FFFFFF"));
        etPeer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        etPeer.setSingleLine(true);
        etPeer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etPeer.setBackgroundColor(Color.parseColor("#22FFFFFF"));
        etPeer.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpEt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpEt.topMargin = dp(12);
        root.addView(etPeer, lpEt);

        tvStatus = new TextView(act);
        tvStatus.setText("选择下面的动作；收藏是并集，设置是用对方覆盖本机");
        tvStatus.setTextColor(Color.parseColor("#99FFFFFF"));
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams lpSt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSt.topMargin = dp(10);
        root.addView(tvStatus, lpSt);

        root.addView(row(button("连接测试", new Runnable() {
            public void run() {
                LanSyncHelper.ping(peer(), cb("正在连接对方…"));
            }
        }), button("拉取收藏", new Runnable() {
            public void run() {
                LanSyncHelper.pullFav(peer(), cb("正在从对方拉取收藏…"));
            }
        })));

        root.addView(row(button("推送收藏", new Runnable() {
            public void run() {
                LanSyncHelper.pushFav(peer(), cb("正在把本机收藏推给对方…"));
            }
        }), button("拉取设置", new Runnable() {
            public void run() {
                LanSyncHelper.pullSettings(peer(), cb("正在从对方拉取设置…"));
            }
        })));

        root.addView(row(button("推送设置", new Runnable() {
            public void run() {
                LanSyncHelper.pushSettings(peer(), cb("正在把本机设置推给对方…"));
            }
        }), button("关闭", new Runnable() {
            public void run() {
                if (dialog != null) dialog.dismiss();
            }
        })));

        dialog = new AlertDialog.Builder(act).setView(root).create();
        dialog.show();
        // 电视端优先把焦点放到输入框，遥控器按一下就能打字
        if (etPeer.requestFocus()) {
            etPeer.setSelection(etPeer.getText().length());
        }
    }

    private String peer() {
        return etPeer == null ? null : etPeer.getText().toString();
    }

    private LanSyncHelper.Callback cb(final String doing) {
        setStatus(doing);
        return new LanSyncHelper.Callback() {
            @Override
            public void onResult(final boolean ok, final String msg) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        setStatus((ok ? "✓ " : "✗ ") + msg);
                    }
                });
            }
        };
    }

    private void setStatus(final String text) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (tvStatus != null) {
                    tvStatus.setTextColor(Color.parseColor("#D9FFFFFF"));
                    tvStatus.setText(text);
                }
            }
        });
    }

    private Button button(String text, final Runnable action) {
        Button b = new Button(act);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return b;
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(10);
        row.setLayoutParams(rowLp);
        for (int i = 0; i < buttons.length; i++) {
            Button b = buttons[i];
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i > 0) blp.setMarginStart(dp(10));
            row.addView(b, blp);
        }
        return row;
    }
}
