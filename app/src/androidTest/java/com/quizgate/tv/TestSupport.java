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
import androidx.test.uiautomator.UiDevice;

import org.junit.Assert;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * 仪器测试公共工具。
 *
 * 三个关键设计：
 * 1. 仪器测试与 App 跑在同一进程，可以反射读取 LockActivity 的私有 QuizEngine，
 *    从而"知道"每道题的正确答案，答题类断言才是确定性的（不靠 OCR 或穷举点击）；
 * 2. 状态准备/清理集中在 resetPrefs() + ensureNoLockActivity()，
 *    避免锁屏页（singleInstance）跨测试残留导致串扰；
 * 3. 所有跨线程 UI 读取（textOnUi/bgColorOnUi）必须等主线程真正执行完
 *    Runnable 再取值——runOnUiThread 是异步 post，post 完立即读会拿到
 *    初始值（null/-1），这是 CI 首跑暴露的竞态 bug。
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

    /**
     * 在主线程读取 TextView 文本；视图不存在、已销毁或文本为空时返回 null。
     *
     * 实现要点：runOnUiThread 只是把 Runnable post 到主线程队列，
     * post 完立刻读 box 会拿到 null（测试线程与主线程竞态）。
     * 必须用 CountDownLatch 等主线程真正执行完再读；
     * 文本由异步逻辑（onResume 的 refreshBankInfo 等）设置时还需轮询重试。
     */
    public static String textOnUi(final Activity a, final int viewId) {
        return textOnUi(a, viewId, 2000);
    }

    public static String textOnUi(final Activity a, final int viewId, long timeoutMs) {
        if (a == null) {
            return null;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            final String[] box = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        View v = a.findViewById(viewId);
                        box[0] = v instanceof TextView
                                ? String.valueOf(((TextView) v).getText()) : null;
                    } finally {
                        latch.countDown();
                    }
                }
            });
            try {
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            if (box[0] != null && !box[0].isEmpty()) {
                return box[0];
            }
            if (System.currentTimeMillis() >= deadline) {
                return box[0];
            }
            sleep(50);
        }
    }

    /** 读取视图背景色；非纯色背景（shape drawable）或视图不存在返回 -1。 */
    public static int bgColorOnUi(final Activity a, final int viewId) {
        if (a == null) {
            return -1;
        }
        final int[] box = {-1};
        final CountDownLatch latch = new CountDownLatch(1);
        a.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    View v = a.findViewById(viewId);
                    Drawable d = v == null ? null : v.getBackground();
                    if (d instanceof ColorDrawable) {
                        box[0] = ((ColorDrawable) d).getColor();
                    }
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
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

    /**
     * 目标控件是否已在屏幕上充分显示（Espresso click 采用的 90% 标准）。
     */
    public static boolean isDisplayed(final int viewId) {
        try {
            onView(withId(viewId)).check(matches(isDisplayingAtLeast(90)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 滚动到目标控件并点击。
     *
     * 为什么不用 Espresso 的 scrollTo()：ScrollToAction 依赖
     * View.requestRectangleOnScreen() 让祖先容器滚动，在 Android TV 模拟器上
     * 滚动后依然抛 "Scrolling to view was attempted, but the view is not
     * displayed"（2026-11-05 CI API 30/34 实测：requestRectangleOnScreen
     * 返回 true，但 isDisplayingAtLeast(90) 复检仍不满足）。
     * 这里改用 UiDevice 注入真实滑动手势——与用户在电视上的操作一致，
     * 也顺带验证 ScrollView 在产品环境里真的滚得动
     * （P0 ②「开始计时」在长设置页中的可达性防线）。
     */
    public static void clickAfterScroll(final int viewId) {
        // 已可见时（大屏一屏放得下）直接点，无需滚动
        try {
            onView(withId(viewId)).perform(click());
            return;
        } catch (Exception notDisplayed) {
            // 落入滚动分支
        }

        UiDevice device = UiDevice.getInstance(
                InstrumentationRegistry.getInstrumentation());
        int width = device.getDisplayWidth();
        int height = device.getDisplayHeight();
        int x = width / 2;
        int from = height * 3 / 4;
        int to = height / 4;
        for (int i = 0; i < 4 && !isDisplayed(viewId); i++) {
            device.swipe(x, from, x, to, 30);
            sleep(300);
        }
        // 仍不可见时让 Espresso 给出明确的失败信息
        onView(withId(viewId)).perform(click());
    }
}
