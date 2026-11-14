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
import com.github.tvbox.osc.mobile.discover.DiscoverItem;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索页下方的「排行 / 浏览」列表：名次 + 海报（角标、评分）+ 标题 + 年份·地区·类型 + 两行简介，
 * 与「全部影片」类 App 的搜索首页列表视觉一致。
 */
public class MobileRankAdapter extends RecyclerView.Adapter<MobileRankAdapter.VH> {

    private final List<DiscoverItem> data = new ArrayList<>();
    private OnItemClick onItemClick;
    /** 当前分类的中文名，用于拼副标题里的类型（「全部」时退回条目自己的分类） */
    private String catLabel = "";

    public interface OnItemClick {
        void onItemClick(DiscoverItem item);
    }

    public void setOnItemClick(OnItemClick listener) {
        this.onItemClick = listener;
    }

    public void setCatLabel(String label) {
        this.catLabel = label == null ? "" : label;
    }

    public void setNewData(List<DiscoverItem> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    /** 续页追加，用于整站分类浏览的滚动加载 */
    public void addData(List<DiscoverItem> list) {
        if (list == null || list.isEmpty()) return;
        int start = data.size();
        data.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public int getDataSize() {
        return data.size();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rank_mobile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH holder, int position) {
        final DiscoverItem it = data.get(position);
        holder.tvRank.setText(String.valueOf(position + 1));

        holder.tvName.setText(TextUtils.isEmpty(it.name) ? "未命名" : it.name);

        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(it.year)) meta.append(it.year.trim());
        if (!TextUtils.isEmpty(it.area)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(it.area.trim());
        }
        String type = typeOf(it);
        if (!TextUtils.isEmpty(type)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(type);
        }
        holder.tvMeta.setText(meta.toString());

        String blurb = TextUtils.isEmpty(it.blurb) ? it.content : it.blurb;
        blurb = clean(blurb);
        if (TextUtils.isEmpty(blurb)) {
            holder.tvBlurb.setVisibility(View.GONE);
        } else {
            holder.tvBlurb.setVisibility(View.VISIBLE);
            holder.tvBlurb.setText(blurb);
        }

        if (!TextUtils.isEmpty(it.remarks)) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText(it.remarks);
        } else {
            holder.tvBadge.setVisibility(View.GONE);
        }

        if (it.score > 0) {
            holder.tvScore.setVisibility(View.VISIBLE);
            holder.tvScore.setText(String.format(java.util.Locale.CHINA, "%.1f", it.score));
        } else {
            holder.tvScore.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(it.pic)) {
            ImgUtil.load(it.pic, holder.ivPoster, 8);
        } else {
            holder.ivPoster.setImageResource(R.drawable.img_loading_placeholder);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClick != null) onItemClick.onItemClick(it);
            }
        });
    }

    /** 副标题里的类型：选中的分类优先，否则用条目 vod_class 的第一个词 */
    private String typeOf(DiscoverItem it) {
        if (!TextUtils.isEmpty(catLabel) && !"全部".equals(catLabel)) return catLabel;
        String cls = it == null ? null : it.cls;
        if (TextUtils.isEmpty(cls)) return "";
        String first = cls.split(",")[0];
        first = first.split("\\|")[0].trim();
        return first;
    }

    /** 简介去 HTML 标签与多余空白 */
    private String clean(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String s = raw.replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvRank;
        ImageView ivPoster;
        TextView tvBadge;
        TextView tvScore;
        TextView tvName;
        TextView tvMeta;
        TextView tvBlurb;

        VH(View v) {
            super(v);
            tvRank = v.findViewById(R.id.tvRank);
            ivPoster = v.findViewById(R.id.ivPoster);
            tvBadge = v.findViewById(R.id.tvBadge);
            tvScore = v.findViewById(R.id.tvScore);
            tvName = v.findViewById(R.id.tvName);
            tvMeta = v.findViewById(R.id.tvMeta);
            tvBlurb = v.findViewById(R.id.tvBlurb);
        }
    }
}