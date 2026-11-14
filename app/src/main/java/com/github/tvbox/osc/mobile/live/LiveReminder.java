package com.github.tvbox.osc.mobile.live;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * 节目开播提醒：按节目开始时间设一个精确闹钟，到点由 LiveReminderReceiver 发通知。
 */
public class LiveReminder {

    public static void schedule(Context ctx, String channel, String title, long triggerAt) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, LiveReminderReceiver.class);
        intent.putExtra("channel", channel);
        intent.putExtra("title", title);
        int requestCode = Math.abs((int) (triggerAt % 100000) + channel.hashCode() % 1000);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } catch (Throwable th) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }
}
