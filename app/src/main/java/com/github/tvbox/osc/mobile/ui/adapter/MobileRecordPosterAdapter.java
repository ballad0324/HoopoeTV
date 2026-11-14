package com.github.tvbox.osc.mobile.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页「我的收藏 / 播放历史」的海报卡适配器：和「小编推荐」同一套视觉
 * （整张封面 + 底部浮层标题/副标题），配合横向 RecyclerView 一屏一张、左右滑切换。
 */
public class MobileRecordPosterAdapter extends RecyclerView.Adapter<MobileRecordPosterAdapter.VH> {

    private final List<MobileRecordAdapter.Row> data = new ArrayList<>();
    private MobileRecordAdapter.OnItemAction action;

    public void setOnItemAction(MobileRecordAdapter.OnItemAction action) {
        this.action = action;
    }

    public void setNewData(List<MobileRecordAdapter.Row> rows) {
        data.clear();
        if (rows != null) data.addAll(rows);
        notifyDataSetChanged();
    }

    public List<MobileRecordAdapter.Row> getData() {
        return data;
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record_poster, parent, false);
        // 首页横条一屏两张：卡片宽 =（屏宽 - 左右各 8dp 内边距 - 中间 10dp 间距）/ 2
        float density = parent.getResources().getDisplayMetrics().density;
        int screen = parent.getResources().getDisplayMetrics().widthPixels;
        int gap = (int) (density * 26 + 0.5f);
        int cardWidth = (screen - gap) / 2;
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int) (density * 10 + 0.5f), 0);
        v.setLayoutParams(lp);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH holder, int position) {
        final MobileRecordAdapter.Row row = data.get(position);
        holder.tvName.setText(TextUtils.isEmpty(row.name) ? "未命名" : row.name);
        holder.tvSub.setText(row.sub == null ? "" : row.sub);
        if (!TextUtils.isEmpty(row.pic)) {
            ImgUtil.load(row.pic, holder.ivThumb, 8);
        } else {
            holder.ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action != null) action.onClick(row);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (action != null) action.onLongClick(row);
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName;
        TextView tvSub;

        VH(View v) {
            super(v);
            ivThumb = v.findViewById(R.id.ivThumb);
            tvName = v.findViewById(R.id.tvName);
            tvSub = v.findViewById(R.id.tvSub);
        }
    }
}
