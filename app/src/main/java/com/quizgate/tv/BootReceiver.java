package com.quizgate.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

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
            try {
                context.startActivity(lock);
            } catch (Exception ignored) {
                // Android 10+ 后台启动 Activity 受限，锁屏会在用户下次亮屏/打开 App 时恢复
            }
            return;
        }
        if (prefs.isTimerRunning() && prefs.remainingMillis() > 0) {
            Intent svc = new Intent(context, LockService.class);
            try {
                // Android 8+ 禁止后台 startService，必须走 startForegroundService，
                // 否则开机后计时无法恢复（表现为「重启后门禁失效」）。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
            } catch (Exception ignored) {
                // 个别 ROM 连前台服务也拦截；用户再次打开 App 时会重新启动计时
            }
        }
    }
}
