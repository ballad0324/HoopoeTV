package com.github.tvbox.osc.mobile.live;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节目单页中列：频道列表。
 *
 * 不再有左侧色条，改用频道名颜色表达状态：
 *   蓝 = 源可用且支持回放；灰 = 源失效或不支持回放。
 * 长按频道名提示具体原因（源失效 / 不支持回放）。
 */
public class EpgChannelAdapter extends RecyclerView.Adapter<EpgChannelAdapter.Holder> {

    public interface OnChannelClick {
        void onChannelClick(int position, LiveChannelItem item);
    }

    public interface CatchupChecker {
        boolean canCatchup(LiveChannelItem item);
    }

    private final List<LiveChannelItem> data = new ArrayList<>();
    private int selected = -1;
    private OnChannelClick click;
    private CatchupChecker checker;
    /** url -> 源是否可用（null 表示还没检测出结果） */
    private final Map<String, Boolean> alive = new HashMap<>();

    /** 蓝 = 正常（源在且能回放） */
    private static final int COLOR_OK = 0xFF1A6DFF;
    /** 灰 = 源失效 / 不支持回放 */
    private static final int COLOR_DIM = 0xFF9AA1AC;

    public void setChecker(CatchupChecker checker) {
        this.checker = checker;
    }

    public void setOnChannelClick(OnChannelClick click) {
        this.click = click;
    }

    public void setData(List<LiveChannelItem> list, int selectedIndex) {
        data.clear();
        if (list != null) data.addAll(list);
        selected = selectedIndex;
        notifyDataSetChanged();
    }

    public void setSelected(int index) {
        selected = index;
        notifyDataSetChanged();
    }

    /** 清空源可用性缓存（换分组/重新检测时调用） */
    public void clearAlive() {
        alive.clear();
        notifyDataSetChanged();
    }

    /** 某个源的检测结果回来后就地刷新那一行 */
    public void updateAlive(String url, boolean ok) {
        if (url == null) return;
        Boolean old = alive.get(url);
        alive.put(url, ok);
        if (old != null && old == ok) return;
        for (int i = 0; i < data.size(); i++) {
            LiveChannelItem item = data.get(i);
            if (item != null && url.equals(item.getUrl())) {
                notifyItemChanged(i);
            }
        }
    }

    private boolean canCatchup(LiveChannelItem item) {
        return checker != null && checker.canCatchup(item);
    }

    /** null = 未检测；true = 源可用；false = 源失效 */
    private Boolean aliveOf(LiveChannelItem item) {
        if (item == null || item.getUrl() == null) return null;
        return alive.get(item.getUrl());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_epg_channel, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final LiveChannelItem item = data.get(position);
        holder.tvName.setText(item.getChannelName() == null ? "" : item.getChannelName());
        final boolean can = canCatchup(item);
        final Boolean aliveNow = aliveOf(item);
        final boolean dead = aliveNow != null && !aliveNow;
        final boolean ok = can && !dead;
        boolean on = position == selected;
        holder.tvName.setSelected(on);
        if (on) {
            holder.tvName.setTextColor(com.github.tvbox.osc.util.SkinManager.text1(holder.tvName.getContext()));
        } else {
            holder.tvName.setTextColor(ok ? COLOR_OK : COLOR_DIM);
        }
        holder.tvName.setAlpha(dead ? 0.55f : 1f);
        holder.tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (click != null) click.onChannelClick(holder.getAdapterPosition(), item);
            }
        });
        holder.tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String msg;
                if (aliveNow == null) {
                    msg = can ? "正常 · 支持回放（源还没检测完）" : "正常 · 不支持回放";
                } else if (dead) {
                    msg = "节目源已失效（这个地址连不上）";
                } else if (!can) {
                    msg = "源正常，但该频道不支持回放";
                } else {
                    msg = "源正常，支持回放";
                }
                Toast.makeText(v.getContext(), item.getChannelName() + "：" + msg, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvName;

        Holder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvEpgChannel);
        }
    }
}