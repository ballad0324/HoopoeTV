package com.github.tvbox.osc.mobile.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏夹 / 历史 两列卡片：封面 + 底部蓝色进度条 + 片名 + 片源标签。
 * 长按某一张时只在这一张上显示右上角删除钮，点删除钮即删除。
 */
public class MobileRecordCardAdapter extends RecyclerView.Adapter<MobileRecordCardAdapter.VH> {

    public static class Row {
        public String vodId = "";
        public String sourceKey = "";
        public String name = "";
        public String pic = "";
        public String badge = "";
        /** 播放进度百分比，-1 表示没有进度（收藏夹不显示进度条） */
        public int percent = -1;
    }

    public interface OnAction {
        void onClick(Row row);

        void onLongClick(Row row, int position);

        void onDelete(Row row, int position);
    }

    private final List<Row> data = new ArrayList<>();
    private OnAction action;
    private int deletePosition = -1;

    public void setOnAction(OnAction action) {
        this.action = action;
    }

    public void setNewData(List<Row> rows) {
        data.clear();
        if (rows != null) data.addAll(rows);
        deletePosition = -1;
        notifyDataSetChanged();
    }

    public void hideDeleteButton() {
        if (deletePosition != -1) {
            int old = deletePosition;
            deletePosition = -1;
            notifyItemChanged(old);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH holder, final int position) {
        final Row row = data.get(position);
        holder.tvName.setText(TextUtils.isEmpty(row.name) ? "未命名" : row.name);
        holder.tvBadge.setText(TextUtils.isEmpty(row.badge) ? "本机" : row.badge);
        if (!TextUtils.isEmpty(row.pic)) {
            ImgUtil.load(row.pic, holder.ivThumb, 8);
        } else {
            holder.ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }

        int pct = row.percent;
        if (pct < 0) {
            holder.llProgress.setVisibility(View.GONE);
        } else {
            if (pct < 2) pct = 2;
            if (pct > 100) pct = 100;
            holder.llProgress.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) holder.vProgressFill.getLayoutParams();
            fp.weight = pct;
            holder.vProgressFill.setLayoutParams(fp);
            LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) holder.vProgressRest.getLayoutParams();
            rp.weight = Math.max(0, 100 - pct);
            holder.vProgressRest.setLayoutParams(rp);
        }

        holder.ivDelete.setVisibility(position == deletePosition ? View.VISIBLE : View.GONE);

        // 封面按宽度锁定 3:4 竖版比例（对齐参考图），只设一次
        if (!"ratio".equals(holder.cardCover.getTag())) {
            holder.cardCover.post(new Runnable() {
                @Override
                public void run() {
                    int w = holder.cardCover.getWidth();
                    if (w <= 0) return;
                    ViewGroup.LayoutParams lp = holder.cardCover.getLayoutParams();
                    int target = w * 4 / 3;
                    if (lp.height != target) {
                        lp.height = target;
                        holder.cardCover.setLayoutParams(lp);
                        holder.cardCover.setTag("ratio");
                    }
                }
            });
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deletePosition != -1) {
                    int old = deletePosition;
                    deletePosition = -1;
                    notifyItemChanged(old);
                    return;
                }
                if (action != null) action.onClick(row);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int old = deletePosition;
                deletePosition = position;
                if (old != -1) notifyItemChanged(old);
                notifyItemChanged(position);
                if (action != null) action.onLongClick(row, position);
                return true;
            }
        });
        holder.ivDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action != null) action.onDelete(row, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        View cardCover;
        ImageView ivThumb, ivDelete;
        TextView tvName, tvBadge;
        LinearLayout llProgress;
        View vProgressFill, vProgressRest;

        VH(View v) {
            super(v);
            cardCover = v.findViewById(R.id.cardCover);
            ivThumb = v.findViewById(R.id.ivThumb);
            ivDelete = v.findViewById(R.id.ivDelete);
            tvName = v.findViewById(R.id.tvName);
            tvBadge = v.findViewById(R.id.tvBadge);
            llProgress = v.findViewById(R.id.llProgress);
            vProgressFill = v.findViewById(R.id.vProgressFill);
            vProgressRest = v.findViewById(R.id.vProgressRest);
        }
    }
}