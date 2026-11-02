package com.quizgate.tv;

import java.util.Random;

/** 题目组装工具：把"正确答案 + 干扰项"打乱成 4 选 1。 */
final class QuestionUtil {

    private QuestionUtil() {
    }

    static Question build(String prompt, String answer, String[] distractors, Random random) {
        String[] opts = new String[4];
        opts[0] = answer;
        int n = 1;
        for (int i = 0; i < distractors.length && n < 4; i++) {
            if (!distractors[i].equals(answer)) {
                opts[n++] = distractors[i];
            }
        }
        while (n < 4) {
            opts[n++] = answer + "?";
        }
        for (int i = 3; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String tmp = opts[i];
            opts[i] = opts[j];
            opts[j] = tmp;
        }
        int idx = 0;
        for (int i = 0; i < 4; i++) {
            if (opts[i].equals(answer)) {
                idx = i;
                break;
            }
        }
        return new Question(prompt, opts, idx);
    }
}
