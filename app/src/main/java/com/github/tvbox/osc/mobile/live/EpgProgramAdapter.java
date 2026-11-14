package com.github.tvbox.osc.mobile.live;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 节目单页右列：节目列表。
 * 每行右侧一个状态标签，用颜色区分能不能回放：
 *   正在播 = 蓝色实底「直播中」
 *   已播可回看 = 蓝色淡底「回看」
 *   未开始 / 频道不支持回看 = 灰色淡底「不可回看」
 */
public class EpgProgramAdapter extends RecyclerView.Adapter<EpgProgramAdapter.Holder> {

    public interface OnProgramClick {
        void onProgramClick(MobileEpgXml.Program program, boolean live, boolean canCatchup);
    }

    private final List<MobileEpgXml.Program> data = new ArrayList<>();
    private boolean channelCanCatchup = true;
    private OnProgramClick click;
    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.CHINA);

    public void setCanCatchup(boolean can) {
        channelCanCatchup = can;
    }

    public void setOnProgramClick(OnProgramClick click) {
        this.click = click;
    }

    public void setData(List<MobileEpgXml.Program> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_epg_program, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final MobileEpgXml.Program p = data.get(position);
        long now = System.currentTimeMillis();
        final boolean live = p.start.getTime() <= now && p.stop.getTime() > now;
        final boolean future = p.start.getTime() > now;
        final boolean canCatchup = channelCanCatchup && !future;
        holder.tvTime.setText(HM.format(p.start));
        holder.tvTitle.setText(p.title == null ? "" : p.title);
        holder.tvRange.setText(HM.format(p.start) + "-" + HM.format(p.stop));

        if (live) {
            holder.itemView.setBackgroundColor(0x141A6DFF);
            holder.tvBadge.setText("直播中");
            badge(holder.tvBadge, 0xFF1A6DFF, 0xFFFFFFFF);
        } else if (canCatchup) {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.tvBadge.setText("回看");
            badge(holder.tvBadge, 0x1A1A6DFF, 0xFF1A6DFF);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.tvBadge.setText(future ? "未开始" : "不可回看");
            badge(holder.tvBadge, 0x14000000, 0xFF8A8F99);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (click != null) click.onProgramClick(p, live, canCatchup);
            }
        });
    }

    private void badge(TextView tv, int bg, int text) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(tv, 9));
        tv.setBackground(d);
        tv.setTextColor(text);
    }

    private int dp(View v, int value) {
        return (int) (v.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvTime;
        TextView tvTitle;
        TextView tvRange;
        TextView tvBadge;

        Holder(View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvEpgTime);
            tvTitle = itemView.findViewById(R.id.tvEpgProgram);
            tvRange = itemView.findViewById(R.id.tvEpgRange);
            tvBadge = itemView.findViewById(R.id.tvEpgBadge);
        }
    }
}
