package com.github.tvbox.osc.mobile.ui.adapter;

import com.github.tvbox.osc.mobile.ui.drawable.UiSkin;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机端选集网格：纯触摸点击，无焦点选中概念（TV 端对应 SeriesAdapter）。
 */
public class MobileSeriesAdapter extends RecyclerView.Adapter<MobileSeriesAdapter.Holder> {

    private final List<VodInfo.VodSeries> data = new ArrayList<>();
    private OnItemClick onItemClick;

    public interface OnItemClick {
        void onItemClick(int position, VodInfo.VodSeries series);
    }

    public void setOnItemClick(OnItemClick click) {
        this.onItemClick = click;
    }

    public void setData(List<VodInfo.VodSeries> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mobile_series, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final VodInfo.VodSeries series = data.get(position);
        holder.tvSeries.setText(series.name);
        holder.tvSeries.setSelected(series.selected);
        // 液态玻璃集数格子：选中蓝玻璃 + 白字，未选中白玻璃 + 灰字
        UiSkin.cell(holder.tvSeries, series.selected);
        holder.tvSeries.setTextColor(series.selected ? 0xFFFFFFFF
                : com.github.tvbox.osc.util.SkinManager.text2(holder.tvSeries.getContext()));
        holder.tvSeries.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClick != null) onItemClick.onItemClick(holder.getAdapterPosition(), series);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvSeries;

        Holder(View itemView) {
            super(itemView);
            tvSeries = itemView.findViewById(R.id.tvSeries);
        }
    }
}
