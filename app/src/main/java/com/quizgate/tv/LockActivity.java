package com.quizgate.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 答题锁屏页：全屏遮挡 + 4 选 1 答题。
 * 规则：答对连对数 +1，答错归零，连对达标才解锁；
 * 家长 PIN 可随时解锁；返回键/HOME 逃逸有兜底自愈。
 */
public class LockActivity extends Activity implements View.OnClickListener {

    private PrefManager prefs;
    private QuizEngine engine;
    private final Handler handler = new Handler();

    private TextView tvTitle;
    private TextView tvProgress;
    private TextView tvPrompt;
    private TextView tvEmpty;
    private final Button[] opts = new Button[4];
    private Button btnParent;

    private boolean accepting = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_lock);

        prefs = new PrefManager(this);
        if (!prefs.isLockActive()) {
            finish();
            return;
        }

        tvTitle = (TextView) findViewById(R.id.tv_lock_title);
        tvProgress = (TextView) findViewById(R.id.tv_lock_progress);
        tvPrompt = (TextView) findViewById(R.id.tv_lock_prompt);
        tvEmpty = (TextView) findViewById(R.id.tv_lock_empty);
        opts[0] = (Button) findViewById(R.id.btn_opt0);
        opts[1] = (Button) findViewById(R.id.btn_opt1);
        opts[2] = (Button) findViewById(R.id.btn_opt2);
        opts[3] = (Button) findViewById(R.id.btn_opt3);
        btnParent = (Button) findViewById(R.id.btn_parent_unlock);

        for (int i = 0; i < 4; i++) {
            opts[i].setOnClickListener(this);
            opts[i].setTag(i);
        }
        btnParent.setOnClickListener(this);

        engine = new QuizEngine(loadBanks(), prefs.getEnabledTypes(),
                prefs.getRequiredStreak(), prefs.getStreak());

        if (!engine.hasQuestions()) {
            showEmptyBank();
        } else {
            renderQuestion(engine.next());
        }
    }

    private List<QuestionBank> loadBanks() {
        return BankLoader.loadAll(this).banks;
    }

    private void showEmptyBank() {
        tvPrompt.setVisibility(View.GONE);
        for (Button b : opts) {
            b.setVisibility(View.GONE);
        }
        tvEmpty.setVisibility(View.VISIBLE);
        tvProgress.setText("题库为空");
    }

    private void renderQuestion(Question q) {
        if (q == null) {
            showEmptyBank();
            return;
        }
        tvPrompt.setVisibility(View.VISIBLE);
        for (Button b : opts) {
            b.setVisibility(View.VISIBLE);
        }
        tvEmpty.setVisibility(View.GONE);

        tvPrompt.setText(q.prompt);
        for (int i = 0; i < 4; i++) {
            opts[i].setText(q.options[i]);
            opts[i].setBackgroundResource(R.drawable.bg_option);
            opts[i].setEnabled(true);
        }
        updateProgress();
        opts[0].requestFocus();
        accepting = true;
    }

    private void updateProgress() {
        tvProgress.setText("连对 " + engine.streak() + " / " + engine.requiredStreak()
                + "（答错重新计数）");
    }

    @Override
    public void onClick(View v) {
        if (!accepting) {
            return;
        }
        if (v == btnParent) {
            PinPadDialog.createVerify(this, getString(R.string.pin_parent_verify),
                    new PinPadDialog.Callback() {
                        @Override
                        public void onSuccess(String pin) {
                            prefs.parentUnlock();
                            stopService(new Intent(LockActivity.this, LockService.class));
                            Toast.makeText(LockActivity.this, "已解锁，门禁已停止", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }).show();
            return;
        }
        int idx = (Integer) v.getTag();
        boolean correct = engine.judge(idx);
        prefs.setStreak(engine.streak());
        accepting = false;

        int green = getResources().getColor(R.color.correct);
        int red = getResources().getColor(R.color.wrong);
        if (correct) {
            v.setBackgroundColor(green);
        } else {
            v.setBackgroundColor(red);
            opts[engine.current().answerIndex].setBackgroundColor(green);
        }
        updateProgress();

        if (engine.unlocked()) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    prefs.unlockAndRestart();
                    startService(new Intent(LockActivity.this, LockService.class));
                    Toast.makeText(LockActivity.this, "解锁成功，计时重新开始", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }, 700L);
        } else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    renderQuestion(engine.next());
                }
            }, 900L);
        }
    }

    @Override
    public void onBackPressed() {
        // 锁屏态吞掉返回键，防止退出答题页
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && !prefs.isLockActive()) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 兜底：锁屏态被切走（HOME/多任务）后 300ms 自愈拉回
        if (prefs != null && prefs.isLockActive()) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (prefs.isLockActive()) {
                        Intent i = new Intent(LockActivity.this, LockActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    }
                }
            }, 300L);
        }
    }
}
