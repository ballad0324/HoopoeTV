package com.github.tvbox.osc.mobile.live;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 「节目单」底部弹层的数据行：时间 | 节目名 | 回看按钮。
 *
 * 三种行状态：
 * - 正在播：整行淡蓝底，按钮蓝实底「直播中」，点了切回直播
 * - 已播且当前频道支持回看：按钮淡蓝底「回看」
 * - 已播但频道不支持回看：按钮灰底「不可回看」，行内文案置灰
 * - 未开始：按钮描边「提醒」
 */
public class EpgSheetAdapter extends RecyclerView.Adapter<EpgSheetAdapter.Holder> {

    public static final int STATE_LIVE = 0;
    public static final int STATE_CATCHUP = 1;
    public static final int STATE_NONE = 2;
    public static final int STATE_FUTURE = 3;

    public interface OnActionClick {
        void onAction(MobileEpgXml.Program program, int state);
    }

    private final Context context;
    private final List<MobileEpgXml.Program> all = new ArrayList<>();
    private final List<MobileEpgXml.Program> shown = new ArrayList<>();
    private boolean canCatchup;
    private int dayOffset; // 0 = 今天，1 = 昨天，2 = 前天
    private OnActionClick listener;
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.CHINA);

    public EpgSheetAdapter(Context context) {
        this.context = context;
    }

    public void setOnActionClick(OnActionClick l) {
        this.listener = l;
    }

    public void setData(List<MobileEpgXml.Program> programs, boolean canCatchup, int dayOffset) {
        all.clear();
        if (programs != null) all.addAll(programs);
        this.canCatchup = canCatchup;
        this.dayOffset = dayOffset;
        rebuild();
    }

    public void setDayOffset(int dayOffset) {
        this.dayOffset = dayOffset;
        rebuild();
    }

    private void rebuild() {
        shown.clear();
        long now = System.currentTimeMillis();
        long dayStart = startOfDay(dayOffset);
        long dayEnd = dayStart + 24L * 60 * 60 * 1000;
        for (MobileEpgXml.Program p : all) {
            long start = p.start.getTime();
            // 跨零点的节目按开始时间归到当天
            if (start >= dayStart && start < dayEnd) shown.add(p);
        }
        notifyDataSetChanged();
        // 定位到「正在播」或当天最接近当前时刻的一行
        int target = 0;
        for (int i = 0; i < shown.size(); i++) {
            MobileEpgXml.Program p = shown.get(i);
            if (p.start.getTime() <= now && p.stop.getTime() > now) {
                target = i;
                break;
            }
            if (p.start.getTime() > now) {
                target = Math.max(0, i - 1);
                break;
            }
        }
        scrollTarget = target;
    }

    private int scrollTarget;

    public int getScrollTarget() {
        return scrollTarget;
    }

    private long startOfDay(int offset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, -offset);
        return c.getTimeInMillis();
    }

    private int stateOf(MobileEpgXml.Program p) {
        long now = System.currentTimeMillis();
        if (p.start.getTime() <= now && p.stop.getTime() > now) return STATE_LIVE;
        if (p.start.getTime() > now) return STATE_FUTURE;
        return canCatchup ? STATE_CATCHUP : STATE_NONE;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_epg_sheet_program, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final MobileEpgXml.Program p = shown.get(position);
        final int state = stateOf(p);
        int primary = context.getResources().getColor(R.color.shell_primary);
        int textDim = context.getResources().getColor(R.color.shell_text_dim);

        holder.tvTime.setText(hm.format(p.start));
        holder.tvName.setText(TextUtils.isEmpty(p.title) ? "（无节目名）" : p.title);
        holder.tvRange.setText(hm.format(p.start) + " - " + hm.format(p.stop));

        // 行底色：正在播最重，已播可回看次之，其余透明
        if (state == STATE_LIVE) {
            holder.row.setBackgroundColor(tint(primary, 0x14));
        } else if (state == STATE_CATCHUP) {
            holder.row.setBackgroundColor(tint(primary, 0x08));
        } else {
            holder.row.setBackgroundColor(Color.TRANSPARENT);
        }

        // 文字颜色：正在播用主色强调，不可回看置灰
        int nameColor = state == STATE_LIVE ? primary
                : (state == STATE_NONE ? textDim : context.getResources().getColor(R.color.shell_text));
        holder.tvName.setTextColor(nameColor);
        holder.tvTime.setTextColor(state == STATE_LIVE ? primary : textDim);

        switch (state) {
            case STATE_LIVE:
                holder.tvAction.setText("直播中");
                holder.tvAction.setTextColor(Color.WHITE);
                holder.tvAction.setBackground(roundRect(primary, 0, 9f, 0));
                holder.tvAction.setAlpha(1f);
                break;
            case STATE_CATCHUP:
                holder.tvAction.setText("回看");
                holder.tvAction.setTextColor(primary);
                holder.tvAction.setBackground(roundRect(tint(primary, 0x1A), 0, 9f, primary));
                holder.tvAction.setAlpha(1f);
                break;
            case STATE_FUTURE:
                holder.tvAction.setText("提醒");
                holder.tvAction.setTextColor(context.getResources().getColor(R.color.shell_text));
                holder.tvAction.setBackground(roundRect(Color.TRANSPARENT, 0, 9f, tint(textDim, 0x66)));
                holder.tvAction.setAlpha(1f);
                break;
            default:
                holder.tvAction.setText("不可回看");
                holder.tvAction.setTextColor(textDim);
                holder.tvAction.setBackground(roundRect(tint(textDim, 0x14), 0, 9f, 0));
                holder.tvAction.setAlpha(0.85f);
                break;
        }
        holder.tvAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state == STATE_NONE) return;
                if (listener != null) listener.onAction(p, state);
            }
        });
        holder.row.setOnClickListener(null);
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    private int tint(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private GradientDrawable roundRect(int fill, int alphaFill, float radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke((int) dp(1f), stroke);
        return d;
    }

    private float dp(float value) {
        return context.getResources().getDisplayMetrics().density * value;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView tvTime;
        final TextView tvName;
        final TextView tvRange;
        final TextView tvAction;

        Holder(View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.llSheetRow);
            tvTime = itemView.findViewById(R.id.tvSheetTime);
            tvName = itemView.findViewById(R.id.tvSheetName);
            tvRange = itemView.findViewById(R.id.tvSheetRange);
            tvAction = itemView.findViewById(R.id.tvSheetAction);
        }
    }
}
