package com.quizgate.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/** 题目组装与数据类校验测试。 */
public class QuestionUtilTest {

    @Test
    public void buildPutsAnswerAtAnswerIndex() {
        Random r = new Random(1);
        for (int seed = 0; seed < 2000; seed++) {
            r.setSeed(seed);
            Question q = QuestionUtil.build("题干", "正确", new String[]{"错1", "错2", "错3"}, r);
            assertEquals("answerIndex 必须指向正确答案",
                    "正确", q.options[q.answerIndex]);
            assertEquals(4, q.options.length);
            assertEquals("题干", q.prompt);
        }
    }

    @Test
    public void buildProducesFourOptions() {
        Question q = QuestionUtil.build("题", "A", new String[]{"B", "C", "D"}, new Random(3));
        assertEquals(4, q.options.length);
        Set<String> uniq = new HashSet<>();
        for (String o : q.options) uniq.add(o);
        assertEquals(4, uniq.size());
    }

    @Test
    public void buildFiltersDistractorsEqualToAnswer() {
        Question q = QuestionUtil.build("题", "A",
                new String[]{"A", "B", "C"}, new Random(5));
        assertEquals("A", q.options[q.answerIndex]);
        int countA = 0;
        for (String o : q.options) if (o.equals("A")) countA++;
        assertEquals("正确答案只能出现一次", 1, countA);
    }

    @Test
    public void buildFillsWhenDistractorsInsufficient() {
        // 只有 1 个有效干扰项时，剩余位置用 answer+"?" 填充，不能崩、不能越界
        Question q = QuestionUtil.build("题", "A",
                new String[]{"A", "B"}, new Random(9));
        assertEquals(4, q.options.length);
        for (String o : q.options) assertNotNull(o);
        assertEquals("A", q.options[q.answerIndex]);
    }

    @Test
    public void buildHandlesAllDistractorsEqualAnswer() {
        Question q = QuestionUtil.build("题", "A",
                new String[]{"A", "A", "A"}, new Random(11));
        assertEquals(4, q.options.length);
        assertEquals("A", q.options[q.answerIndex]);
    }

    @Test
    public void buildRandomizesPosition() {
        Set<Integer> positions = new HashSet<>();
        Random r = new Random(42);
        for (int i = 0; i < 3000; i++) {
            positions.add(QuestionUtil.build("题", "A",
                    new String[]{"B", "C", "D"}, r).answerIndex);
        }
        assertEquals("正确答案位置应打散到 4 个下标", 4, positions.size());
    }

    @Test
    public void buildHandlesUnicodeOptions() {
        Question q = QuestionUtil.build("骛的读音", "yǎo",
                new String[]{"lù", "zhuàn", "miáo"}, new Random(13));
        assertEquals("yǎo", q.options[q.answerIndex]);
        assertEquals(4, q.options.length);
    }

    // ---------- Question 数据类校验 ----------

    @Test
    public void questionRejectsWrongOptionCount() {
        for (String[] bad : new String[][]{
                null, {}, {"A"}, {"A", "B"}, {"A", "B", "C"}, {"A", "B", "C", "D", "E"}
        }) {
            try {
                new Question("题", bad, 0);
                fail("应拒绝选项数 " + (bad == null ? "null" : bad.length));
            } catch (IllegalArgumentException expected) {
                // 符合预期
            }
        }
    }

    @Test
    public void questionRejectsBadAnswerIndex() {
        String[] ok = {"A", "B", "C", "D"};
        for (int bad : new int[]{-1, -100, 4, 99}) {
            try {
                new Question("题", ok, bad);
                fail("应拒绝 answerIndex=" + bad);
            } catch (IllegalArgumentException expected) {
                // 符合预期
            }
        }
    }

    @Test
    public void questionAcceptsValidInput() {
        Question q = new Question("题", new String[]{"A", "B", "C", "D"}, 2);
        assertEquals("题", q.prompt);
        assertEquals(2, q.answerIndex);
        assertEquals("C", q.options[2]);
    }
}
