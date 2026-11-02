package com.quizgate.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 全部持久化状态。锁屏状态与截止时间用墙钟时间戳存储，
 * 保证断电重启后仍能恢复（对应 SOUL 中"重启不丢"的硬需求）。
 */
public class PrefManager {

    private static final String PREF_NAME = "quizgate_prefs";

    private static final String KEY_WATCH_MINUTES = "watch_minutes";
    private static final String KEY_REQUIRED_STREAK = "required_streak";
    private static final String KEY_ENABLED_TYPES = "enabled_types";
    private static final String KEY_PARENT_PIN = "parent_pin";
    private static final String KEY_BOOT_AUTO = "boot_auto";
    private static final String KEY_TIMER_RUNNING = "timer_running";
    private static final String KEY_LOCK_ACTIVE = "lock_active";
    private static final String KEY_LOCK_END_AT = "lock_end_at";
    private static final String KEY_STREAK = "streak";

    public static final int DEFAULT_WATCH_MINUTES = 30;
    public static final int DEFAULT_REQUIRED_STREAK = 3;
    public static final String DEFAULT_PARENT_PIN = "1234";

    private final SharedPreferences prefs;

    public PrefManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getWatchMinutes() {
        return prefs.getInt(KEY_WATCH_MINUTES, DEFAULT_WATCH_MINUTES);
    }

    public void setWatchMinutes(int minutes) {
        prefs.edit().putInt(KEY_WATCH_MINUTES, minutes).apply();
    }

    public int getRequiredStreak() {
        return prefs.getInt(KEY_REQUIRED_STREAK, DEFAULT_REQUIRED_STREAK);
    }

    public void setRequiredStreak(int streak) {
        prefs.edit().putInt(KEY_REQUIRED_STREAK, streak).apply();
    }

    public Set<String> getEnabledTypes() {
        Set<String> def = new HashSet<>();
        def.add(PinyinChoiceType.ID);
        def.add(MathCalcType.ID);
        Set<String> stored = prefs.getStringSet(KEY_ENABLED_TYPES, null);
        return stored == null ? def : new HashSet<>(stored);
    }

    public void setEnabledTypes(Set<String> types) {
        prefs.edit().putStringSet(KEY_ENABLED_TYPES, new HashSet<>(types)).apply();
    }

    public String getParentPin() {
        return prefs.getString(KEY_PARENT_PIN, DEFAULT_PARENT_PIN);
    }

    public void setParentPin(String pin) {
        prefs.edit().putString(KEY_PARENT_PIN, pin).apply();
    }

    public boolean isBootAuto() {
        return prefs.getBoolean(KEY_BOOT_AUTO, true);
    }

    public void setBootAuto(boolean auto) {
        prefs.edit().putBoolean(KEY_BOOT_AUTO, auto).apply();
    }

    public boolean isTimerRunning() {
        return prefs.getBoolean(KEY_TIMER_RUNNING, false);
    }

    public void setTimerRunning(boolean running) {
        prefs.edit().putBoolean(KEY_TIMER_RUNNING, running).apply();
    }

    public boolean isLockActive() {
        return prefs.getBoolean(KEY_LOCK_ACTIVE, false);
    }

    public void setLockActive(boolean active) {
        prefs.edit().putBoolean(KEY_LOCK_ACTIVE, active).apply();
    }

    /** 锁屏截止的墙钟时间戳（System.currentTimeMillis），0 表示无锁定。 */
    public long getLockEndAt() {
        return prefs.getLong(KEY_LOCK_END_AT, 0L);
    }

    public void setLockEndAt(long millis) {
        prefs.edit().putLong(KEY_LOCK_END_AT, millis).apply();
    }

    public int getStreak() {
        return prefs.getInt(KEY_STREAK, 0);
    }

    public void setStreak(int streak) {
        prefs.edit().putInt(KEY_STREAK, streak).apply();
    }

    /** 剩余毫秒数，已过期返回 0。 */
    public long remainingMillis() {
        long end = getLockEndAt();
        if (end <= 0) {
            return 0L;
        }
        return Math.max(0L, end - System.currentTimeMillis());
    }

    /** 启动一轮新的观看计时。 */
    public void startWatchTimer() {
        setLockEndAt(System.currentTimeMillis() + getWatchMinutes() * 60_000L);
        setTimerRunning(true);
        setLockActive(false);
        setStreak(0);
    }

    /** 计时到期，进入锁屏。 */
    public void enterLock() {
        setLockActive(true);
        setLockEndAt(0L);
        setStreak(0);
    }

    /** 答题通关，解除锁屏并重新开始一轮计时。 */
    public void unlockAndRestart() {
        setLockActive(false);
        setStreak(0);
        startWatchTimer();
    }

    /** 家长 PIN 解锁：停掉整个门禁（当日不再计时）。 */
    public void parentUnlock() {
        setLockActive(false);
        setTimerRunning(false);
        setLockEndAt(0L);
        setStreak(0);
    }
}
