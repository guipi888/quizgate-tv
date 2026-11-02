package com.quizgate.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.HashSet;
import java.util.Set;

/** 家长设置页：时长、题数、题型、题库、PIN、权限引导。 */
public class MainActivity extends Activity implements View.OnClickListener {

    private PrefManager prefs;
    private final Handler handler = new Handler();
    private Runnable statusTicker;

    private TextView tvStatus;
    private TextView tvBankInfo;
    private ToggleButton[] minuteButtons = new ToggleButton[6];
    private ToggleButton[] streakButtons = new ToggleButton[3];
    private ToggleButton tgPinyin;
    private ToggleButton tgMath;
    private ToggleButton tgWord;

    private static final int[] MINUTE_VALUES = {15, 30, 45, 60, 90, 120};
    private static final int[] STREAK_VALUES = {3, 5, 10};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefManager(this);

        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvBankInfo = (TextView) findViewById(R.id.tv_bank_info);

        minuteButtons[0] = (ToggleButton) findViewById(R.id.tg_m15);
        minuteButtons[1] = (ToggleButton) findViewById(R.id.tg_m30);
        minuteButtons[2] = (ToggleButton) findViewById(R.id.tg_m45);
        minuteButtons[3] = (ToggleButton) findViewById(R.id.tg_m60);
        minuteButtons[4] = (ToggleButton) findViewById(R.id.tg_m90);
        minuteButtons[5] = (ToggleButton) findViewById(R.id.tg_m120);

        streakButtons[0] = (ToggleButton) findViewById(R.id.tg_s3);
        streakButtons[1] = (ToggleButton) findViewById(R.id.tg_s5);
        streakButtons[2] = (ToggleButton) findViewById(R.id.tg_s10);

        tgPinyin = (ToggleButton) findViewById(R.id.tg_pinyin);
        tgMath = (ToggleButton) findViewById(R.id.tg_math);
        tgWord = (ToggleButton) findViewById(R.id.tg_word);

        findViewById(R.id.btn_rescan_bank).setOnClickListener(this);
        findViewById(R.id.btn_change_pin).setOnClickListener(this);
        findViewById(R.id.btn_overlay_perm).setOnClickListener(this);
        findViewById(R.id.btn_default_home).setOnClickListener(this);
        findViewById(R.id.btn_start_timer).setOnClickListener(this);
        findViewById(R.id.btn_lock_now).setOnClickListener(this);

