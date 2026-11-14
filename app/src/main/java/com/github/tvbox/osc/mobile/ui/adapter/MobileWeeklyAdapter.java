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
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.mobile.ui.LiquidMotion;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/** 首页「本周推荐」横向大卡：封面 + 底部玻璃条（片名 / 备注） */
public class MobileWeeklyAdapter extends RecyclerView.Adapter<MobileWeeklyAdapter.Holder> {

    private final List<Movie.Video> data = new ArrayList<>();
    private OnItemClick onItemClick;

    public interface OnItemClick {
        void onItemClick(Movie.Video video);
    }

    public void setOnItemClick(OnItemClick listener) {
        this.onItemClick = listener;
    }

    public void setNewData(List<Movie.Video> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public Movie.Video first() {
        return data.isEmpty() ? null : data.get(0);
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
                .inflate(R.layout.item_weekly, parent, false);
        v.setClipToOutline(true);
        LiquidMotion.bindPress(v, 0.95f);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final Movie.Video video = data.get(position);
        holder.name.setText(video.name == null ? "" : video.name);
        String sub = TextUtils.isEmpty(video.note) ? (video.year > 0 ? String.valueOf(video.year) : "") : video.note;
        holder.sub.setText(sub);
        holder.sub.setVisibility(TextUtils.isEmpty(sub) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(video.pic)) {
            ImgUtil.load(video.pic.trim(), holder.thumb, 8);
        } else {
            holder.thumb.setImageResource(R.drawable.img_loading_placeholder);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClick != null) onItemClick.onItemClick(video);
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
