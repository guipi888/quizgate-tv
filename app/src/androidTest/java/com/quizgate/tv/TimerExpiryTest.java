package com.quizgate.tv;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

/**
 * P0 ② 完整链路：真实倒计时到期 → 自动弹出答题锁屏。
 *
 * 锁屏页由 LockService 在后台拉起，Android 10+ 需要悬浮窗权限；
 * CI 工作流通过 appops 授予，本地跑请在开发者选项里先开悬浮窗。
 */
public class TimerExpiryTest {

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        TestSupport.grantRuntime(android.Manifest.permission.POST_NOTIFICATIONS);
        TestSupport.resetPrefs();
        TestSupport.stopService();
        TestSupport.ensureNoLockActivity();
        TestSupport.prefs().setWatchMinutes(1); // 1 分钟，最短真实倒计时
    }

    @After
    public void tearDown() {
        if (scenario != null) {
            scenario.close();
        }
        TestSupport.resetPrefs();
        TestSupport.stopService();
        TestSupport.ensureNoLockActivity();
    }

    @Test
    public void timerExpiryBringsUpLockScreen() {
        Assume.assumeTrue("后台拉起锁屏页需要悬浮窗权限（CI 通过 appops 授予）",
                TestSupport.canDrawOverlays());

        scenario = ActivityScenario.launch(MainActivity.class);
        onView(withId(R.id.btn_start_timer)).perform(scrollTo(), click());

        // 1 分钟倒计时 + 服务每秒轮询的余量
        UiDevice device = UiDevice.getInstance(
                InstrumentationRegistry.getInstrumentation());
        boolean shown = device.wait(
                Until.hasObject(By.res(TestSupport.pkg(), "tv_lock_prompt")), 100000);
        assertTrue("倒计时到期后应自动弹出答题锁屏", shown);

        onView(withId(R.id.tv_lock_progress))
                .check(matches(withText(containsString("连对 0 / 3"))));
    }
}
