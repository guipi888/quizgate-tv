package com.quizgate.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 中英互选题型：题干是中文（或英文），从 4 个选项中选正确翻译。
 * 题库 JSON 格式见 README「题库格式」一节。
 */
public class WordChoiceType implements QuestionType {

    public static final String ID = "word_choice";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "英语单词";
    }

    @Override
    public boolean isGenerated() {
        return false;
    }

    @Override
    public Question generate(int difficulty, Random random) {
        return null;
    }

    @Override
    public QuestionBank parse(JSONObject bankJson) throws JSONException {
        String name = bankJson.optString("name", "英语单词题库");
        JSONArray arr = bankJson.getJSONArray("questions");
        List<Question> list = new ArrayList<>(arr.length());
        Random random = new Random();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            String answer = item.getString("a");
            String[] distractors = PinyinChoiceType.toStringArray(item.getJSONArray("d"));
            list.add(QuestionUtil.build(item.getString("q"), answer, distractors, random));
        }
        return new QuestionBank(name, ID, list);
    }
}
