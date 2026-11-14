package com.github.tvbox.osc.mobile.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.mobile.ui.MobileFavorites;
import com.github.tvbox.osc.mobile.ui.MobilePlayHistory;
import com.github.tvbox.osc.mobile.ui.adapter.MobileRecordCardAdapter;
import com.github.tvbox.osc.mobile.ui.view.MobileDockHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史 / 收藏夹：顶部分段胶囊切换，两列卡片居中排列。
 * 卡片 = 封面（无图显示占位）+ 底部蓝色播放进度条 + 片名 + 片源标签；
 * 长按某张卡片只在该卡片右上角出现删除钮，点删除钮即删。
 * 进入页面自动带出最近一条（历史/收藏都可切），列表按时间倒序。
 */
public class MobileRecordActivity extends BaseActivity {

    public static final String MODE_FAV = "fav";
    public static final String MODE_HISTORY = "history";

    private String mode = MODE_HISTORY;
    private RecyclerView rvRecord;
    private TextView tvTabHistory;
    private TextView tvTabFav;
    private TextView tvSection;
    private TextView tvEmpty;
    private MobileRecordCardAdapter adapter;

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

        tvTabHistory = findViewById(R.id.tvTabHistory);
        tvTabFav = findViewById(R.id.tvTabFav);
        tvSection = findViewById(R.id.tvSection);
        tvEmpty = findViewById(R.id.tvEmpty);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tvTabHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(MODE_HISTORY);
            }
        });
        tvTabFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(MODE_FAV);
            }
        });
        findViewById(R.id.tvClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });

        rvRecord = findViewById(R.id.rvRecord);
        rvRecord.setLayoutManager(new GridLayoutManager(this, 2));
        applyColumnPadding();
        adapter = new MobileRecordCardAdapter();
        adapter.setOnAction(new MobileRecordCardAdapter.OnAction() {
            @Override
            public void onClick(MobileRecordCardAdapter.Row row) {
                openDetail(row);
            }

            @Override
            public void onLongClick(MobileRecordCardAdapter.Row row, int position) {
                // 删除钮由适配器直接显示，这里只给一句提示
                Toast.makeText(MobileRecordActivity.this, "点右上角删除钮可移除", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(MobileRecordCardAdapter.Row row, int position) {
                doDelete(row);
            }
        });
        rvRecord.setAdapter(adapter);

        updateTabUi();
        reload();
    }

    /** 两列整体居中：把内容列宽度稳定在 336dp 左右，屏幕再宽也是居中两格 */
    private void applyColumnPadding() {
        float density = getResources().getDisplayMetrics().density;
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int pad = Math.max(16, (widthDp - 336) / 2);
        int px = (int) (pad * density);
        rvRecord.setPadding(px, rvRecord.getPaddingTop(), px, rvRecord.getPaddingBottom());
    }

    private void switchMode(String target) {
        if (target == null || target.equals(mode)) return;
        mode = target;
        adapter.hideDeleteButton();
        updateTabUi();
        reload();
    }

    /** 胶囊选中态：选中白底深字，未选中透明底灰字 */
    private void updateTabUi() {
        boolean history = MODE_HISTORY.equals(mode);
        tvTabHistory.setSelected(history);
        tvTabFav.setSelected(!history);
        tvTabHistory.setBackgroundResource(history ? R.drawable.shape_tab_on : android.R.color.transparent);
        tvTabFav.setBackgroundResource(history ? android.R.color.transparent : R.drawable.shape_tab_on);
        tvTabHistory.setTextColor(ContextCompat.getColor(this, history ? R.color.shell_text : R.color.shell_text_dim));
        tvTabFav.setTextColor(ContextCompat.getColor(this, history ? R.color.shell_text_dim : R.color.shell_text));
        tvSection.setText(history ? "继续观看" : "我的收藏");
    }

    private void reload() {
        List<MobileRecordCardAdapter.Row> rows;
        if (MODE_FAV.equals(mode)) {
            rows = favRows();
        } else {
            rows = historyRows();
        }
        adapter.setNewData(rows);
        tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        // 自动加载：进入即定位到最近一条（列表本身按时间倒序）
        if (!rows.isEmpty()) rvRecord.scrollToPosition(0);
    }

    private List<MobileRecordCardAdapter.Row> historyRows() {
        List<MobileRecordCardAdapter.Row> rows = new ArrayList<>();
        for (MobilePlayHistory.Item it : MobilePlayHistory.load()) {
            MobileRecordCardAdapter.Row row = new MobileRecordCardAdapter.Row();
            row.name = it.name;
            row.pic = it.pic;
            row.vodId = it.vodId;
            row.sourceKey = it.sourceKey;
            row.badge = sourceLabel(it.sourceKey);
            row.percent = (it.duration > 0 && it.progress > 0)
                    ? (int) (it.progress * 100 / it.duration) : -1;
            rows.add(row);
        }
        return rows;
    }

    private List<MobileRecordCardAdapter.Row> favRows() {
        List<MobileRecordCardAdapter.Row> rows = new ArrayList<>();
        for (MobileFavorites.Item it : MobileFavorites.load()) {
            MobileRecordCardAdapter.Row row = new MobileRecordCardAdapter.Row();
            row.name = it.name;
            row.pic = it.pic;
            row.vodId = it.vodId;
            row.sourceKey = it.sourceKey;
            row.badge = sourceLabel(it.sourceKey);
            row.percent = -1;
            rows.add(row);
        }
        return rows;
    }

    /** 片源 key → 显示名（拿不到就退回 key 本身） */
    private String sourceLabel(String key) {
        if (TextUtils.isEmpty(key)) return "本机";
        try {
            SourceBean sb = ApiConfig.get().getSource(key);
            if (sb != null && !TextUtils.isEmpty(sb.getName())) return sb.getName();
        } catch (Throwable ignored) {
        }
        return key;
    }

    private void openDetail(MobileRecordCardAdapter.Row row) {
        Bundle bundle = new Bundle();
        bundle.putString("id", row.vodId);
        bundle.putString("sourceKey", row.sourceKey);
        bundle.putString("title", row.name);
        Intent intent = new Intent(this, MobileDetailActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    private void doDelete(final MobileRecordCardAdapter.Row row) {
        new AlertDialog.Builder(this)
                .setTitle("删除")
                .setMessage("从「" + (MODE_FAV.equals(mode) ? "收藏夹" : "历史") + "」里移除「" + row.name + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (MODE_FAV.equals(mode)) {
                        MobileFavorites.remove(row.vodId, row.sourceKey);
                    } else {
                        MobilePlayHistory.remove(row.vodId, row.sourceKey);
                    }
                    reload();
                    Toast.makeText(MobileRecordActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void confirmClear() {
        if (adapter.getItemCount() == 0) {
            Toast.makeText(this, "没有可清空的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空")
                .setMessage(MODE_FAV.equals(mode) ? "清空全部收藏记录？" : "清空全部播放历史？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    if (MODE_FAV.equals(mode)) {
                        for (MobileFavorites.Item it : MobileFavorites.load()) {
                            MobileFavorites.remove(it.vodId, it.sourceKey);
                        }
                    } else {
                        MobilePlayHistory.clear();
                    }
                    reload();
                    Toast.makeText(MobileRecordActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            updateTabUi();
            reload();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}