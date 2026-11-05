package com.quizgate.tv;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * 答题引擎测试：抽题、判定、连对计数、解锁条件。
 * 直接对应 P0 第 ④（答对+1）⑤（连对 N 题解锁）⑥（答错归零）条。
 */
public class QuizEngineTest {

    private static final String PINYIN = PinyinChoiceType.ID;
    private static final String MATH = MathCalcType.ID;

    private static Question q(String prompt, String answer, String... distractors) {
        return QuestionUtil.build(prompt, answer, distractors, new Random(7));
    }

    private static List<QuestionBank> bank(String type, List<Question> items) {
        List<QuestionBank> banks = new ArrayList<>();
        banks.add(new QuestionBank("测试题库", type, items));
        return banks;
    }

    private static Set<String> types(String... ids) {
        return new LinkedHashSet<>(Arrays.asList(ids));
    }

    /**
     * 构造并立即抽第一题。
     * 注意：QuizEngine 构造函数只恢复 streak，不会自动抽题，
     * 不先调 next() 时 current 为 null（judge 会安全返回 false）。
     */
    private QuizEngine engine(List<Question> items, int need, int initial) {
        QuizEngine e = new QuizEngine(bank(PINYIN, items), types(PINYIN), need, initial);
        assertNotNull("构造后应能立即抽到题", e.next());
        return e;
    }

    // ---------- P0 ④ 答对 +1 ----------

