package com.quizgate.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * 计时前台服务：常驻保活，按墙钟倒计时，
 * 到期拉起全屏答题锁屏；剩余时间写入通知，断电重启不丢。
 */
public class LockService extends Service {

    public static final String ACTION_LOCK_NOW = "com.quizgate.tv.LOCK_NOW";

    private static final String CHANNEL_ID = "quizgate_timer";
    private static final int NOTI_TIMER = 1001;
    private static final int NOTI_LOCK = 1002;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PrefManager prefs;
    private Runnable ticker;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefManager(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_LOCK_NOW.equals(intent.getAction())) {
            prefs.enterLock();
            showLockScreen();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat(buildTimerNotification(prefs.remainingMillis()));
        startTicking();
        return START_STICKY;
    }

    private void startTicking() {
        if (ticker != null) {
            handler.removeCallbacks(ticker);
        }
        ticker = new Runnable() {
            @Override
            public void run() {
                long remaining = prefs.remainingMillis();
                if (remaining <= 0) {
                    prefs.enterLock();
                    showLockScreen();
                    stopSelf();
                    return;
                }
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.notify(NOTI_TIMER, buildTimerNotification(remaining));
                }
                handler.postDelayed(this, 1000L);
            }
        };
        handler.postDelayed(ticker, 1000L);
    }

    private void showLockScreen() {
        Intent i = new Intent(this, LockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Android 10+ 后台启动 Activity 需要悬浮窗权限，已授权则直接拉起；
        // 未授权时退化为通知点击进入（设置页有权限引导）。
        if (Settings.canDrawOverlays(this)) {
            try {
                startActivity(i);
            } catch (Exception ignored) {
                // 部分 ROM 仍会拦截，走通知兜底
            }
        }
        postLockNotification();
    }

    private void postLockNotification() {
        Intent i = new Intent(this, LockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                pendingFlags(), null);
        Notification.Builder b = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("观看时间到")
                .setContentText("答题解锁后才能继续观看")
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setChannelId(CHANNEL_ID);
        }
        Notification n = b.build();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTI_LOCK, n);
        }
    }

    private Notification buildTimerNotification(long remainingMillis) {
        long seconds = remainingMillis / 1000L;
        String text = String.format("本次观看剩余 %02d:%02d", seconds / 60, seconds % 60);
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i,
                pendingFlags(), null);
        Notification.Builder b = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("答题门禁运行中")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setChannelId(CHANNEL_ID);
        }
        return b.build();
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void startForegroundCompat(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTI_TIMER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTI_TIMER, notification);
            }
        } catch (Exception e) {
            // 部分 ROM 或未授予通知权限时，带类型的 startForeground 会抛异常。
            // 逐级降级，保证计时本身能跑起来，而不是让整个服务挂掉。
            try {
                startForeground(NOTI_TIMER, notification);
            } catch (Exception ignored) {
                NotificationManager nm =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    try {
                        nm.notify(NOTI_TIMER, notification);
                    } catch (Exception ignored2) {
                        // 通知被系统彻底禁用：计时仍然继续，只是没有常驻通知
                    }
                }
            }
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "门禁计时", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (ticker != null) {
            handler.removeCallbacks(ticker);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
