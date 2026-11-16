package com.github.tvbox.osc.mobile.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 手机端直播频道网格：纯触摸点击（TV 端是 TvRecyclerView + 焦点选中）。
 */
public class MobileChannelAdapter extends RecyclerView.Adapter<MobileChannelAdapter.Holder> {

    private final List<LiveChannelItem> data = new ArrayList<>();
    private int selectedIndex = -1;
    private OnItemClick onItemClick;

    public interface OnItemClick {
        void onItemClick(int position, LiveChannelItem item);
    }

    public void setOnItemClick(OnItemClick click) {
        this.onItemClick = click;
    }

    public void setData(List<LiveChannelItem> list, int selected) {
        data.clear();
        if (list != null) data.addAll(list);
        selectedIndex = selected;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mobile_channel, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final LiveChannelItem item = data.get(position);
        String name = item.getChannelName();
        holder.tvChannel.setText(name == null ? "" : name);
        boolean on = position == selectedIndex;
        holder.tvChannel.setSelected(on);
        holder.tvChannel.setTextColor((on ? com.github.tvbox.osc.util.SkinManager.text1(holder.tvChannel.getContext()) : com.github.tvbox.osc.util.SkinManager.text2(holder.tvChannel.getContext())));
        holder.tvChannel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClick != null) onItemClick.onItemClick(holder.getAdapterPosition(), item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvChannel;

        Holder(View itemView) {
            super(itemView);
            tvChannel = itemView.findViewById(R.id.tvChannel);
        }
    }
}
