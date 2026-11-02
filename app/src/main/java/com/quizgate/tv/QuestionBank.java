package com.quizgate.tv;

import java.util.List;

/** 一个题库文件：名称 + 题型 id + 题目列表。 */
public class QuestionBank {

    public final String name;
    public final String type;
    public final List<Question> questions;

    public QuestionBank(String name, String type, List<Question> questions) {
        this.name = name;
        this.type = type;
        this.questions = questions;
    }

    public int size() {
        return questions.size();
    }
}
