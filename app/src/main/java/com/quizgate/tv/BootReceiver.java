package com.quizgate.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启与锁屏恢复：
 * 1. 电视重启后若仍处锁屏态，直接拉锁屏页（堵住"关机重启绕过"）；
 * 2. 若计时未结束，恢复计时服务；
 * 3. RESTORE_LOCK 供锁屏页被异常退出后自愈。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        PrefManager prefs = new PrefManager(context);

        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
        boolean isRestore = "com.quizgate.tv.RESTORE_LOCK".equals(action);

        if (!isBoot && !isRestore) {
            return;
        }

        if (prefs.isLockActive()) {
            Intent lock = new Intent(context, LockActivity.class);
            lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(lock);
            return;
        }
        if (prefs.isTimerRunning() && prefs.remainingMillis() > 0) {
            Intent svc = new Intent(context, LockService.class);
            context.startService(svc);
        }
    }
}
