package com.github.tvbox.osc.mobile.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索页的历史词 / 热词标签，网格排布（自己画 chip 背景，不依赖 TV 端的 FlowLayout 深色样式）。
 */
public class MobileSearchTagAdapter extends RecyclerView.Adapter<MobileSearchTagAdapter.Holder> {

    private final List<String> data = new ArrayList<>();
    /** true：每个标签右侧带「×」，用于搜索历史逐条删除 */
    private final boolean withDelete;

    public MobileSearchTagAdapter() {
        this(false);
    }

    public MobileSearchTagAdapter(boolean withDelete) {
        this.withDelete = withDelete;
    }

    public interface OnTagClick {
        void onTagClick(String word);
    }

    public interface OnTagDelete {
        void onTagDelete(String word);
    }

    private OnTagClick onTagClick;
    private OnTagDelete onTagDelete;

    public void setOnTagClick(OnTagClick listener) {
        this.onTagClick = listener;
    }

    public void setOnTagDelete(OnTagDelete listener) {
        this.onTagDelete = listener;
    }

    /** 本地摘掉一条，不依赖外部重新拉库 */
    public void removeWord(String word) {
        int idx = data.indexOf(word);
        if (idx >= 0) {
            data.remove(idx);
            notifyItemRemoved(idx);
        }
    }

    public List<String> getData() {
        return data;
    }

    public void setNewData(List<String> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = withDelete ? R.layout.item_search_hist_mobile : R.layout.item_search_tag_mobile;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new Holder(v, withDelete);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final String word = data.get(position);
        holder.tag.setText(word == null ? "" : word);
        holder.tag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onTagClick != null) onTagClick.onTagClick(word);
            }
        });
        if (holder.del != null) {
            holder.del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onTagDelete != null) onTagDelete.onTagDelete(word);
                }
            });
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tag;
        TextView del;

        Holder(View v, boolean withDelete) {
            super(v);
            tag = v.findViewById(R.id.tvTag);
            del = withDelete ? (TextView) v.findViewById(R.id.tvDelete) : null;
        }
    }
}