    @Test
    public void correctAnswerIncrementsStreak() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) items.add(q("第" + i + "题", "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        assertEquals(0, engine.streak());

        assertTrue(engine.judge(engine.current().answerIndex));
        assertEquals("答对应 +1", 1, engine.streak());

        assertTrue(engine.judge(engine.current().answerIndex));
        assertEquals("连对应继续累加", 2, engine.streak());
    }

    // ---------- P0 ⑤ 连对 N 题解锁 ----------

    @Test
    public void unlocksAfterRequiredStreak() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        for (int need : new int[]{1, 3, 5, 10}) {
            QuizEngine engine = engine(items, need, 0);
            for (int i = 0; i < need; i++) {
                if (i < need - 1) {
                    assertFalse("还差 " + (need - i) + " 题时不应解锁",
                            engine.unlocked());
                }
                assertTrue("第 " + (i + 1) + " 题应判对", engine.judge(engine.current().answerIndex));
                if (engine.current() != null && i + 1 < need) {
                    // 抽下一题
                    assertNotNull(engine.next());
                }
            }
            assertTrue("连对 " + need + " 题应解锁", engine.unlocked());
            assertEquals("解锁后剩余应为 0", 0, engine.remainingToUnlock());
        }
    }

    @Test
    public void remainingToUnlockCountsDown() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 5, 0);
        assertEquals(5, engine.remainingToUnlock());
        engine.judge(engine.current().answerIndex);
        assertEquals(4, engine.remainingToUnlock());
        engine.next();
        engine.judge(engine.current().answerIndex);
        assertEquals(3, engine.remainingToUnlock());
    }

    @Test
    public void initialStreakIsRestored() {
        // 从 SharedPreferences 恢复的连对数必须生效（重启恢复链路）
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 2);
        assertEquals(2, engine.streak());
        assertEquals(1, engine.remainingToUnlock());
        assertTrue(engine.judge(engine.current().answerIndex));
        assertTrue("恢复后再答一题即应解锁", engine.unlocked());
    }

    // ---------- P0 ⑥ 答错归零 ----------

    @Test
    public void wrongAnswerResetsStreak() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        engine.judge(engine.current().answerIndex);
        engine.next();
        engine.judge(engine.current().answerIndex);
        assertEquals(2, engine.streak());

        int wrong = (engine.current().answerIndex + 1) % 4;
        assertFalse(engine.judge(wrong));
        assertEquals("答错必须归零", 0, engine.streak());
        assertFalse(engine.unlocked());
        assertEquals(3, engine.remainingToUnlock());
    }

    @Test
    public void wrongAnswerCanResetAtAnyStreak() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        for (int correctBefore : new int[]{0, 1, 2, 4, 9}) {
            QuizEngine engine = engine(items, 10, 0);
            for (int i = 0; i < correctBefore; i++) {
                engine.judge(engine.current().answerIndex);
                if (i + 1 < correctBefore) engine.next();
            }
            int wrong = (engine.current().answerIndex + 2) % 4;
            engine.judge(wrong);
            assertEquals("连对 " + correctBefore + " 题后答错应归零", 0, engine.streak());
        }
    }

    // ---------- 边界与非法输入 ----------

    /**
     * 越界下标的语义：忽略（返回 false，但不动 streak）。
     * 这是有意设计——UI 只会传 0..3，若因 bug 传出越界值，
     * 不应顺带把孩子已经答对的进度清零。
     * 注意这与"答错"不同：答错会归零，越界是忽略。
     */
    @Test
    public void judgeWithInvalidIndexIsIgnored() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        assertTrue(engine.judge(engine.current().answerIndex));
        assertEquals(1, engine.streak());

        for (int bad : new int[]{-1, -5, 4, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertFalse("非法下标 " + bad + " 应判为不正确", engine.judge(bad));
        }
        assertEquals("越界下标不应清零已有进度", 1, engine.streak());
        assertFalse("1 题未达 3 题，不应解锁", engine.unlocked());
    }

    /** 与上一条对照：真正答错（下标合法但内容错）才归零。 */
    @Test
    public void judgeWithValidWrongIndexResetsStreak() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        assertTrue(engine.judge(engine.current().answerIndex));
        assertEquals(1, engine.streak());

        int wrong = (engine.current().answerIndex + 1) % 4;
        assertFalse(engine.judge(wrong));
        assertEquals("合法下标答错必须归零", 0, engine.streak());
    }

    @Test
    public void judgeBeforeFirstDrawIsSafe() {
        QuizEngine engine = new QuizEngine(bank(PINYIN, new ArrayList<>()), types(PINYIN), 3, 0);
        // 空题库，current 为 null
        assertFalse(engine.judge(0));
        assertEquals(0, engine.streak());
    }

    @Test
    public void requiredStreakZeroUnlocksImmediately() {
        QuizEngine engine = new QuizEngine(bank(PINYIN, new ArrayList<>()), types(PINYIN), 0, 0);
        assertTrue(engine.unlocked());
    }

    // ---------- 抽题 ----------

    @Test
    public void nextNeverReturnsNullWhenBankHasItems() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        for (int i = 0; i < 200; i++) {
            Question q = engine.next();
            assertNotNull("第 " + i + " 次抽题为 null", q);
            assertNotNull(q.prompt);
        }
    }

    @Test
    public void consecutiveQuestionsDifferWhenPoolLarge() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        Question prev = engine.next();
        int same = 0;
        for (int i = 0; i < 500; i++) {
            Question cur = engine.next();
            assertNotNull(cur);
            if (cur.prompt.equals(prev.prompt)) same++;
            prev = cur;
        }
        assertTrue("连续同题率异常: " + same + "/500", same <= 5);
    }

    @Test
    public void generatedTypeNeedsNoBank() {
        // 只开数学题、题库为空，也应能出题（家长零维护）
        QuizEngine engine = new QuizEngine(new ArrayList<>(), types(MATH), 3, 0);
        assertTrue(engine.hasQuestions());
        for (int i = 0; i < 50; i++) {
            Question q = engine.next();
            assertNotNull("纯生成式题型应能出题", q);
        }
    }

    @Test
    public void noEnabledTypesMeansNoQuestions() {
        QuizEngine engine = new QuizEngine(bank(PINYIN, new ArrayList<>()), types(), 3, 0);
        assertFalse(engine.hasQuestions());
        assertNull(engine.next());
    }

    @Test
    public void enabledTypeWithoutBankMeansNoQuestions() {
        // 只开拼音题但没导入题库 → 无题可出
        QuizEngine engine = new QuizEngine(new ArrayList<>(), types(PINYIN), 3, 0);
        assertFalse(engine.hasQuestions());
        assertNull(engine.next());
    }

    @Test
    public void unknownTypeIsIgnored() {
        QuizEngine engine = new QuizEngine(new ArrayList<>(), types("not_a_type"), 3, 0);
        assertFalse(engine.hasQuestions());
        assertNull(engine.next());
    }

    @Test
    public void mixedTypesProduceBothKinds() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) items.add(q("拼音题" + i, "yǎ", "lù", "zhuàn", "miáo"));

        QuizEngine engine = new QuizEngine(bank(PINYIN, items), types(PINYIN, MATH), 3, 0);
        boolean sawPinyin = false, sawMath = false;
        for (int i = 0; i < 300; i++) {
            Question q = engine.next();
            if (q.prompt.startsWith("拼音题")) sawPinyin = true;
            else sawMath = true;
        }
        assertTrue("混合题型应出现拼音题", sawPinyin);
        assertTrue("混合题型应出现数学题", sawMath);
    }

    @Test
    public void allQuestionsFromBankAreValid() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        for (int i = 0; i < 200; i++) {
            Question q = engine.next();
            assertEquals(4, q.options.length);
            assertTrue(q.answerIndex >= 0 && q.answerIndex <= 3);
            assertEquals("A", q.options[q.answerIndex]);
        }
    }

    @Test
    public void currentQuestionIsStableBetweenDraws() {
        List<Question> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(q("题" + i, "A", "B", "C", "D"));

        QuizEngine engine = engine(items, 3, 0);
        Question a = engine.current();
        assertSame("未抽新题时 current 不应变", a, engine.current());
        Question b = engine.next();
        assertNotSame("抽新题后 current 应更新", a, b);
    }
}
