package com.github.tvbox.osc.mobile.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.github.tvbox.osc.mobile.ui.activity.MobileLiveActivity;

/**
 * 开播提醒的落地端：收到闹钟后发一条通知，点通知直接进直播页。
 */
public class LiveReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "live_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        String channel = intent == null ? "" : intent.getStringExtra("channel");
        String title = intent == null ? "" : intent.getStringExtra("title");
        if (channel == null) channel = "";
        if (title == null) title = "";
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "直播开播提醒",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(context, MobileLiveActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, channel.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("开播提醒 · " + channel)
                .setContentText(title)
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify((int) (System.currentTimeMillis() / 1000), builder.build());
    }
}
