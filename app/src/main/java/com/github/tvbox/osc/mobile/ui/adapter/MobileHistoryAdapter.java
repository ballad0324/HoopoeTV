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
import com.github.tvbox.osc.mobile.ui.LiquidMotion;
import com.github.tvbox.osc.mobile.ui.MobilePlayHistory;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** 首页「继续观看」横向卡片：封面 + 片名 + 第 N 集 / 进度 */
public class MobileHistoryAdapter extends RecyclerView.Adapter<MobileHistoryAdapter.Holder> {

    private final List<MobilePlayHistory.Item> data = new ArrayList<>();
    private OnItemClick onItemClick;

    public interface OnItemClick {
        void onItemClick(MobilePlayHistory.Item item);
    }

    public void setOnItemClick(OnItemClick listener) {
        this.onItemClick = listener;
    }

    public void setNewData(List<MobilePlayHistory.Item> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public int size() {
        return data.size();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_history, parent, false);
        LiquidMotion.bindPress(v, 0.94f);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final MobilePlayHistory.Item item = data.get(position);
        holder.name.setText(item.name == null ? "" : item.name);
        String sub = item.subtitle();
        holder.sub.setText(sub);
        holder.sub.setVisibility(TextUtils.isEmpty(sub) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(item.pic)) {
            ImgUtil.load(item.pic.trim(), holder.thumb, 8);
        } else {
            holder.thumb.setImageResource(R.drawable.img_loading_placeholder);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClick != null) onItemClick.onItemClick(item);
            }
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView name;
        TextView sub;

        Holder(View v) {
            super(v);
            thumb = v.findViewById(R.id.ivThumb);
            name = v.findViewById(R.id.tvName);
            sub = v.findViewById(R.id.tvSub);
        }
    }
}
