package com.quizgate.tv;

import androidx.test.core.app.ActivityScenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 家长设置页测试（P0 ①②⑦ + 自定义时长）。
 *
 * 注意：activity_main.xml 是 ScrollView，「开始计时」等按钮在 1080p 一屏外。
 * Espresso 的 scrollTo() 在 TV 模拟器上滚动后仍报 not displayed（CI 首跑实测：
 * immediate=false 走 smoothScrollBy 动画，同帧复检看不到效果），requestChildFocus
 * 被 getRevealOnFocusHint() 把门、requestRectangleOnScreen 断在不冒泡的中间层
 * （均 AOSP 源码 + CI 实证），因此统一走 TestSupport.clickAfterScroll()：
 * 直接定位 ScrollView 手算 scrollTo + 可见性检查——这正是线上反馈「点开始
 * 计时无效应」的根因场景，测试对此形成回归防线。
 */
public class MainActivityTest {

    private ActivityScenario<MainActivity> scenario;
    private MainActivity activity;

    @Before
    public void setUp() {
        TestSupport.grantRuntime(android.Manifest.permission.POST_NOTIFICATIONS);
        TestSupport.resetPrefs();
        TestSupport.stopService();
        TestSupport.ensureNoLockActivity();
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

    private void launchMain() {
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
            @Override
            public void perform(MainActivity a) {
                activity = a;
            }
        });
    }

    /** P0 ①②：初始状态为未启动，内置题库被加载。 */
    @Test
    public void initialStatusAndBankInfo() {
        launchMain();
        onView(withId(R.id.tv_status)).check(matches(withText("状态：未启动")));
        onView(withId(R.id.btn_start_timer)).check(matches(withText("开始计时")));
        String bank = TestSupport.textOnUi(activity, R.id.tv_bank_info);
        assertTrue("题库信息应显示已加载，实际：" + bank,
                bank != null && bank.startsWith("已加载"));
    }

    /** P0 ②：开始计时 → 状态变为计时中、前台服务与常驻通知出现。 */
    @Test
    public void startTimerStartsForegroundService() {
        launchMain();
        TestSupport.clickAfterScroll(R.id.btn_start_timer);

        TestSupport.waitUntil("状态变为计时中", 5000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                String t = TestSupport.textOnUi(activity, R.id.tv_status);
                return t != null && t.contains("计时中");
            }
        });

        PrefManager p = TestSupport.prefs();
        assertTrue("计时标记应置位", p.isTimerRunning());
        assertTrue("应有剩余时间", p.remainingMillis() > 0);

        TestSupport.waitUntil("前台计时通知", 8000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                return TestSupport.hasTimerNotification();
            }
        });
    }

    /** 自定义时长：输入 20 → 保存生效并显示当前自定义值。 */
    @Test
    public void customMinutesDialogSavesValue() {
        launchMain();
        TestSupport.clickAfterScroll(R.id.tg_custom);
        onView(withId(R.id.tv_num_title)).check(matches(withText("输入观看时长（分钟）")));

        onView(withId(R.id.btn_n2)).perform(click());
        onView(withId(R.id.btn_n0)).perform(click());
        onView(withId(R.id.tv_num_value)).check(matches(withText("20")));
        onView(withId(R.id.btn_nok)).perform(click());

        TestSupport.waitUntil("自定义时长生效", 5000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                return "当前自定义：20 分钟"
                        .equals(TestSupport.textOnUi(activity, R.id.tv_custom_info));
            }
        });
        assertEquals(20, TestSupport.prefs().getWatchMinutes());
    }

    /** 自定义时长范围守卫：超过 720 被拒绝；取消对话框不改变原值。 */
    @Test
    public void customMinutesRangeGuardAndCancel() {
        launchMain();
        TestSupport.clickAfterScroll(R.id.tg_custom);

        onView(withId(R.id.btn_n9)).perform(click());
        onView(withId(R.id.btn_n9)).perform(click());
        onView(withId(R.id.btn_n9)).perform(click()); // 999 > 720 → 拒绝
        onView(withId(R.id.tv_num_value)).check(matches(withText("99")));

        pressBack(); // 对话框可取消
        onView(withId(R.id.tv_custom_info)).check(matches(not(isDisplayed())));
        assertEquals("取消后时长不应改变", 30, TestSupport.prefs().getWatchMinutes());
    }

    /** 立即锁定（测试入口）：直接弹出答题锁屏页。 */
    @Test
    public void lockNowOpensLockScreen() {
        launchMain();
        TestSupport.clickAfterScroll(R.id.btn_lock_now);
        onView(withId(R.id.tv_lock_prompt)).check(matches(isDisplayed()));
        assertTrue(TestSupport.prefs().isLockActive());
    }

    /** P0 ⑦：修改 PIN 流程（验旧 → 输新 → 确认新），错误旧 PIN 不清场。 */
    @Test
    public void changePinFlow() {
        launchMain();
        TestSupport.clickAfterScroll(R.id.btn_change_pin);
        onView(withId(R.id.tv_pin_title))
                .check(matches(withText("家长解锁：请输入 PIN")));

        // 旧 PIN 错误：9999
        for (int i = 0; i < 4; i++) {
            onView(withId(R.id.btn_d9)).perform(click());
        }
        onView(withId(R.id.tv_pin_dots)).check(matches(withText("○ ○ ○ ○")));
        onView(withId(R.id.tv_pin_title)).check(matches(isDisplayed()));

        // 旧 PIN 正确：1234 → 输入新 PIN
        onView(withId(R.id.btn_d1)).perform(click());
        onView(withId(R.id.btn_d2)).perform(click());
        onView(withId(R.id.btn_d3)).perform(click());
        onView(withId(R.id.btn_d4)).perform(click());
        onView(withId(R.id.tv_pin_title)).check(matches(withText("请输入新 PIN")));

        // 新 PIN：5678 → 再次确认
        onView(withId(R.id.btn_d5)).perform(click());
        onView(withId(R.id.btn_d6)).perform(click());
        onView(withId(R.id.btn_d7)).perform(click());
        onView(withId(R.id.btn_d8)).perform(click());
        onView(withId(R.id.tv_pin_title)).check(matches(withText("请再次输入新 PIN")));

        // 确认一致：5678
        onView(withId(R.id.btn_d5)).perform(click());
        onView(withId(R.id.btn_d6)).perform(click());
        onView(withId(R.id.btn_d7)).perform(click());
        onView(withId(R.id.btn_d8)).perform(click());

        assertEquals("5678", TestSupport.prefs().getParentPin());
    }
}
