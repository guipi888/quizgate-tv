package com.quizgate.tv;

/** 一道题：题干 + 4 个选项 + 正确答案下标。所有题型统一成这个结构。 */
public class Question {

    public final String prompt;
    public final String[] options;
    public final int answerIndex;

    public Question(String prompt, String[] options, int answerIndex) {
        if (options == null || options.length != 4) {
            throw new IllegalArgumentException("options must contain exactly 4 items");
        }
        if (answerIndex < 0 || answerIndex > 3) {
            throw new IllegalArgumentException("answerIndex out of range");
        }
        this.prompt = prompt;
        this.options = options;
        this.answerIndex = answerIndex;
    }
}
