package com.quizgate.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 答题引擎：抽题、判定、连对计数。
 * 规则（与家长确认的行为一致）：答对 streak+1，答错 streak 归零，
 * 连续答对 requiredStreak 题才解锁。
 */
public class QuizEngine {

    private final List<QuestionBank> banks;
    private final Set<String> enabledTypes;
    private final int requiredStreak;
    private final Random random = new Random();

    private int streak;
    private Question current;
    private String lastPrompt;

    public QuizEngine(List<QuestionBank> banks, Set<String> enabledTypes,
                      int requiredStreak, int initialStreak) {
        this.banks = banks;
        this.enabledTypes = enabledTypes;
        this.requiredStreak = requiredStreak;
        this.streak = initialStreak;
    }

    /** 是否存在可用题目（无题库时锁屏页应提示家长先导入题库）。 */
    public boolean hasQuestions() {
        for (String id : enabledTypes) {
            QuestionType qt = QuestionTypeRegistry.get(id);
            if (qt == null) {
                continue;
            }
            if (qt.isGenerated()) {
                return true;
            }
            for (QuestionBank b : banks) {
                if (b.type.equals(id) && b.size() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public Question next() {
        for (int attempt = 0; attempt < 6; attempt++) {
            Question q = drawOnce();
            if (q == null) {
                return null;
            }
            if (q.prompt.equals(lastPrompt)) {
                continue;
            }
            lastPrompt = q.prompt;
            current = q;
            return q;
        }
        return current;
    }

    private Question drawOnce() {
        List<QuestionType> available = new ArrayList<>();
        for (String id : enabledTypes) {
            QuestionType qt = QuestionTypeRegistry.get(id);
            if (qt == null) {
                continue;
            }
            if (qt.isGenerated()) {
                available.add(qt);
            } else {
                for (QuestionBank b : banks) {
                    if (b.type.equals(id) && b.size() > 0) {
                        available.add(qt);
                        break;
                    }
                }
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        QuestionType chosen = available.get(random.nextInt(available.size()));
        if (chosen.isGenerated()) {
            return chosen.generate(randomDifficulty(), random);
        }
        List<Question> pool = new ArrayList<>();
        for (QuestionBank b : banks) {
            if (b.type.equals(chosen.id())) {
                pool.addAll(b.questions);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private int randomDifficulty() {
        double d = random.nextDouble();
        if (d < 0.4) {
            return 1;
        }
        if (d < 0.8) {
            return 2;
        }
        return 3;
    }

    /**
     * 判定作答：对返回 true 并 streak+1；错返回 false 且 streak 归零。
     * 越界下标（Question 恒为 4 选项，UI tag 恒为 0..3，越界只可能是代码 bug）：
     * 返回 false 但不动 streak——「忽略」语义。
     * 为什么不改为抛异常：本方法在锁屏门禁的核心路径上，抛异常会直接崩溃
     * 锁屏页＝门禁被绕过；忽略一次非法输入零代价（既判不了对也清不了零）。
     * current 为 null（尚未抽题）同理忽略，由 QuizEngineTest 锁定该语义。
     */
    public boolean judge(int optionIndex) {
        if (current == null || optionIndex < 0 || optionIndex > 3) {
            return false;
        }
        boolean correct = optionIndex == current.answerIndex;
        streak = correct ? streak + 1 : 0;
        return correct;
    }

    public int streak() {
        return streak;
    }

    public int requiredStreak() {
        return requiredStreak;
    }

    public int remainingToUnlock() {
        return Math.max(0, requiredStreak - streak);
    }

    public boolean unlocked() {
        return streak >= requiredStreak;
    }

    public Question current() {
        return current;
    }
}
