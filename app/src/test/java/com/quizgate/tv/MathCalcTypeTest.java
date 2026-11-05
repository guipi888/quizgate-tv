package com.quizgate.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * 口算题型测试：出题正确性、选项结构、难度分级。
 * 这是 P0 第 ④⑤⑥ 条的纯逻辑基础。
 */
public class MathCalcTypeTest {

    private static final Pattern PROMPT =
            Pattern.compile("^(\\d+) ([+\\-×÷]) (\\d+) = \\?$");

    private final MathCalcType type = new MathCalcType();

    private int[] parse(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue("题干格式不符: " + prompt, m.matches());
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(3))};
    }

    private String op(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches());
        return m.group(2);
    }

    private int expected(String prompt) {
        int[] ab = parse(prompt);
        int a = ab[0], b = ab[1];
        switch (op(prompt)) {
            case "+": return a + b;
            case "-": return a - b;
            case "×": return a * b;
            case "÷": return a / b;
            default:  throw new AssertionError("未知运算符");
        }
    }

    private void assertValidQuestion(Question q, String context) {
        assertNotNull(context + ": 题目为 null", q);
        assertNotNull(context + ": prompt 为 null", q.prompt);
        assertNotNull(context + ": options 为 null", q.options);
        assertEquals(context + ": 选项必须 4 个", 4, q.options.length);
        for (String o : q.options) {
            assertNotNull(context + ": 存在 null 选项", o);
        }
        assertTrue(context + ": answerIndex 越界", q.answerIndex >= 0 && q.answerIndex <= 3);
        assertTrue(context + ": 题干为空", q.prompt.length() > 0);
        assertTrue(context + ": 题干格式错误: " + q.prompt, PROMPT.matcher(q.prompt).matches());

        // 核心：answerIndex 指向的选项必须等于题干的真实计算结果
        assertEquals(context + ": 正确答案位置错误, prompt=" + q.prompt
                        + " opts=" + String.join(",", q.options),
                String.valueOf(expected(q.prompt)), q.options[q.answerIndex]);

        // 干扰项不能等于正确答案
        for (int i = 0; i < 4; i++) {
            if (i == q.answerIndex) continue;
            assertNotEquals(context + ": 干扰项等于正确答案, prompt=" + q.prompt,
                    q.options[q.answerIndex], q.options[i]);
        }
    }

    @Test
    public void idAndName() {
        assertEquals("math_calc", type.id());
        assertEquals("数学口算", type.displayName());
        assertTrue(type.isGenerated());
    }

    @Test
    public void parseThrows() {
        try {
            type.parse(new org.json.JSONObject());
            fail("生成式题型不应支持 parse");
        } catch (UnsupportedOperationException expected) {
            // 符合预期
        }
    }

    @Test
    public void generateReturnsNullForNonGenerated() {
        assertNull("非生成式题型 generate 应返回 null", new PinyinChoiceType().generate(1, new Random()));
    }

    @Test
    public void difficulty1_withinTen() {
        for (int seed = 0; seed < 3000; seed++) {
            Question q = type.generate(1, new Random(seed));
            assertValidQuestion(q, "L1 seed=" + seed);
            int[] ab = parse(q.prompt);
            int a = ab[0], b = ab[1];
            int ans = expected(q.prompt);
            assertTrue("L1 结果应 <=10: " + q.prompt, ans <= 10);
            assertTrue("L1 操作数范围: " + q.prompt, a >= 1 && a <= 10 && b >= 0 && b <= 9);
        }
    }

    @Test
    public void difficulty2_withinHundredOrTable() {
        for (int seed = 0; seed < 3000; seed++) {
            Question q = type.generate(2, new Random(seed));
            assertValidQuestion(q, "L2 seed=" + seed);
            int[] ab = parse(q.prompt);
            int a = ab[0], b = ab[1];
            String o = op(q.prompt);
            int ans = expected(q.prompt);
            if ("×".equals(o)) {
                assertTrue("L2 乘法应在表内: " + q.prompt, a >= 2 && a <= 9 && b >= 2 && b <= 9);
            } else {
                assertTrue("L2 结果应在 100 以内: " + q.prompt, ans >= 0 && ans <= 100);
                assertTrue("L2 操作数应在 100 以内: " + q.prompt, a <= 99 && b <= 99);
            }
        }
    }

    @Test
    public void difficulty3_twoDigitMultiplyOrDivide() {
        for (int seed = 0; seed < 3000; seed++) {
            Question q = type.generate(3, new Random(seed));
            assertValidQuestion(q, "L3 seed=" + seed);
            int[] ab = parse(q.prompt);
            int a = ab[0], b = ab[1];
            String o = op(q.prompt);
            if ("×".equals(o)) {
                assertTrue("L3 乘法应为两位数×一位数: " + q.prompt, a >= 11 && a <= 49 && b >= 2 && b <= 9);
            } else {
                assertEquals("÷", o);
                assertTrue("L3 除法除数应为一或两位数: " + q.prompt, b >= 2 && b <= 9);
                assertTrue("L3 除法应能整除: " + q.prompt, a % b == 0);
            }
        }
    }

    @Test
    public void optionsAreUnique() {
        for (int d = 1; d <= 3; d++) {
            for (int seed = 0; seed < 1500; seed++) {
                Question q = type.generate(d, new Random(seed));
                Set<String> uniq = new HashSet<>();
                for (String o : q.options) uniq.add(o);
                assertEquals("难度" + d + " seed=" + seed + " 出现重复选项: "
                        + String.join(",", q.options), 4, uniq.size());
            }
        }
    }

    @Test
    public void answerPositionIsRandomized() {
        // 打乱必须真的生效：统计 4 个位置都出现过
        Set<Integer> positions = new HashSet<>();
        for (int seed = 0; seed < 5000; seed++) {
            positions.add(type.generate(2, new Random(seed)).answerIndex);
        }
        assertEquals("正确答案应分布在不同位置", 4, positions.size());
    }

    @Test
    public void consecutiveDrawsDifferInPositionDistribution() {
        // 连续两次抽题，答案位置不应总是相同（防"永远在第一个"的退化）
        int sameCount = 0;
        Random r = new Random(20261105L);
        Question prev = type.generate(2, r);
        for (int i = 0; i < 500; i++) {
            Question cur = type.generate(2, r);
            if (cur.answerIndex == prev.answerIndex) sameCount++;
            prev = cur;
        }
        assertTrue("答案位置重复率异常高: " + sameCount + "/500", sameCount < 400);
    }
}
