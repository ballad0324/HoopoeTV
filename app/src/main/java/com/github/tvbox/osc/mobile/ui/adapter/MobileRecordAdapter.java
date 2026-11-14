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
 * 首页「我的收藏 / 历史播放」与两个记录页共用的行卡：左封面 + 右标题/副标题。
 */
public class MobileRecordAdapter extends RecyclerView.Adapter<MobileRecordAdapter.VH> {

    public static class Row {
        public String name = "";
        public String pic = "";
        public String sub = "";
        public String vodId = "";
        public String sourceKey = "";
        public String flag = "";
    }

    public interface OnItemAction {
        void onClick(Row row);

        void onLongClick(Row row);
    }

    private final List<Row> data = new ArrayList<>();
    private OnItemAction action;

    public void setOnItemAction(OnItemAction action) {
        this.action = action;
    }

    public void setNewData(List<Row> rows) {
        data.clear();
        if (rows != null) data.addAll(rows);
        notifyDataSetChanged();
    }

    public List<Row> getData() {
        return data;
    }

    public Row first() {
        return data.isEmpty() ? null : data.get(0);
    }

    public int size() {
        return data.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH holder, int position) {
        final Row row = data.get(position);
        holder.tvName.setText(TextUtils.isEmpty(row.name) ? "未命名" : row.name);
        holder.tvSub.setText(row.sub == null ? "" : row.sub);
        if (!TextUtils.isEmpty(row.pic)) {
            ImgUtil.load(row.pic, holder.ivPic, 8);
        } else {
            holder.ivPic.setImageResource(R.drawable.bg_hist_cover);
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
        ImageView ivPic;
        TextView tvName;
        TextView tvSub;

        VH(View v) {
            super(v);
            ivPic = v.findViewById(R.id.ivPic);
            tvName = v.findViewById(R.id.tvName);
            tvSub = v.findViewById(R.id.tvSub);
        }
    }
}
