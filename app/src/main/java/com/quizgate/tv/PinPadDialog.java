package com.quizgate.tv;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * 遥控器友好的 4 位 PIN 键盘对话框。
 * verify 模式：校验家长 PIN；input 模式：收集新 PIN。
 */
public class PinPadDialog extends Dialog implements View.OnClickListener {

    public interface Callback {
        void onSuccess(String pin);
    }

    private static final int MAX_LEN = 4;

    private final boolean verifyMode;
    private final Callback callback;
    private final StringBuilder input = new StringBuilder();

    private TextView tvTitle;
    private TextView tvDots;

    public static PinPadDialog createVerify(Context ctx, String title, Callback cb) {
        return new PinPadDialog(ctx, title, true, cb);
    }

    public static PinPadDialog createInput(Context ctx, String title, Callback cb) {
        return new PinPadDialog(ctx, title, false, cb);
    }

    private PinPadDialog(Context ctx, String title, boolean verifyMode, Callback cb) {
        super(ctx);
        this.verifyMode = verifyMode;
        this.callback = cb;
        setCancelable(false);
        setTitle(title);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_pin);

        tvTitle = (TextView) findViewById(R.id.tv_pin_title);
        tvDots = (TextView) findViewById(R.id.tv_pin_dots);
        tvTitle.setText(getTitleForDialog());

        int[] ids = {
                R.id.btn_d1, R.id.btn_d2, R.id.btn_d3,
                R.id.btn_d4, R.id.btn_d5, R.id.btn_d6,
                R.id.btn_d7, R.id.btn_d8, R.id.btn_d9,
                R.id.btn_del, R.id.btn_d0, R.id.btn_ok,
        };
        for (int id : ids) {
            findViewById(id).setOnClickListener(this);
        }
        refreshDots();
    }

    private String getTitleForDialog() {
        CharSequence t = getContext().getString(R.string.pin_parent_verify);
        return verifyMode ? t.toString() : getContext().getString(R.string.pin_enter_new);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_del) {
            if (input.length() > 0) {
                input.deleteCharAt(input.length() - 1);
            }
            refreshDots();
            return;
        }
        if (id == R.id.btn_ok) {
            submit();
            return;
        }
        if (input.length() >= MAX_LEN) {
            return;
        }
        Button b = (Button) v;
        input.append(b.getText().toString());
        refreshDots();
        if (input.length() == MAX_LEN) {
            submit();
        }
    }

    private void submit() {
        if (input.length() < MAX_LEN) {
            shakeDots();
            return;
        }
        if (verifyMode) {
            PrefManager prefs = new PrefManager(getContext());
            if (!prefs.getParentPin().equals(input.toString())) {
                shakeDots();
                input.setLength(0);
                refreshDots();
                return;
            }
        }
        String pin = input.toString();
        dismiss();
        if (callback != null) {
            callback.onSuccess(pin);
        }
    }

    private void refreshDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_LEN; i++) {
            sb.append(i < input.length() ? "●" : "○");
            if (i < MAX_LEN - 1) {
                sb.append(' ');
            }
        }
        tvDots.setText(sb.toString());
    }

    private void shakeDots() {
        android.view.animation.Animation anim =
                android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.shake);
        tvDots.startAnimation(anim);
    }
}
