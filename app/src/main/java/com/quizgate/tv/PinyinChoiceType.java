package com.quizgate.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 汉字拼音题型：题干是汉字，从 4 个拼音中选正确读音。
 * 题库 JSON 格式见 README「题库格式」一节。
 */
public class PinyinChoiceType implements QuestionType {

    public static final String ID = "pinyin_choice";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "汉字拼音";
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
        String name = bankJson.optString("name", "汉字拼音题库");
        JSONArray arr = bankJson.getJSONArray("questions");
        List<Question> list = new ArrayList<>(arr.length());
        Random random = new Random();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            String answer = item.getString("a");
            String[] distractors = toStringArray(item.getJSONArray("d"));
            list.add(QuestionUtil.build(item.getString("q"), answer, distractors, random));
        }
        return new QuestionBank(name, ID, list);
    }

    static String[] toStringArray(JSONArray arr) throws JSONException {
        String[] out = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            out[i] = arr.getString(i);
        }
        return out;
    }
}
