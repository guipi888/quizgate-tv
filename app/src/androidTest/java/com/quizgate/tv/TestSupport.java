package com.quizgate.tv;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * 仪器测试公共工具。
 *
 * 两个关键设计：
 * 1. 仪器测试与 App 跑在同一进程，可以反射读取 LockActivity 的私有 QuizEngine，
 *    从而"知道"每道题的正确答案，答题类断言才是确定性的（不靠 OCR 或穷举点击）；
 * 2. 状态准备/清理集中在 resetPrefs() + ensureNoLockActivity()，
 *    避免锁屏页（singleInstance）跨测试残留导致串扰。
 */
public final class TestSupport {

    /** LockService.NOTI_TIMER，用于识别前台计时通知。 */
    private static final int NOTI_TIMER = 1001;

    private TestSupport() {
    }

    public static Context ctx() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    public static String pkg() {
        return ctx().getPackageName();
    }

    public static PrefManager prefs() {
        return new PrefManager(ctx());
    }

    /** 还原到出厂状态，保证每个测试从同一起点开始。 */
    public static void resetPrefs() {
        PrefManager p = prefs();
        p.setLockActive(false);
        p.setTimerRunning(false);
        p.setLockEndAt(0L);
        p.setStreak(0);
        p.setWatchMinutes(30);
        p.setRequiredStreak(3);
        p.setParentPin("1234");
        p.setBootAuto(true);
        // 只留口算题型：生成式、无需题库文件，且反射可拿到标准答案
        Set<String> only = new HashSet<>();
        only.add(MathCalcType.ID);
        p.setEnabledTypes(only);
    }

    /** 停掉可能被其他测试拉起的计时服务。 */
    public static void stopService() {
        try {
            ctx().stopService(new Intent(ctx(), LockService.class));
        } catch (Exception ignored) {
            // 服务本来就没启动，忽略
        }
    }

    /**
     * 授予运行时权限。仪器进程与 App 同 UID，授权不依赖用户交互。
     * 权限未声明（如 API<33 的 POST_NOTIFICATIONS）时静默失败，不影响主流程。
     */
    public static void grantRuntime(String permission) {
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(pkg(), permission);
        } catch (Exception ignored) {
        }
    }

    public static boolean canDrawOverlays() {
        return Settings.canDrawOverlays(ctx());
    }

    /** LockService 的前台计时通知是否存在（证明服务真的起来了）。 */
    public static boolean hasTimerNotification() {
        NotificationManager nm =
                (NotificationManager) ctx().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return false;
        }
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            if (sbn.getId() == NOTI_TIMER) {
                return true;
            }
            if ("quizgate_timer".equals(sbn.getNotification().getChannelId())) {
                return true;
            }
        }
        return false;
    }

    /** 轮询条件。 */
    public interface Condition {
        boolean test();
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 轮询等待，超时失败并说明在等什么，便于 CI 日志定位。 */
    public static void waitUntil(String what, long timeoutMs, Condition c) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (c.test()) {
                return;
            }
            sleep(80);
        }
        Assert.fail("等待超时：" + what);
    }

    /** 在主线程读取 TextView 文本；视图不存在或已销毁时返回 null。 */
    public static String textOnUi(final Activity a, final int viewId) {
        final String[] box = new String[1];
        if (a != null) {
            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    View v = a.findViewById(viewId);
                    box[0] = v instanceof TextView ? String.valueOf(((TextView) v).getText()) : null;
                }
            });
        }
        return box[0];
    }

    /** 读取视图背景色；非纯色背景（shape drawable）返回 -1。 */
    public static int bgColorOnUi(final Activity a, final int viewId) {
        final int[] box = {-1};
        if (a != null) {
            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    View v = a.findViewById(viewId);
                    Drawable d = v == null ? null : v.getBackground();
                    if (d instanceof ColorDrawable) {
                        box[0] = ((ColorDrawable) d).getColor();
                    }
                }
            });
        }
        return box[0];
    }

    public static int color(int resId) {
        return ctx().getResources().getColor(resId);
    }

    public static int optBtnId(int index) {
        switch (index) {
            case 0:
                return R.id.btn_opt0;
            case 1:
                return R.id.btn_opt1;
            case 2:
                return R.id.btn_opt2;
            case 3:
                return R.id.btn_opt3;
            default:
                throw new IllegalArgumentException("option index: " + index);
        }
    }

    /**
     * 反射读取 LockActivity 的私有 engine。
     * 仪器测试与 App 同进程，测试因此"知道"当前题的正确答案，
     * 点击才是确定性的；不修改任何生产代码。
     */
    public static QuizEngine engineOf(Activity lockActivity) {
        try {
            Field f = LockActivity.class.getDeclaredField("engine");
            f.setAccessible(true);
            return (QuizEngine) f.get(lockActivity);
        } catch (Exception e) {
            throw new RuntimeException("反射读取 QuizEngine 失败", e);
        }
    }

    /**
     * 清掉可能残留的锁屏页。
     * LockActivity 是 singleInstance 且会自愈，直接启动一次即可：
     * 未锁定时 onCreate/onResume 都会自行 finish，残留实例随之销毁。
     */
    public static void ensureNoLockActivity() {
        try {
            ActivityScenario.launch(LockActivity.class).close();
        } catch (Exception ignored) {
        }
    }
}
