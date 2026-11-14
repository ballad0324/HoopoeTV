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

/**
 * 手机端影视卡片适配器：触摸式网格项，不复用 TV 的 GridAdapter（后者按 mm 基准算尺寸）。
 */
public class MobileVodAdapter extends RecyclerView.Adapter<MobileVodAdapter.VodHolder> {

    private static final int TYPE_HEADER = 1000;
    private static final int TYPE_VOD = 1001;

    private final List<Movie.Video> data = new ArrayList<>();
    /** 首屏卡片错落淡入：只对第一次出现的卡片做，滚动回来不再重播 */
    private boolean[] revealed = new boolean[0];
    /** 列表首项：首页的「收藏/历史 + 小编推荐标题」，随列表一起滚动 */
    private View headerView;

    private OnItemClick onItemClick;
    private OnItemLongClick onItemLongClick;

    public interface OnItemClick {
        void onItemClick(Movie.Video video);
    }

    public interface OnItemLongClick {
        void onItemLongClick(Movie.Video video);
    }

    public void setOnItemClick(OnItemClick listener) {
        this.onItemClick = listener;
    }

    public void setOnItemLongClick(OnItemLongClick listener) {
        this.onItemLongClick = listener;
    }

    public void setNewData(List<Movie.Video> list) {
        data.clear();
        if (list != null) data.addAll(list);
        revealed = new boolean[data.size()];
        notifyDataSetChanged();
    }

    public void addData(List<Movie.Video> list) {
        if (list == null || list.isEmpty()) return;
        int start = data.size();
        data.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public List<Movie.Video> getData() {
        return data;
    }

    /** 设置列表首项（首页的收藏/历史模块）。传 null 表示没有头部 */
    public void setHeaderView(View view) {
        this.headerView = view;
        notifyDataSetChanged();
    }

    public boolean hasHeader() {
        return headerView != null;
    }

    @Override
    public int getItemCount() {
        return data.size() + (headerView == null ? 0 : 1);
    }

    @Override
    public int getItemViewType(int position) {
        if (headerView != null && position == 0) return TYPE_HEADER;
        return TYPE_VOD;
    }

    @NonNull
    @Override
    public VodHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            return new VodHolder(headerView);
        }
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_vod_mobile, parent, false);
        // Liquid Glass：整卡按下收缩 + 松手弹簧回弹
        LiquidMotion.bindPress(v, 0.94f);
        return new VodHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VodHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) return;
        final Movie.Video video = data.get(position - (headerView == null ? 0 : 1));
        if (video == null) return;
        holder.name.setText(video.name == null ? "" : video.name);

        // 信息条第二行：年份 · 子分类（类型），缺省时退回备注
        StringBuilder sub = new StringBuilder();
        if (video.year > 0) sub.append(video.year);
        if (!TextUtils.isEmpty(video.type)) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append(video.type);
        }
        if (sub.length() == 0 && !TextUtils.isEmpty(video.note)) sub.append(video.note);
        if (sub.length() == 0) {
            holder.year.setVisibility(View.GONE);
        } else {
            holder.year.setVisibility(View.VISIBLE);
            holder.year.setText(sub.toString());
        }

        if (TextUtils.isEmpty(video.note)) {
            holder.note.setVisibility(View.GONE);
        } else {
            holder.note.setVisibility(View.VISIBLE);
            holder.note.setText(video.note);
        }

        // 首屏（前 10 张）错落淡入，延迟按位置递增
        int dataIndex = position - (headerView == null ? 0 : 1);
        if (dataIndex >= 0 && dataIndex < revealed.length && dataIndex < 10 && !revealed[dataIndex]) {
            revealed[dataIndex] = true;
            LiquidMotion.reveal(holder.itemView, dataIndex * 42L);
        }

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
        if (holder.play != null) {
            holder.play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onItemClick != null) onItemClick.onItemClick(video);
                }
            });
        }
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (onItemLongClick != null) {
                    onItemLongClick.onItemLongClick(video);
                    return true;
                }
                return false;
            }
        });
    }

    static class VodHolder extends RecyclerView.ViewHolder {
        ImageView thumb;
        ImageView play;
        TextView name;
        TextView note;
        TextView year;

        VodHolder(View v) {
            super(v);
            thumb = v.findViewById(R.id.ivThumb);
            play = v.findViewById(R.id.ivPlay);
            name = v.findViewById(R.id.tvName);
            note = v.findViewById(R.id.tvNote);
            year = v.findViewById(R.id.tvYear);
        }
    }
}
