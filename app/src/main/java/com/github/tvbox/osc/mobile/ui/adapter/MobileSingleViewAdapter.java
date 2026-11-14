package com.github.tvbox.osc.mobile.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 只有一个固定 View 的适配器，用来把「搜索历史 + 热门搜索 + 分类页签」当成
 * rvBrowse 的列表头，跟排行列表共用同一个 RecyclerView 滚动。
 */
public class MobileSingleViewAdapter extends RecyclerView.Adapter<MobileSingleViewAdapter.VH> {

    private final View header;

    public MobileSingleViewAdapter(View header) {
        this.header = header;
    }

    @Override
    public int getItemCount() {
        return header == null ? 0 : 1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(header);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
    }

    static class VH extends RecyclerView.ViewHolder {
        VH(View v) {
            super(v);
        }
    }
}