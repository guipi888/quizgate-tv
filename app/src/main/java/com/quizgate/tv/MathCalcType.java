package com.quizgate.tv;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/**
 * 口算题型：程序自动生成，无需题库文件，家长零维护。
 * difficulty：1 = 10 以内加减；2 = 100 以内加减 / 表内乘除；3 = 两位数乘除。
 */
public class MathCalcType implements QuestionType {

    public static final String ID = "math_calc";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "数学口算";
    }

    @Override
    public boolean isGenerated() {
        return true;
    }

    @Override
    public QuestionBank parse(org.json.JSONObject bankJson) {
        throw new UnsupportedOperationException("math_calc is generated, no bank file");
    }

    @Override
    public Question generate(int difficulty, Random random) {
        switch (difficulty) {
            case 1:
                return level1(random);
            case 3:
                return level3(random);
            case 2:
            default:
                return level2(random);
        }
    }

    private Question level1(Random r) {
        if (r.nextBoolean()) {
            int a = 1 + r.nextInt(9);
            int b = 1 + r.nextInt(10 - a);
            return math(a, "+", b, a + b, r);
        }
        int a = 2 + r.nextInt(9);
        int b = 1 + r.nextInt(a);
        return math(a, "-", b, a - b, r);
    }

    private Question level2(Random r) {
        int kind = r.nextInt(3);
        if (kind == 0) {
            int a = 10 + r.nextInt(90);
            int b = 1 + r.nextInt(100 - a);
            return math(a, "+", b, a + b, r);
        }
        if (kind == 1) {
            int a = 11 + r.nextInt(89);
            int b = 1 + r.nextInt(Math.min(a - 1, 99));
            return math(a, "-", b, a - b, r);
        }
        int a = 2 + r.nextInt(8);
        int b = 2 + r.nextInt(8);
        return math(a, "×", b, a * b, r);
    }

    private Question level3(Random r) {
        int kind = r.nextInt(2);
        if (kind == 0) {
            int a = 11 + r.nextInt(39);
            int b = 2 + r.nextInt(8);
            return math(a, "×", b, a * b, r);
        }
        int b = 2 + r.nextInt(8);
        int c = 2 + r.nextInt(9);
        int a = b * c;
        return math(a, "÷", b, c, r);
    }

    private Question math(int a, String op, int b, int answer, Random r) {
        String prompt = a + " " + op + " " + b + " = ?";
        Set<Integer> wrong = new LinkedHashSet<>();
        int[] deltas = {1, -1, 2, -2, 10, -10};
        for (int d : deltas) {
            if (answer + d > 0) {
                wrong.add(answer + d);
            }
        }
        wrong.remove(answer);
        while (wrong.size() < 3) {
            wrong.add(1 + r.nextInt(Math.max(answer + 12, 20)));
            wrong.remove(answer);
        }
        String[] distractors = new String[3];
        int i = 0;
        for (Integer w : wrong) {
            if (i < 3) {
                distractors[i++] = String.valueOf(w);
            }
        }
        return QuestionUtil.build(prompt, String.valueOf(answer), distractors, r);
    }
}