        for (int i = 0; i < minuteButtons.length; i++) {
            final int minutes = MINUTE_VALUES[i];
            minuteButtons[i].setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (isChecked) {
                                prefs.setWatchMinutes(minutes);
                                refreshMinuteButtons();
                            }
                        }
                    });
        }
        for (int i = 0; i < streakButtons.length; i++) {
            final int streak = STREAK_VALUES[i];
            streakButtons[i].setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (isChecked) {
                                prefs.setRequiredStreak(streak);
                                refreshStreakButtons();
                            }
                        }
                    });
        }

        CompoundButton.OnCheckedChangeListener typeListener =
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        saveEnabledTypes();
                    }
                };
        tgPinyin.setOnCheckedChangeListener(typeListener);
        tgMath.setOnCheckedChangeListener(typeListener);
        tgWord.setOnCheckedChangeListener(typeListener);

        refreshAll();
        refreshBankInfo();
    }

    private void refreshAll() {
        refreshMinuteButtons();
        refreshStreakButtons();
        Set<String> enabled = prefs.getEnabledTypes();
        tgPinyin.setChecked(enabled.contains(PinyinChoiceType.ID));
        tgMath.setChecked(enabled.contains(MathCalcType.ID));
        tgWord.setChecked(enabled.contains(WordChoiceType.ID));
    }

    private void refreshMinuteButtons() {
        int cur = prefs.getWatchMinutes();
        for (int i = 0; i < minuteButtons.length; i++) {
            minuteButtons[i].setChecked(MINUTE_VALUES[i] == cur);
        }
    }

    private void refreshStreakButtons() {
        int cur = prefs.getRequiredStreak();
        for (int i = 0; i < streakButtons.length; i++) {
            streakButtons[i].setChecked(STREAK_VALUES[i] == cur);
        }
    }

    private void saveEnabledTypes() {
        Set<String> enabled = new HashSet<>();
        if (tgPinyin.isChecked()) {
            enabled.add(PinyinChoiceType.ID);
        }
        if (tgMath.isChecked()) {
            enabled.add(MathCalcType.ID);
        }
        if (tgWord.isChecked()) {
            enabled.add(WordChoiceType.ID);
        }
        if (enabled.isEmpty()) {
            enabled.add(MathCalcType.ID);
            tgMath.setChecked(true);
            Toast.makeText(this, "至少保留一种题型", Toast.LENGTH_SHORT).show();
        }
        prefs.setEnabledTypes(enabled);
    }

    private void refreshBankInfo() {
        BankLoader.LoadResult r = BankLoader.loadAll(this);
        String text = "已加载 " + r.banks.size() + " 个题库 / " + r.totalQuestions() + " 道题";
        if (r.failedFiles > 0) {
            text += "（" + r.failedFiles + " 个文件解析失败，请检查 JSON 格式）";
        }
        tvBankInfo.setText(text);
    }

    private void refreshStatus() {
        String text;
        if (prefs.isLockActive()) {
            text = "状态：已锁定，请答题解锁";
        } else if (prefs.isTimerRunning()) {
            long seconds = prefs.remainingMillis() / 1000L;
            text = String.format("状态：计时中，剩余 %02d:%02d", seconds / 60, seconds % 60);
        } else {
            text = "状态：未启动";
        }
        tvStatus.setText(text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        refreshBankInfo();
        statusTicker = new Runnable() {
            @Override
            public void run() {
                refreshStatus();
                handler.postDelayed(this, 1000L);
            }
        };
        handler.post(statusTicker);
    }

    @Override
    protected void onPause() {
        if (statusTicker != null) {
            handler.removeCallbacks(statusTicker);
        }
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_start_timer) {
            if (prefs.isLockActive()) {
                Toast.makeText(this, "当前已锁定，答题解锁后才会重新计时", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.startWatchTimer();
            startService(new Intent(this, LockService.class));
            Toast.makeText(this, "计时已开始：" + prefs.getWatchMinutes() + " 分钟", Toast.LENGTH_SHORT).show();
            refreshStatus();
        } else if (id == R.id.btn_lock_now) {
            prefs.enterLock();
            startActivity(new Intent(this, LockActivity.class));
        } else if (id == R.id.btn_rescan_bank) {
            refreshBankInfo();
            Toast.makeText(this, "题库已重新扫描", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.btn_change_pin) {
            changePinFlow();
        } else if (id == R.id.btn_overlay_perm) {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(i);
                    Toast.makeText(this, "请允许「显示在其他应用上层」", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "请到系统设置中允许悬浮窗权限", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.btn_default_home) {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                Toast.makeText(this, "请选择「答题门禁」为默认桌面（可选增强）", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "当前系统不支持自动跳转，请在设置-默认应用中手动选择", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** 修改 PIN：验旧 -> 输新 -> 确认新。 */
    private void changePinFlow() {
        PinPadDialog.createVerify(this, getString(R.string.pin_parent_verify),
                new PinPadDialog.Callback() {
                    @Override
                    public void onSuccess(String pin) {
                        askNewPinOnce();
                    }
                }).show();
    }

    private void askNewPinOnce() {
        PinPadDialog.createInput(this, getString(R.string.pin_enter_new),
                new PinPadDialog.Callback() {
                    @Override
                    public void onSuccess(final String pin) {
                        PinPadDialog.createInput(MainActivity.this, getString(R.string.pin_confirm_new),
                                new PinPadDialog.Callback() {
                                    @Override
                                    public void onSuccess(String pin2) {
                                        if (pin.equals(pin2)) {
                                            prefs.setParentPin(pin);
                                            Toast.makeText(MainActivity.this, "PIN 已修改", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(MainActivity.this, "两次输入不一致，请重试", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }).show();
                    }
                }).show();
    }
}
