package com.github.tvbox.osc.mobile.live;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 回看节目单列表：按日期分组（今天 / 昨天 / 更早），正在播的节目高亮。
 * 点击正在播的节目 = 切回直播；点过去/未来的节目交给外部决定（回看 / 开播提醒）。
 */
public class ProgramListAdapter extends BaseAdapter {

    public static class Row {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_PROGRAM = 1;
        public int type;
        public String header;
        public MobileEpgXml.Program program;
        public boolean live;      // 正在播
        public boolean future;    // 还没开始
    }

    public interface OnProgramClick {
        void onProgramClick(Row row);
    }

    private final Context ctx;
    private final List<Row> rows = new ArrayList<>();
    private OnProgramClick callback;
    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private static final SimpleDateFormat DAY = new SimpleDateFormat("MM-dd", Locale.CHINA);

    public ProgramListAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setCallback(OnProgramClick callback) {
        this.callback = callback;
    }

    public void setPrograms(List<MobileEpgXml.Program> programs) {
        rows.clear();
        if (programs != null) {
            long now = System.currentTimeMillis();
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            long todayStart = today.getTimeInMillis();
            String lastLabel = null;
            for (MobileEpgXml.Program p : programs) {
                String label;
                long st = p.start.getTime();
                if (st >= todayStart) label = "今天";
                else if (st >= todayStart - 24L * 60 * 60 * 1000) label = "昨天";
                else label = DAY.format(p.start);
                if (lastLabel == null || !lastLabel.equals(label)) {
                    Row head = new Row();
                    head.type = Row.TYPE_HEADER;
                    head.header = label;
                    rows.add(head);
                    lastLabel = label;
                }
                Row r = new Row();
                r.type = Row.TYPE_PROGRAM;
                r.program = p;
                r.live = st <= now && p.stop.getTime() > now;
                r.future = st > now;
                rows.add(r);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean isEnabled(int position) {
        return rows.get(position).type == Row.TYPE_PROGRAM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);
        TextView tv;
        if (convertView instanceof TextView) {
            tv = (TextView) convertView;
        } else {
            tv = new TextView(ctx);
            int padH = dp(18);
            tv.setPadding(padH, dp(8), padH, dp(8));
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER_VERTICAL);
        }
        tv.setTextColor(Color.parseColor("#222222"));
        if (row.type == Row.TYPE_HEADER) {
            tv.setText(row.header);
            tv.setTextSize(12);
            tv.setTextColor(Color.parseColor("#8A8A8A"));
            tv.setPadding(dp(18), dp(12), dp(18), dp(4));
        } else {
            MobileEpgXml.Program p = row.program;
            tv.setTextSize(14);
            tv.setPadding(dp(18), dp(9), dp(18), dp(9));
            String time = HM.format(p.start) + " - " + HM.format(p.stop);
            if (row.live) {
                tv.setText(time + "  " + p.title + "  ▶ 正在直播");
                tv.setTextColor(Color.parseColor("#1A6DFF"));
            } else if (row.future) {
                tv.setText(time + "  " + p.title);
                tv.setTextColor(Color.parseColor("#9A6A00"));
            } else {
                tv.setText(time + "  " + p.title);
                tv.setTextColor(Color.parseColor("#222222"));
            }
        }
        return tv;
    }

    private int dp(int v) {
        return (int) (ctx.getResources().getDisplayMetrics().density * v + 0.5f);
    }
}