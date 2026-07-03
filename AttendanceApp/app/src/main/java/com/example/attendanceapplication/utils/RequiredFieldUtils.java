package com.example.attendanceapplication.utils;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.attendanceapplication.R;
import com.google.android.material.textfield.TextInputLayout;

/** Hiển thị dấu sao màu đỏ cho nhãn của các trường bắt buộc. */
public final class RequiredFieldUtils {

    private RequiredFieldUtils() {}

    public static void markRequired(Context context, TextInputLayout inputLayout) {
        inputLayout.setHint(withRedAsterisk(context, inputLayout.getHint()));
    }

    public static void markRequired(Context context, TextView label) {
        label.setText(withRedAsterisk(context, label.getText()));
    }

    private static CharSequence withRedAsterisk(Context context, CharSequence label) {
        String base = label == null ? "" : label.toString().trim();
        if (base.endsWith("*")) {
            base = base.substring(0, base.length() - 1).trim();
        }
        SpannableString requiredLabel = new SpannableString(base + " *");
        requiredLabel.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(context, R.color.error_red)),
                requiredLabel.length() - 1,
                requiredLabel.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return requiredLabel;
    }
}
