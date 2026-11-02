package com.quizgate.tv;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 题型注册表：新增题型只需在此注册一行。 */
public final class QuestionTypeRegistry {

    private static final Map<String, QuestionType> TYPES = new LinkedHashMap<>();

    static {
        register(new PinyinChoiceType());
        register(new WordChoiceType());
        register(new MathCalcType());
    }

    private QuestionTypeRegistry() {
    }

    public static void register(QuestionType type) {
        TYPES.put(type.id(), type);
    }

    public static QuestionType get(String id) {
        return TYPES.get(id);
    }

    public static Collection<QuestionType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }
}
