package com.quizgate.tv;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 题型抽象。每个题型负责把自己的 JSON 题库解析成 Question 列表，
 * 或（生成式题型）按难度直接产出 Question。
 */
public interface QuestionType {

    /** 题型唯一 id，与题库 JSON 的 type 字段及设置项开关对应。 */
    String id();

    /** 设置页显示名。 */
    String displayName();

    /** 解析题库 JSON。生成式题型不支持，抛 UnsupportedOperationException。 */
    QuestionBank parse(JSONObject bankJson) throws JSONException;

    /** 生成式题型（如口算）返回 true，此类题型不需要题库文件。 */
    boolean isGenerated();

    /** 生成一道题，仅生成式题型使用；非生成式返回 null。 */
    Question generate(int difficulty, java.util.Random random);
}
