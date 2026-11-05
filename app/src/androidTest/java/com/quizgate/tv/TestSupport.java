package com.quizgate.tv;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;
import org.junit.Assert;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
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
        Assert.fail("等待超时：" + what + dumpDiagnostics());
    }

    /**
     * 轮询超时时的失败现场取证。
     *
     * 背景（2026-11-05 十跑，API 34 首次跑到测试段）：
     * startTimerStartsForegroundService 等「前台计时通知」8 秒超时失败，
     * 但同样等通知的 BootRestoreTest（20 秒窗口、广播路径）通过——
     * 无法从外部区分是「8 秒窗口在 API 34 软渲染负载下太紧」还是
     * 「UI 路径 startForegroundService 抛异常被 catch 转 Toast」。
     * 转储活跃通知清单 + LockService 运行态 + 计时状态，一次跑分辨两说。
     */
    private static String dumpDiagnostics() {
        StringBuilder sb = new StringBuilder("\n[诊断]");
        try {
            sb.append(" SDK=").append(android.os.Build.VERSION.SDK_INT);
            PrefManager p = prefs();
            sb.append(" timerRunning=").append(p.isTimerRunning())
                    .append(" lockActive=").append(p.isLockActive())
                    .append(" remaining=").append(p.remainingMillis());
            android.app.ActivityManager am = (android.app.ActivityManager) ctx()
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                boolean svcRunning = false;
                for (android.app.ActivityManager.RunningServiceInfo info
                        : am.getRunningServices(50)) {
                    if (LockService.class.getName().equals(info.service.getClassName())) {
                        svcRunning = true;
                        break;
                    }
                }
                sb.append(" LockServiceRunning=").append(svcRunning);
            }
            NotificationManager nm = (NotificationManager) ctx()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                sb.append(" areNotificationsEnabled=").append(nm.areNotificationsEnabled());
                StatusBarNotification[] actives = nm.getActiveNotifications();
                sb.append(" activeNotifications=").append(actives.length);
                for (StatusBarNotification sbn : actives) {
                    sb.append("\n  [通知] id=").append(sbn.getId())
                            .append(" channel=").append(sbn.getNotification().getChannelId())
                            .append(" ongoing=").append(
                                    (sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0);
                }
            }
        } catch (Exception e) {
            sb.append(" 采集失败: ").append(e);
        }
        return sb.toString();
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
     *
     * 必须捕 Throwable：视图 <90% 可见时 Espresso 的
     * check(matches(isDisplayingAtLeast(90))) 抛
     * AssertionErrorHandler$AssertionFailedWithCauseError，它继承自
     * org.junit.AssertionFailedError → java.lang.AssertionError →
     * **java.lang.Error，不是 Exception**。catch (Exception) 捕不住它，
     * 复检会在第一次调用时就炸穿整个 clickAfterScroll（2026-11-05 CI
     * 四跑实证：滚动分支后的一切代码从未执行）。
     */
    public static boolean isDisplayed(final int viewId) {
        try {
            onView(withId(viewId)).check(matches(isDisplayingAtLeast(90)));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 滚动到目标控件并点击。
     *
     * 为什么不用 Espresso 的 scrollTo()：ScrollToAction 调
     * View.requestRectangleOnScreen(rect, false)，immediate=false 时
     * ScrollView.requestChildRectangleOnScreen 走 smoothScrollBy
     * 动画——perform 返回时动画还没跑，同帧的可见性复检必然失败
     * （CI 首跑实测）。
     * 为什么不用 requestChildFocus()：ScrollView.requestChildFocus 自
     * Android 9 起被 getRevealOnFocusHint() 把门，布局不设
     * android:revealOnFocus 时它什么都不做（AOSP 源码 + CI 实测：
     * 调用后按钮 y 坐标仍是 1709，scrollY 未变）。
     * 为什么不用 requestRectangleOnScreen()：ViewGroup 的默认实现
     * requestChildRectangleOnScreen 直接 return false、**不向父链冒泡**，
     * view 的直接父是普通 LinearLayout 时请求断在中间层，ScrollView
     * 从未被问到（AOSP ViewGroup 源码 + CI 四跑实证）。
     * 主方案：直接定位最近 ScrollView 祖先，按屏幕坐标手算 scrollTo
     * （maxScroll 用 ScrollView.onLayout 同款公式 clamp）。位置用
     * getLocationOnScreen 实测，不依赖父子层级的 getTop() 累加。
     * 滚动后仍不可见时 perform 内抛出带全部中间值的 RuntimeException，
     * CI test report 堆栈直接可读，不再盲猜根因。
     */
    public static void clickAfterScroll(final int viewId) {
        // 已可见时（一屏放得下）直接点，无需滚动
        try {
            onView(withId(viewId)).perform(click());
            return;
        } catch (Exception notDisplayed) {
            // 落入滚动分支（click 的约束失败是 PerformException，Exception 子类）
        }

        scrollIntoView(viewId);

        // scrollTo 是同步的，留一小段轮询复检余量应对延迟布局
        for (int i = 0; i < 10 && !isDisplayed(viewId); i++) {
            sleep(100);
        }

        // 仍不可见时让 Espresso 给出明确的失败信息
        onView(withId(viewId)).perform(click());
    }

    /**
     * 把目标视图滚进最近的 ScrollView 可见区。
     * ViewAction 保证 perform 在主线程执行。
     *
     * 手算坐标：view.getLocationOnScreen 拿实时屏幕位置，减 ScrollView
     * 自身位置和 paddingTop 得到应设的 scrollY；maxScroll 与
     * ScrollView.onLayout 的 scrollRange 公式一致
     * （childHeight - (height - paddingTop - paddingBottom)），防止滚过头
     * 把目标甩出屏幕顶部。滚动后复检目标仍不可见则抛诊断异常。
     */
    private static void scrollIntoView(final int viewId) {
        onView(withId(viewId)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(View.class);
            }

            @Override
            public String getDescription() {
                return "scroll into view via direct scrollTo";
            }

            @Override
            public void perform(UiController uiController, View view) {
                ViewParent p = view.getParent();
                while (p != null && !(p instanceof ScrollView)) {
                    p = p.getParent();
                }
                if (!(p instanceof ScrollView)) {
                    throw new RuntimeException(
                            "DIAG-scroll: view 上方找不到 ScrollView 祖先");
                }
                ScrollView sv = (ScrollView) p;
                int[] vLoc = new int[2];
                int[] svLoc = new int[2];
                view.getLocationOnScreen(vLoc);
                sv.getLocationOnScreen(svLoc);
                int padTop = sv.getPaddingTop();
                View content = sv.getChildAt(0);
                int contentH = content == null ? -1 : content.getHeight();
                int maxScroll = content == null ? 0
                        : Math.max(0, contentH + padTop + sv.getPaddingBottom()
                                - sv.getHeight());
                int target = vLoc[1] - svLoc[1] - padTop;
                if (target < 0) {
                    target = 0;
                }
                if (target > maxScroll) {
                    target = maxScroll;
                }
                sv.scrollTo(0, target);
                int after = sv.getScrollY();
                int viewTopAfter = vLoc[1] - after;
                int svBottom = svLoc[1] + sv.getHeight();
                if (viewTopAfter + view.getHeight() / 2 > svBottom
                        || viewTopAfter + view.getHeight() < svLoc[1] + padTop) {
                    throw new RuntimeException("DIAG-scroll: 滚动后目标仍不可见"
                            + " vLoc.y=" + vLoc[1] + " svLoc.y=" + svLoc[1]
                            + " padTop=" + padTop + " svH=" + sv.getHeight()
                            + " contentH=" + contentH + " maxScroll=" + maxScroll
                            + " target=" + target + " scrollYAfter=" + after
                            + " viewH=" + view.getHeight());
                }
            }
        });
    }
}
