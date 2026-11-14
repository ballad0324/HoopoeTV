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
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 手机端搜索结果列表：与「小编推荐 / 搜索页排行」同一套行卡视觉 ——
 * 海报(右上角标) + 标题 + 年份·地区·类型 + 两行简介 + 来源小字。
 * 结果直接在搜索页的结果区里展示，不再跳独立页面。
 */
public class MobileSearchAdapter extends RecyclerView.Adapter<MobileSearchAdapter.Holder> {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");

    private final List<Movie.Video> data = new ArrayList<>();

    public interface OnItemClick {
        void onItemClick(Movie.Video video);
    }

    private OnItemClick onItemClick;

    public void setOnItemClick(OnItemClick listener) {
        this.onItemClick = listener;
    }

    public void setNewData(List<Movie.Video> list) {
        data.clear();
        if (list != null) data.addAll(list);
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

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_search_mobile, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final Movie.Video video = data.get(position);
        holder.name.setText(video.name == null ? "" : video.name);

        // 右上角标：优先 note（如「已完结」「HD」「更新至xx集」）
        String badge = clean(video.note);
        if (TextUtils.isEmpty(badge)) badge = clean(video.tag);
        if ("folder".equals(badge) || "cover".equals(badge)) badge = "";
        if (TextUtils.isEmpty(badge)) {
            holder.badge.setVisibility(View.GONE);
        } else {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(badge);
        }

        // 年份 · 地区 · 类型
        StringBuilder meta = new StringBuilder();
        if (video.year > 0) meta.append(video.year);
        String area = clean(video.area);
        if (!TextUtils.isEmpty(area)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(area);
        }
        String type = clean(video.type);
        if (!TextUtils.isEmpty(type)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(type);
        }
        if (meta.length() > 0) {
            holder.note.setVisibility(View.VISIBLE);
            holder.note.setText(meta.toString());
        } else {
            holder.note.setVisibility(View.GONE);
        }

        // 两行简介
        String blurb = clean(video.des);
        if (TextUtils.isEmpty(blurb)) {
            holder.blurb.setVisibility(View.GONE);
        } else {
            holder.blurb.setVisibility(View.VISIBLE);
            holder.blurb.setText(blurb);
        }

        String site = siteName(video.sourceKey);
        if (TextUtils.isEmpty(site)) {
            holder.site.setVisibility(View.GONE);
        } else {
            holder.site.setVisibility(View.VISIBLE);
            holder.site.setText("来源：" + site);
        }

        if (!TextUtils.isEmpty(video.pic)) {
            ImgUtil.load(video.pic.trim(), holder.pic, 8);
        } else {
            holder.pic.setImageResource(R.drawable.img_loading_placeholder);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onItemClick != null) onItemClick.onItemClick(video);
            }
        });
    }

    /** 去 HTML 标签 / 实体、压空白，便于当简介用 */
    private String clean(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String s = raw.trim();
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">");
        s = TAG.matcher(s).replaceAll(" ");
        s = SPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /** sourceKey 在接口里查不到时退回 key 本身，至少能看出结果来自哪个源 */
    private String siteName(String sourceKey) {
        if (TextUtils.isEmpty(sourceKey)) return "";
        SourceBean bean = ApiConfig.get().getSource(sourceKey);
        if (bean != null && !TextUtils.isEmpty(bean.getName())) return bean.getName();
        return sourceKey;
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView pic;
        TextView badge;
        TextView name;
        TextView note;
        TextView blurb;
        TextView site;

        Holder(View v) {
            super(v);
            pic = v.findViewById(R.id.ivPic);
            badge = v.findViewById(R.id.tvBadge);
            name = v.findViewById(R.id.tvName);
            note = v.findViewById(R.id.tvNote);
            blurb = v.findViewById(R.id.tvBlurb);
            site = v.findViewById(R.id.tvSite);
        }
    }
}