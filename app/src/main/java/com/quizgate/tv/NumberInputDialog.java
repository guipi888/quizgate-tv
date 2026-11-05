package com.quizgate.tv;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

/**
 * 遥控器友好的纯数字输入对话框，用于自定义观看时长等数值配置。
 * 数字通过按钮 id 映射取得，不依赖按钮文案。
 */
public class NumberInputDialog extends Dialog implements View.OnClickListener {

    public interface Callback {
        void onConfirm(int value);
    }

    /** 下标即数字值。 */
    private static final int[] DIGIT_IDS = {
            R.id.btn_n0, R.id.btn_n1, R.id.btn_n2, R.id.btn_n3, R.id.btn_n4,
            R.id.btn_n5, R.id.btn_n6, R.id.btn_n7, R.id.btn_n8, R.id.btn_n9,
    };

    private final String title;
    private final int min;
    private final int max;
    private final Callback callback;
    private final int maxDigits;
    private final StringBuilder input = new StringBuilder();

    private TextView tvValue;

    public static NumberInputDialog create(Context ctx, String title, int min, int max, Callback cb) {
        return new NumberInputDialog(ctx, title, min, max, cb);
    }

    private NumberInputDialog(Context ctx, String title, int min, int max, Callback cb) {
        super(ctx);
        this.title = title;
        this.min = min;
        this.max = max;
        this.callback = cb;
        this.maxDigits = String.valueOf(max).length();
        setCancelable(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_number);

        ((TextView) findViewById(R.id.tv_num_title)).setText(title);
        ((TextView) findViewById(R.id.tv_num_hint)).setText("范围 " + min + " - " + max);
        tvValue = (TextView) findViewById(R.id.tv_num_value);

        int[] ids = {
                R.id.btn_n1, R.id.btn_n2, R.id.btn_n3,
                R.id.btn_n4, R.id.btn_n5, R.id.btn_n6,
                R.id.btn_n7, R.id.btn_n8, R.id.btn_n9,
                R.id.btn_ndel, R.id.btn_n0, R.id.btn_nok,
        };
        for (int id : ids) {
            findViewById(id).setOnClickListener(this);
        }
        refresh();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_ndel) {
            if (input.length() > 0) {
                input.deleteCharAt(input.length() - 1);
            }
            refresh();
            return;
        }
        if (id == R.id.btn_nok) {
            submit();
            return;
        }
        int digit = digitForId(id);
        if (digit < 0 || input.length() >= maxDigits) {
            return;
        }
        if (input.length() == 0 && digit == 0) {
            return;
        }
        int next;
        try {
            next = Integer.parseInt(input.toString() + digit);
        } catch (NumberFormatException e) {
            return;
        }
        if (next > max) {
            shake();
            return;
        }
        input.append((char) ('0' + digit));
        refresh();
    }

    private static int digitForId(int id) {
        for (int i = 0; i < DIGIT_IDS.length; i++) {
            if (DIGIT_IDS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private void submit() {
        if (input.length() == 0) {
            shake();
            return;
        }
        int value;
        try {
            value = Integer.parseInt(input.toString());
        } catch (NumberFormatException e) {
            shake();
            return;
        }
        if (value < min || value > max) {
            shake();
            return;
        }
        dismiss();
        if (callback != null) {
            callback.onConfirm(value);
        }
    }

    private void refresh() {
        tvValue.setText(input.length() == 0 ? "—" : input.toString());
    }

    private void shake() {
        tvValue.startAnimation(
                AnimationUtils.loadAnimation(getContext(), R.anim.shake));
    }
}
