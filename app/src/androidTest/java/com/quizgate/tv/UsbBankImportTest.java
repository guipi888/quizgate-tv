package com.quizgate.tv;

import android.os.Build;

import androidx.test.core.app.ActivityScenario;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * P0 ⑥ U 盘题库导入。
 *
 * 已知限制（本次 TV 适配实测确认）：READ_EXTERNAL_STORAGE 声明了
 * maxSdkVersion="32"，Android 13+ 既不申请也没有等价读权限，
 * 直接 File 路径读共享存储会被系统拦截；Android 11+ 对非媒体文件的
 * 直接 File 访问同样收紧。因此本测试只在 API ≤ 32 上验证，
 * 33+ 自动跳过——要覆盖新系统需要引入 SAF 目录选择或
 * MANAGE_EXTERNAL_STORAGE，属产品决策，见 README「已知限制」。
 */
public class UsbBankImportTest {

    private static final String BANK_DIR = "/sdcard/QuizGate/banks";
    private static final String BANK_FILE = BANK_DIR + "/usb_bank_sample.json";
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
    public void usbBankIsLoadedWithoutDuplicates() {
        Assume.assumeTrue("READ_EXTERNAL_STORAGE 仅声明到 API 32", Build.VERSION.SDK_INT <= 32);
        TestSupport.grantRuntime(android.Manifest.permission.READ_EXTERNAL_STORAGE);

        File f = new File(BANK_FILE);
        Assume.assumeTrue("CI 会先 adb push 题库样本；本地运行请手动放置到 " + BANK_DIR,
                f.exists());

        int builtin = BankLoader.loadBuiltin(TestSupport.ctx()).totalQuestions();
        BankLoader.LoadResult r = BankLoader.loadAll(TestSupport.ctx());

        int hits = 0;
        for (QuestionBank b : r.banks) {
            if (BANK_NAME.equals(b.name)) {
                hits++;
                assertEquals("题库题数应完整导入", BANK_QUESTIONS, b.size());
            }
        }
        assertEquals("同一文件不得重复入库（/sdcard 重复扫描回归防线）", 1, hits);
        assertEquals("U 盘 3 题应计入总量", builtin + BANK_QUESTIONS, r.totalQuestions());

        // 设置页题库信息应反映导入结果
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<MainActivity>() {
            @Override
            public void perform(MainActivity a) {
                activity = a;
            }
        });
        String info = TestSupport.textOnUi(activity, R.id.tv_bank_info);
        assertTrue("题库信息应含导入后的总数，实际：" + info,
                info != null && info.contains(String.valueOf(builtin + BANK_QUESTIONS)));
    }
}
