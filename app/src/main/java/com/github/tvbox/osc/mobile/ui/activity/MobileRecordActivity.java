package com.github.tvbox.osc.mobile.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.compose.ui.platform.ComposeView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.github.tvbox.osc.mobile.ui.MobilePlayHistory;
import com.github.tvbox.osc.mobile.ui.compose.MobileRecordScreen;
import com.github.tvbox.osc.mobile.ui.compose.RecordHost;
import com.github.tvbox.osc.mobile.ui.compose.RecordRow;
import com.github.tvbox.osc.mobile.ui.compose.RecordUiState;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;

/**
 * 历史 / 收藏夹页（Jetpack Compose 试点）。
 * <p>
 * 页面本身（返回键、分段胶囊、分区标题、两列卡片、进度条、长按删除钮）全部由
 * {@code mobile/ui/compose/RecordScreen.kt} 绘制；这个 Activity 只做三件事：
 * 1) 绑定底部 dock；2) 把 Compose 挂到 ComposeView；3) 承接需要 Android 弹窗/跳转的动作。
 */
public class MobileRecordActivity extends BaseActivity implements RecordHost {

    public static final String MODE_FAV = "fav";
    public static final String MODE_HISTORY = "history";

    private String mode = MODE_HISTORY;
    private ComposeView cvRecord;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_record_mobile;
    }

    @Override
    public float getSizeInDp() {
        return 360;
    }

    @Override
    protected void init() {
        MobileDockHelper.bind(this, MobileDockHelper.TAB_VOD);
        mode = getIntent() == null ? MODE_HISTORY : getIntent().getStringExtra("mode");
        if (mode == null) mode = MODE_HISTORY;

        cvRecord = findViewById(R.id.cvRecord);
        MobileRecordScreen.bindRecordScreen(cvRecord, MODE_FAV.equals(mode), this);
    }

    // ---------------- RecordHost 回调 ----------------

    @Override
    public void onBack() {
        finish();
    }

    @Override
    public void onOpenRecord(RecordRow row) {
        Bundle bundle = new Bundle();
        bundle.putString("id", row.getVodId());
        bundle.putString("sourceKey", row.getSourceKey());
        bundle.putString("title", row.getName());
        Intent intent = new Intent(this, MobileDetailActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    @Override
    public void onDeleteRecord(final RecordRow row, final boolean fav) {
        new AlertDialog.Builder(this)
                .setTitle("删除")
                .setMessage("从「" + (fav ? "收藏夹" : "历史") + "」里移除「" + row.getName() + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (fav) {
                        MobileFavorites.remove(row.getVodId(), row.getSourceKey());
                    } else {
                        MobilePlayHistory.remove(row.getVodId(), row.getSourceKey());
                    }
                    RecordUiState.bump();
                    Toast.makeText(MobileRecordActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    public void onClearRecords(final boolean fav) {
        new AlertDialog.Builder(this)
                .setTitle("清空")
                .setMessage(fav ? "清空全部收藏记录？" : "清空全部播放历史？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    if (fav) {
                        for (MobileFavorites.Item it : MobileFavorites.load()) {
                            MobileFavorites.remove(it.vodId, it.sourceKey);
                        }
                    } else {
                        MobilePlayHistory.clear();
                    }
                    RecordUiState.bump();
                    Toast.makeText(MobileRecordActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回到本页（例如从详情页返回）时让 Compose 重读本地记录
        RecordUiState.bump();
    }
}
