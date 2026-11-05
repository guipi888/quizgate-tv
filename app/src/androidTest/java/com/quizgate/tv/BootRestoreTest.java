package com.quizgate.tv;

import android.content.Intent;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertTrue;

/**
 * P0 ⑧ 重启/异常恢复（仪器测试层）。
 *
 * BootReceiver 对 BOOT_COMPLETED / QUICKBOOT_POWERON / RESTORE_LOCK
 * 走同一段恢复逻辑，这里发 RESTORE_LOCK 广播覆盖两条分支：
 * 锁屏态 → 拉回锁屏页；计时中 → 恢复前台服务。
 * 真实 BOOT_COMPLETED 的端到端验证由 CI 工作流用 adb reboot 完成。
 */
public class BootRestoreTest {

    @Before
    public void setUp() {
        TestSupport.grantRuntime(android.Manifest.permission.POST_NOTIFICATIONS);
        TestSupport.resetPrefs();
        TestSupport.stopService();
        TestSupport.ensureNoLockActivity();
    }

    @After
    public void tearDown() {
        TestSupport.resetPrefs();
        TestSupport.stopService();
        TestSupport.ensureNoLockActivity();
    }

    private void sendRestoreBroadcast() {
        Intent i = new Intent("com.quizgate.tv.RESTORE_LOCK");
        i.setPackage(TestSupport.pkg());
        TestSupport.ctx().sendBroadcast(i);
    }

    /** 锁屏态收到恢复广播 → 重新拉出锁屏页。 */
    @Test
    public void restoreBroadcastReopensLockScreen() {
        // Android 10+ 后台启动 Activity 需要悬浮窗权限（CI 通过 appops 授予）
        Assume.assumeTrue("后台拉起 Activity 需要悬浮窗权限",
                Build.VERSION.SDK_INT < 29 || TestSupport.canDrawOverlays());

        TestSupport.prefs().setLockActive(true);
        sendRestoreBroadcast();

        UiDevice device = UiDevice.getInstance(
                InstrumentationRegistry.getInstrumentation());
        boolean shown = device.wait(
                Until.hasObject(By.res(TestSupport.pkg(), "tv_lock_title")), 20000);
        assertTrue("恢复广播应拉回锁屏页", shown);

        onView(withId(R.id.tv_lock_title)).check(matches(isDisplayed()));
    }

    /** 计时中收到恢复广播 → 前台计时服务重新拉起。 */
    @Test
    public void restoreBroadcastRestartsTimerService() {
        PrefManager p = TestSupport.prefs();
        p.setWatchMinutes(30);
        p.startWatchTimer();
        assertTrue("前置条件：计时中且未锁屏", p.isTimerRunning() && !p.isLockActive());

        sendRestoreBroadcast();

        TestSupport.waitUntil("计时服务恢复", 20000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                return TestSupport.hasTimerNotification();
            }
        });
    }
}
