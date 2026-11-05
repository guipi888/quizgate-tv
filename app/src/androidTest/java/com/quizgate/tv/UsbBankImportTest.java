package com.quizgate.tv;

import androidx.test.core.app.ActivityScenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * P0 ⑥ U 盘题库导入的存储边界测试。
 *
 * 2026-11-05 CI 实测（API 30/34 真实 TV 镜像）确认的事实：
 * targetSdk 33 的应用在 API 29+ 设备上一律受分区存储约束，
 * requestLegacyExternalStorage 只对 targetSdk 29 有效，因此 File 路径
 * 直接读 /sdcard/QuizGate/banks 在现代电视系统上不可用
 * （README 原记录的「Android 13+」有误，实为 Android 10+/API 29+）。
 *
 * CI 仍会把 U 盘样本推送到该目录，本测试按系统能力分两支断言：
 *   1. 若样本可读（旧系统/未来接入 SAF 或 MANAGE_EXTERNAL_STORAGE）：
 *      正向断言——恰好入库一次（canonical 去重回归防线）、计入总量；
 *   2. 若样本不可读（当前 CI 的 API 30/34）：优雅降级——
 *      不崩溃、不计入总量、内置题库不受影响、设置页正常显示。
 * 恢复 U 盘导入属产品决策，见 README「已知限制」与 Roadmap。
 */
public class UsbBankImportTest {

    private static final String BANK_NAME = "U盘导入测试题库";
    private static final int BANK_QUESTIONS = 3;

    private ActivityScenario<MainActivity> scenario;
    private MainActivity activity;

    @Before
    public void setUp() {
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

    @Test
    public void usbImportBoundaryOnModernStorage() {
        int builtin = BankLoader.loadBuiltin(TestSupport.ctx()).totalQuestions();
        assertTrue("内置题库应至少有一批（assets/banks）", builtin > 0);

        BankLoader.LoadResult r = BankLoader.loadAll(TestSupport.ctx());

        int hits = 0;
        List<QuestionBank> banks = r.banks;
        for (int i = 0; i < banks.size(); i++) {
            if (BANK_NAME.equals(banks.get(i).name)) {
                hits++;
                assertEquals("题库题数应完整导入", BANK_QUESTIONS, banks.get(i).size());
            }
        }

        if (hits > 0) {
            // 分支 1：外置样本可读（旧系统，或未来接入 SAF/所有文件访问后）
            assertEquals("同一文件不得重复入库（canonical 去重回归防线）", 1, hits);
            assertEquals("U 盘 3 题应计入总量", builtin + BANK_QUESTIONS, r.totalQuestions());
        } else {
            // 分支 2：分区存储边界（当前 API 29+ 的现实）——安静跳过，不污染内置题库
            assertEquals("不可读的外置题库不得计入总量", builtin, r.totalQuestions());
        }

        // 设置页题库信息应始终反映内置题库的加载结果
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
            @Override
            public void perform(MainActivity a) {
                activity = a;
            }
        });
        String info = TestSupport.textOnUi(activity, R.id.tv_bank_info);
        assertTrue("设置页应显示题库已加载，实际：" + info,
                info != null && info.startsWith("已加载"));
    }
}
