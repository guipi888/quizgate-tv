package com.quizgate.tv;

import androidx.test.core.app.ActivityScenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 答题锁屏页测试（P0 ③④⑤⑦）。
 *
 * 正确答案通过反射读 QuizEngine.current() 获得，点击是确定性的；
 * 视觉反馈（对=绿/错=红+高亮正确项）直接读背景色断言。
 */
public class LockActivityTest {

    private ActivityScenario<LockActivity> scenario;
    private LockActivity activity;

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

    private void launchLock() {
        // LockActivity.onCreate/onResume 在未锁定时会直接 finish()——
        // 不先进入锁屏态就启动，ActivityScenario 拿到的是已销毁的
        // Activity，onActivity 回调抛 NPE（CI 首跑 5 个用例全灭于此）。
        // enterLock() 即"计时到期进入锁屏"的真实状态。
        TestSupport.prefs().enterLock();
        scenario = ActivityScenario.launch(LockActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<LockActivity>() {
            @Override
            public void perform(LockActivity a) {
                activity = a;
            }
        });
    }

    /** P0 ③：锁屏页渲染一道口算题，且 4 个选项互不相同。 */
    @Test
    public void rendersQuestionWithFourDistinctOptions() {
        launchLock();
        onView(withId(R.id.tv_lock_title)).check(matches(withText("观看时间到")));
        String prompt = TestSupport.textOnUi(activity, R.id.tv_lock_prompt);
        assertNotNull("题干不得为空", prompt);
        assertTrue("题干应为口算题，实际：" + prompt,
                prompt.matches("\\d+ [+\\-×÷] \\d+ = \\?"));

        java.util.Set<String> opts = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) {
            String t = TestSupport.textOnUi(activity, TestSupport.optBtnId(i));
            assertNotNull("选项不得为空", t);
            assertTrue("选项不得为空文本", t.length() > 0);
            opts.add(t);
        }
        assertEquals("4 个选项应互不相同", 4, opts.size());
    }

    /** P0 ④：答对连对 +1，选中项变绿。 */
    @Test
    public void correctAnswerIncrementsStreakAndTurnsGreen() {
        launchLock();
        Question q = TestSupport.engineOf(activity).current();
        assertNotNull(q);
        onView(withId(TestSupport.optBtnId(q.answerIndex))).perform(click());

        TestSupport.waitUntil("连对 1", 5000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                String t = TestSupport.textOnUi(activity, R.id.tv_lock_progress);
                return t != null && t.startsWith("连对 1 / 3");
            }
        });
        assertEquals("答对应高亮为绿色",
                TestSupport.color(R.color.correct),
                TestSupport.bgColorOnUi(activity, TestSupport.optBtnId(q.answerIndex)));
    }

    /** P0 ⑤：答错连对归零，选中项变红且高亮正确项。 */
    @Test
    public void wrongAnswerResetsStreakAndHighlightsCorrect() {
        launchLock();
        final Question q = TestSupport.engineOf(activity).current();
        assertNotNull(q);
        final int wrongIdx = (q.answerIndex + 1) % 4;
        onView(withId(TestSupport.optBtnId(wrongIdx))).perform(click());

        TestSupport.waitUntil("连对归零", 5000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                String t = TestSupport.textOnUi(activity, R.id.tv_lock_progress);
                return t != null && t.startsWith("连对 0 / 3");
            }
        });
        assertEquals("错选应标红",
                TestSupport.color(R.color.wrong),
                TestSupport.bgColorOnUi(activity, TestSupport.optBtnId(wrongIdx)));
        assertEquals("应同时高亮正确项",
                TestSupport.color(R.color.correct),
                TestSupport.bgColorOnUi(activity, TestSupport.optBtnId(q.answerIndex)));
    }

    /** P0 ④⑤：连对 3 题自动解锁，并重新开始一轮计时。 */
    @Test
    public void threeCorrectAnswersUnlockAndRestartTimer() {
        launchLock();
        for (int round = 1; round <= 3; round++) {
            final Question q = TestSupport.engineOf(activity).current();
            assertNotNull(q);
            onView(withId(TestSupport.optBtnId(q.answerIndex))).perform(click());

            final String want = "连对 " + round + " / 3";
            TestSupport.waitUntil(want, 5000, new TestSupport.Condition() {
                @Override
                public boolean test() {
                    String t = TestSupport.textOnUi(activity, R.id.tv_lock_progress);
                    return t != null && t.startsWith(want);
                }
            });

            if (round < 3) {
                TestSupport.waitUntil("下一题渲染", 5000, new TestSupport.Condition() {
                    @Override
                    public boolean test() {
                        return TestSupport.engineOf(activity).current() != q;
                    }
                });
            }
        }

        TestSupport.waitUntil("解锁后锁屏页结束", 8000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                return activity.isFinishing();
            }
        });

        PrefManager p = TestSupport.prefs();
        assertTrue("解锁后锁屏应关闭", !p.isLockActive());
        assertTrue("解锁后应重新开始计时", p.isTimerRunning() && p.remainingMillis() > 0);
    }

    /** P0 ⑦：家长 PIN 错误不清锁，输入正确 PIN 后解锁并停掉整个门禁。 */
    @Test
    public void parentPinUnlock() {
        launchLock();
        onView(withId(R.id.btn_parent_unlock)).perform(click());
        onView(withId(R.id.tv_pin_title))
                .check(matches(withText("家长解锁：请输入 PIN")));

        // 错误 PIN：9999 → 抖动清空，对话框仍在
        for (int i = 0; i < 4; i++) {
            onView(withId(R.id.btn_d9)).perform(click());
        }
        onView(withId(R.id.tv_pin_dots)).check(matches(withText("○ ○ ○ ○")));
        onView(withId(R.id.tv_pin_title)).check(matches(isDisplayed()));

        // 正确 PIN：1234（默认 PIN）→ 解锁
        onView(withId(R.id.btn_d1)).perform(click());
        onView(withId(R.id.btn_d2)).perform(click());
        onView(withId(R.id.btn_d3)).perform(click());
        onView(withId(R.id.btn_d4)).perform(click());

        TestSupport.waitUntil("家长解锁后门禁停止", 8000, new TestSupport.Condition() {
            @Override
            public boolean test() {
                PrefManager p = TestSupport.prefs();
                return !p.isLockActive() && !p.isTimerRunning() && activity.isFinishing();
            }
        });
    }
}
